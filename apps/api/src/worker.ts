import { config as loadEnv } from 'dotenv';
import { resolve } from 'node:path';
import { Job, Queue, Worker } from 'bullmq';
import Redis from 'ioredis';
import { PrismaClient } from '@erp/database';
import { graphql } from './shopify-client';
import { evaluateCompanyAlerts } from './analytics';

loadEnv({ path: resolve(__dirname, '../../../.env') });
if (!process.env.DATABASE_URL || !process.env.REDIS_URL) throw new Error('DATABASE_URL and REDIS_URL are required');

const db = new PrismaClient();
const connection = new Redis(process.env.REDIS_URL, { maxRetriesPerRequest: null });

type SyncTrace = (stage: string, details?: Record<string, unknown>) => void;

function safeSyncError(error: unknown) {
  const message = error instanceof Error ? error.message : 'Unknown Shopify sync error';
  return message
    .replace(/shp[a-z]*_[A-Za-z0-9_-]+/gi, '[REDACTED]')
    .replace(/(access[_ -]?token|client[_ -]?secret|authorization[_ -]?code)\s*[:=]\s*[^\s,;]+/gi, '$1=[REDACTED]')
    .slice(0, 4000);
}

function writeSyncTrace(syncJobId: string, kind: string, stage: string, details: Record<string, unknown> = {}) {
  console.log(JSON.stringify({ event: 'shopify.sync', syncJobId, kind, stage, ...details }));
}

type Page<T> = { nodes: T[]; pageInfo: { hasNextPage: boolean; endCursor: string | null } };
type ProductNode = {
  id: string; title: string; handle: string | null; status: string; productType: string | null; vendor: string | null; updatedAt: string;
  featuredMedia: { preview: { image: { url: string } | null } | null } | null;
  variants: { nodes: Array<{ id: string; title: string; sku: string | null; barcode: string | null; price: string; position: number; inventoryItem: { id: string } | null }> };
};
type OrderNode = {
  id: string; name: string; email: string | null; displayFinancialStatus: string | null; displayFulfillmentStatus: string | null; currencyCode: string;
  subtotalPriceSet: MoneySet; totalPriceSet: MoneySet; totalTaxSet: MoneySet | null; totalShippingPriceSet: MoneySet; totalDiscountsSet: MoneySet;
  cancelledAt: string | null; closedAt: string | null; createdAt: string; updatedAt: string;
  tags: string[]; note: string | null; shippingAddress: Record<string, string | null> | null; billingAddress: Record<string, string | null> | null;
  customer: { id: string; displayName: string; email: string | null; phone: string | null; numberOfOrders: string | number; amountSpent: { amount: string; currencyCode: string } } | null;
  lineItems: { nodes: Array<{ id: string; title: string; sku: string | null; quantity: number; originalUnitPriceSet: MoneySet; totalDiscountSet: MoneySet; variant: { id: string } | null }> };
  refunds: Array<{ id: string; createdAt: string; note: string | null; totalRefundedSet: MoneySet; refundLineItems: { nodes: Array<{ quantity: number; subtotalSet: MoneySet; lineItem: { id: string } | null }> } }>;
  fulfillments: Array<{ id: string; status: string; createdAt: string; updatedAt: string; trackingInfo: Array<{ company: string | null; number: string | null; url: string | null }>;
    fulfillmentLineItems: { nodes: Array<{ quantity: number; lineItem: { id: string } }> } }>;
};
type MoneySet = { shopMoney: { amount: string; currencyCode: string } };
type InventoryNode = { id: string; variant: { id: string } | null; inventoryLevels: { nodes: Array<{ location: { id: string; name: string }; quantities: Array<{ name: string; quantity: number }> }> } };

const PRODUCT_FIELDS = `id title handle status productType vendor updatedAt featuredMedia { preview { image { url } } }
  variants(first: 250) { nodes { id title sku barcode price position inventoryItem { id } } }`;
