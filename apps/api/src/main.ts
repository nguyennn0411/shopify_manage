import 'reflect-metadata';
import { config as loadEnv } from 'dotenv';
import { resolve } from 'node:path';
import { Module, Controller, Get, Logger, ValidationPipe } from '@nestjs/common';
import { APP_GUARD, NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import cookieParser from 'cookie-parser';
import helmet from 'helmet';
import type { NextFunction, Request, Response } from 'express';
import { randomUUID } from 'node:crypto';
import { AccessGuard, DatabaseService, Public, RedisService, SessionService } from './core';
import { verifyCsrfToken } from './security';
import { AuthController, AuthService } from './auth';
import { AdminService, AuditController, CompanyController, RolesController, StoresController, UsersController } from './admin';
import { ShopifyCallbackController, ShopifyService, StoreShopifyController } from './shopify';
import { CommerceController, CommerceService } from './commerce';
import { FinanceController, FinanceService } from './finance';
import { OperationsController, OperationsService } from './operations';
import { CompanyManagementController, CompanyManagementService } from './company-management';
import { AnalyticsController, AnalyticsService } from './analytics';
import { LifecycleController, LifecycleService } from './lifecycle';

@Controller('health')
class HealthController {
  constructor(private readonly db: DatabaseService, private readonly redis: RedisService) {}
  @Public() @Get('live') live() { return { status: 'ok' }; }
  @Public() @Get('ready') ready() { return this.status(); }
  @Public() @Get() async status() {
    await Promise.all([this.db.$queryRaw`SELECT 1`, this.redis.ping()]);
    return { status: 'ok' };
  }
}

@Module({
  controllers: [HealthController, AuthController, CompanyController, StoresController, UsersController, RolesController, AuditController, StoreShopifyController, ShopifyCallbackController, CommerceController, FinanceController, OperationsController, CompanyManagementController, AnalyticsController, LifecycleController],
  providers: [DatabaseService, RedisService, SessionService, AuthService, AdminService, ShopifyService, CommerceService, FinanceService, OperationsService, CompanyManagementService, AnalyticsService, LifecycleService, { provide: APP_GUARD, useClass: AccessGuard }],
})
class AppModule {}

async function bootstrap() {
  loadEnv({ path: resolve(__dirname, '../../../.env') });
  if (!/^[a-fA-F0-9]{64}$/.test(process.env.MFA_ENCRYPTION_KEY || '')) throw new Error('MFA_ENCRYPTION_KEY must be 64 hex characters');
  if (!/^[a-fA-F0-9]{64}$/.test(process.env.INTEGRATION_ENCRYPTION_KEY || '')) throw new Error('INTEGRATION_ENCRYPTION_KEY must be 64 hex characters');
  if (!/^[a-fA-F0-9]{64}$/.test(process.env.CSRF_SECRET || '')) throw new Error('CSRF_SECRET must be 64 hex characters');
  if (!process.env.DATABASE_URL || !process.env.REDIS_URL || !process.env.APP_ORIGIN) throw new Error('DATABASE_URL, REDIS_URL and APP_ORIGIN are required');
  const app = await NestFactory.create(AppModule, { logger: ['error', 'warn', 'log'], rawBody: true });
  app.getHttpAdapter().getInstance().set('trust proxy', 1);
  app.setGlobalPrefix('api/v1');
  app.use(helmet());
  app.use(cookieParser());
  app.enableCors({ origin: process.env.APP_ORIGIN, credentials: true });
  const redis = app.get(RedisService);
  const httpLogger = new Logger('HTTP');
  app.use(async (req: Request, res: Response, next: NextFunction) => {
    const startedAt = Date.now();
    const requestId = String(req.headers['x-request-id'] || randomUUID()).slice(0, 128);
    res.setHeader('x-request-id', requestId);
    res.on('finish', () => httpLogger.log(JSON.stringify({ requestId, method: req.method, path: req.path, status: res.statusCode, durationMs: Date.now() - startedAt })));
    const changing = !['GET', 'HEAD', 'OPTIONS'].includes(req.method);
    const windowSeconds = 300;
    const limit = changing ? 120 : 300;
    const rateKey = `rate:${req.ip || 'unknown'}:${changing ? 'write' : 'read'}:${Math.floor(Date.now() / (windowSeconds * 1000))}`;
    const used = await redis.incr(rateKey);
    if (used === 1) await redis.expire(rateKey, windowSeconds + 5);
    res.setHeader('x-ratelimit-limit', String(limit));
    res.setHeader('x-ratelimit-remaining', String(Math.max(0, limit - used)));
    if (used > limit) return res.status(429).json({ message: 'Quá nhiều yêu cầu, vui lòng thử lại sau', requestId });

    if (!['GET', 'HEAD', 'OPTIONS'].includes(req.method)) {
      const origin = req.headers.origin;
      if (origin && origin !== process.env.APP_ORIGIN) return res.status(403).json({ message: 'Origin không hợp lệ', requestId });
      if (!origin && req.headers.cookie) return res.status(403).json({ message: 'Thiếu Origin', requestId });
      const sessionToken = req.cookies?.erp_session;
      const isLogin = req.path.endsWith('/auth/login');
      if (sessionToken && !isLogin) {
        const cookieToken = req.cookies?.erp_csrf;
        const headerToken = req.headers['x-csrf-token'];
        if (typeof cookieToken !== 'string' || typeof headerToken !== 'string' || cookieToken !== headerToken || !verifyCsrfToken(sessionToken, headerToken)) {
          return res.status(403).json({ message: 'Yêu cầu bảo mật CSRF không hợp lệ', requestId });
        }
      }
    }
    next();
  });
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
  const config = new DocumentBuilder().setTitle('Company ERP API').setVersion('1.0').addCookieAuth('erp_session').build();
  SwaggerModule.setup('api/docs', app, SwaggerModule.createDocument(app, config));
  await app.listen(Number(process.env.PORT || process.env.API_PORT || 4000), '0.0.0.0');
}

bootstrap();
