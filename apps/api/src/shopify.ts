import { BadRequestException, Controller, ForbiddenException, Get, Headers, Injectable, Logger, OnModuleDestroy, Param, Post, Query, Req, Res } from '@nestjs/common';
import { Prisma, SyncKind } from '@erp/database';
import { waitUntil } from '@vercel/functions';
import { Queue } from 'bullmq';
import { randomBytes } from 'node:crypto';
import type { Response } from 'express';
import { actor, AppRequest, DatabaseService, Public, RedisService, RequireStorePermission } from './core';
import { exchangeCode, graphqlWithToken, REQUIRED_SCOPES, saveToken, shopifyScopeError } from './shopify-client';
import { validShopDomain, verifyOAuthQuery, verifyWebhook } from './shopify-security';
import { processSync } from './worker';

const WEBHOOK_TOPICS = ['PRODUCTS_CREATE', 'PRODUCTS_UPDATE', 'PRODUCTS_DELETE', 'ORDERS_CREATE', 'ORDERS_UPDATED', 'ORDERS_CANCELLED', 'ORDERS_DELETE', 'REFUNDS_CREATE', 'FULFILLMENTS_CREATE', 'FULFILLMENTS_UPDATE', 'INVENTORY_LEVELS_UPDATE', 'APP_UNINSTALLED'];

