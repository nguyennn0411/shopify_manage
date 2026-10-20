import { BadRequestException, Body, Controller, ForbiddenException, Get, Injectable, Param, Patch, Post, Query, Req } from '@nestjs/common';
import { Prisma } from '@erp/database';
import { IsDateString, IsOptional, IsString, IsUUID, Length, Matches, MaxLength } from 'class-validator';
import { actor, AppRequest, DatabaseService, RequirePermission } from './core';

const moneyPattern = /^\d{1,14}(\.\d{1,4})?$/;
class CostQuery { @IsOptional() @IsUUID() internalSkuId?: string; }
class CreateCostDto {
  @IsUUID() internalSkuId!: string;
  @IsString() @Matches(moneyPattern) amount!: string;
  @Matches(/^[A-Z]{3}$/) currency!: string;
  @IsDateString() effectiveFrom!: string;
  @IsOptional() @IsString() @MaxLength(500) note?: string;
}
class CreateCategoryDto { @IsString() @Length(2, 80) name!: string; @Matches(/^[A-Z0-9_]{2,40}$/) code!: string; }
class ExpenseQuery {
  @IsOptional() @IsUUID() storeId?: string;
  @IsOptional() @IsString() status?: 'DRAFT' | 'APPROVED' | 'VOID';
  @IsOptional() @IsDateString() from?: string;
  @IsOptional() @IsDateString() to?: string;
}
class CreateExpenseDto {
  @IsUUID() categoryId!: string;
  @IsOptional() @IsUUID() storeId?: string;
  @IsOptional() @IsUUID() orderId?: string;
  @IsString() @Matches(moneyPattern) amount!: string;
  @Matches(/^[A-Z]{3}$/) currency!: string;
  @IsDateString() expenseDate!: string;
  @IsString() @Length(2, 500) description!: string;
  @IsOptional() @IsString() @MaxLength(120) reference?: string;
}
class ProfitQuery { @IsOptional() @IsUUID() storeId?: string; @IsOptional() @IsDateString() from?: string; @IsOptional() @IsDateString() to?: string; }

