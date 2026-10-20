import { Body, Controller, Delete, ForbiddenException, Get, Injectable, Param, Patch, Post, Query, Req, Res } from '@nestjs/common';
import { Transform, Type } from 'class-transformer';
import { IsBoolean, IsDateString, IsInt, IsOptional, IsString, IsUUID, Length, Max, MaxLength, Min } from 'class-validator';
import type { Response } from 'express';
import { actor, AppRequest, DatabaseService, RequireScopedPermission } from './core';

class ListQuery {
  @IsOptional() @IsUUID() storeId?: string;
  @IsOptional() @IsString() @MaxLength(120) search?: string;
  @IsOptional() @IsUUID() cursor?: string;
  @IsOptional() @Type(() => Number) @IsInt() @Min(1) @Max(100) limit = 50;
}
class OrderQuery extends ListQuery {
  @IsOptional() @IsString() @MaxLength(60) financialStatus?: string;
  @IsOptional() @IsString() @MaxLength(60) fulfillmentStatus?: string;
  @IsOptional() @IsDateString() from?: string;
  @IsOptional() @IsDateString() to?: string;
}
class ProductQuery extends ListQuery {
  @IsOptional() @IsString() @MaxLength(60) status?: string;
  @IsOptional() @IsString() @MaxLength(120) vendor?: string;
}
class InventoryQuery extends ListQuery {
  @IsOptional() @IsString() @MaxLength(200) locationId?: string;
  @IsOptional() @Transform(({ value }) => value === true || value === 'true') @IsBoolean() lowStock?: boolean;
}
class CreateSkuDto {
  @IsString() @Length(1, 80) code!: string;
  @IsString() @Length(2, 160) name!: string;
  @IsOptional() @IsString() @MaxLength(120) barcode?: string;
}
class UpdateSkuDto {
  @IsOptional() @IsString() @Length(1, 80) code?: string;
  @IsOptional() @IsString() @Length(2, 160) name?: string;
  @IsOptional() @IsString() @MaxLength(120) barcode?: string;
  @IsOptional() @IsBoolean() active?: boolean;
}
class MapVariantDto { @IsUUID() variantId!: string; }

