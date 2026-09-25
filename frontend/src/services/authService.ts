import api from '../api';
import type { User, TokenResponse, RoleName } from '../types';

export async function register(email: string, password: string, fullName: string, businessName: string) {
  const res = await api.post<User>('/auth/register', { email, password, fullName, businessName });
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

export async function verifyEmail(token: string) {
  const res = await api.post<{ message: string }>(`/auth/verify-email/${token}`, {});
  return res.data;
}

export async function initiatePasswordReset(email: string) {
  const res = await api.post<{ message: string }>('/auth/password-reset/initiate', { email });
  return res.data;
}

export async function completePasswordReset(token: string, newPassword: string) {
  const res = await api.post<{ message: string }>('/auth/password-reset/complete', { token, newPassword });
  return res.data;
}

export async function assignRole(userId: string, role: RoleName) {
  const res = await api.post<User>('/auth/roles/assign', { userId, role });
  return res.data;
}
