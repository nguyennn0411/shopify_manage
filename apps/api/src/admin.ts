import { BadRequestException, Body, Controller, Delete, ForbiddenException, Get, Injectable, Param, Patch, Post, Query, Req } from '@nestjs/common';
import { IsArray, IsEmail, IsEnum, IsOptional, IsString, IsUUID, Length, Matches, MaxLength } from 'class-validator';
import { StoreStatus, UserStatus } from '@erp/database';
import { actor, AppRequest, DatabaseService, RequirePermission } from './core';
import { hashPassword } from './security';

class UpdateCompanyDto {
  @IsOptional() @IsString() @Length(2, 120) name?: string;
  @IsOptional() @Matches(/^[A-Z]{3}$/) baseCurrency?: string;
  @IsOptional() @IsString() @MaxLength(64) timezone?: string;
}
class CreateStoreDto {
  @IsString() @Length(2, 120) name!: string;
  @Matches(/^[a-z0-9][a-z0-9-]*\.myshopify\.com$/) shopifyDomain!: string;
  @Matches(/^[A-Z]{3}$/) currency!: string;
}
class UpdateStoreDto {
  @IsOptional() @IsString() @Length(2, 120) name?: string;
  @IsOptional() @Matches(/^[A-Z]{3}$/) currency?: string;
  @IsOptional() @IsEnum(StoreStatus) status?: StoreStatus;
}
class CreateUserDto {
  @IsEmail() email!: string;
  @IsString() @Length(2, 120) name!: string;
  @IsString() @Length(12, 128) password!: string;
  @IsUUID() roleId!: string;
}
class UpdateUserDto {
  @IsOptional() @IsString() @Length(2, 120) name?: string;
  @IsOptional() @IsEnum(UserStatus) status?: UserStatus;
}
class AssignRoleDto {
  @IsUUID() roleId!: string;
  @IsOptional() @IsUUID() storeId?: string;
  @IsOptional() @IsUUID() warehouseId?: string;
}
class CreateRoleDto {
  @IsString() @Length(2, 80) name!: string;
  @IsOptional() @IsString() @MaxLength(250) description?: string;
  @IsArray() @IsString({ each: true }) permissions!: string[];
}
class UpdateRoleDto {
  @IsOptional() @IsString() @Length(2, 80) name?: string;
  @IsOptional() @IsString() @MaxLength(250) description?: string;
  @IsOptional() @IsArray() @IsString({ each: true }) permissions?: string[];
}

