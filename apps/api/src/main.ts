import 'reflect-metadata';
import { config as loadEnv } from 'dotenv';
import { resolve } from 'node:path';
import { Module, Controller, Get, ValidationPipe } from '@nestjs/common';
import { APP_GUARD, NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import cookieParser from 'cookie-parser';
import helmet from 'helmet';
import { AccessGuard, DatabaseService, Public, RedisService, SessionService } from './core';
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
  if (!process.env.DATABASE_URL || !process.env.REDIS_URL || !process.env.APP_ORIGIN) throw new Error('DATABASE_URL, REDIS_URL and APP_ORIGIN are required');
  const app = await NestFactory.create(AppModule, { logger: ['error', 'warn', 'log'], rawBody: true });
  app.setGlobalPrefix('api/v1');
  app.use(helmet());
  app.use(cookieParser());
  app.enableCors({ origin: process.env.APP_ORIGIN, credentials: true });
  app.use((req: { method: string; headers: Record<string, string | undefined> }, res: { status: (n: number) => { json: (body: object) => void } }, next: () => void) => {
    if (!['GET', 'HEAD', 'OPTIONS'].includes(req.method)) {
      const origin = req.headers.origin;
      if (origin && origin !== process.env.APP_ORIGIN) return res.status(403).json({ message: 'Origin không hợp lệ' });
      if (!origin && req.headers.cookie) return res.status(403).json({ message: 'Thiếu Origin' });
    }
    next();
  });
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
  const config = new DocumentBuilder().setTitle('Company ERP API').setVersion('1.0').addCookieAuth('erp_session').build();
  SwaggerModule.setup('api/docs', app, SwaggerModule.createDocument(app, config));
  await app.listen(Number(process.env.API_PORT || 4000), '0.0.0.0');
}

bootstrap();