@Injectable()
export class CommerceService {
  constructor(private readonly db: DatabaseService) {}
  private storeWhere(req: AppRequest, requested?: string) {
    const { companyId } = actor(req);
    const allowed = req.allowedStoreIds;
    if (requested && allowed && !allowed.includes(requested)) throw new ForbiddenException('Không có quyền với store này');
    return { companyId, ...(requested ? { id: requested } : allowed ? { id: { in: allowed } } : {}) };
  }
  async orders(req: AppRequest, query: OrderQuery) {
    const stores = await this.db.store.findMany({ where: this.storeWhere(req, query.storeId), select: { id: true } });
    const storeIds = stores.map(store => store.id);
    const rows = await this.db.order.findMany({ where: { storeId: { in: storeIds },
      ...(query.search ? { OR: [{ name: { contains: query.search, mode: 'insensitive' as const } }, { email: { contains: query.search, mode: 'insensitive' as const } }, { customer: { displayName: { contains: query.search, mode: 'insensitive' as const } } }] } : {}),
      ...(query.financialStatus ? { financialStatus: query.financialStatus } : {}), ...(query.fulfillmentStatus ? { fulfillmentStatus: query.fulfillmentStatus } : {}),
      ...((query.from || query.to) ? { shopifyCreatedAt: { ...(query.from ? { gte: new Date(query.from) } : {}), ...(query.to ? { lte: new Date(query.to) } : {}) } } : {}) },
      include: { store: { select: { id: true, name: true } }, customer: { select: { displayName: true } }, _count: { select: { items: true, refunds: true, fulfillments: true } } },
      orderBy: [{ shopifyCreatedAt: 'desc' }, { id: 'desc' }], take: query.limit + 1, ...(query.cursor ? { cursor: { id: query.cursor }, skip: 1 } : {}) });
    const hasMore = rows.length > query.limit;
    const data = hasMore ? rows.slice(0, query.limit) : rows;
    return { data, nextCursor: hasMore ? data[data.length - 1].id : null };
  }
  async order(req: AppRequest, id: string) {
    const allowedStores = await this.db.store.findMany({ where: this.storeWhere(req), select: { id: true } });
    const row = await this.db.order.findFirst({ where: { id, storeId: { in: allowedStores.map(store => store.id) } },
      include: { store: { select: { id: true, name: true } }, customer: true,
        items: { include: { variant: { include: { product: { select: { title: true } }, mapping: { include: { internalSku: true } } } } } },
        refunds: { include: { items: true }, orderBy: { shopifyCreatedAt: 'desc' } },
        fulfillments: { include: { items: true }, orderBy: { shopifyCreatedAt: 'desc' } }, events: { orderBy: { occurredAt: 'desc' } } } });
    if (!row) throw new ForbiddenException('Không tìm thấy đơn hàng trong phạm vi được cấp');
    return row;
  }
  async products(req: AppRequest, query: ProductQuery) {
    const stores = await this.db.store.findMany({ where: this.storeWhere(req, query.storeId), select: { id: true } });
    const rows = await this.db.product.findMany({ where: { storeId: { in: stores.map(store => store.id) },
      ...(query.search ? { OR: [{ title: { contains: query.search, mode: 'insensitive' as const } }, { handle: { contains: query.search, mode: 'insensitive' as const } }, { variants: { some: { sku: { contains: query.search, mode: 'insensitive' as const } } } }] } : {}),
      ...(query.status ? { status: query.status } : {}), ...(query.vendor ? { vendor: query.vendor } : {}) },
      include: { store: { select: { id: true, name: true } }, variants: { include: { mapping: { include: { internalSku: true } }, inventory: true } } },
      orderBy: [{ title: 'asc' }, { id: 'asc' }], take: query.limit + 1, ...(query.cursor ? { cursor: { id: query.cursor }, skip: 1 } : {}) });
    const hasMore = rows.length > query.limit;
    const data = hasMore ? rows.slice(0, query.limit) : rows;
    return { data, nextCursor: hasMore ? data[data.length - 1].id : null };
  }
  async inventory(req: AppRequest, query: InventoryQuery) {
    const stores = await this.db.store.findMany({ where: this.storeWhere(req, query.storeId), select: { id: true } });
    const rows = await this.db.inventoryLevel.findMany({ where: { storeId: { in: stores.map(store => store.id) },
      ...(query.locationId ? { locationId: query.locationId } : {}), ...(query.lowStock ? { available: { lte: 5 } } : {}),
      ...(query.search ? { variant: { OR: [{ sku: { contains: query.search, mode: 'insensitive' as const } }, { title: { contains: query.search, mode: 'insensitive' as const } }, { product: { title: { contains: query.search, mode: 'insensitive' as const } } }] } } : {}) },
      include: { store: { select: { id: true, name: true } }, variant: { include: { product: { select: { title: true } }, mapping: { include: { internalSku: true } } } } },
      orderBy: [{ available: 'asc' }, { id: 'asc' }], take: query.limit + 1, ...(query.cursor ? { cursor: { id: query.cursor }, skip: 1 } : {}) });
    const hasMore = rows.length > query.limit;
    const data = hasMore ? rows.slice(0, query.limit) : rows;
    return { data, nextCursor: hasMore ? data[data.length - 1].id : null };
  }
  async customers(req: AppRequest, query: ListQuery) {
    const stores = await this.db.store.findMany({ where: this.storeWhere(req, query.storeId), select: { id: true } });
    const rows = await this.db.customer.findMany({ where: { storeId: { in: stores.map(store => store.id) }, ...(query.search ? { OR: [
      { displayName: { contains: query.search, mode: 'insensitive' as const } }, { email: { contains: query.search, mode: 'insensitive' as const } }, { phone: { contains: query.search } }] } : {}) },
      include: { store: { select: { id: true, name: true } } }, orderBy: [{ totalSpent: 'desc' }, { id: 'asc' }], take: query.limit + 1,
      ...(query.cursor ? { cursor: { id: query.cursor }, skip: 1 } : {}) });
    const hasMore = rows.length > query.limit;
    const data = hasMore ? rows.slice(0, query.limit) : rows;
    return { data, nextCursor: hasMore ? data[data.length - 1].id : null };
  }
  async skus(req: AppRequest, query: ListQuery) {
    const { companyId } = actor(req);
    const allowed = req.allowedStoreIds;
    return this.db.internalSku.findMany({
      where: { companyId,
        ...(query.search ? { OR: [{ code: { contains: query.search, mode: 'insensitive' as const } }, { name: { contains: query.search, mode: 'insensitive' as const } }] } : {}),
        ...(allowed ? { mappings: { some: { variant: { product: { storeId: { in: allowed } } } } } } : {}) },
      include: { mappings: {
        where: allowed ? { variant: { product: { storeId: { in: allowed } } } } : {},
        include: { variant: { include: { product: { include: { store: { select: { id: true, name: true } } } } } } },
      } },
      orderBy: { code: 'asc' }, take: query.limit,
    });
  }
  async createSku(req: AppRequest, dto: CreateSkuDto) {
    const { companyId, userId } = actor(req);
    const sku = await this.db.internalSku.create({ data: { companyId, code: dto.code.trim().toUpperCase(), name: dto.name.trim(), barcode: dto.barcode?.trim() || null } });
    await this.db.auditLog.create({ data: { companyId, actorId: userId, action: 'sku.create', resourceType: 'internal_sku', resourceId: sku.id } });
    return sku;
  }
  async updateSku(req: AppRequest, id: string, dto: UpdateSkuDto) {
    const { companyId, userId } = actor(req);
    if (!await this.db.internalSku.findFirst({ where: { id, companyId } })) throw new ForbiddenException();
    const sku = await this.db.internalSku.update({ where: { id }, data: { code: dto.code?.trim().toUpperCase(), name: dto.name?.trim(), barcode: dto.barcode?.trim(), active: dto.active } });
    await this.db.auditLog.create({ data: { companyId, actorId: userId, action: 'sku.update', resourceType: 'internal_sku', resourceId: id } });
    return sku;
  }
  async mapVariant(req: AppRequest, skuId: string, dto: MapVariantDto) {
    const { companyId, userId } = actor(req);
    const sku = await this.db.internalSku.findFirst({ where: { id: skuId, companyId } });
    const variant = await this.db.variant.findFirst({ where: { id: dto.variantId, product: { store: this.storeWhere(req) } } });
    if (!sku || !variant) throw new ForbiddenException('SKU hoặc variant ngoài phạm vi được cấp');
    const mapping = await this.db.variantMapping.upsert({ where: { variantId: variant.id }, create: { internalSkuId: sku.id, variantId: variant.id }, update: { internalSkuId: sku.id } });
    await this.db.auditLog.create({ data: { companyId, actorId: userId, action: 'sku.map', resourceType: 'variant', resourceId: variant.id, metadata: { internalSkuId: sku.id } } });
    return mapping;
  }
  async unmapVariant(req: AppRequest, skuId: string, variantId: string) {
    const { companyId, userId } = actor(req);
    const mapping = await this.db.variantMapping.findFirst({ where: { internalSkuId: skuId, variantId, internalSku: { companyId }, variant: { product: { store: this.storeWhere(req) } } } });
    if (!mapping) throw new ForbiddenException();
    await this.db.variantMapping.delete({ where: { id: mapping.id } });
    await this.db.auditLog.create({ data: { companyId, actorId: userId, action: 'sku.unmap', resourceType: 'variant', resourceId: variantId, metadata: { internalSkuId: skuId } } });
    return { removed: true };
  }
  async orderCsv(req: AppRequest, query: OrderQuery) {
    const result = await this.orders(req, Object.assign(query, { limit: 100 }));
    return csv(['Order', 'Store', 'Created', 'Financial status', 'Fulfillment status', 'Customer', 'Currency', 'Total'], result.data.map(order => [
      order.name, order.store.name, order.shopifyCreatedAt.toISOString(), order.financialStatus, order.fulfillmentStatus,
      order.customer?.displayName || order.email, order.currency, order.total.toString(),
    ]));
  }
  async inventoryCsv(req: AppRequest, query: InventoryQuery) {
    const result = await this.inventory(req, Object.assign(query, { limit: 100 }));
    return csv(['Store', 'Product', 'Variant', 'SKU', 'Internal SKU', 'Location', 'Available', 'On hand'], result.data.map(level => [
      level.store.name, level.variant.product.title, level.variant.title, level.variant.sku, level.variant.mapping?.internalSku.code,
      level.locationName, level.available, level.onHand,
    ]));
  }
}