@Injectable()
export class AdminService {
  constructor(private readonly db: DatabaseService) {}
  private async assertGrantable(request: AppRequest, codes: string[]) {
    const { companyId, userId } = actor(request);
    const grants = await this.db.userRoleAssignment.findMany({ where: { companyId, userId, storeId: null, warehouseId: null },
      include: { role: { include: { permissions: true } } } });
    const allowed = new Set(grants.flatMap(x => x.role.permissions.map(p => p.permissionCode)));
    if (codes.some(code => !allowed.has(code))) throw new ForbiddenException('Không thể cấp quyền cao hơn quyền hiện có');
  }
  async company(request: AppRequest) { return this.db.company.findUniqueOrThrow({ where: { id: actor(request).companyId } }); }
  async updateCompany(request: AppRequest, dto: UpdateCompanyDto) {
    const { companyId, userId } = actor(request);
    return this.db.$transaction(async tx => {
      const company = await tx.company.update({ where: { id: companyId }, data: dto });
      await tx.auditLog.create({ data: { companyId, actorId: userId, action: 'company.update', resourceType: 'company', resourceId: companyId } });
      return company;
    });
  }
  async stores(request: AppRequest) {
    return this.db.store.findMany({ where: { companyId: actor(request).companyId },
      include: { connection: { select: { scopes: true, installedAt: true, accessTokenExpiresAt: true } } }, orderBy: { createdAt: 'desc' } });
  }
  async createStore(request: AppRequest, dto: CreateStoreDto) {
    const { companyId, userId } = actor(request);
    return this.db.$transaction(async tx => {
      const store = await tx.store.create({ data: { companyId, name: dto.name.trim(), shopifyDomain: dto.shopifyDomain.toLowerCase(), currency: dto.currency, status: 'DRAFT' } });
      await tx.auditLog.create({ data: { companyId, actorId: userId, action: 'store.create', resourceType: 'store', resourceId: store.id } });
      return store;
    });
  }
  async updateStore(request: AppRequest, id: string, dto: UpdateStoreDto) {
    const { companyId, userId } = actor(request);
    const store = await this.db.store.findFirst({ where: { id, companyId } });
    if (!store) throw new ForbiddenException();
    if (dto.status === 'CONNECTED') throw new BadRequestException('Kết nối Shopify chỉ được tạo qua luồng tích hợp');
    return this.db.$transaction(async tx => {
      const updated = await tx.store.update({ where: { id }, data: dto });
      await tx.auditLog.create({ data: { companyId, actorId: userId, action: 'store.update', resourceType: 'store', resourceId: id } });
      return updated;
    });
  }
  async users(request: AppRequest, search?: string) {
    const companyId = actor(request).companyId;
    return this.db.user.findMany({
      where: { memberships: { some: { companyId } }, ...(search ? { OR: [{ email: { contains: search, mode: 'insensitive' } }, { name: { contains: search, mode: 'insensitive' } }] } : {}) },
      select: { id: true, email: true, name: true, status: true, mfaEnabled: true, lastLoginAt: true, createdAt: true,
        assignments: { where: { companyId }, select: { id: true, storeId: true, warehouseId: true, role: { select: { id: true, name: true } } } } },
      orderBy: { createdAt: 'desc' }, take: 100,
    });
  }
  async createUser(request: AppRequest, dto: CreateUserDto) {
    const { companyId, userId } = actor(request);
    const role = await this.db.role.findFirst({ where: { id: dto.roleId, companyId }, include: { permissions: true } });
    if (!role) throw new BadRequestException('Vai trò không thuộc công ty');
    await this.assertGrantable(request, role.permissions.map(x => x.permissionCode));
    const email = dto.email.trim().toLowerCase();
    if (await this.db.user.findUnique({ where: { email } })) throw new BadRequestException('Email đã tồn tại');
    return this.db.$transaction(async tx => {
      const created = await tx.user.create({ data: { email, name: dto.name.trim(), passwordHash: hashPassword(dto.password),
        memberships: { create: { companyId } }, assignments: { create: { companyId, roleId: dto.roleId } } } });
      await tx.auditLog.create({ data: { companyId, actorId: userId, action: 'user.create', resourceType: 'user', resourceId: created.id } });
      return { id: created.id, email: created.email, name: created.name, status: created.status };
    });
  }
  async updateUser(request: AppRequest, id: string, dto: UpdateUserDto) {
    const { companyId, userId } = actor(request);
    if (id === userId && dto.status === 'DISABLED') throw new BadRequestException('Không thể tự khóa tài khoản');
    if (!await this.db.companyMembership.findUnique({ where: { companyId_userId: { companyId, userId: id } } })) throw new ForbiddenException();
    return this.db.$transaction(async tx => {
      const updated = await tx.user.update({ where: { id }, data: dto, select: { id: true, email: true, name: true, status: true } });
      await tx.auditLog.create({ data: { companyId, actorId: userId, action: 'user.update', resourceType: 'user', resourceId: id } });
      return updated;
    });
  }
  async assignRole(request: AppRequest, targetUserId: string, dto: AssignRoleDto) {
    const { companyId, userId } = actor(request);
    if (dto.storeId && dto.warehouseId) throw new BadRequestException('Chỉ chọn một loại phạm vi');
    const [membership, role, store, warehouse] = await Promise.all([
      this.db.companyMembership.findUnique({ where: { companyId_userId: { companyId, userId: targetUserId } } }),
      this.db.role.findFirst({ where: { id: dto.roleId, companyId }, include: { permissions: true } }),
      dto.storeId ? this.db.store.findFirst({ where: { id: dto.storeId, companyId } }) : Promise.resolve(true),
      dto.warehouseId ? this.db.warehouse.findFirst({ where: { id: dto.warehouseId, companyId } }) : Promise.resolve(true),
    ]);
    if (!membership || !role || !store || !warehouse) throw new BadRequestException('Người dùng, vai trò hoặc phạm vi không hợp lệ');
    await this.assertGrantable(request, role.permissions.map(x => x.permissionCode));
    return this.db.$transaction(async tx => {
      const existing = await tx.userRoleAssignment.findFirst({ where: { userId: targetUserId, companyId, roleId: dto.roleId, storeId: dto.storeId || null, warehouseId: dto.warehouseId || null } });
      if (existing) throw new BadRequestException('Vai trò đã được gán');
      const assignment = await tx.userRoleAssignment.create({ data: { userId: targetUserId, companyId, roleId: dto.roleId, storeId: dto.storeId, warehouseId: dto.warehouseId } });
      await tx.auditLog.create({ data: { companyId, actorId: userId, action: 'role.assign', resourceType: 'user', resourceId: targetUserId, metadata: { roleId: dto.roleId, storeId: dto.storeId || null, warehouseId: dto.warehouseId || null } } });
      return assignment;
    });
  }
  async revokeRole(request: AppRequest, targetUserId: string, assignmentId: string) {
    const { companyId, userId } = actor(request);
    if (targetUserId === userId) throw new BadRequestException('Không thể tự thu hồi vai trò');
    const assignment = await this.db.userRoleAssignment.findFirst({ where: { id: assignmentId, userId: targetUserId, companyId }, include: { role: { include: { permissions: true } } } });
    if (!assignment) throw new BadRequestException('Vai trò được gán không tồn tại');
    await this.assertGrantable(request, assignment.role.permissions.map(x => x.permissionCode));
    return this.db.$transaction(async tx => {
      await tx.userRoleAssignment.delete({ where: { id: assignmentId } });
      await tx.auditLog.create({ data: { companyId, actorId: userId, action: 'role.revoke', resourceType: 'user', resourceId: targetUserId, metadata: { roleId: assignment.roleId, storeId: assignment.storeId, warehouseId: assignment.warehouseId } } });
      return { revoked: true };
    });
  }
  async roles(request: AppRequest) {
    return this.db.role.findMany({ where: { companyId: actor(request).companyId }, include: { permissions: { select: { permissionCode: true } }, _count: { select: { assignments: true } } }, orderBy: { name: 'asc' } });
  }
  permissions() { return this.db.permission.findMany({ orderBy: { code: 'asc' } }); }
  private async validatePermissions(codes: string[]) {
    const unique = [...new Set(codes)];
    if (await this.db.permission.count({ where: { code: { in: unique } } }) !== unique.length) throw new BadRequestException('Có quyền không hợp lệ');
    return unique;
  }
  async createRole(request: AppRequest, dto: CreateRoleDto) {
    const { companyId, userId } = actor(request);
    const codes = await this.validatePermissions(dto.permissions);
    await this.assertGrantable(request, codes);
    return this.db.$transaction(async tx => {
      const role = await tx.role.create({ data: { companyId, name: dto.name.trim(), description: dto.description,
        permissions: { create: codes.map(permissionCode => ({ permissionCode })) } } });
      await tx.auditLog.create({ data: { companyId, actorId: userId, action: 'role.create', resourceType: 'role', resourceId: role.id } });
      return role;
    });
  }
  async updateRole(request: AppRequest, id: string, dto: UpdateRoleDto) {
    const { companyId, userId } = actor(request);
    const role = await this.db.role.findFirst({ where: { id, companyId } });
    if (!role) throw new ForbiddenException();
    if (role.isSystem) throw new BadRequestException('Không chỉnh sửa vai trò hệ thống');
    const codes = dto.permissions ? await this.validatePermissions(dto.permissions) : undefined;
    if (codes) await this.assertGrantable(request, codes);
    return this.db.$transaction(async tx => {
      const updated = await tx.role.update({ where: { id }, data: { name: dto.name?.trim(), description: dto.description,
        ...(codes ? { permissions: { deleteMany: {}, create: codes.map(permissionCode => ({ permissionCode })) } } : {}) } });
      await tx.auditLog.create({ data: { companyId, actorId: userId, action: 'role.update', resourceType: 'role', resourceId: id } });
      return updated;
    });
  }
  async audit(request: AppRequest, cursor?: string) {
    return this.db.auditLog.findMany({ where: { companyId: actor(request).companyId }, take: 50,
      ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}), orderBy: [{ createdAt: 'desc' }, { id: 'desc' }],
      include: { actor: { select: { name: true, email: true } } } });
  }
}

