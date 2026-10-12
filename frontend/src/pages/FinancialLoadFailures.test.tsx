import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaymentsPage } from './PaymentsPage';
import { ApiKeysPage } from './ApiKeysPage';
import { QrPaymentsPage } from './QrPaymentsPage';

const payment = vi.hoisted(() => ({ listPayments: vi.fn(), createPayment: vi.fn(), getPayment: vi.fn() }));
const merchant = vi.hoisted(() => ({ listApiKeys: vi.fn(), createApiKey: vi.fn(), revokeApiKey: vi.fn(), updateApiKey: vi.fn() }));
vi.mock('../services/paymentService', () => payment);
vi.mock('../services/merchantService', () => merchant);
vi.mock('../components/Toast', () => ({ toast: vi.fn() }));

beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

const captured = {
  paymentReference: 'pay_verified_test', merchantOrderId: 'test-order', amount: 10, currency: 'INR',
  status: 'CAPTURED', provider: 'sandbox', environment: 'TEST', attempts: [],
  createdAt: '2026-10-12T06:30:00Z',
};

test('payment load failure stays visible and read-only retry restores actual records', async () => {
  payment.listPayments.mockRejectedValueOnce(new Error('timeout')).mockResolvedValueOnce({
    data: [captured], page: 0, size: 20, totalElements: 1, totalPages: 1,
  });
  render(<MemoryRouter><PaymentsPage /></MemoryRouter>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Payments are unavailable');
  expect(screen.queryByText('No payments found')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
  expect(await screen.findByRole('link', { name: 'pay_verified_test' })).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(payment.listPayments).toHaveBeenCalledTimes(2);
  expect(payment.createPayment).not.toHaveBeenCalled();
});

test('successful empty payment history still shows the real empty state', async () => {
  payment.listPayments.mockResolvedValue({ data: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  render(<MemoryRouter><PaymentsPage /></MemoryRouter>);
  expect(await screen.findByText('No payments found')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('a late failure from an old payment filter cannot replace the current records', async () => {
  let rejectOld!: (reason: Error) => void;
  payment.listPayments.mockReturnValueOnce(new Promise((_, reject) => { rejectOld = reject; }))
    .mockResolvedValueOnce({ data: [captured], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  render(<MemoryRouter><PaymentsPage /></MemoryRouter>);
  fireEvent.click(screen.getByRole('button', { name: 'CAPTURED' }));
  await screen.findByRole('link', { name: 'pay_verified_test' });
  await act(async () => { rejectOld(new Error('old request timed out')); });
  expect(screen.getByRole('link', { name: 'pay_verified_test' })).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(payment.listPayments).toHaveBeenLastCalledWith({ status: 'CAPTURED', page: 0, size: 20 });
});

test('a late success from an old payment filter cannot hide the current load failure', async () => {
  let resolveOld!: (result: unknown) => void;
  payment.listPayments.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve; }))
    .mockRejectedValueOnce(new Error('new request timed out'));
  render(<MemoryRouter><PaymentsPage /></MemoryRouter>);
  fireEvent.click(screen.getByRole('button', { name: 'FAILED' }));
  await screen.findByRole('alert');
  await act(async () => { resolveOld({ data: [captured], page: 0, size: 20, totalElements: 1, totalPages: 1 }); });
  expect(screen.getByRole('alert')).toHaveTextContent('Payments are unavailable');
  expect(screen.queryByRole('link', { name: 'pay_verified_test' })).not.toBeInTheDocument();
});

test('API key load failure is distinct from an empty list and retry creates no credentials', async () => {
  merchant.listApiKeys.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([]);
  render(<MemoryRouter><ApiKeysPage /></MemoryRouter>);
  expect(await screen.findByRole('alert')).toHaveTextContent('API keys are unavailable');
  expect(screen.queryByText('No API keys yet')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
  expect(await screen.findByText('No API keys yet')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(merchant.listApiKeys).toHaveBeenCalledTimes(2);
  expect(merchant.createApiKey).not.toHaveBeenCalled();
  expect(merchant.revokeApiKey).not.toHaveBeenCalled();
});

test('QR order check opens separately, preserving the form and key for an explicit retry', async () => {
  payment.createPayment.mockRejectedValueOnce(new Error('timeout')).mockResolvedValueOnce(captured);
  render(<MemoryRouter><QrPaymentsPage /></MemoryRouter>);
  fireEvent.click(screen.getByRole('button', { name: 'Create QR Payment' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Check Payments for the order before retrying');
  const orderCheck = screen.getByRole('link', { name: 'View payments in new tab' });
  expect(orderCheck).toHaveAttribute('href', '/payments');
  expect(orderCheck).toHaveAttribute('target', '_blank');
  expect(orderCheck).toHaveAttribute('rel', 'noopener noreferrer');
  fireEvent.click(orderCheck);
  expect(payment.createPayment).toHaveBeenCalledTimes(1);
  const original = payment.createPayment.mock.calls[0];
  fireEvent.click(screen.getByRole('button', { name: 'Create QR Payment' }));
  await waitFor(() => expect(payment.createPayment).toHaveBeenCalledTimes(2));
  expect(payment.createPayment.mock.calls[1]).toEqual(original);
  await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
});
