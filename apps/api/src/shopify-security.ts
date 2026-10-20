import { createCipheriv, createDecipheriv, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';

function key() {
  const value = process.env.SHOPIFY_TOKEN_ENCRYPTION_KEY;
  if (!value || !/^[a-f\d]{64}$/i.test(value)) throw new Error('SHOPIFY_TOKEN_ENCRYPTION_KEY must be 64 hex characters');
  return Buffer.from(value, 'hex');
}

export function encryptToken(token: string) {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key(), iv);
  const body = Buffer.concat([cipher.update(token, 'utf8'), cipher.final()]);
  return [iv, cipher.getAuthTag(), body].map(x => x.toString('base64url')).join('.');
}

export function decryptToken(encrypted: string) {
  const [iv, tag, body] = encrypted.split('.').map(x => Buffer.from(x, 'base64url'));
  if (!iv || !tag || !body || iv.length !== 12 || tag.length !== 16) throw new Error('Invalid encrypted token');
  const decipher = createDecipheriv('aes-256-gcm', key(), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(body), decipher.final()]).toString('utf8');
}

export function verifyWebhook(raw: Buffer, header: string, secret: string) {
  if (!/^[A-Za-z0-9+/]{43}=$/.test(header)) return false;
  const expected = createHmac('sha256', secret).update(raw).digest();
  const supplied = Buffer.from(header, 'base64');
  return supplied.length === expected.length && timingSafeEqual(supplied, expected);
}

export function verifyOAuthQuery(query: Record<string, unknown>, secret: string) {
  const signature = query.hmac;
  if (typeof signature !== 'string' || !/^[a-f\d]{64}$/i.test(signature)) return false;
  const input = Object.entries(query)
    .filter(([name, value]) => name !== 'hmac' && name !== 'signature' && typeof value === 'string')
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([name, value]) => `${name}=${value}`)
    .join('&');
  const expected = createHmac('sha256', secret).update(input).digest();
  return timingSafeEqual(Buffer.from(signature, 'hex'), expected);
}

export function validShopDomain(value: unknown): value is string {
  return typeof value === 'string' && /^[a-z0-9][a-z0-9-]*\.myshopify\.com$/.test(value);
}
