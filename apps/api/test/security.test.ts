import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { createHmac } from 'node:crypto';
import { base32Encode, hashPassword, verifyPassword, verifyTotp } from '../src/security';
import { decryptToken, encryptToken, validShopDomain, verifyOAuthQuery, verifyWebhook } from '../src/shopify-security';
import { decryptIntegrationCredentials, encryptIntegrationCredentials } from '../src/integration-security';
import { weightedMovingAverageForecast } from '../src/analytics-math';
import { exchangeCode, missingRequiredShopifyScopes, shopifyScopeError } from '../src/shopify-client';

test('password hash verifies the correct password and rejects another', () => {
  const hash = hashPassword('a strong password 123!');
  assert.equal(verifyPassword('a strong password 123!', hash), true);
  assert.equal(verifyPassword('wrong password', hash), false);
});

test('TOTP accepts current code and rejects an invalid one', () => {
  const secretBytes = Buffer.from('12345678901234567890');
  const secret = base32Encode(secretBytes);
  const now = 60_000;
  const message = Buffer.alloc(8);
  message.writeBigUInt64BE(2n);
  const digest = createHmac('sha1', secretBytes).update(message).digest();
  const index = digest[digest.length - 1] & 15;
  const code = String((digest.readUInt32BE(index) & 0x7fffffff) % 1_000_000).padStart(6, '0');
  assert.equal(verifyTotp(secret, code, now), true);
  assert.equal(verifyTotp(secret, 'not-six', now), false);
});

test('Shopify webhook and OAuth HMAC use constant canonical inputs', () => {
  const secret = 'shopify-secret';
  const body = Buffer.from('{"id":42}');
  const webhookHmac = createHmac('sha256', secret).update(body).digest('base64');
  assert.equal(verifyWebhook(body, webhookHmac, secret), true);
  assert.equal(verifyWebhook(Buffer.from('{"id":43}'), webhookHmac, secret), false);
  const query = { shop: 'valid-store.myshopify.com', timestamp: '123', code: 'abc' };
  const canonical = 'code=abc&shop=valid-store.myshopify.com&timestamp=123';
  const hmac = createHmac('sha256', secret).update(canonical).digest('hex');
  assert.equal(verifyOAuthQuery({ ...query, hmac }, secret), true);
  assert.equal(verifyOAuthQuery({ ...query, shop: 'other.myshopify.com', hmac }, secret), false);
  assert.equal(validShopDomain('valid-store.myshopify.com'), true);
  assert.equal(validShopDomain('myshopify.com.evil.test'), false);
});

test('Shopify token encryption authenticates ciphertext', () => {
  const before = process.env.SHOPIFY_TOKEN_ENCRYPTION_KEY;
  process.env.SHOPIFY_TOKEN_ENCRYPTION_KEY = 'ab'.repeat(32);
  try {
    const encrypted = encryptToken('shpat_private');
    assert.notEqual(encrypted, 'shpat_private');
    assert.equal(decryptToken(encrypted), 'shpat_private');
    const parts = encrypted.split('.');
    const ciphertext = Buffer.from(parts[2], 'base64url'); ciphertext[0] ^= 1; parts[2] = ciphertext.toString('base64url');
    assert.throws(() => decryptToken(parts.join('.')));
  } finally {
    if (before === undefined) delete process.env.SHOPIFY_TOKEN_ENCRYPTION_KEY;
    else process.env.SHOPIFY_TOKEN_ENCRYPTION_KEY = before;
  }
});

test('Shopify OAuth diagnostics include safe response metadata without secrets', async () => {
  const previousFetch = globalThis.fetch;
  const previousClientId = process.env.SHOPIFY_CLIENT_ID;
  const previousSecret = process.env.SHOPIFY_CLIENT_SECRET;
  process.env.SHOPIFY_CLIENT_ID = 'public-client-id';
  process.env.SHOPIFY_CLIENT_SECRET = 'private-client-secret';
  globalThis.fetch = async () => new Response(JSON.stringify({ error: 'invalid_client', error_description: 'Rejected private-client-secret and authorization-code-value' }), { status: 401, headers: { 'content-type': 'application/json' } });
  try {
    await assert.rejects(() => exchangeCode('valid-store.myshopify.com', 'authorization-code-value'), error => {
      const message = error instanceof Error ? error.message : String(error);
      assert.match(message, /HTTP 401/);
      assert.match(message, /invalid_client/);
      assert.equal(message.includes('private-client-secret'), false);
      assert.equal(message.includes('authorization-code-value'), false);
      return true;
    });
  } finally {
    globalThis.fetch = previousFetch;
    if (previousClientId === undefined) delete process.env.SHOPIFY_CLIENT_ID; else process.env.SHOPIFY_CLIENT_ID = previousClientId;
    if (previousSecret === undefined) delete process.env.SHOPIFY_CLIENT_SECRET; else process.env.SHOPIFY_CLIENT_SECRET = previousSecret;
  }
});

