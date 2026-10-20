import { CanActivate, ExecutionContext, ForbiddenException, Injectable, OnModuleDestroy, OnModuleInit, SetMetadata, UnauthorizedException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { PrismaClient } from '@erp/database';
import Redis from 'ioredis';
import type { Request } from 'express';
import { createHash, randomBytes } from 'node:crypto';

export const Public = () => SetMetadata('public', true);
export const RequirePermission = (code: string) => SetMetadata('permission', code);
export const RequireStorePermission = (code: string, parameter = 'storeId') => SetMetadata('storePermission', { code, parameter });
export const RequireScopedPermission = (code: string) => SetMetadata('scopedPermission', code);
export const RequireWarehouseScopedPermission = (code: string) => SetMetadata('warehouseScopedPermission', code);
export type SessionData = { userId: string; version: number; state: 'active' | 'mfa-required' | 'mfa-enroll'; createdAt: number };
export type AppRequest = Request & { session?: SessionData; companyId?: string; rawBody?: Buffer; allowedStoreIds?: string[] | null; allowedWarehouseIds?: string[] | null };

@Injectable()
export class DatabaseService extends PrismaClient implements OnModuleInit, OnModuleDestroy {
  async onModuleInit() { await this.$connect(); }
  async onModuleDestroy() { await this.$disconnect(); }
}

@Injectable()
export class RedisService extends Redis implements OnModuleDestroy {
  constructor() { super(process.env.REDIS_URL || 'redis://localhost:6379', { maxRetriesPerRequest: 1 }); }
  async onModuleDestroy() { await this.quit(); }
}

@Injectable()
export class SessionService {
  constructor(private readonly redis: RedisService) {}
  private key(token: string) { return `session:${createHash('sha256').update(token).digest('hex')}`; }
  async create(data: SessionData) {
    const token = randomBytes(32).toString('base64url');
    await this.redis.set(this.key(token), JSON.stringify(data), 'EX', 60 * 60 * 12);
    return token;
  }
  async read(token?: string): Promise<SessionData | null> {
    if (!token) return null;
    const raw = await this.redis.get(this.key(token));
    return raw ? JSON.parse(raw) as SessionData : null;
  }
  async update(token: string, data: SessionData) { await this.redis.set(this.key(token), JSON.stringify(data), 'EX', 60 * 60 * 12); }
  async destroy(token?: string) { if (token) await this.redis.del(this.key(token)); }
}

@Injectable()
export class AccessGuard implements CanActivate {
  constructor(private readonly reflector: Reflector, private readonly sessions: SessionService, private readonly db: DatabaseService) {}
  async canActivate(context: ExecutionContext): Promise<boolean> {
    if (this.reflector.getAllAndOverride<boolean>('public', [context.getHandler(), context.getClass()])) return true;
    const request = context.switchToHttp().getRequest<AppRequest>();
    const session = await this.sessions.read(request.cookies?.erp_session);
    if (!session || session.state !== 'active') throw new UnauthorizedException('Phiên đăng nhập không hợp lệ');
    const user = await this.db.user.findUnique({ where: { id: session.userId }, select: { status: true, sessionVersion: true } });
    if (!user || user.status !== 'ACTIVE' || user.sessionVersion !== session.version) throw new UnauthorizedException('Phiên đăng nhập không hợp lệ');
    request.session = session;
    const storePermission = this.reflector.getAllAndOverride<{ code: string; parameter: string }>('storePermission', [context.getHandler(), context.getClass()]);
    const scopedPermission = this.reflector.getAllAndOverride<string>('scopedPermission', [context.getHandler(), context.getClass()]);
    const warehouseScopedPermission = this.reflector.getAllAndOverride<string>('warehouseScopedPermission', [context.getHandler(), context.getClass()]);
    const code = storePermission?.code || scopedPermission || warehouseScopedPermission || this.reflector.getAllAndOverride<string>('permission', [context.getHandler(), context.getClass()]);
    if (!code) return true;
    const companyId = request.headers['x-company-id'];
    if (typeof companyId !== 'string' || !/^[a-f0-9-]{36}$/i.test(companyId)) throw new ForbiddenException('Thiếu phạm vi công ty');
    const membership = await this.db.companyMembership.findUnique({ where: { companyId_userId: { companyId, userId: session.userId } } });
    if (!membership) throw new ForbiddenException('Không thuộc công ty');
    const parameterValue = storePermission ? request.params[storePermission.parameter] : undefined;
    const storeId = typeof parameterValue === 'string' ? parameterValue : undefined;
    if (storePermission && (!storeId || !/^[a-f0-9-]{36}$/i.test(storeId))) throw new ForbiddenException('Phạm vi store không hợp lệ');
    const assignments = await this.db.userRoleAssignment.findMany({
      where: { userId: session.userId, companyId,
        ...(warehouseScopedPermission ? { storeId: null } : { warehouseId: null }),
        ...(storeId ? { OR: [{ storeId: null }, { storeId }] } : scopedPermission ? {} : { storeId: null }), role: { permissions: { some: { permissionCode: code } } } },
      select: { id: true, storeId: true, warehouseId: true },
    });
    if (!assignments.length) throw new ForbiddenException('Không có quyền thực hiện');
    request.companyId = companyId;
    if (scopedPermission) request.allowedStoreIds = assignments.some(item => item.storeId === null) ? null : [...new Set(assignments.flatMap(item => item.storeId ? [item.storeId] : []))];
    if (warehouseScopedPermission) request.allowedWarehouseIds = assignments.some(item => item.warehouseId === null) ? null : [...new Set(assignments.flatMap(item => item.warehouseId ? [item.warehouseId] : []))];
    return true;
  }
}

export function actor(request: AppRequest) {
  if (!request.session || !request.companyId) throw new UnauthorizedException();
  return { userId: request.session.userId, companyId: request.companyId };
}