@Injectable()
export class FinanceService {
  constructor(private readonly db: DatabaseService) {}
  async costs(req: AppRequest, query: CostQuery) {
    const { companyId } = actor(req);
    return this.db.costPriceHistory.findMany({ where: { internalSku: { companyId }, ...(query.internalSkuId ? { internalSkuId: query.internalSkuId } : {}) },
      include: { internalSku: { select: { id: true, code: true, name: true } } }, orderBy: [{ effectiveFrom: 'desc' }, { createdAt: 'desc' }], take: 500 });
  }
  async createCost(req: AppRequest, dto: CreateCostDto) {
    const { companyId, userId } = actor(req);
    const sku = await this.db.internalSku.findFirst({ where: { id: dto.internalSkuId, companyId } });
    if (!sku) throw new ForbiddenException('SKU không thuộc công ty');
    const cost = await this.db.costPriceHistory.create({ data: { internalSkuId: sku.id, amount: dto.amount, currency: dto.currency,
      effectiveFrom: new Date(dto.effectiveFrom), note: dto.note, createdById: userId } });
    await this.db.auditLog.create({ data: { companyId, actorId: userId, action: 'cost.create', resourceType: 'internal_sku', resourceId: sku.id,
      metadata: { costId: cost.id, amount: dto.amount, currency: dto.currency, effectiveFrom: dto.effectiveFrom } } });
    return cost;
  }
  categories(req: AppRequest) { return this.db.expenseCategory.findMany({ where: { companyId: actor(req).companyId, active: true }, orderBy: { name: 'asc' } }); }
  async createCategory(req: AppRequest, dto: CreateCategoryDto) {
    const { companyId, userId } = actor(req);
    const category = await this.db.expenseCategory.create({ data: { companyId, name: dto.name.trim(), code: dto.code } });
    await this.db.auditLog.create({ data: { companyId, actorId: userId, action: 'expense_category.create', resourceType: 'expense_category', resourceId: category.id } });
    return category;
  }
  async expenses(req: AppRequest, query: ExpenseQuery) {
    const { companyId } = actor(req);
    return this.db.expense.findMany({ where: { companyId, ...(query.storeId ? { storeId: query.storeId } : {}), ...(query.status ? { status: query.status } : {}),
      ...((query.from || query.to) ? { expenseDate: { ...(query.from ? { gte: new Date(query.from) } : {}), ...(query.to ? { lte: new Date(query.to) } : {}) } } : {}) },
      include: { category: true, store: { select: { id: true, name: true } }, order: { select: { id: true, name: true } } }, orderBy: { expenseDate: 'desc' }, take: 500 });
  }
  async createExpense(req: AppRequest, dto: CreateExpenseDto) {
    const { companyId, userId } = actor(req);
    const [category, store, order] = await Promise.all([
      this.db.expenseCategory.findFirst({ where: { id: dto.categoryId, companyId, active: true } }),
      dto.storeId ? this.db.store.findFirst({ where: { id: dto.storeId, companyId } }) : Promise.resolve(null),
      dto.orderId ? this.db.order.findFirst({ where: { id: dto.orderId, store: { companyId } } }) : Promise.resolve(null),
    ]);
    if (!category || (dto.storeId && !store) || (dto.orderId && !order)) throw new BadRequestException('Category, store hoặc order không hợp lệ');
    if (order && dto.storeId && order.storeId !== dto.storeId) throw new BadRequestException('Order không thuộc store đã chọn');
    if (order && order.currency !== dto.currency) throw new BadRequestException('Tiền tệ expense phải trùng tiền tệ order');
    const expense = await this.db.expense.create({ data: { companyId, categoryId: category.id, storeId: dto.storeId || order?.storeId,
      orderId: order?.id, amount: dto.amount, currency: dto.currency, expenseDate: new Date(dto.expenseDate), description: dto.description.trim(),
      reference: dto.reference?.trim(), createdById: userId } });
    await this.db.auditLog.create({ data: { companyId, actorId: userId, action: 'expense.create', resourceType: 'expense', resourceId: expense.id } });
    return expense;
  }
  async approveExpense(req: AppRequest, id: string) {
    const { companyId, userId } = actor(req);
    const expense = await this.db.expense.findFirst({ where: { id, companyId, status: 'DRAFT' } });
    if (!expense) throw new BadRequestException('Expense không ở trạng thái chờ duyệt');
    const updated = await this.db.expense.update({ where: { id }, data: { status: 'APPROVED', approvedById: userId, approvedAt: new Date() } });
    await this.db.auditLog.create({ data: { companyId, actorId: userId, action: 'expense.approve', resourceType: 'expense', resourceId: id } });
    if (expense.orderId) await this.calculateOrder(expense.orderId);
    return updated;
  }
  async voidExpense(req: AppRequest, id: string) {
    const { companyId, userId } = actor(req);
    const expense = await this.db.expense.findFirst({ where: { id, companyId, status: { not: 'VOID' } } });
    if (!expense) throw new BadRequestException('Expense không tồn tại hoặc đã void');
    const updated = await this.db.expense.update({ where: { id }, data: { status: 'VOID' } });
    await this.db.auditLog.create({ data: { companyId, actorId: userId, action: 'expense.void', resourceType: 'expense', resourceId: id } });
    if (expense.orderId) await this.calculateOrder(expense.orderId);
    return updated;
  }
  async calculateOrder(orderId: string) {
    const order = await this.db.order.findUniqueOrThrow({ where: { id: orderId }, include: {
      items: { include: { variant: { include: { mapping: true } } } }, refunds: { include: { items: true } },
      expenses: { where: { status: 'APPROVED' } }, profitSnapshot: true,
    } });
    let incompleteCost = false;
    let cogs = new Prisma.Decimal(0);
    for (const item of order.items) {
      let unitCost = item.costUnitSnapshot;
      if (!unitCost && item.variant?.mapping) {
        const cost = await this.db.costPriceHistory.findFirst({ where: { internalSkuId: item.variant.mapping.internalSkuId,
          effectiveFrom: { lte: order.shopifyCreatedAt }, currency: order.currency }, orderBy: { effectiveFrom: 'desc' } });
        if (cost) {
          unitCost = cost.amount;
          await this.db.orderItem.update({ where: { id: item.id }, data: { costUnitSnapshot: unitCost, costTotalSnapshot: unitCost.mul(item.quantity) } });
        }
      }
      if (!unitCost) incompleteCost = true;
      else cogs = cogs.add(unitCost.mul(item.quantity));
    }
    let refundAmount = new Prisma.Decimal(0);
    let costReversal = new Prisma.Decimal(0);
    for (const refund of order.refunds) {
      refundAmount = refundAmount.add(refund.amount);
      for (const refundItem of refund.items) {
        const orderItem = order.items.find(item => item.id === refundItem.orderItemId);
        const reversal = orderItem?.costUnitSnapshot?.mul(refundItem.quantity) || new Prisma.Decimal(0);
        costReversal = costReversal.add(reversal);
        await this.db.refundLineItem.update({ where: { id: refundItem.id }, data: { costReversal: reversal } });
      }
    }
    cogs = Prisma.Decimal.max(0, cogs.sub(costReversal));
    const grossRevenue = order.subtotal.add(order.totalDiscount);
    const netRevenue = grossRevenue.sub(order.totalDiscount).sub(refundAmount).add(order.totalShipping);
    const expenseTotal = order.expenses.reduce((sum, expense) => sum.add(expense.amount), new Prisma.Decimal(0));
    const transactionFees = order.profitSnapshot?.transactionFees || new Prisma.Decimal(0);
    const netProfit = netRevenue.sub(cogs).sub(transactionFees).sub(expenseTotal);
    const marginPercent = netRevenue.isZero() ? new Prisma.Decimal(0) : netProfit.div(netRevenue).mul(100);
    return this.db.profitSnapshot.upsert({ where: { orderId }, create: { orderId, currency: order.currency, grossRevenue,
      discounts: order.totalDiscount, refunds: refundAmount, shippingIncome: order.totalShipping, taxes: order.totalTax, netRevenue, cogs,
      transactionFees, expenses: expenseTotal, netProfit, marginPercent, incompleteCost }, update: { currency: order.currency, grossRevenue,
      discounts: order.totalDiscount, refunds: refundAmount, shippingIncome: order.totalShipping, taxes: order.totalTax, netRevenue, cogs,
      transactionFees, expenses: expenseTotal, netProfit, marginPercent, incompleteCost, calculatedAt: new Date() } });
  }
  async recalculate(req: AppRequest, query: ProfitQuery) {
    const { companyId, userId } = actor(req);
    const orders = await this.db.order.findMany({ where: { store: { companyId }, ...(query.storeId ? { storeId: query.storeId } : {}),
      ...((query.from || query.to) ? { shopifyCreatedAt: { ...(query.from ? { gte: new Date(query.from) } : {}), ...(query.to ? { lte: new Date(query.to) } : {}) } } : {}) },
      select: { id: true }, orderBy: { shopifyCreatedAt: 'desc' }, take: 500 });
    for (const order of orders) await this.calculateOrder(order.id);
    await this.db.auditLog.create({ data: { companyId, actorId: userId, action: 'profit.recalculate', resourceType: 'profit', resourceId: companyId, metadata: { count: orders.length } } });
    return { recalculated: orders.length, limited: orders.length === 500 };
  }
  async report(req: AppRequest, query: ProfitQuery) {
    const { companyId } = actor(req);
    const snapshots = await this.db.profitSnapshot.findMany({ where: { order: { store: { companyId }, ...(query.storeId ? { storeId: query.storeId } : {}),
      ...((query.from || query.to) ? { shopifyCreatedAt: { ...(query.from ? { gte: new Date(query.from) } : {}), ...(query.to ? { lte: new Date(query.to) } : {}) } } : {}) } },
      include: { order: { select: { id: true, name: true, shopifyCreatedAt: true, store: { select: { id: true, name: true } } } } }, orderBy: { order: { shopifyCreatedAt: 'desc' } }, take: 500 });
    const groups = new Map<string, { currency: string; netRevenue: Prisma.Decimal; cogs: Prisma.Decimal; expenses: Prisma.Decimal; netProfit: Prisma.Decimal; orders: number; incomplete: number }>();
    for (const item of snapshots) {
      const group = groups.get(item.currency) || { currency: item.currency, netRevenue: new Prisma.Decimal(0), cogs: new Prisma.Decimal(0), expenses: new Prisma.Decimal(0), netProfit: new Prisma.Decimal(0), orders: 0, incomplete: 0 };
      group.netRevenue = group.netRevenue.add(item.netRevenue); group.cogs = group.cogs.add(item.cogs); group.expenses = group.expenses.add(item.expenses); group.netProfit = group.netProfit.add(item.netProfit); group.orders++; if (item.incompleteCost) group.incomplete++;
      groups.set(item.currency, group);
    }
    return { summary: [...groups.values()], data: snapshots };
  }
}

