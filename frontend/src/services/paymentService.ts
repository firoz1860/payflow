import api from '../api';
import type { Payment, PageResponse, PaymentMethod } from '../types';

export async function createPayment(data: {
  amount: number;
  currency: string;
  merchantOrderId?: string;
  customerId?: string;
  description?: string;
  paymentMethod: PaymentMethod;
  returnUrl?: string;
  metadata?: Record<string, string>;
}, idempotencyKey: string) {
  const res = await api.post<Payment>('/payments', data, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
  return res.data;
}

export async function getPayment(reference: string) {
  const res = await api.get<Payment>(`/payments/${reference}`);
  return res.data;
}

export async function listPayments(params: { status?: string; page?: number; size?: number }) {
  const res = await api.get<PageResponse<Payment>>('/payments', { params });
  return res.data;
}

export async function cancelPayment(reference: string, reason?: string) {
  const res = await api.post<Payment>(`/payments/${reference}/cancel`, reason ? { reason } : {});
  return res.data;
}
