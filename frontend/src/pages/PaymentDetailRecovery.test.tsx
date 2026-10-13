import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { PaymentDetailPage } from './PaymentDetailPage';
import type { Payment } from '../types';

const service = vi.hoisted(() => ({ getPayment: vi.fn(), cancelPayment: vi.fn() }));
const permissions = vi.hoisted(() => ({ create: true }));
const checkout = vi.hoisted(() => ({ notify: undefined as undefined | ((payment: Payment) => void) }));
vi.mock('../services/paymentService', () => service);
vi.mock('../components/RazorpayCheckout', () => ({ RazorpayCheckout: ({ onUpdated }: { onUpdated: (payment: Payment) => void }) => {
  checkout.notify = onUpdated;
  return null;
} }));
vi.mock('../components/Toast', () => ({ toast: vi.fn() }));
vi.mock('../store/auth', () => ({ useAuthStore: (selector: any) => selector({
  hasPermission: (permission: string) => permission === 'payments:create' && permissions.create,
}) }));
beforeEach(() => { vi.resetAllMocks(); permissions.create = true; checkout.notify = undefined; });
afterEach(() => { cleanup(); vi.useRealTimers(); });

const old: Payment = {
  paymentReference: 'pay_old', merchantOrderId: 'test-old', amount: 10, currency: 'INR',
  refundedAmount: 0, refundableAmount: 10, status: 'CAPTURED', environment: 'TEST',
  description: 'Old TEST payment', provider: 'sandbox', providerPaymentId: 'sbx_old',
  checkoutUrl: null, qrCode: null, failureCode: null, failureMessage: null, metadata: null,
  attempts: [], createdAt: '2026-10-13T06:00:00Z', updatedAt: '2026-10-13T06:00:00Z', expiresAt: null,
};
const pending = { ...old, status: 'PENDING' };
const current = { ...old, paymentReference: 'pay_current', merchantOrderId: 'test-current', description: 'Current TEST payment' };
function page() {
  render(<MemoryRouter initialEntries={['/payments/pay_old']}>
    <Link to="/payments/pay_current">Switch payment</Link>
    <Routes><Route path="/payments/:reference" element={<PaymentDetailPage />} /></Routes>
  </MemoryRouter>);
}

