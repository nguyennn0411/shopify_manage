import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';

function key() {
  const value = process.env.INTEGRATION_ENCRYPTION_KEY;
  if (!value || !/^[a-f\d]{64}$/i.test(value)) throw new Error('INTEGRATION_ENCRYPTION_KEY must be 64 hex characters');
  return Buffer.from(value, 'hex');
}
export function encryptIntegrationCredentials(credentials: Record<string, string>) {
  const iv = randomBytes(12); const cipher = createCipheriv('aes-256-gcm', key(), iv);
  const body = Buffer.concat([cipher.update(JSON.stringify(credentials), 'utf8'), cipher.final()]);
  return [iv, cipher.getAuthTag(), body].map(value => value.toString('base64url')).join('.');
}
export function decryptIntegrationCredentials(value: string): Record<string, string> {
  const parts = value.split('.').map(part => Buffer.from(part, 'base64url'));
  if (parts.length !== 3 || parts[0].length !== 12 || parts[1].length !== 16) throw new Error('Invalid encrypted integration credentials');
  const decipher = createDecipheriv('aes-256-gcm', key(), parts[0]); decipher.setAuthTag(parts[1]);
  const parsed: unknown = JSON.parse(Buffer.concat([decipher.update(parts[2]), decipher.final()]).toString('utf8'));
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) || Object.values(parsed).some(item => typeof item !== 'string')) throw new Error('Invalid integration credentials payload');
  return parsed as Record<string, string>;
}
