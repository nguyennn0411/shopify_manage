import { BadRequestException, Body, Controller, Delete, ForbiddenException, Get, Injectable, Param, Patch, Post, Req } from '@nestjs/common';
import { BusinessFunction, FulfillmentJobStatus, Prisma, ProcurementPaymentStatus, ProductLifecycleStatus, ShipmentEventStatus, SupplierContractStatus, SupplierQuoteStatus, WorkTaskPriority } from '@erp/database';
import { Type } from 'class-transformer';
import { ArrayMinSize, IsArray, IsDateString, IsEmail, IsEnum, IsInt, IsObject, IsOptional, IsString, IsUrl, IsUUID, Length, Matches, Max, MaxLength, Min, ValidateNested } from 'class-validator';
import { randomUUID } from 'node:crypto';
import { actor, AppRequest, DatabaseService, RedisService, RequirePermission, RequireWarehouseScopedPermission } from './core';
import { ShopifyService } from './shopify';
import { graphql } from './shopify-client';

const money = /^\d{1,14}(\.\d{1,4})?$/;
const currency = /^[A-Z]{3}$/;
class QuoteItemDto { @IsUUID() internalSkuId!: string; @IsInt() @Min(1) quantity!: number; @IsInt() @Min(1) minOrderQty!: number; @Matches(money) unitPrice!: string; }
class CreateSupplierDto { @Matches(/^[A-Z0-9_-]{2,30}$/) code!: string; @IsString() @Length(2, 160) name!: string; @IsOptional() @IsString() @MaxLength(120) contactName?: string; @IsOptional() @IsEmail() @MaxLength(200) email?: string; @IsOptional() @IsString() @MaxLength(60) phone?: string; @IsOptional() @IsString() @MaxLength(200) paymentTerms?: string; }
class CreateQuoteDto { @IsUUID() supplierId!: string; @IsEnum(SupplierQuoteStatus) status!: SupplierQuoteStatus; @Matches(currency) currency!: string; @IsOptional() @IsDateString() validUntil?: string; @IsOptional() @IsInt() @Min(0) leadTimeDays?: number; @IsOptional() @IsString() @MaxLength(200) paymentTerms?: string; @IsOptional() @IsString() @MaxLength(2000) notes?: string; @IsArray() @ArrayMinSize(1) @ValidateNested({ each: true }) @Type(() => QuoteItemDto) items!: QuoteItemDto[]; }
class CreateContractDto { @IsUUID() supplierId!: string; @IsString() @Length(2, 160) title!: string; @IsEnum(SupplierContractStatus) status!: SupplierContractStatus; @IsDateString() startDate!: string; @IsOptional() @IsDateString() endDate?: string; @Matches(currency) currency!: string; @IsOptional() @Matches(money) value?: string; @IsOptional() @IsString() @MaxLength(5000) terms?: string; @IsOptional() @IsUrl({ require_protocol: true }) documentUrl?: string; }
class CreateReviewDto { @IsUUID() supplierId!: string; @IsDateString() periodStart!: string; @IsDateString() periodEnd!: string; @IsInt() @Min(1) @Max(5) qualityScore!: number; @IsInt() @Min(1) @Max(5) deliveryScore!: number; @IsInt() @Min(1) @Max(5) priceScore!: number; @IsInt() @Min(1) @Max(5) serviceScore!: number; @IsOptional() @IsString() @MaxLength(2000) notes?: string; }
class RequestItemDto { @IsUUID() internalSkuId!: string; @IsInt() @Min(1) quantity!: number; @IsOptional() @Matches(money) targetUnitCost?: string; @IsOptional() @IsString() @MaxLength(500) notes?: string; }
class CreateRequestDto { @IsString() @Length(2, 160) title!: string; @IsOptional() @IsUUID() departmentId?: string; @IsEnum(WorkTaskPriority) priority!: WorkTaskPriority; @IsOptional() @IsDateString() neededBy?: string; @IsOptional() @IsString() @MaxLength(2000) justification?: string; @IsArray() @ArrayMinSize(1) @ValidateNested({ each: true }) @Type(() => RequestItemDto) items!: RequestItemDto[]; }
class DecisionDto { @IsOptional() @IsString() @MaxLength(1000) note?: string; }
class ConvertRequestDto { @IsUUID() supplierId!: string; @IsUUID() warehouseId!: string; @IsOptional() @IsUUID() supplierQuoteId?: string; @Matches(currency) currency!: string; @IsOptional() @IsDateString() expectedAt?: string; }
class CreatePaymentDto { @IsUUID() purchaseOrderId!: string; @Matches(money) amount!: string; @Matches(currency) currency!: string; @IsOptional() @IsDateString() dueDate?: string; @IsOptional() @IsString() @MaxLength(120) paymentMethod?: string; }
class MarkPaidDto { @IsOptional() @IsString() @MaxLength(160) externalReference?: string; @IsOptional() @IsDateString() paidAt?: string; }
class UpsertPimDto { @IsEnum(ProductLifecycleStatus) lifecycleStatus!: ProductLifecycleStatus; @IsOptional() @IsString() @MaxLength(120) brand?: string; @IsOptional() @IsString() @MaxLength(120) category?: string; @IsOptional() @IsString() @MaxLength(10000) description?: string; @IsOptional() @IsObject() specifications?: Prisma.InputJsonObject; @IsOptional() @IsString() @MaxLength(32) hsCode?: string; @IsOptional() @IsString() @MaxLength(80) countryOfOrigin?: string; @IsOptional() @IsInt() @Min(0) weightGrams?: number; @IsOptional() @IsObject() dimensions?: Prisma.InputJsonObject; @IsOptional() @IsObject() seo?: Prisma.InputJsonObject; }
class AddMediaDto { @IsString() @Matches(/^(IMAGE|VIDEO|DOCUMENT)$/) type!: string; @IsUrl({ require_protocol: true }) url!: string; @IsOptional() @IsString() @MaxLength(300) altText?: string; @IsInt() @Min(0) position!: number; }
class DepartmentLinkDto { @IsUUID() departmentId!: string; @IsEnum(BusinessFunction) function!: BusinessFunction; @IsOptional() @IsString() @MaxLength(1000) ownerNote?: string; }
class JobItemDto { @IsUUID() orderItemId!: string; @IsInt() @Min(1) quantity!: number; }
class CreateJobDto { @IsUUID() orderId!: string; @IsUUID() warehouseId!: string; @IsEnum(WorkTaskPriority) priority!: WorkTaskPriority; @IsOptional() @IsDateString() promisedAt?: string; @IsOptional() @IsString() @MaxLength(1000) note?: string; @IsArray() @ArrayMinSize(1) @ValidateNested({ each: true }) @Type(() => JobItemDto) items!: JobItemDto[]; }
class JobStatusDto { @IsEnum(FulfillmentJobStatus) status!: FulfillmentJobStatus; @IsOptional() @IsString() @MaxLength(1000) note?: string; }
class ShipmentDto { @IsString() @Length(2, 100) carrier!: string; @IsOptional() @IsString() @MaxLength(100) service?: string; @IsString() @Length(2, 160) trackingNumber!: string; @IsOptional() @IsUrl({ require_protocol: true }) trackingUrl?: string; @IsOptional() @IsUrl({ require_protocol: true }) labelUrl?: string; @IsOptional() @Matches(money) shippingCost?: string; @IsOptional() @Matches(currency) currency?: string; }
class ShipmentEventDto { @IsEnum(ShipmentEventStatus) status!: ShipmentEventStatus; @IsDateString() occurredAt!: string; @IsOptional() @IsString() @MaxLength(160) location?: string; @IsOptional() @IsString() @MaxLength(1000) description?: string; }

