import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { DashboardPage } from './DashboardPage';
import { AnalyticsPage } from './AnalyticsPage';
import { LedgerPage } from './LedgerPage';
import { useAuthStore } from '../store/auth';
import type { User } from '../types';

const payment = vi.hoisted(() => ({ listPayments: vi.fn(), createPayment: vi.fn() }));
const merchant = vi.hoisted(() => ({ getMyMerchant: vi.fn(), listApiKeys: vi.fn(), updateMyMerchant: vi.fn() }));
const onboarding = vi.hoisted(() => ({ getOnboarding: vi.fn(), updateOnboarding: vi.fn() }));
const ledger = vi.hoisted(() => ({ listLedgerAccounts: vi.fn(), listLedgerPostings: vi.fn() }));
vi.mock('../services/paymentService', async original => ({ ...await original<typeof import('../services/paymentService')>(), ...payment }));
vi.mock('../services/merchantService', async original => ({ ...await original<typeof import('../services/merchantService')>(), ...merchant }));
vi.mock('../services/onboardingService', async original => ({ ...await original<typeof import('../services/onboardingService')>(), ...onboarding }));
vi.mock('../services/ledgerService', () => ledger);
vi.mock('../components/Toast', () => ({ toast: vi.fn() }));
// Charts need browser layout; the summary values and error states are rendered by the real pages.
vi.mock('recharts', async original => ({ ...await original<typeof import('recharts')>(), ResponsiveContainer: () => null }));

const captured = {
  paymentReference: 'pay_summary_verified', amount: 10, currency: 'INR', status: 'CAPTURED',
  attempts: [], createdAt: '2026-10-12T14:00:00Z',
};
const posting = {
  id: 'posting_summary_verified', sourceId: 'pay_summary_verified', sourceType: 'PAYMENT',
  totalDebit: 10, totalCredit: 10, currency: 'INR', createdAt: '2026-10-12T14:00:00Z',
};

beforeEach(() => {
  vi.resetAllMocks();
  useAuthStore.setState({ user: {
    id: 'summary-test-merchant', email: 'guest@payflow.demo', emailVerified: true, fullName: 'Guest Merchant',
    roles: ['MERCHANT_OWNER'], permissions: ['api_keys:manage'], onboarding: { status: 'COMPLETED' },
  } as User });
  payment.listPayments.mockResolvedValue({ data: [captured] });
  merchant.getMyMerchant.mockResolvedValue({ defaultCurrency: 'INR' });
  merchant.listApiKeys.mockResolvedValue([]);
  onboarding.getOnboarding.mockResolvedValue({ status: 'COMPLETED' });
  ledger.listLedgerAccounts.mockResolvedValue([{ accountType: 'MERCHANT_PAYABLE', balance: 9.8, currency: 'INR' }]);
  ledger.listLedgerPostings.mockResolvedValue([posting]);
});
afterEach(() => { cleanup(); useAuthStore.setState({ user: null }); });

test.each(['payments', 'merchant', 'keys', 'onboarding'])(
  'dashboard reports a failed %s read instead of fabricating totals or setup state', async dependency => {
    const read = { payments: payment.listPayments, merchant: merchant.getMyMerchant,
      keys: merchant.listApiKeys, onboarding: onboarding.getOnboarding }[dependency]!;
    read.mockRejectedValueOnce(new Error('unavailable'));
    render(<MemoryRouter><DashboardPage /></MemoryRouter>);
    expect(await screen.findByRole('alert')).toHaveTextContent('Dashboard is unavailable');
    expect(screen.queryByText('Total Volume')).not.toBeInTheDocument();
    expect(screen.queryByText('No payments yet')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('link', { name: /pay_summary_verified/ })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(payment.createPayment).not.toHaveBeenCalled();
    expect(merchant.updateMyMerchant).not.toHaveBeenCalled();
    expect(onboarding.updateOnboarding).not.toHaveBeenCalled();
  },
);

test('a verified empty dashboard remains an actual empty state', async () => {
  payment.listPayments.mockResolvedValue({ data: [] });
  render(<MemoryRouter><DashboardPage /></MemoryRouter>);
  expect(await screen.findByText('No payments yet')).toBeInTheDocument();
  expect(screen.getByText('Total Volume')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('dashboard skips reads the current role cannot perform', async () => {
  const user = useAuthStore.getState().user!;
  useAuthStore.setState({ user: { ...user, roles: ['MERCHANT_VIEWER'], permissions: [] } });
  merchant.listApiKeys.mockRejectedValue(new Error('forbidden'));
  onboarding.getOnboarding.mockRejectedValue(new Error('forbidden'));
  render(<MemoryRouter><DashboardPage /></MemoryRouter>);
  await screen.findByRole('link', { name: /pay_summary_verified/ });
  expect(merchant.listApiKeys).not.toHaveBeenCalled();
  expect(onboarding.getOnboarding).not.toHaveBeenCalled();
});

test('analytics shows unavailable until a read-only retry returns actual payments', async () => {
  payment.listPayments.mockRejectedValueOnce(new Error('timeout'));
  render(<MemoryRouter><AnalyticsPage /></MemoryRouter>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Analytics are unavailable');
  expect(screen.queryByText('Success Rate')).not.toBeInTheDocument();
  expect(screen.queryByText('No captured payments yet')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
  expect(await screen.findByText('100.0%')).toBeInTheDocument();
  expect(payment.createPayment).not.toHaveBeenCalled();
});

test('analytics hides previously verified totals when refresh fails', async () => {
  render(<MemoryRouter><AnalyticsPage /></MemoryRouter>);
  await screen.findByText('100.0%');
  payment.listPayments.mockRejectedValueOnce(new Error('timeout'));
  fireEvent.click(screen.getByRole('button', { name: 'Refresh' }));
  await screen.findByRole('alert');
  expect(screen.queryByText('100.0%')).not.toBeInTheDocument();
});

test.each(['accounts', 'postings'])('ledger failure loading %s cannot report balanced or zero', async dependency => {
  const read = dependency === 'accounts' ? ledger.listLedgerAccounts : ledger.listLedgerPostings;
  read.mockRejectedValueOnce(new Error('timeout'));
  render(<MemoryRouter><LedgerPage /></MemoryRouter>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Ledger is unavailable');
  expect(screen.queryByText('BALANCED')).not.toBeInTheDocument();
  expect(screen.queryByText('Merchant Payable')).not.toBeInTheDocument();
  expect(screen.queryByText('No ledger postings yet')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
  expect(await screen.findByText('pay_summary_verified')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('ledger hides old balances and health when refresh fails', async () => {
  render(<MemoryRouter><LedgerPage /></MemoryRouter>);
  await screen.findByText('pay_summary_verified');
  ledger.listLedgerPostings.mockRejectedValueOnce(new Error('timeout'));
  fireEvent.click(screen.getByRole('button', { name: 'Refresh' }));
  await screen.findByRole('alert');
  expect(screen.queryByText('Merchant Payable')).not.toBeInTheDocument();
  expect(screen.queryByText('BALANCED')).not.toBeInTheDocument();
});