const ORDER_FIELDS = `id name email displayFinancialStatus displayFulfillmentStatus currencyCode cancelledAt closedAt createdAt updatedAt tags note
  shippingAddress { address1 address2 city province provinceCode country countryCodeV2 zip phone name company }
  billingAddress { address1 address2 city province provinceCode country countryCodeV2 zip phone name company }
  customer { id displayName email phone numberOfOrders amountSpent { amount currencyCode } }
  subtotalPriceSet { shopMoney { amount currencyCode } } totalPriceSet { shopMoney { amount currencyCode } }
  totalTaxSet { shopMoney { amount currencyCode } } totalShippingPriceSet { shopMoney { amount currencyCode } }
  totalDiscountsSet { shopMoney { amount currencyCode } }
  lineItems(first: 250) { nodes { id title sku quantity originalUnitPriceSet { shopMoney { amount currencyCode } }
    totalDiscountSet { shopMoney { amount currencyCode } } variant { id } } }
  refunds { id createdAt note totalRefundedSet { shopMoney { amount currencyCode } }
    refundLineItems(first: 250) { nodes { quantity subtotalSet { shopMoney { amount currencyCode } } lineItem { id } } } }
  fulfillments { id status createdAt updatedAt trackingInfo(first: 10) { company number url }
    fulfillmentLineItems(first: 250) { nodes { quantity lineItem { id } } } }`;
const INVENTORY_FIELDS = `id variant { id } inventoryLevels(first: 100) { nodes { location { id name } quantities(names: ["available", "on_hand"]) { name quantity } } }`;

async function upsertProduct(storeId: string, currency: string, node: ProductNode) {
  await db.$transaction(async tx => {
    const product = await tx.product.upsert({ where: { storeId_shopifyId: { storeId, shopifyId: node.id } },
      create: { storeId, shopifyId: node.id, title: node.title, handle: node.handle, status: node.status, productType: node.productType,
        vendor: node.vendor, imageUrl: node.featuredMedia?.preview?.image?.url, shopifyUpdatedAt: new Date(node.updatedAt) },
      update: { title: node.title, handle: node.handle, status: node.status, productType: node.productType, vendor: node.vendor,
        imageUrl: node.featuredMedia?.preview?.image?.url, shopifyUpdatedAt: new Date(node.updatedAt) } });
    const currentIds = node.variants.nodes.map(variant => variant.id);
    await tx.variant.deleteMany({ where: { productId: product.id, ...(currentIds.length ? { shopifyId: { notIn: currentIds } } : {}) } });
    for (const variant of node.variants.nodes) {
      await tx.variant.upsert({ where: { shopifyId: variant.id },
        create: { productId: product.id, shopifyId: variant.id, inventoryItemId: variant.inventoryItem?.id, title: variant.title,
          sku: variant.sku, barcode: variant.barcode, price: variant.price, currency, position: variant.position },
        update: { productId: product.id, inventoryItemId: variant.inventoryItem?.id, title: variant.title, sku: variant.sku,
          barcode: variant.barcode, price: variant.price, currency, position: variant.position } });
    }
  });
}