@Injectable()
export class LifecycleService {
  constructor(private readonly db: DatabaseService, private readonly redis: RedisService, private readonly shopify: ShopifyService) {}
  private ids(req: AppRequest) { return req.allowedWarehouseIds == null ? undefined : { in: req.allowedWarehouseIds }; }
  private number(prefix: string) { return `${prefix}-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}-${randomUUID().slice(0, 8).toUpperCase()}`; }
  private audit(companyId: string, actorId: string, action: string, resourceType: string, resourceId: string, metadata?: Prisma.InputJsonObject) { return this.db.auditLog.create({ data: { companyId, actorId, action, resourceType, resourceId, metadata } }); }
  private async companyEntity(model: 'supplier' | 'internalSku' | 'department', id: string, companyId: string) {
    const row = model === 'supplier' ? await this.db.supplier.findFirst({ where: { id, companyId, active: true } }) : model === 'internalSku' ? await this.db.internalSku.findFirst({ where: { id, companyId } }) : await this.db.department.findFirst({ where: { id, companyId, active: true } });
    if (!row) throw new BadRequestException(`${model} không hợp lệ`);
    return row;
  }

  supplierWorkspace(req: AppRequest) {
    const companyId = actor(req).companyId;
    return Promise.all([
      this.db.supplier.findMany({ where: { companyId }, include: { quotes: { include: { items: { include: { internalSku: true } } }, orderBy: { createdAt: 'desc' }, take: 5 }, contracts: { orderBy: { endDate: 'desc' }, take: 5 }, reviews: { orderBy: { periodEnd: 'desc' }, take: 5 } }, orderBy: { name: 'asc' } }),
      this.db.supplierPerformanceReview.aggregate({ where: { companyId }, _avg: { overallScore: true } }),
    ]).then(([suppliers, performance]) => ({ suppliers, averageScore: performance._avg.overallScore }));
  }
  async createSupplier(req: AppRequest, dto: CreateSupplierDto) { const { companyId, userId } = actor(req); const row = await this.db.supplier.create({ data: { companyId, code: dto.code, name: dto.name.trim(), contactName: dto.contactName, email: dto.email, phone: dto.phone, paymentTerms: dto.paymentTerms } }); await this.audit(companyId, userId, 'supplier.create', 'supplier', row.id); return row; }
  async createQuote(req: AppRequest, dto: CreateQuoteDto) {
    const { companyId, userId } = actor(req); await this.companyEntity('supplier', dto.supplierId, companyId);
    if (new Set(dto.items.map(x => x.internalSkuId)).size !== dto.items.length) throw new BadRequestException('Mỗi SKU chỉ được có một dòng báo giá');
    const count = await this.db.internalSku.count({ where: { companyId, id: { in: dto.items.map(x => x.internalSkuId) } } });
    if (count !== dto.items.length) throw new BadRequestException('SKU không hợp lệ');
    const row = await this.db.supplierQuote.create({ data: { companyId, supplierId: dto.supplierId, number: this.number('RFQ'), status: dto.status, currency: dto.currency, validUntil: dto.validUntil ? new Date(dto.validUntil) : null, leadTimeDays: dto.leadTimeDays, paymentTerms: dto.paymentTerms, notes: dto.notes, createdById: userId, items: { create: dto.items } }, include: { items: true } });
    await this.audit(companyId, userId, 'supplier_quote.create', 'supplier_quote', row.id); return row;
  }
  async createContract(req: AppRequest, dto: CreateContractDto) {
    const { companyId, userId } = actor(req); await this.companyEntity('supplier', dto.supplierId, companyId);
    if (dto.endDate && new Date(dto.endDate) < new Date(dto.startDate)) throw new BadRequestException('Ngày kết thúc hợp đồng không hợp lệ');
    const row = await this.db.supplierContract.create({ data: { companyId, supplierId: dto.supplierId, number: this.number('CON'), title: dto.title.trim(), status: dto.status, startDate: new Date(dto.startDate), endDate: dto.endDate ? new Date(dto.endDate) : null, currency: dto.currency, value: dto.value, terms: dto.terms, documentUrl: dto.documentUrl, createdById: userId } });
    await this.audit(companyId, userId, 'supplier_contract.create', 'supplier_contract', row.id); return row;
  }
  async createReview(req: AppRequest, dto: CreateReviewDto) {
    const { companyId, userId } = actor(req); await this.companyEntity('supplier', dto.supplierId, companyId);
    if (new Date(dto.periodEnd) < new Date(dto.periodStart)) throw new BadRequestException('Kỳ đánh giá không hợp lệ');
    const overallScore = (dto.qualityScore + dto.deliveryScore + dto.priceScore + dto.serviceScore) / 4;
    const row = await this.db.supplierPerformanceReview.create({ data: { companyId, supplierId: dto.supplierId, periodStart: new Date(dto.periodStart), periodEnd: new Date(dto.periodEnd), qualityScore: dto.qualityScore, deliveryScore: dto.deliveryScore, priceScore: dto.priceScore, serviceScore: dto.serviceScore, overallScore, notes: dto.notes, reviewedById: userId } });
    await this.audit(companyId, userId, 'supplier_review.create', 'supplier_review', row.id, { overallScore }); return row;
  }

