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

export class ApiError extends Error {
  constructor(message: string, public readonly status: number, public readonly requestId?: string) { super(message); this.name = 'ApiError'; }
}

function cookie(name: string) {
  if (typeof document === 'undefined') return '';
  return document.cookie.split('; ').find(item => item.startsWith(`${name}=`))?.slice(name.length + 1) || '';
}

async function csrfToken() {
  const current = cookie('erp_csrf');
  if (current) return decodeURIComponent(current);
  const response = await fetch(`${base}/auth/csrf`, { credentials: 'include', cache: 'no-store' });
  if (!response.ok) return '';
  const body = await response.json().catch(() => ({})) as { csrfToken?: string };
  return body.csrfToken || '';
}

function safeMessage(status: number, body: { message?: string | string[] }, fallback: string) {
  if (status >= 500) return 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.';
  if (status === 401) return 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn.';
  if (status === 403) return typeof body.message === 'string' ? body.message : 'Bạn không có quyền thực hiện thao tác này.';
  const value = Array.isArray(body.message) ? body.message.join(', ') : body.message;
  return typeof value === 'string' && value.length <= 300 ? value : fallback;
}

function sessionExpired(path: string, status: number) {
  if (status !== 401 || path.startsWith('/auth/login') || path.startsWith('/auth/mfa')) return;
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('erp:session-expired'));
}

export async function api<T>(path: string, options: RequestInit = {}, companyId?: string): Promise<T> {
  const method = (options.method || 'GET').toUpperCase();
  const csrf = !['GET', 'HEAD', 'OPTIONS'].includes(method) && path !== '/auth/login' ? await csrfToken() : '';
  const response = await fetch(`${base}${path}`, { ...options, credentials: 'include', cache: 'no-store', headers: {
    ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(companyId ? { 'x-company-id': companyId } : {}), ...(csrf ? { 'x-csrf-token': csrf } : {}), ...options.headers,
  } });
  const body = await response.json().catch(() => ({})) as { message?: string | string[]; requestId?: string };
  if (!response.ok) {
    sessionExpired(path, response.status);
    throw new ApiError(safeMessage(response.status, body, `Yêu cầu thất bại (HTTP ${response.status})`), response.status, body.requestId || response.headers.get('x-request-id') || undefined);
  }
  return body as T;
}

export async function downloadFile(path: string, companyId: string, filename: string) {
  const response = await fetch(`${base}${path}`, { credentials: 'include', cache: 'no-store', headers: { 'x-company-id': companyId } });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string | string[]; requestId?: string };
    sessionExpired(path, response.status);
    throw new ApiError(safeMessage(response.status, body, `Không thể tải tệp (HTTP ${response.status})`), response.status, body.requestId);
  }
  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement('a');
  anchor.href = url; anchor.download = filename; anchor.click();
  URL.revokeObjectURL(url);
}
