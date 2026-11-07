// Shared HTTP boundary for the frontend monolith.
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

export const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  timeout: 30_000,
  headers: { Accept: 'application/json' },
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

let csrfPromise: Promise<string> | null = null;
export async function ensureCsrf() {
  if (!csrfPromise) csrfPromise = api.get<{ token: string }>('/auth/csrf').then(response => response.data.token).finally(() => { csrfPromise = null; });
  return csrfPromise;
}

api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const method = config.method?.toUpperCase() || 'GET';
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && config.url !== '/auth/csrf') await ensureCsrf();
  config.headers.set('X-Request-Id', crypto.randomUUID());
  return config;
});

api.interceptors.response.use(response => response, (error: AxiosError<{ title?: string; detail?: string; instance?: string }>) => {
  if (error.response?.status === 401 && !error.config?.url?.includes('/auth/me')) window.dispatchEvent(new Event('erp:session-expired'));
  const status = error.response?.status;
  const safeMessage = status && status >= 500 ? 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.' : error.response?.data?.title || 'Không thể hoàn tất yêu cầu.';
  return Promise.reject(Object.assign(new Error(safeMessage), { status, requestId: error.response?.headers['x-request-id'] }));
});