  procurementWorkspace(req: AppRequest) {
    const companyId = actor(req).companyId;
    return Promise.all([
      this.db.purchaseRequest.findMany({ where: { companyId }, include: { department: true, items: { include: { internalSku: true } }, purchaseOrders: { include: { supplier: true, warehouse: true } } }, orderBy: { createdAt: 'desc' }, take: 200 }),
      this.db.purchaseOrder.findMany({ where: { companyId, warehouseId: this.ids(req) }, include: { supplier: true, warehouse: true, items: { include: { internalSku: true } }, payments: true, movements: { where: { type: 'RECEIPT' }, include: { lines: true } } }, orderBy: { createdAt: 'desc' }, take: 200 }),
      this.db.procurementPayment.findMany({ where: { companyId, purchaseOrder: { warehouseId: this.ids(req) } }, include: { purchaseOrder: { select: { number: true } } }, orderBy: { createdAt: 'desc' }, take: 200 }),
    ]).then(([requests, purchaseOrders, payments]) => ({ requests, purchaseOrders, payments }));
  }
  async createRequest(req: AppRequest, dto: CreateRequestDto) {
    const { companyId, userId } = actor(req);
    if (dto.departmentId) await this.companyEntity('department', dto.departmentId, companyId);
    if (new Set(dto.items.map(x => x.internalSkuId)).size !== dto.items.length) throw new BadRequestException('Mỗi SKU chỉ được có một dòng yêu cầu');
    const count = await this.db.internalSku.count({ where: { companyId, id: { in: dto.items.map(x => x.internalSkuId) } } }); if (count !== dto.items.length) throw new BadRequestException('SKU không hợp lệ');
    const row = await this.db.purchaseRequest.create({ data: { companyId, number: this.number('PR'), title: dto.title.trim(), departmentId: dto.departmentId, priority: dto.priority, neededBy: dto.neededBy ? new Date(dto.neededBy) : null, justification: dto.justification, requestedById: userId, items: { create: dto.items } }, include: { items: true } });
    await this.audit(companyId, userId, 'purchase_request.create', 'purchase_request', row.id); return row;
  }
  async submitRequest(req: AppRequest, id: string) {
    const { companyId, userId } = actor(req); const row = await this.db.purchaseRequest.findFirst({ where: { id, companyId, status: 'DRAFT' } }); if (!row) throw new BadRequestException('Yêu cầu mua không ở trạng thái nháp');
    await this.db.$transaction([this.db.purchaseRequest.update({ where: { id }, data: { status: 'SUBMITTED' } }), this.db.approvalRequest.create({ data: { companyId, entityType: 'PURCHASE_REQUEST', entityId: id, requestedById: userId } }), this.audit(companyId, userId, 'purchase_request.submit', 'purchase_request', id)]); return { submitted: true };
  }
  async decideRequest(req: AppRequest, id: string, approve: boolean, note?: string) {
    const { companyId, userId } = actor(req); const row = await this.db.purchaseRequest.findFirst({ where: { id, companyId, status: 'SUBMITTED' } }); if (!row) throw new BadRequestException('Yêu cầu mua không chờ duyệt');
    await this.db.$transaction([this.db.purchaseRequest.update({ where: { id }, data: approve ? { status: 'APPROVED', approvedById: userId, approvedAt: new Date() } : { status: 'REJECTED' } }), this.db.approvalRequest.updateMany({ where: { entityType: 'PURCHASE_REQUEST', entityId: id, status: 'PENDING' }, data: { status: approve ? 'APPROVED' : 'REJECTED', decidedById: userId, decidedAt: new Date(), decisionNote: note } }), this.audit(companyId, userId, approve ? 'purchase_request.approve' : 'purchase_request.reject', 'purchase_request', id)]); return { approved: approve };
  }
  async convertRequest(req: AppRequest, id: string, dto: ConvertRequestDto) {
    const { companyId, userId } = actor(req); const request = await this.db.purchaseRequest.findFirst({ where: { id, companyId, status: 'APPROVED' }, include: { items: true } }); if (!request) throw new BadRequestException('Yêu cầu mua chưa được duyệt');
    const [supplier, warehouse, quote] = await Promise.all([this.db.supplier.findFirst({ where: { id: dto.supplierId, companyId, active: true } }), this.db.warehouse.findFirst({ where: { id: dto.warehouseId, companyId, active: true } }), dto.supplierQuoteId ? this.db.supplierQuote.findFirst({ where: { id: dto.supplierQuoteId, companyId, supplierId: dto.supplierId, status: { in: ['RECEIVED', 'ACCEPTED'] } }, include: { items: true } }) : null]);
    if (!supplier || !warehouse || (req.allowedWarehouseIds && !req.allowedWarehouseIds.includes(warehouse.id))) throw new BadRequestException('NCC hoặc kho không hợp lệ');
    const quotePrice = new Map(quote?.items.map(x => [x.internalSkuId, x.unitPrice]) || []);
    if (request.items.some(x => !quotePrice.has(x.internalSkuId) && !x.targetUnitCost)) throw new BadRequestException('Thiếu giá mục tiêu hoặc báo giá cho SKU');
    const po = await this.db.$transaction(async tx => {
      const claimed = await tx.purchaseRequest.updateMany({ where: { id, companyId, status: 'APPROVED' }, data: { status: 'CONVERTED' } });
      if (claimed.count !== 1) throw new BadRequestException('Yêu cầu mua đã được chuyển thành PO');
      const created = await tx.purchaseOrder.create({ data: { companyId, supplierId: supplier.id, warehouseId: warehouse.id, purchaseRequestId: request.id, supplierQuoteId: quote?.id, number: this.number('PO'), currency: dto.currency, expectedAt: dto.expectedAt ? new Date(dto.expectedAt) : request.neededBy, note: `Tạo từ ${request.number}`, items: { create: request.items.map(item => ({ internalSkuId: item.internalSkuId, quantity: item.quantity, unitCost: quotePrice.get(item.internalSkuId) || item.targetUnitCost! })) } }, include: { items: true } });
      await tx.auditLog.create({ data: { companyId, actorId: userId, action: 'purchase_request.convert', resourceType: 'purchase_order', resourceId: created.id, metadata: { purchaseRequestId: id } } }); return created;
    }, { isolationLevel: Prisma.TransactionIsolationLevel.Serializable }); return po;
  }
  async createPayment(req: AppRequest, dto: CreatePaymentDto) {
    const { companyId, userId } = actor(req); const po = await this.db.purchaseOrder.findFirst({ where: { id: dto.purchaseOrderId, companyId }, include: { items: true, payments: { where: { status: { not: 'VOID' } } } } }); if (!po) throw new BadRequestException('PO không hợp lệ');
    if (po.currency !== dto.currency) throw new BadRequestException('Tiền tệ thanh toán phải khớp PO');
    const total = po.items.reduce((sum, item) => sum + item.quantity * Number(item.unitCost), 0); const planned = po.payments.reduce((sum, item) => sum + Number(item.amount), 0);
    if (planned + Number(dto.amount) > total + 0.0001) throw new BadRequestException('Tổng thanh toán vượt giá trị PO');
    const row = await this.db.procurementPayment.create({ data: { companyId, purchaseOrderId: po.id, reference: this.number('PAY'), amount: dto.amount, currency: dto.currency, dueDate: dto.dueDate ? new Date(dto.dueDate) : null, paymentMethod: dto.paymentMethod, createdById: userId } }); await this.audit(companyId, userId, 'procurement_payment.create', 'procurement_payment', row.id); return row;
  }
  async paymentStatus(req: AppRequest, id: string, status: ProcurementPaymentStatus, dto?: MarkPaidDto) {
    const { companyId, userId } = actor(req); const row = await this.db.procurementPayment.findFirst({ where: { id, companyId } }); if (!row) throw new BadRequestException('Thanh toán không hợp lệ');
    const allowed: Record<ProcurementPaymentStatus, ProcurementPaymentStatus[]> = { PLANNED: ['PENDING_APPROVAL', 'VOID'], PENDING_APPROVAL: ['APPROVED', 'VOID'], APPROVED: ['PAID', 'VOID'], PAID: [], VOID: [] };
    if (!allowed[row.status].includes(status)) throw new BadRequestException(`Không thể chuyển ${row.status} sang ${status}`);
    const changed = await this.db.procurementPayment.updateMany({ where: { id, companyId, status: row.status }, data: { status, ...(status === 'APPROVED' ? { approvedById: userId } : {}), ...(status === 'PAID' ? { paidAt: dto?.paidAt ? new Date(dto.paidAt) : new Date(), externalReference: dto?.externalReference } : {}) } });
    if (changed.count !== 1) throw new BadRequestException('Trạng thái thanh toán vừa được thay đổi');
    await this.audit(companyId, userId, `procurement_payment.${status.toLowerCase()}`, 'procurement_payment', id); return this.db.procurementPayment.findUniqueOrThrow({ where: { id } });
  }