async function upsertOrder(storeId: string, node: OrderNode) {
  await db.$transaction(async tx => {
    const customer = node.customer ? await tx.customer.upsert({ where: { storeId_shopifyId: { storeId, shopifyId: node.customer.id } },
      create: { storeId, shopifyId: node.customer.id, displayName: node.customer.displayName, email: node.customer.email, phone: node.customer.phone,
        ordersCount: Number(node.customer.numberOfOrders) || 0, totalSpent: node.customer.amountSpent.amount, currency: node.customer.amountSpent.currencyCode || node.currencyCode },
      update: { displayName: node.customer.displayName, email: node.customer.email, phone: node.customer.phone,
        ordersCount: Number(node.customer.numberOfOrders) || 0, totalSpent: node.customer.amountSpent.amount, currency: node.customer.amountSpent.currencyCode || node.currencyCode } }) : null;
    const order = await tx.order.upsert({ where: { storeId_shopifyId: { storeId, shopifyId: node.id } },
      create: { storeId, shopifyId: node.id, name: node.name, email: node.email, financialStatus: node.displayFinancialStatus,
        fulfillmentStatus: node.displayFulfillmentStatus, currency: node.currencyCode, subtotal: node.subtotalPriceSet.shopMoney.amount,
        total: node.totalPriceSet.shopMoney.amount, totalTax: node.totalTaxSet?.shopMoney.amount || '0',
        totalShipping: node.totalShippingPriceSet.shopMoney.amount, totalDiscount: node.totalDiscountsSet.shopMoney.amount,
        cancelledAt: node.cancelledAt ? new Date(node.cancelledAt) : null, closedAt: node.closedAt ? new Date(node.closedAt) : null,
        shopifyCreatedAt: new Date(node.createdAt), shopifyUpdatedAt: new Date(node.updatedAt), customerId: customer?.id,
        tags: node.tags, note: node.note, shippingAddress: node.shippingAddress || undefined, billingAddress: node.billingAddress || undefined },
      update: { name: node.name, email: node.email, financialStatus: node.displayFinancialStatus,
        fulfillmentStatus: node.displayFulfillmentStatus, currency: node.currencyCode, subtotal: node.subtotalPriceSet.shopMoney.amount,
        total: node.totalPriceSet.shopMoney.amount, totalTax: node.totalTaxSet?.shopMoney.amount || '0',
        totalShipping: node.totalShippingPriceSet.shopMoney.amount, totalDiscount: node.totalDiscountsSet.shopMoney.amount,
        cancelledAt: node.cancelledAt ? new Date(node.cancelledAt) : null, closedAt: node.closedAt ? new Date(node.closedAt) : null,
        shopifyCreatedAt: new Date(node.createdAt), shopifyUpdatedAt: new Date(node.updatedAt), customerId: customer?.id || null,
        tags: node.tags, note: node.note, shippingAddress: node.shippingAddress || undefined, billingAddress: node.billingAddress || undefined } });
    await tx.refund.deleteMany({ where: { orderId: order.id } });
    await tx.fulfillment.deleteMany({ where: { orderId: order.id } });
    const variantShopifyIds = node.lineItems.nodes.flatMap(item => item.variant ? [item.variant.id] : []);
    const variants = await tx.variant.findMany({ where: { shopifyId: { in: variantShopifyIds }, product: { storeId } }, select: { id: true, shopifyId: true } });
    const byShopifyId = new Map(variants.map(variant => [variant.shopifyId, variant.id]));
    const currentItemIds = node.lineItems.nodes.map(item => item.id);
    await tx.orderItem.deleteMany({ where: { orderId: order.id, ...(currentItemIds.length ? { shopifyId: { notIn: currentItemIds } } : {}) } });
    for (const item of node.lineItems.nodes) {
      const data = { variantId: item.variant ? byShopifyId.get(item.variant.id) : null, title: item.title, sku: item.sku, quantity: item.quantity,
        unitPrice: item.originalUnitPriceSet.shopMoney.amount, discount: item.totalDiscountSet.shopMoney.amount };
      await tx.orderItem.upsert({ where: { orderId_shopifyId: { orderId: order.id, shopifyId: item.id } },
        create: { orderId: order.id, shopifyId: item.id, ...data }, update: data });
    }
    const savedItems = await tx.orderItem.findMany({ where: { orderId: order.id }, select: { id: true, shopifyId: true } });
    const itemIds = new Map(savedItems.map(item => [item.shopifyId, item.id]));
    for (const refund of node.refunds) {
      await tx.refund.create({ data: { orderId: order.id, shopifyId: refund.id, amount: refund.totalRefundedSet.shopMoney.amount,
        currency: refund.totalRefundedSet.shopMoney.currencyCode, note: refund.note, shopifyCreatedAt: new Date(refund.createdAt),
        items: { create: refund.refundLineItems.nodes.map(item => ({ orderItemId: item.lineItem ? itemIds.get(item.lineItem.id) : undefined,
          quantity: item.quantity, subtotal: item.subtotalSet.shopMoney.amount })) } } });
      await tx.orderEvent.upsert({ where: { orderId_type_title_occurredAt: { orderId: order.id, type: 'REFUND', title: `Refund ${refund.id}`, occurredAt: new Date(refund.createdAt) } },
        create: { storeId, orderId: order.id, type: 'REFUND', title: `Refund ${refund.id}`, description: `${refund.totalRefundedSet.shopMoney.amount} ${refund.totalRefundedSet.shopMoney.currencyCode}`, occurredAt: new Date(refund.createdAt) }, update: {} });
    }
    for (const fulfillment of node.fulfillments) {
      const tracking = fulfillment.trackingInfo[0];
      await tx.fulfillment.create({ data: { orderId: order.id, shopifyId: fulfillment.id, status: fulfillment.status,
        trackingCompany: tracking?.company, trackingNumber: tracking?.number, trackingUrl: tracking?.url,
        shopifyCreatedAt: new Date(fulfillment.createdAt), shopifyUpdatedAt: new Date(fulfillment.updatedAt),
        items: { create: fulfillment.fulfillmentLineItems.nodes.map(item => ({ orderItemId: itemIds.get(item.lineItem.id), quantity: item.quantity })) } } });
      await tx.orderEvent.upsert({ where: { orderId_type_title_occurredAt: { orderId: order.id, type: 'FULFILLMENT', title: `Fulfillment ${fulfillment.id}`, occurredAt: new Date(fulfillment.createdAt) } },
        create: { storeId, orderId: order.id, type: 'FULFILLMENT', title: `Fulfillment ${fulfillment.id}`, description: fulfillment.status, occurredAt: new Date(fulfillment.createdAt) }, update: { description: fulfillment.status } });
    }
    await tx.orderEvent.upsert({ where: { orderId_type_title_occurredAt: { orderId: order.id, type: 'ORDER_CREATED', title: node.name, occurredAt: new Date(node.createdAt) } },
      create: { storeId, orderId: order.id, type: 'ORDER_CREATED', title: node.name, occurredAt: new Date(node.createdAt) }, update: {} });
  });
}

