import api from '../api';
import type { User, TokenResponse } from '../types';

export async function register(email: string, password: string, fullName: string, merchantId?: string, role?: string) {
  const res = await api.post<User>('/auth/register', { email, password, fullName, merchantId, role });
  return res.data;
}

export async function login(email: string, password: string) {
  const res = await api.post<TokenResponse>('/auth/login', { email, password });
  return res.data;
}

export async function refresh(refreshToken: string) {
  const res = await api.post<TokenResponse>('/auth/refresh', { refreshToken });
  return res.data;
}

export async function logout(refreshToken: string) {
  await api.post('/auth/logout', { refreshToken });
}

export async function logoutAll() {
  await api.post('/auth/logout-all', {});
}

export async function getMe() {
  const res = await api.get<User>('/auth/me');
  return res.data;
}
