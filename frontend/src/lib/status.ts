import { LucideIcon } from 'lucide-react';

export const statusConfig: Record<string, { color: string; bg: string; label: string }> = {
  CREATED: { color: 'text-slate-600', bg: 'bg-slate-100', label: 'Created' },
  PENDING: { color: 'text-amber-700', bg: 'bg-amber-100', label: 'Pending' },
  PROCESSING: { color: 'text-blue-700', bg: 'bg-blue-100', label: 'Processing' },
  AUTHORIZED: { color: 'text-indigo-700', bg: 'bg-indigo-100', label: 'Authorized' },
  CAPTURED: { color: 'text-emerald-700', bg: 'bg-emerald-100', label: 'Captured' },
  FAILED: { color: 'text-red-700', bg: 'bg-red-100', label: 'Failed' },
  CANCELLED: { color: 'text-slate-500', bg: 'bg-slate-100', label: 'Cancelled' },
  PARTIALLY_REFUNDED: { color: 'text-cyan-700', bg: 'bg-cyan-100', label: 'Partially Refunded' },
  REFUNDED: { color: 'text-purple-700', bg: 'bg-purple-100', label: 'Refunded' },
};

export const merchantStatusConfig: Record<string, { color: string; bg: string; label: string }> = {
  ACTIVE: { color: 'text-emerald-700', bg: 'bg-emerald-100', label: 'Active' },
  PENDING: { color: 'text-amber-700', bg: 'bg-amber-100', label: 'Pending' },
  SUSPENDED: { color: 'text-orange-700', bg: 'bg-orange-100', label: 'Suspended' },
  BLOCKED: { color: 'text-red-700', bg: 'bg-red-100', label: 'Blocked' },
};

export const paymentMethodConfig: Record<string, { label: string; icon: string }> = {
  CARD: { label: 'Card', icon: '💳' },
  UPI: { label: 'UPI', icon: '📱' },
  QR: { label: 'QR Code', icon: '🔳' },
  NET_BANKING: { label: 'Net Banking', icon: '🏦' },
  WALLET: { label: 'Wallet', icon: '👛' },
};

export type StatusConfig = typeof statusConfig;
