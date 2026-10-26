import { Prisma, PrismaClient } from '@erp/database';
import Redis from 'ioredis';
import { randomUUID } from 'node:crypto';
import { decryptToken, encryptToken, validShopDomain } from './shopify-security';

export const REQUIRED_SCOPES = ['write_products', 'read_orders', 'read_inventory', 'read_locations', 'read_customers'];
type TokenResponse = { access_token: string; scope: string; expires_in?: number; refresh_token?: string; refresh_token_expires_in?: number };
type RawTokenResponse = Omit<TokenResponse, 'scope'> & { scope?: unknown };

function safeResponseFields(value: unknown): string {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return 'none';
  return Object.keys(value).filter(key => /^[a-z][a-z0-9_]{0,48}$/i.test(key)).sort().slice(0, 20).join(',') || 'none';
}

function safeErrorValue(value: unknown, redactions: string[]): string | null {
  if (value === undefined || value === null) return null;
  let text = typeof value === 'string' ? value : JSON.stringify(value);
  for (const secret of redactions) if (secret.length >= 6) text = text.replaceAll(secret, '[redacted]');
  return text
    .replace(/\b(?:shpat|shprt|shpss|shpua|shppa)_[A-Za-z0-9_-]+\b/gi, '[redacted]')
    .replace(/([?&](?:code|client_secret|access_token|refresh_token)=)[^&\s]+/gi, '$1[redacted]')
    .replace(/[\r\n\t]+/g, ' ')
    .slice(0, 240);
}

export async function exchangeCode(shop: string, code: string): Promise<TokenResponse> {
  const response = await fetch(`https://${shop}/admin/oauth/access_token`, {
    method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: process.env.SHOPIFY_CLIENT_ID!, client_secret: process.env.SHOPIFY_CLIENT_SECRET!, code, expiring: '1' }),
  });
  const body = await response.text();
  let payload: Record<string, unknown> | null = null;
  try {
    const parsed = JSON.parse(body) as unknown;
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) payload = parsed as Record<string, unknown>;
  } catch { /* Metadata below deliberately excludes the raw response body. */ }
  const fields = safeResponseFields(payload);
  const redactions = [code, process.env.SHOPIFY_CLIENT_SECRET || '', typeof payload?.access_token === 'string' ? payload.access_token : '', typeof payload?.refresh_token === 'string' ? payload.refresh_token : ''];
  if (!response.ok) {
    const errorCode = safeErrorValue(payload?.error, redactions);
    const description = safeErrorValue(payload?.error_description ?? payload?.errors, redactions);
    throw new Error(`Shopify OAuth exchange failed (HTTP ${response.status}; error=${errorCode || 'none'}; description=${description || 'none'}; fields=${fields})`);
  }
  const rawToken = payload as RawTokenResponse | null;
  if (!rawToken || typeof rawToken.access_token !== 'string' || !rawToken.access_token) {
    throw new Error(`Shopify returned an invalid offline token response (HTTP ${response.status}; fields=${fields}; hasAccessToken=${Boolean(rawToken?.access_token)})`);
  }
  let scope = typeof rawToken.scope === 'string'
    ? rawToken.scope.split(',').map(value => value.trim()).filter(Boolean)
    : Array.isArray(rawToken.scope) ? rawToken.scope.filter((value): value is string => typeof value === 'string' && value.length > 0) : [];
  if (scope.length === 0) {
    const installation = await graphqlWithToken<{ currentAppInstallation: { accessScopes: Array<{ handle: string }> } }>(shop, rawToken.access_token,
      '{ currentAppInstallation { accessScopes { handle } } }');
    scope = installation.currentAppInstallation.accessScopes.map(item => item.handle).filter(Boolean);
  }
  if (scope.length === 0) throw new Error(`Shopify token has no granted Admin API scopes (HTTP ${response.status}; fields=${fields})`);
  const token: TokenResponse = { ...rawToken, scope: [...new Set(scope)].join(',') };
  const expiryFieldCount = [token.expires_in, token.refresh_token, token.refresh_token_expires_in].filter(value => value !== undefined && value !== null).length;
  if (expiryFieldCount !== 0 && expiryFieldCount !== 3) throw new Error(`Shopify returned incomplete token expiry metadata (HTTP ${response.status}; fields=${fields})`);
  if (expiryFieldCount === 3 && (!(Number.isFinite(token.expires_in) && token.expires_in! > 0) || typeof token.refresh_token !== 'string' || !token.refresh_token || !(Number.isFinite(token.refresh_token_expires_in) && token.refresh_token_expires_in! > 0))) {
    throw new Error(`Shopify returned invalid token expiry metadata (HTTP ${response.status}; fields=${fields})`);
  }
  return token;
}

type TokenDatabase = PrismaClient | Prisma.TransactionClient;

