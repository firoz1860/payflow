import api from '../api';

export interface WebhookEndpointSummary {
  id?: string;
  url?: string;
  status?: string;
  events?: string[];
  successRate?: number;
  lastDeliveryAt?: string;
}

export async function listWebhookEndpoints() {
  const res = await api.get<WebhookEndpointSummary[]>('/webhook-endpoints');
  return Array.isArray(res.data) ? res.data : [];
}