  pimWorkspace(req: AppRequest) { return this.db.internalSku.findMany({ where: { companyId: actor(req).companyId }, include: { pimProfile: { include: { media: { orderBy: { position: 'asc' } } } }, departmentLinks: { include: { department: true } }, mappings: { include: { variant: { include: { product: { include: { store: { select: { id: true, name: true, status: true } } } } } } } }, costs: { orderBy: { effectiveFrom: 'desc' }, take: 1 } }, orderBy: { code: 'asc' }, take: 500 }); }
  async upsertPim(req: AppRequest, id: string, dto: UpsertPimDto) {
    const { companyId, userId } = actor(req); await this.companyEntity('internalSku', id, companyId);
    const row = await this.db.pimProfile.upsert({ where: { internalSkuId: id }, create: { internalSkuId: id, updatedById: userId, ...dto }, update: { updatedById: userId, ...dto } }); await this.audit(companyId, userId, 'pim.update', 'internal_sku', id); return row;
  }
  async addMedia(req: AppRequest, id: string, dto: AddMediaDto) { const { companyId, userId } = actor(req); const profile = await this.db.pimProfile.findFirst({ where: { internalSkuId: id, internalSku: { companyId } } }); if (!profile) throw new BadRequestException('Hãy tạo hồ sơ PIM trước'); const row = await this.db.productMedia.create({ data: { pimProfileId: id, ...dto } }); await this.audit(companyId, userId, 'pim.media.add', 'product_media', row.id); return row; }
  async deleteMedia(req: AppRequest, id: string) { const { companyId, userId } = actor(req); const row = await this.db.productMedia.findFirst({ where: { id, pimProfile: { internalSku: { companyId } } } }); if (!row) throw new ForbiddenException(); await this.db.productMedia.delete({ where: { id } }); await this.audit(companyId, userId, 'pim.media.delete', 'product_media', id); return { deleted: true }; }
  async linkDepartment(req: AppRequest, id: string, dto: DepartmentLinkDto) { const { companyId, userId } = actor(req); await Promise.all([this.companyEntity('internalSku', id, companyId), this.companyEntity('department', dto.departmentId, companyId)]); const row = await this.db.productDepartmentLink.upsert({ where: { internalSkuId_function: { internalSkuId: id, function: dto.function } }, create: { internalSkuId: id, ...dto }, update: { departmentId: dto.departmentId, ownerNote: dto.ownerNote } }); await this.audit(companyId, userId, 'pim.department.link', 'internal_sku', id, { function: dto.function, departmentId: dto.departmentId }); return row; }
  async syncPim(req: AppRequest, id: string) {
    const { companyId, userId } = actor(req); const sku = await this.db.internalSku.findFirst({ where: { id, companyId }, include: { pimProfile: { include: { media: { orderBy: { position: 'asc' } } } }, mappings: { include: { variant: { include: { product: { include: { store: true } } } } } } } });
    if (!sku?.pimProfile) throw new BadRequestException('Hồ sơ PIM chưa hoàn chỉnh');
    const groups = new Map<string, typeof sku.mappings>(); for (const mapping of sku.mappings) { const list = groups.get(mapping.variant.productId) || []; list.push(mapping); groups.set(mapping.variant.productId, list); }
    const results: Array<{ storeId: string; productId: string; syncJobId: string }> = [];
    for (const mappings of groups.values()) {
      const product = mappings[0].variant.product; if (product.store.status !== 'CONNECTED') continue;
      const mutation = `mutation ProductUpdate($productId: ID!, $product: ProductUpdateInput!, $variants: [ProductVariantsBulkInput!]!) { productUpdate(product: $product) { userErrors { field message } } productVariantsBulkUpdate(productId: $productId, variants: $variants) { userErrors { field message } } }`;
      const profile = sku.pimProfile; const seo = profile.seo as { title?: string; description?: string } | null;
      const response = await graphql<{ productUpdate: { userErrors: Array<{ message: string }> }; productVariantsBulkUpdate: { userErrors: Array<{ message: string }> } }>(this.db, this.redis, product.storeId, mutation, { productId: product.shopifyId, product: { id: product.shopifyId, title: sku.name, descriptionHtml: profile.description, productType: profile.category, vendor: profile.brand, seo: seo || undefined }, variants: mappings.map(x => ({ id: x.variant.shopifyId, sku: sku.code, barcode: sku.barcode })) });
      const errors = [...response.productUpdate.userErrors, ...response.productVariantsBulkUpdate.userErrors]; if (errors.length) throw new BadRequestException(errors.map(x => x.message).join('; '));
      const job = await this.shopify.enqueue(product.storeId, 'PRODUCTS'); results.push({ storeId: product.storeId, productId: product.shopifyId, syncJobId: job.id });
    }
    if (!results.length) throw new BadRequestException('SKU chưa map với sản phẩm của store Shopify đã kết nối');
    await this.audit(companyId, userId, 'pim.shopify.sync', 'internal_sku', id, { stores: results.map(x => x.storeId) }); return { synced: results };
  }