async function upsertInventory(storeId: string, node: InventoryNode) {
  if (!node.variant) return;
  const variant = await db.variant.findFirst({ where: { shopifyId: node.variant.id, product: { storeId } }, select: { id: true } });
  if (!variant) return;
  for (const level of node.inventoryLevels.nodes) {
    const quantities = new Map(level.quantities.map(quantity => [quantity.name, quantity.quantity]));
    await db.inventoryLevel.upsert({ where: { variantId_locationId: { variantId: variant.id, locationId: level.location.id } },
      create: { storeId, variantId: variant.id, locationId: level.location.id, locationName: level.location.name,
        available: quantities.get('available') || 0, onHand: quantities.get('on_hand') },
      update: { locationName: level.location.name, available: quantities.get('available') || 0, onHand: quantities.get('on_hand') } });
  }
}

async function syncProducts(storeId: string, initialCursor: string | null, onPage: (cursor: string | null, count: number) => Promise<void>, trace: SyncTrace) {
  const store = await db.store.findUniqueOrThrow({ where: { id: storeId } });
  let cursor = initialCursor;
  do {
    trace('shopify_request', { resource: 'products', hasCursor: Boolean(cursor) });
    const result = await graphql<{ products: Page<ProductNode> }>(db, connection, storeId,
      `query($after: String) { products(first: 50, after: $after, sortKey: UPDATED_AT) { nodes { ${PRODUCT_FIELDS} } pageInfo { hasNextPage endCursor } } }`, { after: cursor });
    trace('shopify_response', { resource: 'products', count: result.products.nodes.length, hasNextPage: result.products.pageInfo.hasNextPage });
    for (const node of result.products.nodes) await upsertProduct(storeId, store.currency, node);
    cursor = result.products.pageInfo.endCursor;
    await onPage(cursor, result.products.nodes.length);
    trace('database_saved', { resource: 'products', count: result.products.nodes.length });
    if (!result.products.pageInfo.hasNextPage) break;
  } while (cursor);
}