@Injectable()
export class ShopifyService implements OnModuleDestroy {
  readonly queue: Queue;
  private readonly logger = new Logger(ShopifyService.name);
  constructor(private readonly db: DatabaseService, private readonly redis: RedisService) {
    this.queue = new Queue('shopify-sync', { connection: redis.duplicate({ maxRetriesPerRequest: 1, enableOfflineQueue: false }) });
  }
  async onModuleDestroy() { await this.queue.close(); }
  private configured() {
    if (!process.env.SHOPIFY_CLIENT_ID || !process.env.SHOPIFY_CLIENT_SECRET || !process.env.SHOPIFY_CALLBACK_URL || !process.env.SHOPIFY_WEBHOOK_URL) {
      throw new BadRequestException('Thiếu cấu hình Shopify OAuth và webhook trong biến môi trường');
    }
    if (!process.env.SHOPIFY_CALLBACK_URL.startsWith('https://') || !process.env.SHOPIFY_WEBHOOK_URL.startsWith('https://')) {
      throw new BadRequestException('Shopify callback và webhook cần HTTPS công khai');
    }
  }
  private async store(req: AppRequest, storeId: string) {
    const store = await this.db.store.findFirst({ where: { id: storeId, companyId: actor(req).companyId } });
    if (!store) throw new ForbiddenException('Store không thuộc công ty');
    return store;
  }
  async connect(req: AppRequest, storeId: string) {
    this.configured();
    const store = await this.store(req, storeId);
    if (!validShopDomain(store.shopifyDomain) || store.status === 'DISABLED') throw new BadRequestException('Store không hợp lệ hoặc đã tắt');
    const nonce = randomBytes(32).toString('base64url');
    await this.redis.set(`shopify:oauth:${nonce}`, JSON.stringify({ storeId, companyId: store.companyId, actorId: actor(req).userId, shop: store.shopifyDomain }), 'EX', 600, 'NX');
    const url = new URL(`https://${store.shopifyDomain}/admin/oauth/authorize`);
    url.searchParams.set('client_id', process.env.SHOPIFY_CLIENT_ID!);
    url.searchParams.set('scope', REQUIRED_SCOPES.join(','));
    url.searchParams.set('redirect_uri', process.env.SHOPIFY_CALLBACK_URL!);
    url.searchParams.set('state', nonce);
    return { authorizationUrl: url.toString() };
  }
  async callback(query: Record<string, unknown>) {
    this.configured();
    const { state, code, shop, timestamp } = query;
    if (typeof state !== 'string' || typeof code !== 'string' || !validShopDomain(shop) || typeof timestamp !== 'string') throw new BadRequestException('Shopify callback không hợp lệ');
    if (Math.abs(Date.now() / 1000 - Number(timestamp)) > 300) throw new BadRequestException('Shopify callback đã hết hạn');
    if (!verifyOAuthQuery(query, process.env.SHOPIFY_CLIENT_SECRET!)) throw new ForbiddenException('Shopify OAuth HMAC không hợp lệ');
    const stateData = await this.redis.getdel(`shopify:oauth:${state}`);
    if (!stateData) throw new ForbiddenException('Shopify OAuth state không hợp lệ hoặc đã dùng');
    const saved = JSON.parse(stateData) as { storeId: string; companyId: string; actorId: string; shop: string };
    if (saved.shop !== shop) throw new ForbiddenException('Shopify shop không khớp');
    const store = await this.db.store.findFirst({ where: { id: saved.storeId, companyId: saved.companyId, shopifyDomain: shop, status: { not: 'DISABLED' } } });
    if (!store) throw new ForbiddenException('Store không tồn tại');
    const token = await exchangeCode(shop, code);
    const scopeError = shopifyScopeError(token.scope.split(','));
    if (scopeError) throw new BadRequestException(scopeError);
    const verification = await graphqlWithToken<{ shop: { myshopifyDomain: string } }>(shop, token.access_token, '{ shop { myshopifyDomain } }');
    if (verification.shop.myshopifyDomain !== shop) throw new ForbiddenException('Shopify token thuộc shop khác');
    await this.registerWebhooks(shop, token.access_token);
    await this.db.$transaction(async tx => {
      await saveToken(tx, store.id, token);
      await tx.store.update({ where: { id: store.id }, data: { status: 'CONNECTED' } });
      await tx.auditLog.create({ data: { companyId: store.companyId, actorId: saved.actorId, action: 'shopify.connect', resourceType: 'store', resourceId: store.id,
        metadata: { scopes: token.scope.split(',') } } });
    });
    await this.enqueueMany(store.id, ['PRODUCTS', 'ORDERS', 'INVENTORY']);
    return store.id;
  }
  private async registerWebhooks(shop: string, token: string) {
    const existing = await graphqlWithToken<{ webhookSubscriptions: { nodes: Array<{ topic: string; uri: string }> } }>(shop, token,
      '{ webhookSubscriptions(first: 250) { nodes { topic uri } } }');
    const query = 'mutation($topic: WebhookSubscriptionTopic!, $webhookSubscription: WebhookSubscriptionInput!) { webhookSubscriptionCreate(topic: $topic, webhookSubscription: $webhookSubscription) { userErrors { message } webhookSubscription { id } } }';
    for (const topic of WEBHOOK_TOPICS) {
      if (existing.webhookSubscriptions.nodes.some(item => item.topic === topic && item.uri === process.env.SHOPIFY_WEBHOOK_URL)) continue;
      const result = await graphqlWithToken<{ webhookSubscriptionCreate: { userErrors: { message: string }[] } }>(shop, token, query,
        { topic, webhookSubscription: { uri: process.env.SHOPIFY_WEBHOOK_URL, format: 'JSON' } });
      const errors = result.webhookSubscriptionCreate.userErrors.map(x => x.message);
      if (errors.length && !errors.every(x => /already|taken|exists/i.test(x))) throw new Error(`Shopify webhook ${topic}: ${errors.join('; ')}`);
    }
  }
  private async dispatchSync(rows: Array<{ id: string; kind: SyncKind }>) {
    if (process.env.VERCEL) {
      this.logger.log(JSON.stringify({ event: 'shopify.sync.dispatch', runtime: 'vercel-wait-until', jobs: rows.map(row => ({ id: row.id, kind: row.kind })) }));
      try {
        waitUntil((async () => {
          for (const row of rows) {
            try { await processSync(row.id); }
            catch { /* processSync persists and logs a redacted FAILED reason. */ }
          }
        })());
      } catch {
        const reason = 'Không thể khởi tạo background task trên Vercel';
        await this.db.syncJob.updateMany({ where: { id: { in: rows.map(row => row.id) }, status: 'QUEUED' },
          data: { status: 'FAILED', error: `Không thể giao tác vụ cho Vercel background worker: ${reason}`, completedAt: new Date() } });
        throw new Error(reason);
      }
      return;
    }
    for (const row of rows) {
      try {
        await this.queue.add('sync', { syncJobId: row.id }, { jobId: row.id, attempts: 5,
          backoff: { type: 'exponential', delay: 2000 }, removeOnComplete: 1000, removeOnFail: 1000 });
      } catch {
        this.logger.warn(`Không thể đưa sync job ${row.id} vào BullMQ; worker sẽ thử phục hồi hàng đợi`);
      }
    }
  }
  private async enqueueMany(storeId: string, kinds: SyncKind[]) {
    const rows = await this.db.$transaction(kinds.map(kind => this.db.syncJob.create({ data: { storeId, kind } })));
    await this.dispatchSync(rows);
    return rows;
  }
  async enqueue(storeId: string, kind: SyncKind) {
    const [row] = await this.enqueueMany(storeId, [kind]);
    return row;
  }
  async manualSync(req: AppRequest, storeId: string, kind: SyncKind) {
    const store = await this.store(req, storeId);
    if (store.status !== 'CONNECTED') throw new BadRequestException('Store chưa kết nối Shopify');
    if (!['PRODUCTS', 'ORDERS', 'INVENTORY'].includes(kind)) throw new BadRequestException('Loại đồng bộ không hợp lệ');
    return this.enqueue(store.id, kind);
  }
  async fullSync(req: AppRequest, storeId: string) {
    const store = await this.store(req, storeId);
    if (store.status !== 'CONNECTED') throw new BadRequestException('Store chưa kết nối Shopify');
    return this.enqueueMany(store.id, ['PRODUCTS', 'ORDERS', 'INVENTORY']);
  }
  async jobs(req: AppRequest, storeId: string) {
    await this.store(req, storeId);
    await this.db.syncJob.updateMany({ where: { storeId, status: 'QUEUED', createdAt: { lt: new Date(Date.now() - 5 * 60_000) } },
      data: { status: 'FAILED', completedAt: new Date(), error: 'Không có worker production nhận tác vụ trong 5 phút. Hãy bấm Thử lại để chạy bằng cơ chế background mới.' } });
    return this.db.syncJob.findMany({ where: { storeId }, orderBy: { createdAt: 'desc' }, take: 50 });
  }
  async retry(req: AppRequest, storeId: string, id: string) {
    await this.store(req, storeId);
    const row = await this.db.syncJob.findFirst({ where: { id, storeId, status: 'FAILED' } });
    if (!row) throw new BadRequestException('Sync job không ở trạng thái lỗi');
    const queued = await this.db.syncJob.update({ where: { id }, data: { status: 'QUEUED', error: null, startedAt: null, completedAt: null } });
    await this.dispatchSync([queued]);
    return { queued: true, id: queued.id };
  }
  async webhook(req: AppRequest, hmac: string | undefined, webhookId: string | undefined, topic: string | undefined, shop: string | undefined) {
    if (!req.rawBody || !hmac || !process.env.SHOPIFY_CLIENT_SECRET || !verifyWebhook(req.rawBody, hmac, process.env.SHOPIFY_CLIENT_SECRET)) throw new ForbiddenException('Webhook HMAC không hợp lệ');
    if (!validShopDomain(shop) || !webhookId || !topic || !/^[\w/-]{1,100}$/.test(topic)) throw new BadRequestException('Webhook header không hợp lệ');
    const store = await this.db.store.findUnique({ where: { shopifyDomain: shop } });
    if (!store || store.status !== 'CONNECTED') return { accepted: true };
    let payload: Prisma.InputJsonValue;
    try { payload = JSON.parse(req.rawBody.toString('utf8')) as Prisma.InputJsonValue; } catch { throw new BadRequestException('Webhook JSON không hợp lệ'); }
    const existing = await this.db.webhookEvent.findUnique({ where: { storeId_webhookId: { storeId: store.id, webhookId } } });
    if (existing) return { accepted: true, duplicate: true };
    const row = await this.db.webhookEvent.create({ data: { storeId: store.id, webhookId, topic, payload } });
    try { await this.queue.add('webhook', { webhookEventId: row.id }, { jobId: row.id, attempts: 5, backoff: { type: 'exponential', delay: 2000 }, removeOnComplete: 1000 }); }
    catch { /* Persisted event is recovered by worker's reconciliation loop. */ }
    return { accepted: true };
  }
}