test('Shopify OAuth accepts complete expiring and non-expiring offline token responses', async () => {
  const previousFetch = globalThis.fetch;
  const responses = [
    { access_token: 'token-one', scope: 'write_products, read_orders' },
    { access_token: 'token-two', scope: 'write_products,read_orders', expires_in: 3600, refresh_token: 'refresh-two', refresh_token_expires_in: 7776000 },
  ];
  try {
    globalThis.fetch = async () => new Response(JSON.stringify(responses.shift()), { status: 200, headers: { 'content-type': 'application/json' } });
    assert.equal((await exchangeCode('valid-store.myshopify.com', 'code-one')).scope, 'read_orders,write_products');
    assert.equal((await exchangeCode('valid-store.myshopify.com', 'code-two')).expires_in, 3600);
  } finally {
    globalThis.fetch = previousFetch;
  }
});

test('Shopify OAuth reads granted scopes from Admin GraphQL when token response scope is empty', async () => {
  const previousFetch = globalThis.fetch;
  let request = 0;
  try {
    globalThis.fetch = async () => {
      request += 1;
      if (request === 1) return new Response(JSON.stringify({ access_token: 'token-three', scope: null, expires_in: 3600, refresh_token: 'refresh-three', refresh_token_expires_in: 7776000 }), { status: 200, headers: { 'content-type': 'application/json' } });
      return new Response(JSON.stringify({ data: { currentAppInstallation: { accessScopes: [{ handle: 'write_products' }, { handle: 'read_orders' }] } } }), { status: 200, headers: { 'content-type': 'application/json' } });
    };
    assert.equal((await exchangeCode('valid-store.myshopify.com', 'code-three')).scope, 'read_orders,write_products');
    assert.equal(request, 2);
  } finally {
    globalThis.fetch = previousFetch;
  }
});

test('Shopify scope diagnostics report required, granted and missing permissions', () => {
  const granted = ['write_products', 'write_inventory', 'read_locations'];
  assert.deepEqual(missingRequiredShopifyScopes(granted), ['read_orders', 'read_customers']);
  const message = shopifyScopeError(granted);
  assert.match(message || '', /ERP yêu cầu: write_products, read_orders, read_inventory, read_locations, read_customers/);
  assert.match(message || '', /Shopify đã cấp: read_locations, write_inventory, write_products/);
  assert.match(message || '', /Còn thiếu: read_orders, read_customers/);
  assert.equal(shopifyScopeError(['write_products', 'write_orders', 'write_inventory', 'write_locations', 'write_customers']), null);
});

test('integration credentials are encrypted and authenticated', () => {
  const before = process.env.INTEGRATION_ENCRYPTION_KEY;
  process.env.INTEGRATION_ENCRYPTION_KEY = 'cd'.repeat(32);
  try {
    const source = { accessToken: 'secret-token', accountId: '123' };
    const encrypted = encryptIntegrationCredentials(source);
    assert.equal(encrypted.includes(source.accessToken), false);
    assert.deepEqual(decryptIntegrationCredentials(encrypted), source);
    const parts = encrypted.split('.');
    const tag = Buffer.from(parts[1], 'base64url'); tag[0] ^= 1; parts[1] = tag.toString('base64url');
    assert.throws(() => decryptIntegrationCredentials(parts.join('.')));
  } finally {
    if (before === undefined) delete process.env.INTEGRATION_ENCRYPTION_KEY;
    else process.env.INTEGRATION_ENCRYPTION_KEY = before;
  }
});

test('forecast uses weighted 7/28 day demand and reports backtest error', () => {
  const history = [...Array(28).fill(2), ...Array(7).fill(6)];
  const result = weightedMovingAverageForecast(history, 3, new Date('2026-10-21T00:00:00.000Z'));
  assert.deepEqual(result.predictions.map(item => item.date), ['2026-10-21', '2026-10-22', '2026-10-23']);
  assert.equal(result.predictions[0].units, 4.95);
  assert.ok(result.meanAbsoluteError !== null && result.meanAbsoluteError > 0);
  assert.throws(() => weightedMovingAverageForecast([1, 2], 7, new Date()));
});