@Controller('company')
export class CompanyController {
  constructor(private readonly admin: AdminService) {}
  @Get() @RequirePermission('company.read') get(@Req() req: AppRequest) { return this.admin.company(req); }
  @Patch() @RequirePermission('company.write') update(@Req() req: AppRequest, @Body() dto: UpdateCompanyDto) { return this.admin.updateCompany(req, dto); }
}

@Controller('stores')
export class StoresController {
  constructor(private readonly admin: AdminService) {}
  @Get() @RequirePermission('stores.read') list(@Req() req: AppRequest) { return this.admin.stores(req); }
  @Post() @RequirePermission('stores.write') create(@Req() req: AppRequest, @Body() dto: CreateStoreDto) { return this.admin.createStore(req, dto); }
  @Patch(':id') @RequirePermission('stores.write') update(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: UpdateStoreDto) { return this.admin.updateStore(req, id, dto); }
}

@Controller('users')
export class UsersController {
  constructor(private readonly admin: AdminService) {}
  @Get() @RequirePermission('users.read') list(@Req() req: AppRequest, @Query('search') search?: string) { return this.admin.users(req, search); }
  @Post() @RequirePermission('users.write') create(@Req() req: AppRequest, @Body() dto: CreateUserDto) { return this.admin.createUser(req, dto); }
  @Patch(':id') @RequirePermission('users.write') update(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: UpdateUserDto) { return this.admin.updateUser(req, id, dto); }
  @Post(':id/roles') @RequirePermission('users.write') assign(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: AssignRoleDto) { return this.admin.assignRole(req, id, dto); }
  @Delete(':id/roles/:assignmentId') @RequirePermission('users.write') revoke(@Req() req: AppRequest, @Param('id') id: string, @Param('assignmentId') assignmentId: string) { return this.admin.revokeRole(req, id, assignmentId); }
}

@Controller('roles')
export class RolesController {
  constructor(private readonly admin: AdminService) {}
  @Get() @RequirePermission('roles.read') list(@Req() req: AppRequest) { return this.admin.roles(req); }
  @Get('permissions') @RequirePermission('roles.read') permissions() { return this.admin.permissions(); }
  @Post() @RequirePermission('roles.write') create(@Req() req: AppRequest, @Body() dto: CreateRoleDto) { return this.admin.createRole(req, dto); }
  @Patch(':id') @RequirePermission('roles.write') update(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: UpdateRoleDto) { return this.admin.updateRole(req, id, dto); }
}

@Controller('audit-logs')
export class AuditController {
  constructor(private readonly admin: AdminService) {}
  @Get() @RequirePermission('audit.read') list(@Req() req: AppRequest, @Query('cursor') cursor?: string) { return this.admin.audit(req, cursor); }
}
