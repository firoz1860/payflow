import api from '../api';
import type { Merchant, ApiKey, CreateApiKeyResponse, MerchantStatus } from '../types';

export async function getMyMerchant() {
  const res = await api.get<Merchant>('/merchants/me');
  return res.data;
}

export async function updateMyMerchant(data: { businessName?: string; phone?: string; defaultCurrency?: string }) {
  const res = await api.patch<Merchant>('/merchants/me', data);
  return res.data;
}

export async function createApiKey(data: {
  environment: string;
  keyType: string;
  label?: string;
  scopes?: string[];
  expiresAt?: string;
}) {
  const res = await api.post<CreateApiKeyResponse>('/merchants/me/api-keys', data);
  return res.data;
}

export async function listApiKeys() {
  const res = await api.get<ApiKey[]>('/merchants/me/api-keys');
  return res.data;
}

export async function revokeApiKey(keyId: string) {
  await api.delete(`/merchants/me/api-keys/${keyId}`);
}

// Admin endpoints
export async function createMerchant(data: {
  businessName: string;
  email: string;
  phone?: string;
  country: string;
  defaultCurrency: string;
}) {
  const res = await api.post<Merchant>('/merchants', data);
  return res.data;
}

export async function getMerchant(id: string) {
  const res = await api.get<Merchant>(`/merchants/${id}`);
  return res.data;
}

export async function changeMerchantStatus(id: string, status: MerchantStatus, reason?: string) {
  const res = await api.patch<Merchant>(`/merchants/${id}/status`, { status, reason });
  return res.data;
}

export async function updateMerchantPricing(id: string, data: {
  feePercentage?: number;
  fixedFee?: number;
  settlementDelayDays?: number;
}) {
  const res = await api.patch<Merchant>(`/merchants/${id}/pricing`, data);
  return res.data;
}

export async function enableLiveMode(id: string) {
  const res = await api.post<Merchant>(`/merchants/${id}/live-mode`, {});
  return res.data;
}