@Controller('stores/:storeId/shopify')
export class StoreShopifyController {
  constructor(private readonly shopify: ShopifyService) {}
  @Post('connect') @RequireStorePermission('stores.write') connect(@Req() req: AppRequest, @Param('storeId') id: string) { return this.shopify.connect(req, id); }
  @Get('sync-jobs') @RequireStorePermission('stores.read') jobs(@Req() req: AppRequest, @Param('storeId') id: string) { return this.shopify.jobs(req, id); }
  @Post('sync') @RequireStorePermission('stores.write') fullSync(@Req() req: AppRequest, @Param('storeId') id: string) { return this.shopify.fullSync(req, id); }
  @Post('sync/:kind') @RequireStorePermission('stores.write') sync(@Req() req: AppRequest, @Param('storeId') id: string, @Param('kind') kind: SyncKind) { return this.shopify.manualSync(req, id, kind); }
  @Post('sync-jobs/:id/retry') @RequireStorePermission('stores.write') retry(@Req() req: AppRequest, @Param('storeId') storeId: string, @Param('id') id: string) { return this.shopify.retry(req, storeId, id); }
}

@Controller('shopify')
export class ShopifyCallbackController {
  private readonly logger = new Logger(ShopifyCallbackController.name);
  constructor(private readonly shopify: ShopifyService) {}
  @Public() @Get('callback') async callback(@Query() query: Record<string, unknown>, @Res() response: Response) {
    try {
      await this.shopify.callback(query);
      response.redirect(`${process.env.APP_ORIGIN}/dashboard/stores?shopify=connected`);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Shopify connection failed';
      this.logger.error(`Shopify OAuth callback failed: ${message}`, error instanceof Error ? error.stack : undefined);
      response.redirect(`${process.env.APP_ORIGIN}/dashboard/stores?shopify_error=${encodeURIComponent(message.slice(0, 1500))}`);
    }
  }
  @Public() @Post('webhook') webhook(@Req() req: AppRequest,
    @Headers('x-shopify-hmac-sha256') hmac?: string, @Headers('x-shopify-webhook-id') id?: string,
    @Headers('x-shopify-topic') topic?: string, @Headers('x-shopify-shop-domain') shop?: string) {
    return this.shopify.webhook(req, hmac, id, topic, shop);
  }
}