@Controller('finance')
export class FinanceController {
  constructor(private readonly finance: FinanceService) {}
  @Get('costs') @RequirePermission('finance.read') costs(@Req() req: AppRequest, @Query() query: CostQuery) { return this.finance.costs(req, query); }
  @Post('costs') @RequirePermission('finance.write') createCost(@Req() req: AppRequest, @Body() dto: CreateCostDto) { return this.finance.createCost(req, dto); }
  @Get('expense-categories') @RequirePermission('finance.read') categories(@Req() req: AppRequest) { return this.finance.categories(req); }
  @Post('expense-categories') @RequirePermission('finance.write') createCategory(@Req() req: AppRequest, @Body() dto: CreateCategoryDto) { return this.finance.createCategory(req, dto); }
  @Get('expenses') @RequirePermission('finance.read') expenses(@Req() req: AppRequest, @Query() query: ExpenseQuery) { return this.finance.expenses(req, query); }
  @Post('expenses') @RequirePermission('finance.write') createExpense(@Req() req: AppRequest, @Body() dto: CreateExpenseDto) { return this.finance.createExpense(req, dto); }
  @Post('expenses/:id/approve') @RequirePermission('finance.approve') approve(@Req() req: AppRequest, @Param('id') id: string) { return this.finance.approveExpense(req, id); }
  @Patch('expenses/:id/void') @RequirePermission('finance.approve') void(@Req() req: AppRequest, @Param('id') id: string) { return this.finance.voidExpense(req, id); }
  @Post('profit/recalculate') @RequirePermission('finance.write') recalculate(@Req() req: AppRequest, @Query() query: ProfitQuery) { return this.finance.recalculate(req, query); }
  @Get('profit') @RequirePermission('finance.read') report(@Req() req: AppRequest, @Query() query: ProfitQuery) { return this.finance.report(req, query); }
}
