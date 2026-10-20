import { BadRequestException, Body, Controller, ForbiddenException, Get, Injectable, Param, Post, Query, Req } from '@nestjs/common';
import { ApprovalStatus, Prisma, StockMovementType } from '@erp/database';
import { Type } from 'class-transformer';
import { ArrayMinSize, IsArray, IsDateString, IsEmail, IsEnum, IsInt, IsOptional, IsPositive, IsString, IsUUID, Length, Matches, MaxLength, Min, ValidateNested } from 'class-validator';
import { randomUUID } from 'node:crypto';
import { actor, AppRequest, DatabaseService, RequirePermission, RequireWarehouseScopedPermission } from './core';

const moneyPattern = /^\d{1,14}(\.\d{1,4})?$/;
class CreateWarehouseDto { @Matches(/^[A-Z0-9_-]{2,30}$/) code!: string; @IsString() @Length(2, 120) name!: string; }
class CreateSupplierDto { @Matches(/^[A-Z0-9_-]{2,30}$/) code!: string; @IsString() @Length(2, 160) name!: string; @IsOptional() @IsString() contactName?: string; @IsOptional() @IsEmail() email?: string; @IsOptional() @IsString() phone?: string; @IsOptional() @IsString() @MaxLength(200) paymentTerms?: string; }
class PurchaseItemDto { @IsUUID() internalSkuId!: string; @IsInt() @IsPositive() quantity!: number; @IsString() @Matches(moneyPattern) unitCost!: string; }
class CreatePurchaseOrderDto {
  @IsUUID() supplierId!: string; @IsUUID() warehouseId!: string; @Matches(/^[A-Z]{3}$/) currency!: string;
  @IsOptional() @IsDateString() expectedAt?: string; @IsOptional() @IsString() @MaxLength(1000) note?: string;
  @IsArray() @ArrayMinSize(1) @ValidateNested({ each: true }) @Type(() => PurchaseItemDto) items!: PurchaseItemDto[];
}
class MovementLineDto { @IsUUID() internalSkuId!: string; @IsInt() quantity!: number; @IsOptional() @IsString() @Matches(moneyPattern) unitCost?: string; }
class CreateMovementDto {
  @IsEnum(StockMovementType) type!: StockMovementType; @IsOptional() @IsUUID() fromWarehouseId?: string; @IsOptional() @IsUUID() toWarehouseId?: string;
  @IsOptional() @IsUUID() purchaseOrderId?: string; @IsOptional() @IsString() @MaxLength(120) reference?: string; @IsOptional() @IsString() @MaxLength(1000) note?: string;
  @IsArray() @ArrayMinSize(1) @ValidateNested({ each: true }) @Type(() => MovementLineDto) lines!: MovementLineDto[];
}
class StockCountItemDto { @IsUUID() internalSkuId!: string; @IsInt() @Min(0) counted!: number; }
class CreateStockCountDto { @IsUUID() warehouseId!: string; @IsDateString() countedAt!: string; @IsOptional() @IsString() @MaxLength(1000) note?: string; @IsArray() @ArrayMinSize(1) @ValidateNested({ each: true }) @Type(() => StockCountItemDto) items!: StockCountItemDto[]; }
class ReturnItemDto { @IsUUID() orderItemId!: string; @IsInt() @IsPositive() quantity!: number; @IsOptional() @IsString() @MaxLength(120) condition?: string; }
class CreateReturnDto { @IsUUID() orderId!: string; @IsString() @Length(2, 1000) reason!: string; @IsArray() @ArrayMinSize(1) @ValidateNested({ each: true }) @Type(() => ReturnItemDto) items!: ReturnItemDto[]; }
class DecisionDto { @IsOptional() @IsString() @MaxLength(1000) note?: string; }
class ReceiveReturnDto { @IsUUID() warehouseId!: string; }

