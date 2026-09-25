import api from '../api';
import type { LedgerAccount, LedgerEntry, LedgerPosting } from '../types';

export async function listLedgerAccounts() {
  const res = await api.get<LedgerAccount[]>('/ledger/accounts');
  return res.data;
}

export async function listLedgerPostings(limit = 50) {
  const res = await api.get<LedgerPosting[]>('/ledger/postings', { params: { limit } });
  return res.data;
}

export async function getLedgerEntries(postingId: string) {
  const res = await api.get<LedgerEntry[]>(`/ledger/postings/${postingId}/entries`);
  return res.data;
}