async function syncOrders(storeId: string, initialCursor: string | null, onPage: (cursor: string | null, count: number) => Promise<void>, trace: SyncTrace) {
  let cursor = initialCursor;
  do {
    trace('shopify_request', { resource: 'orders', hasCursor: Boolean(cursor) });
    const result = await graphql<{ orders: Page<OrderNode> }>(db, connection, storeId,
      `query($after: String) { orders(first: 50, after: $after, sortKey: UPDATED_AT) { nodes { ${ORDER_FIELDS} } pageInfo { hasNextPage endCursor } } }`, { after: cursor });
    trace('shopify_response', { resource: 'orders', count: result.orders.nodes.length, hasNextPage: result.orders.pageInfo.hasNextPage });
    for (const node of result.orders.nodes) await upsertOrder(storeId, node);
    cursor = result.orders.pageInfo.endCursor;
    await onPage(cursor, result.orders.nodes.length);
    trace('database_saved', { resource: 'orders', count: result.orders.nodes.length });
    if (!result.orders.pageInfo.hasNextPage) break;
  } while (cursor);
}

async function syncInventory(storeId: string, initialCursor: string | null, onPage: (cursor: string | null, count: number) => Promise<void>, trace: SyncTrace) {
  let cursor = initialCursor;
  do {
    trace('shopify_request', { resource: 'inventoryItems', hasCursor: Boolean(cursor) });
    const result = await graphql<{ inventoryItems: Page<InventoryNode> }>(db, connection, storeId,
      `query($after: String) { inventoryItems(first: 50, after: $after) { nodes { ${INVENTORY_FIELDS} } pageInfo { hasNextPage endCursor } } }`, { after: cursor });
    trace('shopify_response', { resource: 'inventoryItems', count: result.inventoryItems.nodes.length, hasNextPage: result.inventoryItems.pageInfo.hasNextPage });
    for (const node of result.inventoryItems.nodes) await upsertInventory(storeId, node);
    cursor = result.inventoryItems.pageInfo.endCursor;
    await onPage(cursor, result.inventoryItems.nodes.length);
    trace('database_saved', { resource: 'inventoryItems', count: result.inventoryItems.nodes.length });
    if (!result.inventoryItems.pageInfo.hasNextPage) break;
  } while (cursor);
}

export async function processSync(syncJobId: string) {
  let kind = 'UNKNOWN';
  try {
    const claimed = await db.syncJob.updateMany({ where: { id: syncJobId, status: 'QUEUED' },
      data: { status: 'RUNNING', startedAt: new Date(), completedAt: null, error: null } });
    if (!claimed.count) {
      const current = await db.syncJob.findUnique({ where: { id: syncJobId } });
      if (current?.status === 'RUNNING' || current?.status === 'COMPLETED') return current;
      throw new Error(current ? `Sync job cannot run from status ${current.status}` : 'Sync job not found');
    }
    const row = await db.syncJob.findUniqueOrThrow({ where: { id: syncJobId } });
    kind = row.kind;
    const trace: SyncTrace = (stage, details) => writeSyncTrace(row.id, row.kind, stage, details);
    trace('claimed', { resumed: Boolean(row.cursor), processed: row.processed });
    let processed = row.processed;
    const onPage = async (cursor: string | null, count: number) => {
      processed += count;
      await db.syncJob.update({ where: { id: row.id }, data: { cursor, processed } });
    };
    if (row.kind === 'PRODUCTS') await syncProducts(row.storeId, row.cursor, onPage, trace);
    else if (row.kind === 'ORDERS') await syncOrders(row.storeId, row.cursor, onPage, trace);
    else if (row.kind === 'INVENTORY') await syncInventory(row.storeId, row.cursor, onPage, trace);
    else throw new Error(`Unsupported sync kind ${row.kind}`);
    const completed = await db.syncJob.update({ where: { id: row.id }, data: { status: 'COMPLETED', cursor: null, completedAt: new Date(), error: null } });
    trace('completed', { processed });
    return completed;
  } catch (error) {
    const reason = safeSyncError(error);
    await db.syncJob.updateMany({ where: { id: syncJobId, status: { in: ['QUEUED', 'RUNNING'] } },
      data: { status: 'FAILED', error: reason, completedAt: new Date() } });
    console.error(JSON.stringify({ event: 'shopify.sync', syncJobId, kind, stage: 'failed', reason }));
    throw error;
  }
}