function csv(headers: string[], rows: unknown[][]) {
  const cell = (value: unknown) => {
    let text = value == null ? '' : String(value);
    if (/^[=+\-@]/.test(text)) text = `'${text}`;
    return `"${text.replaceAll('"', '""')}"`;
  };
  return `\uFEFF${[headers, ...rows].map(row => row.map(cell).join(',')).join('\r\n')}`;
}
function sendCsv(response: Response, filename: string, content: string) {
  response.setHeader('content-type', 'text/csv; charset=utf-8');
  response.setHeader('content-disposition', `attachment; filename="${filename}"`);
  response.send(content);
}

@Controller('commerce')
export class CommerceController {
  constructor(private readonly commerce: CommerceService) {}
  @Get('orders') @RequireScopedPermission('sales.read') orders(@Req() req: AppRequest, @Query() query: OrderQuery) { return this.commerce.orders(req, query); }
  @Get('orders/export') @RequireScopedPermission('sales.read') async orderExport(@Req() req: AppRequest, @Query() query: OrderQuery, @Res() res: Response) { sendCsv(res, 'orders.csv', await this.commerce.orderCsv(req, query)); }
  @Get('orders/:id') @RequireScopedPermission('sales.read') order(@Req() req: AppRequest, @Param('id') id: string) { return this.commerce.order(req, id); }
  @Get('products') @RequireScopedPermission('sales.read') products(@Req() req: AppRequest, @Query() query: ProductQuery) { return this.commerce.products(req, query); }
  @Get('inventory') @RequireScopedPermission('inventory.read') inventory(@Req() req: AppRequest, @Query() query: InventoryQuery) { return this.commerce.inventory(req, query); }
  @Get('inventory/export') @RequireScopedPermission('inventory.read') async inventoryExport(@Req() req: AppRequest, @Query() query: InventoryQuery, @Res() res: Response) { sendCsv(res, 'inventory.csv', await this.commerce.inventoryCsv(req, query)); }
  @Get('customers') @RequireScopedPermission('sales.read') customers(@Req() req: AppRequest, @Query() query: ListQuery) { return this.commerce.customers(req, query); }
  @Get('skus') @RequireScopedPermission('inventory.read') skus(@Req() req: AppRequest, @Query() query: ListQuery) { return this.commerce.skus(req, query); }
  @Post('skus') @RequireScopedPermission('inventory.write') createSku(@Req() req: AppRequest, @Body() dto: CreateSkuDto) { return this.commerce.createSku(req, dto); }
  @Patch('skus/:id') @RequireScopedPermission('inventory.write') updateSku(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: UpdateSkuDto) { return this.commerce.updateSku(req, id, dto); }
  @Post('skus/:id/mappings') @RequireScopedPermission('inventory.write') map(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: MapVariantDto) { return this.commerce.mapVariant(req, id, dto); }
  @Delete('skus/:id/mappings/:variantId') @RequireScopedPermission('inventory.write') unmap(@Req() req: AppRequest, @Param('id') id: string, @Param('variantId') variantId: string) { return this.commerce.unmapVariant(req, id, variantId); }
}