@Injectable()
export class OperationsService {
  constructor(private readonly db: DatabaseService) {}
  private warehouseIds(req: AppRequest) { return req.allowedWarehouseIds == null ? undefined : { in: req.allowedWarehouseIds }; }
  warehouses(req: AppRequest) { return this.db.warehouse.findMany({ where: { companyId: actor(req).companyId, id: this.warehouseIds(req) }, include: { balances: { include: { internalSku: true } } }, orderBy: { name: 'asc' } }); }
  async createWarehouse(req: AppRequest, dto: CreateWarehouseDto) {
    const { companyId, userId } = actor(req); const row = await this.db.warehouse.create({ data: { companyId, code: dto.code, name: dto.name.trim() } });
    await this.audit(companyId, userId, 'warehouse.create', 'warehouse', row.id); return row;
  }
  suppliers(req: AppRequest) { return this.db.supplier.findMany({ where: { companyId: actor(req).companyId }, orderBy: { name: 'asc' } }); }
  skus(req: AppRequest) { return this.db.internalSku.findMany({ where: { companyId: actor(req).companyId, active: true }, select: { id: true, code: true, name: true }, orderBy: { code: 'asc' }, take: 1000 }); }
  async createSupplier(req: AppRequest, dto: CreateSupplierDto) {
    const { companyId, userId } = actor(req); const row = await this.db.supplier.create({ data: { companyId, code: dto.code, name: dto.name.trim(), contactName: dto.contactName, email: dto.email, phone: dto.phone, paymentTerms: dto.paymentTerms } });
    await this.audit(companyId, userId, 'supplier.create', 'supplier', row.id); return row;
  }
  purchaseOrders(req: AppRequest) { return this.db.purchaseOrder.findMany({ where: { companyId: actor(req).companyId, warehouseId: this.warehouseIds(req) }, include: { supplier: true, warehouse: true, items: { include: { internalSku: true } } }, orderBy: { createdAt: 'desc' }, take: 200 }); }
  async createPurchaseOrder(req: AppRequest, dto: CreatePurchaseOrderDto) {
    const { companyId, userId } = actor(req);
    const [supplier, warehouse, skus] = await Promise.all([this.db.supplier.findFirst({ where: { id: dto.supplierId, companyId, active: true } }), this.db.warehouse.findFirst({ where: { id: dto.warehouseId, companyId, active: true } }), this.db.internalSku.count({ where: { companyId, id: { in: dto.items.map(item => item.internalSkuId) } } })]);
    if (!supplier || !warehouse || (req.allowedWarehouseIds && !req.allowedWarehouseIds.includes(warehouse.id)) || skus !== new Set(dto.items.map(item => item.internalSkuId)).size) throw new BadRequestException('Supplier, warehouse hoặc SKU không hợp lệ');
    if (new Set(dto.items.map(item => item.internalSkuId)).size !== dto.items.length) throw new BadRequestException('Mỗi SKU chỉ được xuất hiện một lần trong PO');
    const number = `PO-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}-${randomUUID().slice(0, 8).toUpperCase()}`;
    const row = await this.db.purchaseOrder.create({ data: { companyId, supplierId: supplier.id, warehouseId: warehouse.id, number, currency: dto.currency, expectedAt: dto.expectedAt ? new Date(dto.expectedAt) : null, note: dto.note,
      items: { create: dto.items.map(item => ({ internalSkuId: item.internalSkuId, quantity: item.quantity, unitCost: item.unitCost })) } }, include: { items: true } });
    await this.audit(companyId, userId, 'purchase_order.create', 'purchase_order', row.id); return row;
  }
  async submitPurchaseOrder(req: AppRequest, id: string) {
    const { companyId, userId } = actor(req); const po = await this.db.purchaseOrder.findFirst({ where: { id, companyId, status: 'DRAFT', warehouseId: this.warehouseIds(req) } });
    if (!po) throw new BadRequestException('PO không ở trạng thái nháp');
    await this.db.$transaction([this.db.purchaseOrder.update({ where: { id }, data: { status: 'SUBMITTED', submittedById: userId } }), this.db.approvalRequest.create({ data: { companyId, entityType: 'PURCHASE_ORDER', entityId: id, requestedById: userId } })]);
    return { submitted: true };
  }
  async decidePurchaseOrder(req: AppRequest, id: string, approve: boolean, note?: string) {
    const { companyId, userId } = actor(req); const po = await this.db.purchaseOrder.findFirst({ where: { id, companyId, status: 'SUBMITTED', warehouseId: this.warehouseIds(req) } });
    if (!po) throw new BadRequestException('PO không chờ duyệt');
    await this.db.$transaction([this.db.purchaseOrder.update({ where: { id }, data: approve ? { status: 'APPROVED', approvedById: userId, approvedAt: new Date() } : { status: 'DRAFT' } }),
      this.db.approvalRequest.updateMany({ where: { entityType: 'PURCHASE_ORDER', entityId: id, status: 'PENDING' }, data: { status: approve ? 'APPROVED' : 'REJECTED', decidedById: userId, decidedAt: new Date(), decisionNote: note } }),
      this.db.auditLog.create({ data: { companyId, actorId: userId, action: approve ? 'purchase_order.approve' : 'purchase_order.reject', resourceType: 'purchase_order', resourceId: id } })]);
    return { approved: approve };
  }
  movements(req: AppRequest) { const ids = req.allowedWarehouseIds; return this.db.stockMovement.findMany({ where: { companyId: actor(req).companyId, ...(ids == null ? {} : { OR: [{ fromWarehouseId: { in: ids } }, { toWarehouseId: { in: ids } }] }) }, include: { fromWarehouse: true, toWarehouse: true, lines: { include: { internalSku: true } } }, orderBy: { createdAt: 'desc' }, take: 200 }); }
  private validateMovement(dto: CreateMovementDto) {
    if (dto.lines.some(line => line.quantity === 0)) throw new BadRequestException('Số lượng movement không được bằng 0');
    if (dto.type !== 'ADJUSTMENT' && dto.lines.some(line => line.quantity < 0)) throw new BadRequestException('Chỉ adjustment được dùng số lượng âm');
    if (dto.type === 'TRANSFER' && (!dto.fromWarehouseId || !dto.toWarehouseId || dto.fromWarehouseId === dto.toWarehouseId)) throw new BadRequestException('Transfer cần hai warehouse khác nhau');
    if (['RECEIPT', 'RETURN_IN'].includes(dto.type) && !dto.toWarehouseId) throw new BadRequestException('Movement nhập cần warehouse đích');
    if (dto.type === 'RETURN_OUT' && !dto.fromWarehouseId) throw new BadRequestException('Movement xuất cần warehouse nguồn');
    if (dto.type === 'ADJUSTMENT' && !dto.toWarehouseId) throw new BadRequestException('Adjustment cần warehouse');
  }
  async createMovement(req: AppRequest, dto: CreateMovementDto) {
    this.validateMovement(dto); const { companyId, userId } = actor(req);
    const warehouseIds = [dto.fromWarehouseId, dto.toWarehouseId].filter(Boolean) as string[];
    if (req.allowedWarehouseIds && warehouseIds.some(id => !req.allowedWarehouseIds!.includes(id))) throw new ForbiddenException('Warehouse ngoài phạm vi được cấp');
    if (await this.db.warehouse.count({ where: { companyId, id: { in: warehouseIds } } }) !== new Set(warehouseIds).size) throw new ForbiddenException('Warehouse ngoài công ty');
    if (await this.db.internalSku.count({ where: { companyId, id: { in: dto.lines.map(line => line.internalSkuId) } } }) !== new Set(dto.lines.map(line => line.internalSkuId)).size) throw new BadRequestException('SKU không hợp lệ');
    if (new Set(dto.lines.map(line => line.internalSkuId)).size !== dto.lines.length) throw new BadRequestException('Mỗi SKU chỉ được xuất hiện một lần trong movement');
    if (dto.purchaseOrderId) {
      if (dto.type !== 'RECEIPT') throw new BadRequestException('Movement gắn PO phải là receipt');
      const po = await this.db.purchaseOrder.findFirst({ where: { id: dto.purchaseOrderId, companyId, status: { in: ['APPROVED', 'ORDERED', 'PARTIALLY_RECEIVED'] }, warehouseId: dto.toWarehouseId } });
      if (!po) throw new BadRequestException('PO chưa được duyệt hoặc không khớp warehouse nhận');
    }
    const row = await this.db.stockMovement.create({ data: { companyId, createdById: userId, type: dto.type, fromWarehouseId: dto.fromWarehouseId, toWarehouseId: dto.toWarehouseId,
      purchaseOrderId: dto.purchaseOrderId, reference: dto.reference, note: dto.note, lines: { create: dto.lines.map(line => ({ internalSkuId: line.internalSkuId, quantity: line.quantity, unitCost: line.unitCost })) } }, include: { lines: true } });
    await this.audit(companyId, userId, 'stock_movement.create', 'stock_movement', row.id); return row;
  }
  async postMovement(req: AppRequest, id: string) {
    const { companyId, userId } = actor(req);
    return this.db.$transaction(async tx => {
      const ids = req.allowedWarehouseIds;
      const movement = await tx.stockMovement.findFirst({ where: { id, companyId, status: 'DRAFT', ...(ids == null ? {} : { OR: [{ fromWarehouseId: { in: ids } }, { toWarehouseId: { in: ids } }] }) }, include: { lines: true, purchaseOrder: { include: { items: true } } } });
      if (!movement) throw new BadRequestException('Movement không ở trạng thái nháp');
      if (ids && [movement.fromWarehouseId, movement.toWarehouseId].filter(Boolean).some(warehouseId => !ids.includes(warehouseId!))) throw new ForbiddenException('Movement đi qua warehouse ngoài phạm vi được cấp');
      if (movement.purchaseOrder && !['APPROVED', 'ORDERED', 'PARTIALLY_RECEIVED'].includes(movement.purchaseOrder.status)) throw new BadRequestException('PO không ở trạng thái có thể nhận hàng');
      for (const line of movement.lines) {
        const absolute = Math.abs(line.quantity);
        if (movement.fromWarehouseId) {
          const balance = await tx.warehouseBalance.findUnique({ where: { warehouseId_internalSkuId: { warehouseId: movement.fromWarehouseId, internalSkuId: line.internalSkuId } } });
          if (!balance || balance.quantity < absolute) throw new BadRequestException(`Không đủ tồn cho SKU ${line.internalSkuId}`);
          await tx.warehouseBalance.update({ where: { id: balance.id }, data: { quantity: { decrement: absolute } } });
        }
        if (movement.toWarehouseId) {
          const delta = movement.type === 'ADJUSTMENT' ? line.quantity : absolute;
          const key = { warehouseId_internalSkuId: { warehouseId: movement.toWarehouseId, internalSkuId: line.internalSkuId } };
          if (movement.type === 'ADJUSTMENT') {
            const existing = await tx.warehouseBalance.findUnique({ where: key });
            const next = (existing?.quantity || 0) + delta;
            if (next < 0) throw new BadRequestException('Adjustment làm tồn kho âm');
            await tx.warehouseBalance.upsert({ where: key, create: { warehouseId: movement.toWarehouseId, internalSkuId: line.internalSkuId, quantity: next }, update: { quantity: next } });
          } else {
            await tx.warehouseBalance.upsert({ where: key, create: { warehouseId: movement.toWarehouseId, internalSkuId: line.internalSkuId, quantity: delta }, update: { quantity: { increment: delta } } });
          }
        }
        if (movement.purchaseOrder) {
          const poItem = movement.purchaseOrder.items.find(item => item.internalSkuId === line.internalSkuId);
          if (!poItem || poItem.receivedQuantity + absolute > poItem.quantity) throw new BadRequestException('Số lượng nhận vượt PO');
          await tx.purchaseOrderItem.update({ where: { id: poItem.id }, data: { receivedQuantity: { increment: absolute } } });
        }
      }
      if (movement.purchaseOrder) {
        const remaining = await tx.purchaseOrderItem.count({ where: { purchaseOrderId: movement.purchaseOrder.id, receivedQuantity: { lt: tx.purchaseOrderItem.fields.quantity } } });
        const received = await tx.purchaseOrderItem.aggregate({ where: { purchaseOrderId: movement.purchaseOrder.id }, _sum: { receivedQuantity: true } });
        await tx.purchaseOrder.update({ where: { id: movement.purchaseOrder.id }, data: { status: remaining === 0 ? 'RECEIVED' : (received._sum.receivedQuantity || 0) > 0 ? 'PARTIALLY_RECEIVED' : 'ORDERED' } });
      }
      const posted = await tx.stockMovement.update({ where: { id }, data: { status: 'POSTED', postedById: userId, postedAt: new Date() } });
      await tx.auditLog.create({ data: { companyId, actorId: userId, action: 'stock_movement.post', resourceType: 'stock_movement', resourceId: id } });
      return posted;
    }, { isolationLevel: Prisma.TransactionIsolationLevel.Serializable });
  }
  stockCounts(req: AppRequest) { return this.db.stockCount.findMany({ where: { companyId: actor(req).companyId, warehouseId: this.warehouseIds(req) }, include: { warehouse: true, items: { include: { internalSku: true } } }, orderBy: { countedAt: 'desc' }, take: 100 }); }
  async createStockCount(req: AppRequest, dto: CreateStockCountDto) {
    const { companyId, userId } = actor(req); const warehouse = await this.db.warehouse.findFirst({ where: { id: dto.warehouseId, companyId } });
    if (!warehouse) throw new ForbiddenException();
    if (req.allowedWarehouseIds && !req.allowedWarehouseIds.includes(warehouse.id)) throw new ForbiddenException('Warehouse ngoài phạm vi được cấp');
    const balances = await this.db.warehouseBalance.findMany({ where: { warehouseId: warehouse.id, internalSkuId: { in: dto.items.map(item => item.internalSkuId) } } }); const expected = new Map(balances.map(item => [item.internalSkuId, item.quantity]));
    const count = await this.db.stockCount.create({ data: { companyId, warehouseId: warehouse.id, countedAt: new Date(dto.countedAt), note: dto.note, createdById: userId,
      items: { create: dto.items.map(item => ({ internalSkuId: item.internalSkuId, expected: expected.get(item.internalSkuId) || 0, counted: item.counted, variance: item.counted - (expected.get(item.internalSkuId) || 0) })) } }, include: { items: true } });
    await this.audit(companyId, userId, 'stock_count.create', 'stock_count', count.id); return count;
  }
  async submitStockCount(req: AppRequest, id: string) {
    const { companyId, userId } = actor(req); const count = await this.db.stockCount.findFirst({ where: { id, companyId, status: 'DRAFT', warehouseId: this.warehouseIds(req) } }); if (!count) throw new BadRequestException('Phiếu kiểm kê không ở trạng thái nháp');
    await this.db.$transaction([this.db.stockCount.update({ where: { id }, data: { status: 'SUBMITTED' } }), this.db.approvalRequest.create({ data: { companyId, entityType: 'STOCK_COUNT', entityId: id, requestedById: userId } })]); return { submitted: true };
  }
  async approveStockCount(req: AppRequest, id: string, approve: boolean, note?: string) {
    const { companyId, userId } = actor(req); const count = await this.db.stockCount.findFirst({ where: { id, companyId, status: 'SUBMITTED', warehouseId: this.warehouseIds(req) }, include: { items: true } }); if (!count) throw new BadRequestException('Phiếu kiểm kê không chờ duyệt');
    if (!approve) { await this.db.$transaction([this.db.stockCount.update({ where: { id }, data: { status: 'REJECTED' } }), this.decideApproval('STOCK_COUNT', id, userId, 'REJECTED', note)]); return { approved: false }; }
    const lines = count.items.filter(item => item.variance !== 0).map(item => ({ internalSkuId: item.internalSkuId, quantity: item.variance }));
    let movementId: string | null = null;
    if (lines.length) { const movement = await this.createMovement(req, { type: 'ADJUSTMENT', toWarehouseId: count.warehouseId, reference: `STOCK_COUNT:${id}`, lines }); movementId = movement.id; await this.postMovement(req, movement.id); }
    await this.db.$transaction([this.db.stockCount.update({ where: { id }, data: { status: 'APPROVED', approvedById: userId, approvedAt: new Date() } }), this.decideApproval('STOCK_COUNT', id, userId, 'APPROVED', note)]); return { approved: true, movementId };
  }
  returns(req: AppRequest) { return this.db.returnRequest.findMany({ where: { companyId: actor(req).companyId }, include: { store: true, order: { select: { name: true } }, items: { include: { orderItem: true, internalSku: true } } }, orderBy: { createdAt: 'desc' }, take: 200 }); }
  async createReturn(req: AppRequest, dto: CreateReturnDto) {
    const { companyId, userId } = actor(req); const order = await this.db.order.findFirst({ where: { id: dto.orderId, store: { companyId } }, include: { items: { include: { variant: { include: { mapping: true } } } } } }); if (!order) throw new ForbiddenException();
    for (const item of dto.items) { const orderItem = order.items.find(row => row.id === item.orderItemId); if (!orderItem || item.quantity > orderItem.quantity) throw new BadRequestException('Số lượng trả không hợp lệ'); }
    if (new Set(dto.items.map(item => item.orderItemId)).size !== dto.items.length) throw new BadRequestException('Mỗi order item chỉ được xuất hiện một lần');
    const number = `RET-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}-${randomUUID().slice(0, 8).toUpperCase()}`;
    const row = await this.db.returnRequest.create({ data: { companyId, storeId: order.storeId, orderId: order.id, number, reason: dto.reason, requestedById: userId,
      items: { create: dto.items.map(item => { const source = order.items.find(row => row.id === item.orderItemId)!; return { orderItemId: source.id, internalSkuId: source.variant?.mapping?.internalSkuId, quantity: item.quantity, condition: item.condition }; }) } }, include: { items: true } });
    await this.db.approvalRequest.create({ data: { companyId, entityType: 'RETURN', entityId: row.id, requestedById: userId } }); await this.audit(companyId, userId, 'return.create', 'return', row.id); return row;
  }
  async decideReturn(req: AppRequest, id: string, approve: boolean, note?: string) {
    const { companyId, userId } = actor(req); const row = await this.db.returnRequest.findFirst({ where: { id, companyId, status: 'REQUESTED' } }); if (!row) throw new BadRequestException('Return không chờ duyệt');
    await this.db.$transaction([this.db.returnRequest.update({ where: { id }, data: approve ? { status: 'APPROVED', approvedById: userId, approvedAt: new Date() } : { status: 'REJECTED' } }), this.decideApproval('RETURN', id, userId, approve ? 'APPROVED' : 'REJECTED', note)]); return { approved: approve };
  }
  async receiveReturn(req: AppRequest, id: string, dto: ReceiveReturnDto) {
    const { companyId } = actor(req); const row = await this.db.returnRequest.findFirst({ where: { id, companyId, status: 'APPROVED' }, include: { items: true } }); if (!row) throw new BadRequestException('Return chưa được duyệt');
    const quantities = new Map<string, number>(); for (const item of row.items) if (item.internalSkuId) quantities.set(item.internalSkuId, (quantities.get(item.internalSkuId) || 0) + item.quantity);
    const lines = [...quantities].map(([internalSkuId, quantity]) => ({ internalSkuId, quantity })); if (!lines.length) throw new BadRequestException('Return chưa map SKU nội bộ');
    const movement = await this.createMovement(req, { type: 'RETURN_IN', toWarehouseId: dto.warehouseId, reference: row.number, lines }); await this.postMovement(req, movement.id);
    await this.db.returnRequest.update({ where: { id }, data: { status: 'RECEIVED' } }); return { received: true, movementId: movement.id };
  }
  async approvals(req: AppRequest, status?: ApprovalStatus) { const companyId = actor(req).companyId, ids = req.allowedWarehouseIds; let scope: Prisma.ApprovalRequestWhereInput = {}; if (ids) { const [purchaseOrders, counts] = await Promise.all([this.db.purchaseOrder.findMany({ where: { companyId, warehouseId: { in: ids } }, select: { id: true } }), this.db.stockCount.findMany({ where: { companyId, warehouseId: { in: ids } }, select: { id: true } })]); scope = { OR: [{ entityType: 'PURCHASE_ORDER', entityId: { in: purchaseOrders.map(x => x.id) } }, { entityType: 'STOCK_COUNT', entityId: { in: counts.map(x => x.id) } }] }; } return this.db.approvalRequest.findMany({ where: { companyId, ...(status ? { status } : {}), ...scope }, orderBy: { createdAt: 'desc' }, take: 200 }); }
  private decideApproval(entityType: string, entityId: string, userId: string, status: 'APPROVED' | 'REJECTED', note?: string) { return this.db.approvalRequest.updateMany({ where: { entityType, entityId, status: 'PENDING' }, data: { status, decidedById: userId, decidedAt: new Date(), decisionNote: note } }); }
  private audit(companyId: string, actorId: string, action: string, resourceType: string, resourceId: string) { return this.db.auditLog.create({ data: { companyId, actorId, action, resourceType, resourceId } }); }
}

