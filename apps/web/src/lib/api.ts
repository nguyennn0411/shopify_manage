export type Company = { id: string; name: string; code: string; baseCurrency: string; timezone: string };
export type Role = { id: string; name: string; description?: string; isSystem: boolean; permissions: { permissionCode: string }[]; _count?: { assignments: number } };
export type User = { id: string; email: string; name: string; status: 'ACTIVE' | 'DISABLED'; mfaEnabled: boolean; lastLoginAt: string | null; assignments: { id: string; storeId: string | null; warehouseId: string | null; role: { id: string; name: string } }[] };
export type Warehouse = { id: string; code: string; name: string };
export type Store = { id: string; name: string; shopifyDomain: string; currency: string; status: 'DRAFT' | 'CONNECTED' | 'DISABLED'; createdAt: string;
  connection?: { scopes: string[]; installedAt: string; accessTokenExpiresAt: string | null } | null };
export type SyncJob = { id: string; kind: 'PRODUCTS' | 'ORDERS' | 'INVENTORY' | 'WEBHOOK'; status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'; processed: number; error: string | null; createdAt: string; completedAt: string | null };
export type Audit = { id: string; action: string; resourceType: string; resourceId: string; createdAt: string; actor: { name: string; email: string } | null };
export type Me = { id: string; email: string; name: string; mfaEnabled: boolean; memberships: { company: Company }[]; assignments: { companyId: string; storeId: string | null; warehouseId: string | null; role: { id: string; name: string; permissions: { permissionCode: string }[] } }[] };

const base = process.env.NEXT_PUBLIC_API_PATH || '/backend/api/v1';
export async function api<T>(path: string, options: RequestInit = {}, companyId?: string): Promise<T> {
  const response = await fetch(`${base}${path}`, { ...options, credentials: 'include', cache: 'no-store', headers: {
    ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(companyId ? { 'x-company-id': companyId } : {}), ...options.headers,
  } });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(Array.isArray(body.message) ? body.message.join(', ') : body.message || `HTTP ${response.status}`);
  return body as T;
}

export async function downloadFile(path: string, companyId: string, filename: string) {
  const response = await fetch(`${base}${path}`, { credentials: 'include', cache: 'no-store', headers: { 'x-company-id': companyId } });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.message || `HTTP ${response.status}`);
  }
  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement('a');
  anchor.href = url; anchor.download = filename; anchor.click();
  URL.revokeObjectURL(url);
}
