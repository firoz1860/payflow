import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { MerchantProfilePage } from './MerchantProfilePage';
import { AdminMerchantDetailPage } from './AdminMerchantDetailPage';
import { LedgerPostingDetailPage } from './LedgerPostingDetailPage';
import type { LedgerEntry, LedgerPosting, Merchant } from '../types';

const merchant = vi.hoisted(() => ({
  getMyMerchant: vi.fn(), updateMyMerchant: vi.fn(), getMerchant: vi.fn(),
  changeMerchantStatus: vi.fn(), updateMerchantPricing: vi.fn(), enableLiveMode: vi.fn(),
}));
const ledger = vi.hoisted(() => ({ getLedgerPosting: vi.fn(), listLedgerPostings: vi.fn(), getLedgerEntries: vi.fn() }));
vi.mock('../services/merchantService', () => merchant);
vi.mock('../services/ledgerService', () => ledger);
vi.mock('../components/Toast', () => ({ toast: vi.fn() }));
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

const profile: Merchant = {
  id: 'merchant-old', merchantCode: 'MC_TEST', businessName: 'Fictional Merchant',
  email: 'merchant@example.com', phone: '', status: 'ACTIVE', country: 'IN', defaultCurrency: 'INR',
  feePercentage: 2, fixedFee: 0, settlementDelayDays: 7, liveModeEnabled: false,
  createdAt: '2026-10-13T03:00:00Z', updatedAt: '2026-10-13T03:00:00Z',
};
const currentMerchant = { ...profile, id: 'merchant-current', businessName: 'Current Merchant', feePercentage: 3 };
const oldId = '11111111-1111-4111-8111-111111111111';
const currentId = '22222222-2222-4222-8222-222222222222';
const posting: LedgerPosting = {
  id: oldId, sourceType: 'PAYMENT', sourceId: 'pay_old', currency: 'INR', totalDebit: 10,
  totalCredit: 10, description: 'Historical posting', createdAt: '2026-10-01T03:00:00Z',
};
const currentPosting = { ...posting, id: currentId, sourceId: 'pay_current', description: 'Current posting' };
const lines = (id: string): LedgerEntry[] => [
  { id: 'entry-debit', postingId: id, accountId: 'account-debit', entryType: 'DEBIT', amount: 10, currency: 'INR', description: null, createdAt: posting.createdAt },
  { id: 'entry-credit', postingId: id, accountId: 'account-credit', entryType: 'CREDIT', amount: 10, currency: 'INR', description: null, createdAt: posting.createdAt },
];
function admin() {
  return render(<MemoryRouter initialEntries={['/admin/merchants/merchant-old']}>
    <Link to="/admin/merchants/merchant-current">Switch merchant</Link>
    <Routes><Route path="/admin/merchants/:id" element={<AdminMerchantDetailPage />} /></Routes>
  </MemoryRouter>);
}
function ledgerPage() {
  return render(<MemoryRouter initialEntries={[`/ledger/postings/${oldId}`]}>
    <Link to={`/ledger/postings/${currentId}`}>Switch posting</Link>
    <Routes><Route path="/ledger/postings/:postingId" element={<LedgerPostingDetailPage />} /></Routes>
  </MemoryRouter>);
}

