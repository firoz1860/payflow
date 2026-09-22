import axios, { AxiosError } from 'axios';
import { useAuthStore } from './store/auth';
import type { ApiError } from './types';

// Base URL of the PayFlow API gateway.
// - Production (e.g. Vercel): set VITE_API_URL to the deployed gateway, incl. the
//   `/api/v1` suffix, e.g. https://payflow-gateway.onrender.com/api/v1
// - Local dev: leave VITE_API_URL unset -> falls back to '/api/v1', which the Vite
//   dev server proxies to http://localhost:8080 (see vite.config.ts).
export const API_BASE_URL =
  import.meta.env.VITE_API_URL?.replace(/\/+$/, '') || '/api/v1';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshing = false;
let waiters: ((token: string | null) => void)[] = [];

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as any;
    if (error.response?.status === 401 && !original._retry) {
      const refreshToken = useAuthStore.getState().refreshToken;
      if (!refreshToken || refreshing) {
        if (refreshing) {
          return new Promise((resolve, reject) => {
            waiters.push((token) => {
              if (token) {
                original._retry = true;
                original.headers.Authorization = `Bearer ${token}`;
                resolve(api(original));
              } else {
                reject(error);
              }
            });
          });
        }
        useAuthStore.getState().logout();
        return Promise.reject(error);
      }
      refreshing = true;
      original._retry = true;
      try {
        const res = await axios.post(`${API_BASE_URL}/auth/refresh`, { refreshToken }, {
          headers: { 'Content-Type': 'application/json' },
        });
        const { accessToken, refreshToken: newRefresh } = res.data;
        useAuthStore.getState().setTokens(accessToken, newRefresh);
        waiters.forEach((w) => w(accessToken));
        waiters = [];
        original.headers.Authorization = `Bearer ${accessToken}`;
        return api(original);
      } catch {
        useAuthStore.getState().logout();
        waiters.forEach((w) => w(null));
        waiters = [];
        return Promise.reject(error);
      } finally {
        refreshing = false;
      }
    }
    return Promise.reject(error);
  }
);

export function extractError(err: unknown): string {
  if (axios.isAxiosError<ApiError>(err)) {
    return err.response?.data?.message || err.message;
  }
  if (err instanceof Error) return err.message;
  return 'An unexpected error occurred';
}

export function extractErrorCode(err: unknown): string {
  if (axios.isAxiosError<ApiError>(err)) {
    return err.response?.data?.code || '';
  }
  return '';
}

export default api;