  fulfillmentWorkspace(req: AppRequest) { return this.db.fulfillmentJob.findMany({ where: { companyId: actor(req).companyId, warehouseId: this.ids(req) }, include: { warehouse: true, order: { include: { store: { select: { name: true } } } }, items: { include: { orderItem: true, internalSku: true } }, shipment: { include: { events: { orderBy: { occurredAt: 'desc' } } } } }, orderBy: { createdAt: 'desc' }, take: 200 }); }
  async createJob(req: AppRequest, dto: CreateJobDto) {
    const { companyId, userId } = actor(req); const [warehouse, order] = await Promise.all([this.db.warehouse.findFirst({ where: { id: dto.warehouseId, companyId, active: true } }), this.db.order.findFirst({ where: { id: dto.orderId, store: { companyId } }, include: { items: { include: { variant: { include: { mapping: true } } } } } })]);
    if (!warehouse || !order || (req.allowedWarehouseIds && !req.allowedWarehouseIds.includes(warehouse.id))) throw new BadRequestException('Đơn hàng hoặc kho không hợp lệ');
    if (new Set(dto.items.map(x => x.orderItemId)).size !== dto.items.length) throw new BadRequestException('Mỗi dòng đơn chỉ được chọn một lần');
    const existing = await this.db.fulfillmentJobItem.groupBy({ by: ['orderItemId'], where: { fulfillmentJob: { orderId: order.id, status: { notIn: ['CANCELLED', 'RETURNED'] } } }, _sum: { quantity: true } }); const allocated = new Map(existing.map(x => [x.orderItemId, x._sum.quantity || 0]));
    const lines = dto.items.map(item => { const source = order.items.find(x => x.id === item.orderItemId); if (!source || item.quantity + (allocated.get(item.orderItemId) || 0) > source.quantity) throw new BadRequestException('Số lượng fulfillment vượt số lượng đơn'); return { orderItemId: source.id, internalSkuId: source.variant?.mapping?.internalSkuId, quantity: item.quantity }; });
    const row = await this.db.fulfillmentJob.create({ data: { companyId, orderId: order.id, warehouseId: warehouse.id, number: this.number('FUL'), priority: dto.priority, promisedAt: dto.promisedAt ? new Date(dto.promisedAt) : null, note: dto.note, createdById: userId, items: { create: lines } }, include: { items: true } }); await this.audit(companyId, userId, 'fulfillment.create', 'fulfillment_job', row.id); return row;
  }
  async jobStatus(req: AppRequest, id: string, dto: JobStatusDto) {
    const { companyId, userId } = actor(req);
    return this.db.$transaction(async tx => {
      const job = await tx.fulfillmentJob.findFirst({ where: { id, companyId, warehouseId: this.ids(req) }, include: { items: true, shipment: true } }); if (!job) throw new BadRequestException('Fulfillment không hợp lệ');
      const allowed: Record<FulfillmentJobStatus, FulfillmentJobStatus[]> = { OPEN: ['PICKING', 'CANCELLED'], PICKING: ['PACKED', 'EXCEPTION', 'CANCELLED'], PACKED: ['SHIPPED', 'EXCEPTION', 'CANCELLED'], SHIPPED: ['DELIVERED', 'EXCEPTION', 'RETURNED'], DELIVERED: ['RETURNED'], EXCEPTION: ['PICKING', 'PACKED', 'SHIPPED', 'CANCELLED', 'RETURNED'], CANCELLED: [], RETURNED: [] };
      if (!allowed[job.status].includes(dto.status)) throw new BadRequestException(`Không thể chuyển ${job.status} sang ${dto.status}`);
      if (dto.status === 'SHIPPED' && !job.shipment) throw new BadRequestException('Cần tạo vận đơn trước khi giao hàng');
      if (dto.status === 'SHIPPED') {
        const quantities = new Map<string, number>(); for (const item of job.items) { if (!item.internalSkuId) throw new BadRequestException('Dòng hàng chưa map SKU nội bộ'); quantities.set(item.internalSkuId, (quantities.get(item.internalSkuId) || 0) + item.quantity); }
        for (const [internalSkuId, quantity] of quantities) { const balance = await tx.warehouseBalance.findUnique({ where: { warehouseId_internalSkuId: { warehouseId: job.warehouseId, internalSkuId } } }); if (!balance || balance.quantity < quantity) throw new BadRequestException(`Không đủ tồn cho SKU ${internalSkuId}`); await tx.warehouseBalance.update({ where: { id: balance.id }, data: { quantity: { decrement: quantity } } }); }
      }
      const changed = await tx.fulfillmentJob.updateMany({ where: { id, status: job.status }, data: { status: dto.status, note: dto.note ?? job.note, ...(dto.status === 'PACKED' ? { packedAt: new Date() } : {}), ...(dto.status === 'SHIPPED' ? { shippedAt: new Date() } : {}), ...(dto.status === 'DELIVERED' ? { deliveredAt: new Date() } : {}) } });
      if (changed.count !== 1) throw new BadRequestException('Fulfillment vừa được xử lý bởi yêu cầu khác');
      await tx.auditLog.create({ data: { companyId, actorId: userId, action: `fulfillment.${dto.status.toLowerCase()}`, resourceType: 'fulfillment_job', resourceId: id } });
      return tx.fulfillmentJob.findUniqueOrThrow({ where: { id } });
    }, { isolationLevel: Prisma.TransactionIsolationLevel.Serializable });
  }
  async upsertShipment(req: AppRequest, id: string, dto: ShipmentDto) { const { companyId, userId } = actor(req); const job = await this.db.fulfillmentJob.findFirst({ where: { id, companyId, warehouseId: this.ids(req), status: { in: ['OPEN', 'PICKING', 'PACKED', 'EXCEPTION'] } } }); if (!job) throw new BadRequestException('Fulfillment không cho phép cập nhật vận đơn'); const row = await this.db.fulfillmentShipment.upsert({ where: { fulfillmentJobId: id }, create: { fulfillmentJobId: id, ...dto }, update: dto }); await this.audit(companyId, userId, 'fulfillment.shipment.update', 'fulfillment_job', id); return row; }
  async addShipmentEvent(req: AppRequest, id: string, dto: ShipmentEventDto) { const { companyId, userId } = actor(req); const shipment = await this.db.fulfillmentShipment.findFirst({ where: { id, fulfillmentJob: { companyId, warehouseId: this.ids(req) } } }); if (!shipment) throw new ForbiddenException(); const row = await this.db.shipmentEvent.create({ data: { shipmentId: id, status: dto.status, occurredAt: new Date(dto.occurredAt), location: dto.location, description: dto.description } }); const next: Partial<Record<ShipmentEventStatus, FulfillmentJobStatus>> = { DELIVERED: 'DELIVERED', EXCEPTION: 'EXCEPTION', RETURNED: 'RETURNED' }; if (next[dto.status]) await this.db.fulfillmentJob.update({ where: { id: shipment.fulfillmentJobId }, data: { status: next[dto.status], ...(dto.status === 'DELIVERED' ? { deliveredAt: new Date(dto.occurredAt) } : {}) } }); await this.audit(companyId, userId, 'fulfillment.tracking.event', 'shipment', id, { status: dto.status }); return row; }
}