export async function saveToken(db: TokenDatabase, storeId: string, token: TokenResponse) {
  const now = Date.now();
  await db.shopifyConnection.upsert({ where: { storeId },
    create: { storeId, encryptedAccessToken: encryptToken(token.access_token), encryptedRefreshToken: token.refresh_token ? encryptToken(token.refresh_token) : null,
      accessTokenExpiresAt: token.expires_in ? new Date(now + token.expires_in * 1000) : null,
      refreshTokenExpiresAt: token.refresh_token_expires_in ? new Date(now + token.refresh_token_expires_in * 1000) : null,
      scopes: token.scope.split(',').map(scope => scope.trim()).filter(Boolean) },
    update: { encryptedAccessToken: encryptToken(token.access_token), encryptedRefreshToken: token.refresh_token ? encryptToken(token.refresh_token) : null,
      accessTokenExpiresAt: token.expires_in ? new Date(now + token.expires_in * 1000) : null,
      refreshTokenExpiresAt: token.refresh_token_expires_in ? new Date(now + token.refresh_token_expires_in * 1000) : null,
      scopes: token.scope.split(',').map(scope => scope.trim()).filter(Boolean) },
  });
}

async function currentToken(db: PrismaClient, redis: Redis, storeId: string) {
  const connection = await db.shopifyConnection.findUnique({ where: { storeId }, include: { store: true } });
  if (!connection || !validShopDomain(connection.store.shopifyDomain) || connection.store.status !== 'CONNECTED') throw new Error('Shopify store is not connected');
  if (!connection.accessTokenExpiresAt || connection.accessTokenExpiresAt.getTime() > Date.now() + 120_000) {
    return { shop: connection.store.shopifyDomain, token: decryptToken(connection.encryptedAccessToken) };
  }
  if (!connection.encryptedRefreshToken || (connection.refreshTokenExpiresAt && connection.refreshTokenExpiresAt.getTime() < Date.now())) throw new Error('Shopify refresh token expired; reconnect store');
  const lock = `shopify:refresh:${storeId}`;
  const owner = randomUUID();
  if (await redis.set(lock, owner, 'PX', 30_000, 'NX') !== 'OK') {
    for (let i = 0; i < 30; i++) {
      await new Promise(resolve => setTimeout(resolve, 500));
      const latest = await db.shopifyConnection.findUniqueOrThrow({ where: { storeId } });
      if (latest.accessTokenExpiresAt && latest.accessTokenExpiresAt.getTime() > Date.now() + 120_000) return { shop: connection.store.shopifyDomain, token: decryptToken(latest.encryptedAccessToken) };
    }
    throw new Error('Shopify token refresh lock timed out');
  }
  try {
    const latest = await db.shopifyConnection.findUniqueOrThrow({ where: { storeId } });
    if (latest.accessTokenExpiresAt && latest.accessTokenExpiresAt.getTime() > Date.now() + 120_000) return { shop: connection.store.shopifyDomain, token: decryptToken(latest.encryptedAccessToken) };
    const response = await fetch(`https://${connection.store.shopifyDomain}/admin/oauth/access_token`, {
      method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ client_id: process.env.SHOPIFY_CLIENT_ID!, client_secret: process.env.SHOPIFY_CLIENT_SECRET!,
        grant_type: 'refresh_token', refresh_token: decryptToken(latest.encryptedRefreshToken!) }),
    });
    if (!response.ok) throw new Error(`Shopify token refresh failed (${response.status})`);
    const token = await response.json() as TokenResponse;
    if (!token.access_token || !token.refresh_token || !token.expires_in) throw new Error('Invalid Shopify refresh response');
    token.scope ||= latest.scopes.join(',');
    await saveToken(db, storeId, token);
    return { shop: connection.store.shopifyDomain, token: token.access_token };
  } finally {
    await redis.eval('if redis.call("get", KEYS[1]) == ARGV[1] then return redis.call("del", KEYS[1]) else return 0 end', 1, lock, owner);
  }
}

export async function graphql<T>(db: PrismaClient, redis: Redis, storeId: string, query: string, variables?: object): Promise<T> {
  const { shop, token } = await currentToken(db, redis, storeId);
  return graphqlWithToken<T>(shop, token, query, variables);
}

export async function graphqlWithToken<T>(shop: string, token: string, query: string, variables?: object): Promise<T> {
  if (!validShopDomain(shop)) throw new Error('Invalid Shopify domain');
  for (let attempt = 0; attempt < 4; attempt++) {
    const apiVersion = process.env.SHOPIFY_API_VERSION || '2026-10';
    const response = await fetch(`https://${shop}/admin/api/${apiVersion}/graphql.json`, {
      method: 'POST', headers: { 'content-type': 'application/json', 'x-shopify-access-token': token }, body: JSON.stringify({ query, variables }),
    });
    if (response.status === 429 || response.status >= 500) {
      if (attempt === 3) throw new Error(`Shopify GraphQL HTTP ${response.status}`);
      const retryAfter = Number(response.headers.get('retry-after'));
      await new Promise(resolve => setTimeout(resolve, Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter * 1000 : 500 * 2 ** attempt));
      continue;
    }
    if (!response.ok) throw new Error(`Shopify GraphQL HTTP ${response.status}`);
    const result = await response.json() as { data?: T; errors?: { message: string }[] };
    if (result.errors?.length) throw new Error(result.errors.map(x => x.message).join('; '));
    if (!result.data) throw new Error('Shopify GraphQL response has no data');
    return result.data;
  }
  throw new Error('Shopify GraphQL retries exhausted');
}