test('a payment read outage stays recoverable and retry only reads status', async () => {
  service.getPayment.mockRejectedValueOnce({ response: { status: 503 } }).mockResolvedValueOnce(old);
  page();
  expect(await screen.findByRole('alert')).toHaveTextContent('Payment details are unavailable');
  expect(screen.queryByText('Payment not found')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Cancel Payment' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
  await screen.findByText('pay_old', { exact: true });
  expect(screen.getByText('Captured', { exact: true })).toBeInTheDocument();
  expect(service.getPayment).toHaveBeenCalledTimes(2);
  expect(service.cancelPayment).not.toHaveBeenCalled();
});

test('a confirmed payment 404 retains the missing-record state', async () => {
  service.getPayment.mockRejectedValue({ response: { status: 404 } });
  page();
  await screen.findByText('Payment not found');
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test.each(['success', 'failure'])('a late payment read %s cannot replace the current route', async outcome => {
  let finish!: (value: Payment) => void;
  let fail!: (error: unknown) => void;
  service.getPayment.mockReturnValueOnce(new Promise<Payment>((resolve, reject) => { finish = resolve; fail = reject; }))
    .mockResolvedValueOnce(current);
  page();
  fireEvent.click(screen.getByRole('link', { name: 'Switch payment' }));
  await screen.findByText('pay_current', { exact: true });
  await act(async () => { if (outcome === 'success') finish(old); else fail({ response: { status: 404 } }); });
  expect(screen.getByText('pay_current', { exact: true })).toBeInTheDocument();
  expect(screen.queryByText('pay_old', { exact: true })).not.toBeInTheDocument();
  expect(screen.queryByText('Payment not found')).not.toBeInTheDocument();
});

test('an incorrect payment response identity never exposes its actions', async () => {
  service.getPayment.mockResolvedValue({ ...pending, paymentReference: 'pay_unrelated' });
  page();
  expect(await screen.findByRole('alert')).toHaveTextContent('Payment details are unavailable');
  expect(screen.queryByText('pay_unrelated', { exact: true })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Cancel Payment' })).not.toBeInTheDocument();
});

test('a late cancellation cannot overwrite a different payment after navigation', async () => {
  let finish!: (value: Payment) => void;
  service.getPayment.mockResolvedValueOnce(pending).mockResolvedValueOnce(current);
  service.cancelPayment.mockReturnValueOnce(new Promise<Payment>(resolve => { finish = resolve; }));
  page();
  fireEvent.click(await screen.findByRole('button', { name: 'Cancel Payment' }));
  fireEvent.click(screen.getByRole('button', { name: 'Confirm Cancel' }));
  fireEvent.click(screen.getByRole('link', { name: 'Switch payment' }));
  await screen.findByText('pay_current', { exact: true });
  await act(async () => { finish({ ...pending, status: 'CANCELLED' }); });
  expect(screen.getByText('pay_current', { exact: true })).toBeInTheDocument();
  expect(screen.queryByText('Cancelled', { exact: true })).not.toBeInTheDocument();
  expect(service.cancelPayment).toHaveBeenCalledOnce();
  expect(service.cancelPayment).toHaveBeenCalledWith('pay_old', undefined);
});

test('double cancellation submission sends one request while the result is pending', async () => {
  service.getPayment.mockResolvedValue(pending);
  service.cancelPayment.mockReturnValue(new Promise(() => {}));
  page();
  fireEvent.click(await screen.findByRole('button', { name: 'Cancel Payment' }));
  const confirm = screen.getByRole('button', { name: 'Confirm Cancel' });
  fireEvent.click(confirm);
  fireEvent.click(confirm);
  expect(service.cancelPayment).toHaveBeenCalledOnce();
  expect(confirm).toBeDisabled();
});

test('read-only payment users cannot start a cancellation', async () => {
  permissions.create = false;
  service.getPayment.mockResolvedValue(pending);
  page();
  await screen.findByText('pay_old', { exact: true });
  expect(screen.queryByRole('button', { name: 'Cancel Payment' })).not.toBeInTheDocument();
});

test('an uncertain cancellation requires a status read without replaying the action', async () => {
  service.getPayment.mockResolvedValueOnce(pending).mockResolvedValueOnce(old);
  service.cancelPayment.mockRejectedValue({ response: { status: 503 } });
  page();
  fireEvent.click(await screen.findByRole('button', { name: 'Cancel Payment' }));
  fireEvent.click(screen.getByRole('button', { name: 'Confirm Cancel' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Cancellation result could not be confirmed');
  expect(screen.queryByRole('button', { name: 'Cancel Payment' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
  await screen.findByText('Captured', { exact: true });
  expect(service.cancelPayment).toHaveBeenCalledOnce();
  expect(service.getPayment).toHaveBeenCalledTimes(2);
});

test('a late poll cannot replace a newer manual refresh with the same status', async () => {
  let finish!: (value: Payment) => void;
  service.getPayment.mockResolvedValueOnce(pending)
    .mockReturnValueOnce(new Promise<Payment>(resolve => { finish = resolve; }))
    .mockResolvedValueOnce({ ...pending, description: 'Refreshed payment data' });
  vi.useFakeTimers();
  page();
  await act(async () => {});
  expect(screen.getByText('Pending', { exact: true })).toBeInTheDocument();
  await act(async () => { vi.advanceTimersByTime(5000); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Refresh' })); });
  expect(screen.getByText('Refreshed payment data', { exact: true })).toBeInTheDocument();
  await act(async () => { finish(pending); });
  expect(screen.getByText('Refreshed payment data', { exact: true })).toBeInTheDocument();
  expect(screen.queryByText('Old TEST payment', { exact: true })).not.toBeInTheDocument();
});

test('a checkout response for an unrelated payment cannot replace the displayed payment', async () => {
  service.getPayment.mockResolvedValue({ ...pending, provider: 'razorpay' });
  page();
  await screen.findByText('pay_old', { exact: true });
  await act(async () => { checkout.notify!({ ...old, paymentReference: 'pay_unrelated', provider: 'razorpay' }); });
  expect(screen.getByText('pay_old', { exact: true })).toBeInTheDocument();
  expect(screen.queryByText('pay_unrelated', { exact: true })).not.toBeInTheDocument();
});

test('a late checkout callback cannot replace a different payment after navigation', async () => {
  service.getPayment.mockResolvedValueOnce({ ...pending, provider: 'razorpay' }).mockResolvedValueOnce(current);
  page();
  await screen.findByText('pay_old', { exact: true });
  const notify = checkout.notify!;
  fireEvent.click(screen.getByRole('link', { name: 'Switch payment' }));
  await screen.findByText('pay_current', { exact: true });
  await act(async () => { notify({ ...old, provider: 'razorpay' }); });
  expect(screen.getByText('pay_current', { exact: true })).toBeInTheDocument();
  expect(screen.queryByText('pay_old', { exact: true })).not.toBeInTheDocument();
});

test('a failed status poll removes payment actions until a read-only retry confirms status', async () => {
  service.getPayment.mockResolvedValueOnce(pending).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(old);
  vi.useFakeTimers();
  page();
  await act(async () => {});
  expect(screen.getByText('Pending', { exact: true })).toBeInTheDocument();
  await act(async () => { vi.advanceTimersByTime(5000); });
  expect(screen.getByRole('alert')).toHaveTextContent('Payment details are unavailable');
  expect(screen.queryByRole('button', { name: 'Cancel Payment' })).not.toBeInTheDocument();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Retry' })); });
  expect(screen.getByText('Captured', { exact: true })).toBeInTheDocument();
  expect(service.cancelPayment).not.toHaveBeenCalled();
});