function gid(resource: 'Product' | 'Order' | 'InventoryItem', raw: unknown) {
  if (typeof raw === 'string' && raw.startsWith('gid://shopify/')) return raw;
  if ((typeof raw === 'number' || typeof raw === 'string') && /^\d+$/.test(String(raw))) return `gid://shopify/${resource}/${raw}`;
  return null;
}

async function refreshOrder(storeId: string, id: string) {
  const result = await graphql<{ node: OrderNode | null }>(db, connection, storeId,
    `query($id: ID!) { node(id: $id) { ... on Order { ${ORDER_FIELDS} } } }`, { id });
  if (result.node) await upsertOrder(storeId, result.node);
}

async function processWebhook(webhookEventId: string) {
  const event = await db.webhookEvent.findUniqueOrThrow({ where: { id: webhookEventId }, include: { store: true } });
  if (event.status === 'PROCESSED') return;
  const payload = event.payload as Record<string, unknown>;
  const topic = event.topic.toLowerCase().replaceAll('_', '/');
  if (topic === 'app/uninstalled') {
    await db.$transaction([db.shopifyConnection.deleteMany({ where: { storeId: event.storeId } }), db.store.update({ where: { id: event.storeId }, data: { status: 'DRAFT' } })]);
  } else if (topic.startsWith('products/')) {
    const id = gid('Product', payload.admin_graphql_api_id || payload.id);
    if (!id) throw new Error('Product webhook has no valid ID');
    if (topic === 'products/delete') await db.product.deleteMany({ where: { storeId: event.storeId, shopifyId: id } });
    else {
      const result = await graphql<{ node: ProductNode | null }>(db, connection, event.storeId,
        `query($id: ID!) { node(id: $id) { ... on Product { ${PRODUCT_FIELDS} } } }`, { id });
      if (result.node) await upsertProduct(event.storeId, event.store.currency, result.node);
    }
  } else if (topic.startsWith('orders/')) {
    const id = gid('Order', payload.admin_graphql_api_id || payload.id);
    if (!id) throw new Error('Order webhook has no valid ID');
    if (topic === 'orders/delete') await db.order.deleteMany({ where: { storeId: event.storeId, shopifyId: id } });
    else await refreshOrder(event.storeId, id);
  } else if (topic === 'refunds/create' || topic.startsWith('fulfillments/')) {
    const id = gid('Order', payload.order_id);
    if (!id) throw new Error('Order-related webhook has no valid order ID');
    await refreshOrder(event.storeId, id);
  } else if (topic === 'inventory/levels/update' || topic === 'inventory_levels/update') {
    const id = gid('InventoryItem', payload.inventory_item_id);
    if (!id) throw new Error('Inventory webhook has no valid inventory item ID');
    const result = await graphql<{ node: InventoryNode | null }>(db, connection, event.storeId,
      `query($id: ID!) { node(id: $id) { ... on InventoryItem { ${INVENTORY_FIELDS} } } }`, { id });
    if (result.node) await upsertInventory(event.storeId, result.node);
  }
  await db.webhookEvent.update({ where: { id: event.id }, data: { status: 'PROCESSED', processedAt: new Date(), error: null } });
}