test('merchant read failure stays distinct from a missing account and retries perform no update', async () => {
  merchant.getMyMerchant.mockRejectedValueOnce(new Error('timeout')).mockResolvedValueOnce(profile);
  render(<MemoryRouter><MerchantProfilePage /></MemoryRouter>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Merchant profile is unavailable');
  expect(screen.queryByText('No merchant account found.')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Save Changes' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
  await screen.findByRole('heading', { name: 'Fictional Merchant' });
  expect(merchant.getMyMerchant).toHaveBeenCalledTimes(2);
  expect(merchant.updateMyMerchant).not.toHaveBeenCalled();
});

test('admin read failures hide actions until a read-only retry returns the merchant', async () => {
  merchant.getMerchant.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(profile);
  admin();
  expect(await screen.findByRole('alert')).toHaveTextContent('Merchant details are unavailable');
  expect(screen.queryByText('Merchant not found')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Enable Live Mode' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
  await screen.findByRole('heading', { name: 'Fictional Merchant', level: 1 });
  expect(merchant.getMerchant).toHaveBeenCalledTimes(2);
  expect(merchant.changeMerchantStatus).not.toHaveBeenCalled();
  expect(merchant.updateMerchantPricing).not.toHaveBeenCalled();
  expect(merchant.enableLiveMode).not.toHaveBeenCalled();
});

test.each(['profile', 'admin', 'ledger'])('a confirmed 404 retains the %s missing-record state', async page => {
  const missing = { response: { status: 404 } };
  if (page === 'profile') {
    merchant.getMyMerchant.mockRejectedValue(missing);
    render(<MemoryRouter><MerchantProfilePage /></MemoryRouter>);
    await screen.findByText('No merchant account found.');
  } else if (page === 'admin') {
    merchant.getMerchant.mockRejectedValue(missing);
    admin();
    await screen.findByText('Merchant not found');
  } else {
    ledger.getLedgerPosting.mockRejectedValue(missing);
    ledger.listLedgerPostings.mockRejectedValue(missing);
    ledger.getLedgerEntries.mockRejectedValue(missing);
    ledgerPage();
    await screen.findByText('Ledger posting not found.');
  }
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('an older posting loads directly even when it is absent from recent history', async () => {
  ledger.getLedgerPosting.mockResolvedValue(posting);
  ledger.listLedgerPostings.mockResolvedValue([]);
  ledger.getLedgerEntries.mockResolvedValue(lines(oldId));
  ledgerPage();
  await screen.findByRole('heading', { name: oldId });
  expect(screen.getByText('BALANCED', { exact: true })).toBeInTheDocument();
  expect(ledger.getLedgerPosting).toHaveBeenCalledWith(oldId);
  expect(ledger.listLedgerPostings).not.toHaveBeenCalled();
});

test('a failed ledger entry read hides totals and balance until a retry succeeds', async () => {
  ledger.getLedgerPosting.mockResolvedValue(posting);
  ledger.listLedgerPostings.mockResolvedValue([posting]);
  ledger.getLedgerEntries.mockRejectedValueOnce(new Error('timeout')).mockResolvedValueOnce(lines(oldId));
  ledgerPage();
  expect(await screen.findByRole('alert')).toHaveTextContent('Ledger posting is unavailable');
  expect(screen.queryByText('Ledger posting not found.')).not.toBeInTheDocument();
  expect(screen.queryByText('BALANCED', { exact: true })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
  await screen.findByText('BALANCED', { exact: true });
  expect(ledger.getLedgerPosting).toHaveBeenCalledTimes(2);
  expect(ledger.getLedgerEntries).toHaveBeenCalledTimes(2);
});

test('missing ledger lines cannot display a zero-value balanced posting', async () => {
  ledger.getLedgerPosting.mockResolvedValue(posting);
  ledger.listLedgerPostings.mockResolvedValue([posting]);
  ledger.getLedgerEntries.mockResolvedValue([]);
  ledgerPage();
  expect(await screen.findByRole('alert')).toHaveTextContent('Ledger posting is unavailable');
  expect(screen.queryByText('BALANCED', { exact: true })).not.toBeInTheDocument();
});

test('a late merchant read cannot replace details for the current route', async () => {
  let finish!: (m: Merchant) => void;
  merchant.getMerchant.mockReturnValueOnce(new Promise<Merchant>(resolve => { finish = resolve; }))
    .mockResolvedValueOnce(currentMerchant);
  admin();
  fireEvent.click(screen.getByRole('link', { name: 'Switch merchant' }));
  await screen.findByRole('heading', { name: 'Current Merchant', level: 1 });
  await act(async () => { finish(profile); });
  expect(screen.getByRole('heading', { name: 'Current Merchant', level: 1 })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Fictional Merchant', level: 1 })).not.toBeInTheDocument();
});

test('a late status update cannot overwrite another merchant after navigation', async () => {
  let finish!: (m: Merchant) => void;
  merchant.getMerchant.mockResolvedValueOnce(profile).mockResolvedValueOnce(currentMerchant);
  merchant.changeMerchantStatus.mockReturnValueOnce(new Promise<Merchant>(resolve => { finish = resolve; }));
  admin();
  fireEvent.click(await screen.findByRole('button', { name: 'Suspend' }));
  fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));
  fireEvent.click(screen.getByRole('link', { name: 'Switch merchant' }));
  await screen.findByRole('heading', { name: 'Current Merchant', level: 1 });
  await act(async () => { finish({ ...profile, status: 'SUSPENDED' }); });
  expect(screen.getByRole('heading', { name: 'Current Merchant', level: 1 })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Fictional Merchant', level: 1 })).not.toBeInTheDocument();
  expect(merchant.changeMerchantStatus).toHaveBeenCalledOnce();
  expect(merchant.changeMerchantStatus).toHaveBeenCalledWith('merchant-old', 'SUSPENDED', undefined);
});

test.each(['success', 'failure'])('a late posting %s cannot change details for the current route', async outcome => {
  let finish!: (p: LedgerPosting) => void;
  let fail!: (err: unknown) => void;
  const old = new Promise<LedgerPosting>((resolve, reject) => { finish = resolve; fail = reject; });
  ledger.getLedgerPosting.mockReturnValueOnce(old).mockResolvedValueOnce(currentPosting);
  ledger.listLedgerPostings.mockImplementationOnce(() => old.then(p => [p])).mockResolvedValueOnce([currentPosting]);
  ledger.getLedgerEntries.mockImplementation((id: string) => Promise.resolve(lines(id)));
  ledgerPage();
  fireEvent.click(screen.getByRole('link', { name: 'Switch posting' }));
  await screen.findByRole('heading', { name: currentId });
  await act(async () => { if (outcome === 'success') finish(posting); else fail({ response: { status: 404 } }); });
  expect(screen.getByRole('heading', { name: currentId })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: oldId })).not.toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(screen.queryByText('Ledger posting not found.')).not.toBeInTheDocument();
});