@Controller('lifecycle')
export class LifecycleController {
  constructor(private readonly service: LifecycleService) {}
  @Get('suppliers') @RequirePermission('supplier.read') suppliers(@Req() req: AppRequest) { return this.service.supplierWorkspace(req); }
  @Post('suppliers') @RequirePermission('supplier.write') createSupplier(@Req() req: AppRequest, @Body() dto: CreateSupplierDto) { return this.service.createSupplier(req, dto); }
  @Post('suppliers/quotes') @RequirePermission('supplier.write') quote(@Req() req: AppRequest, @Body() dto: CreateQuoteDto) { return this.service.createQuote(req, dto); }
  @Post('suppliers/contracts') @RequirePermission('supplier.write') contract(@Req() req: AppRequest, @Body() dto: CreateContractDto) { return this.service.createContract(req, dto); }
  @Post('suppliers/reviews') @RequirePermission('supplier.write') review(@Req() req: AppRequest, @Body() dto: CreateReviewDto) { return this.service.createReview(req, dto); }
  @Get('procurement') @RequireWarehouseScopedPermission('procurement.read') procurement(@Req() req: AppRequest) { return this.service.procurementWorkspace(req); }
  @Post('procurement/requests') @RequirePermission('procurement.write') request(@Req() req: AppRequest, @Body() dto: CreateRequestDto) { return this.service.createRequest(req, dto); }
  @Post('procurement/requests/:id/submit') @RequirePermission('procurement.write') submit(@Req() req: AppRequest, @Param('id') id: string) { return this.service.submitRequest(req, id); }
  @Post('procurement/requests/:id/approve') @RequirePermission('procurement.approve') approve(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: DecisionDto) { return this.service.decideRequest(req, id, true, dto.note); }
  @Post('procurement/requests/:id/reject') @RequirePermission('procurement.approve') reject(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: DecisionDto) { return this.service.decideRequest(req, id, false, dto.note); }
  @Post('procurement/requests/:id/convert') @RequireWarehouseScopedPermission('procurement.write') convert(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: ConvertRequestDto) { return this.service.convertRequest(req, id, dto); }
  @Post('procurement/payments') @RequirePermission('finance.write') payment(@Req() req: AppRequest, @Body() dto: CreatePaymentDto) { return this.service.createPayment(req, dto); }
  @Post('procurement/payments/:id/submit') @RequirePermission('finance.write') submitPayment(@Req() req: AppRequest, @Param('id') id: string) { return this.service.paymentStatus(req, id, 'PENDING_APPROVAL'); }
  @Post('procurement/payments/:id/approve') @RequirePermission('finance.approve') approvePayment(@Req() req: AppRequest, @Param('id') id: string) { return this.service.paymentStatus(req, id, 'APPROVED'); }
  @Post('procurement/payments/:id/paid') @RequirePermission('finance.write') paid(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: MarkPaidDto) { return this.service.paymentStatus(req, id, 'PAID', dto); }
  @Get('pim') @RequirePermission('pim.read') pim(@Req() req: AppRequest) { return this.service.pimWorkspace(req); }
  @Patch('pim/:id') @RequirePermission('pim.write') updatePim(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: UpsertPimDto) { return this.service.upsertPim(req, id, dto); }
  @Post('pim/:id/media') @RequirePermission('pim.write') media(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: AddMediaDto) { return this.service.addMedia(req, id, dto); }
  @Delete('pim/media/:id') @RequirePermission('pim.write') deleteMedia(@Req() req: AppRequest, @Param('id') id: string) { return this.service.deleteMedia(req, id); }
  @Post('pim/:id/departments') @RequirePermission('pim.write') department(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: DepartmentLinkDto) { return this.service.linkDepartment(req, id, dto); }
  @Post('pim/:id/shopify-sync') @RequirePermission('pim.write') sync(@Req() req: AppRequest, @Param('id') id: string) { return this.service.syncPim(req, id); }
  @Get('fulfillment') @RequireWarehouseScopedPermission('fulfillment.read') jobs(@Req() req: AppRequest) { return this.service.fulfillmentWorkspace(req); }
  @Post('fulfillment') @RequireWarehouseScopedPermission('fulfillment.write') createJob(@Req() req: AppRequest, @Body() dto: CreateJobDto) { return this.service.createJob(req, dto); }
  @Patch('fulfillment/:id/status') @RequireWarehouseScopedPermission('fulfillment.write') status(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: JobStatusDto) { return this.service.jobStatus(req, id, dto); }
  @Post('fulfillment/:id/shipment') @RequireWarehouseScopedPermission('fulfillment.write') shipment(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: ShipmentDto) { return this.service.upsertShipment(req, id, dto); }
  @Post('fulfillment/shipments/:id/events') @RequireWarehouseScopedPermission('fulfillment.write') event(@Req() req: AppRequest, @Param('id') id: string, @Body() dto: ShipmentEventDto) { return this.service.addShipmentEvent(req, id, dto); }
}