export async function processWebhookForServerless(webhookEventId: string) {
  try {
    return await processWebhook(webhookEventId);
  } catch (error) {
    const reason = safeSyncError(error);
    await db.webhookEvent.updateMany({ where: { id: webhookEventId, status: 'RECEIVED' },
      data: { status: 'FAILED', error: reason } });
    console.error(JSON.stringify({ event: 'shopify.webhook', webhookEventId, stage: 'failed', reason }));
    throw error;
  }
}

type QueueData = { syncJobId?: string; webhookEventId?: string };

function startWorker() {
  const queueConnection = new Redis(process.env.REDIS_URL!, { maxRetriesPerRequest: null });
  const queue = new Queue<QueueData>('shopify-sync', { connection: queueConnection });
  const worker = new Worker<QueueData>('shopify-sync', async (job: Job<QueueData>) => {
    if (job.name === 'sync' && job.data.syncJobId) return processSync(job.data.syncJobId);
    if (job.name === 'webhook' && job.data.webhookEventId) return processWebhook(job.data.webhookEventId);
    throw new Error('Invalid Shopify queue job');
  }, { connection, concurrency: 1 });

  worker.on('failed', async (job, error) => {
    if (!job) return;
    const finalAttempt = job.attemptsMade >= (job.opts.attempts || 1);
    if (job.name === 'sync' && job.data.syncJobId) {
      await db.syncJob.updateMany({ where: { id: job.data.syncJobId }, data: { status: finalAttempt ? 'FAILED' : 'QUEUED',
        error: safeSyncError(error), completedAt: finalAttempt ? new Date() : null } });
    } else if (job.name === 'webhook' && job.data.webhookEventId) {
      await db.webhookEvent.updateMany({ where: { id: job.data.webhookEventId }, data: { status: finalAttempt ? 'FAILED' : 'RECEIVED', error: safeSyncError(error) } });
    }
  });

  async function recoverPersistedWebhooks() {
    const events = await db.webhookEvent.findMany({ where: { status: 'RECEIVED' }, select: { id: true }, orderBy: { receivedAt: 'asc' }, take: 500 });
    for (const event of events) {
      await queue.add('webhook', { webhookEventId: event.id }, { jobId: event.id, attempts: 5, backoff: { type: 'exponential', delay: 2000 }, removeOnComplete: 1000, removeOnFail: 1000 });
    }
    const syncJobs = await db.syncJob.findMany({ where: { status: 'QUEUED' }, select: { id: true }, orderBy: { createdAt: 'asc' }, take: 500 });
    for (const syncJob of syncJobs) {
      await queue.add('sync', { syncJobId: syncJob.id }, { jobId: syncJob.id, attempts: 5, backoff: { type: 'exponential', delay: 2000 }, removeOnComplete: 1000, removeOnFail: 1000 });
    }
  }

  const recoveryTimer = setInterval(() => void recoverPersistedWebhooks(), 60_000);
  void recoverPersistedWebhooks();
  let alertsRunning = false;
  async function evaluateAlerts() {
    if (alertsRunning) return;
    alertsRunning = true;
    try {
      const companies = await db.company.findMany({ where: { alertRules: { some: { enabled: true } } }, select: { id: true } });
      for (const company of companies) await evaluateCompanyAlerts(db, company.id);
    } finally { alertsRunning = false; }
  }
  const alertTimer = setInterval(() => void evaluateAlerts(), 15 * 60_000);
  void evaluateAlerts();

  async function shutdown() {
    clearInterval(recoveryTimer);
    clearInterval(alertTimer);
    await worker.close();
    await queue.close();
    await db.$disconnect();
    await connection.quit();
    await queueConnection.quit();
  }
  process.once('SIGINT', () => void shutdown().finally(() => process.exit(0)));
  process.once('SIGTERM', () => void shutdown().finally(() => process.exit(0)));
}

if (require.main === module) startWorker();