@Controller('operations')
export class OperationsController {
  constructor(private readonly operations: OperationsService) {}
  @Get('warehouses') @RequireWarehouseScopedPermission('inventory.read') warehouses(@Req() req: AppRequest) { return this.operations.warehouses(req); }
  @Post('warehouses') @RequirePermission('inventory.write') createWarehouse(@Req() req: AppRequest, @Body() dto: CreateWarehouseDto) { return this.operations.createWarehouse(req, dto); }
  @Get('suppliers') @RequireWarehouseScopedPermission('inventory.read') suppliers(@Req() req: AppRequest) { return this.operations.suppliers(req); }
  @Get('skus') @RequireWarehouseScopedPermission('inventory.read') skus(@Req() req: AppRequest) { return this.operations.skus(req); }
  @Post('suppliers') @RequirePermission('inventory.write') createSupplier(@Req() req: AppRequest, @Body() dto: CreateSupplierDto) { return this.operations.createSupplier(req, dto); }
  @Get('purchase-orders') @RequireWarehouseScopedPermission('inventory.read') purchaseOrders(@Req() req: AppRequest) { return this.operations.purchaseOrders(req); }
  @Post('purchase-orders') @RequireWarehouseScopedPermission('inventory.write') createPurchaseOrder(@Req() req: AppRequest, @Body() dto: CreatePurchaseOrderDto) { return this.operations.createPurchaseOrder(req, dto); }
  @Post('purchase-orders/:id/submit') @RequireWarehouseScopedPermission('inventory.write') submitPo(@Req() req: AppRequest, @Param('id') id: string) { return this.operations.submitPurchaseOrder(req, id); }
  @Post('purchase-orders/:id/approve') @RequireWarehouseScopedPermission('inventory.approve') approvePo(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: DecisionDto) { return this.operations.decidePurchaseOrder(req, id, true, dto.note); }
  @Post('purchase-orders/:id/reject') @RequireWarehouseScopedPermission('inventory.approve') rejectPo(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: DecisionDto) { return this.operations.decidePurchaseOrder(req, id, false, dto.note); }
  @Get('movements') @RequireWarehouseScopedPermission('inventory.read') movements(@Req() req: AppRequest) { return this.operations.movements(req); }
  @Post('movements') @RequireWarehouseScopedPermission('inventory.write') createMovement(@Req() req: AppRequest, @Body() dto: CreateMovementDto) { return this.operations.createMovement(req, dto); }
  @Post('movements/:id/post') @RequireWarehouseScopedPermission('inventory.approve') postMovement(@Req() req: AppRequest, @Param('id') id: string) { return this.operations.postMovement(req, id); }
  @Get('stock-counts') @RequireWarehouseScopedPermission('inventory.read') counts(@Req() req: AppRequest) { return this.operations.stockCounts(req); }
  @Post('stock-counts') @RequireWarehouseScopedPermission('inventory.write') createCount(@Req() req: AppRequest, @Body() dto: CreateStockCountDto) { return this.operations.createStockCount(req, dto); }
  @Post('stock-counts/:id/submit') @RequireWarehouseScopedPermission('inventory.write') submitCount(@Req() req: AppRequest, @Param('id') id: string) { return this.operations.submitStockCount(req, id); }
  @Post('stock-counts/:id/approve') @RequireWarehouseScopedPermission('inventory.approve') approveCount(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: DecisionDto) { return this.operations.approveStockCount(req, id, true, dto.note); }
  @Post('stock-counts/:id/reject') @RequireWarehouseScopedPermission('inventory.approve') rejectCount(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: DecisionDto) { return this.operations.approveStockCount(req, id, false, dto.note); }
  @Get('returns') @RequirePermission('sales.read') returns(@Req() req: AppRequest) { return this.operations.returns(req); }
  @Post('returns') @RequirePermission('sales.write') createReturn(@Req() req: AppRequest, @Body() dto: CreateReturnDto) { return this.operations.createReturn(req, dto); }
  @Post('returns/:id/approve') @RequirePermission('inventory.approve') approveReturn(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: DecisionDto) { return this.operations.decideReturn(req, id, true, dto.note); }
  @Post('returns/:id/reject') @RequirePermission('inventory.approve') rejectReturn(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: DecisionDto) { return this.operations.decideReturn(req, id, false, dto.note); }
  @Post('returns/:id/receive') @RequireWarehouseScopedPermission('inventory.write') receiveReturn(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: ReceiveReturnDto) { return this.operations.receiveReturn(req, id, dto); }
  @Get('approvals') @RequireWarehouseScopedPermission('inventory.approve') approvals(@Req() req: AppRequest, @Query('status') status?: ApprovalStatus) { return this.operations.approvals(req, status); }
}
