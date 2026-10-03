// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup, act } from '@testing-library/react';
import { RazorpayCheckout } from './RazorpayCheckout';
import type { RazorpayOptions } from '../lib/razorpay';
const mocks = vi.hoisted(() => ({ getCheckout: vi.fn(), verifyCheckout: vi.fn(), getPayment: vi.fn(), reconcileCheckout: vi.fn(), load: vi.fn() }));
vi.mock('../services/paymentService', () => ({ getCheckout: mocks.getCheckout, verifyCheckout: mocks.verifyCheckout, getPayment: mocks.getPayment, reconcileCheckout: mocks.reconcileCheckout }));
vi.mock('../lib/razorpay', () => ({ loadRazorpay: mocks.load }));
let options: RazorpayOptions; let failed: () => void;
class Checkout { constructor(input: RazorpayOptions) { options = input; } open() {} close() {} on(_event: string, handler: () => void) { failed = handler; } }
const payment = { paymentReference: 'pay_test', status: 'CAPTURED' };
beforeEach(() => {
  vi.clearAllMocks(); options = undefined as unknown as RazorpayOptions;
  mocks.load.mockResolvedValue(Checkout);
  mocks.getCheckout.mockResolvedValue({ provider: 'razorpay', mode: 'TEST', keyId: 'rzp_test_example', orderId: 'order_one', amountMinor: 100, currency: 'INR' });
  mocks.verifyCheckout.mockResolvedValue(payment); mocks.getPayment.mockResolvedValue(payment); mocks.reconcileCheckout.mockResolvedValue(payment);
});
afterEach(() => { cleanup(); vi.useRealTimers(); });
async function launch() { fireEvent.click(screen.getByRole('button', { name: 'Pay with Razorpay (TEST)' })); await waitFor(() => expect(options?.order_id).toBe('order_one')); }
test('opens provider Checkout with server options and verifies signed evidence', async () => {
  const updated = vi.fn(); render(<RazorpayCheckout paymentReference="pay_test" onUpdated={updated} />); await launch();
  expect(options.amount).toBe(100); expect(options.key).toBe('rzp_test_example');
  await act(async () => options.handler({ razorpay_order_id: 'order_one', razorpay_payment_id: 'pay_real', razorpay_signature: 'signed' }));
  expect(mocks.verifyCheckout).toHaveBeenCalledWith('pay_test', { orderId: 'order_one', paymentId: 'pay_real', signature: 'signed' });
  expect(updated).toHaveBeenCalledWith(payment);
});
test('dismissal leaves payment state unchanged and allows reopening', async () => {
  const updated = vi.fn(); render(<RazorpayCheckout paymentReference="pay_test" onUpdated={updated} />); await launch();
  act(() => options.modal.ondismiss()); expect(updated).not.toHaveBeenCalled(); expect(mocks.verifyCheckout).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: 'Pay with Razorpay (TEST)' }).hasAttribute('disabled')).toBe(false);
});
test('duplicate clicks create one checkout launch', async () => {
  render(<RazorpayCheckout paymentReference="pay_test" onUpdated={vi.fn()} />);
  const button = screen.getByRole('button', { name: 'Pay with Razorpay (TEST)' }); fireEvent.click(button); fireEvent.click(button);
  await waitFor(() => expect(mocks.getCheckout).toHaveBeenCalledTimes(1));
});
test('script load failure shows retryable error', async () => {
  mocks.load.mockRejectedValue(new Error('Unable to load Razorpay checkout'));
  render(<RazorpayCheckout paymentReference="pay_test" onUpdated={vi.fn()} />); fireEvent.click(screen.getByRole('button', { name: 'Pay with Razorpay (TEST)' }));
  await waitFor(() => expect(screen.getByRole('status').textContent).toContain('Unable to load'));
});
test('provider decline is shown without fabricating captured state', async () => {
  const updated = vi.fn(); render(<RazorpayCheckout paymentReference="pay_test" onUpdated={updated} />); await launch();act(() => failed());
  expect(screen.getByRole('status').textContent).toContain('declined'); expect(updated).not.toHaveBeenCalled();
});
test('verification failure never claims successful payment', async () => {
  mocks.verifyCheckout.mockRejectedValue(new Error('Signature rejected'));const updated = vi.fn();
  render(<RazorpayCheckout paymentReference="pay_test" onUpdated={updated} />);await launch();
  await act(async () => options.handler({ razorpay_order_id: 'order_one', razorpay_payment_id: 'pay_real', razorpay_signature: 'bad' }));
  expect(updated).not.toHaveBeenCalled();expect(screen.getByRole('status').textContent).toContain('Signature rejected');
});
test('unmount ignores late checkout callbacks', async () => {
  const updated = vi.fn();const view=render(<RazorpayCheckout paymentReference="pay_test" onUpdated={updated} />);await launch();view.unmount();
  await act(async () => options.handler({ razorpay_order_id: 'order_one', razorpay_payment_id: 'pay_real', razorpay_signature: 'signed' }));
  expect(mocks.verifyCheckout).not.toHaveBeenCalled();expect(updated).not.toHaveBeenCalled();
});
test('pending verification polls for at most 60 seconds and then offers manual refresh', async () => {
  vi.useFakeTimers(); const pending = { paymentReference: 'pay_test', status: 'PENDING' };
  mocks.verifyCheckout.mockResolvedValue(pending); mocks.getPayment.mockResolvedValue(pending);
  const updated = vi.fn(); render(<RazorpayCheckout paymentReference="pay_test" onUpdated={updated} />);
  await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Pay with Razorpay (TEST)' })));
  await act(async () => options.handler({ razorpay_order_id: 'order_one', razorpay_payment_id: 'pay_real', razorpay_signature: 'signed' }));
  await act(async () => vi.advanceTimersByTimeAsync(60000));
  expect(screen.getByRole('status').textContent).toContain('still processing');const calls=mocks.getPayment.mock.calls.length;
  await act(async () => vi.advanceTimersByTimeAsync(60000));expect(mocks.getPayment.mock.calls.length).toBe(calls);
  expect(screen.getByRole('button', { name: 'Refresh payment status' }).hasAttribute('disabled')).toBe(false);
});
test('unmount clears pending status polling', async () => {
  vi.useFakeTimers(); const pending = { paymentReference: 'pay_test', status: 'PENDING' };
  mocks.verifyCheckout.mockResolvedValue(pending);mocks.getPayment.mockResolvedValue(pending);
  const view=render(<RazorpayCheckout paymentReference="pay_test" onUpdated={vi.fn()} />);
  await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Pay with Razorpay (TEST)' })));
  await act(async () => options.handler({ razorpay_order_id: 'order_one', razorpay_payment_id: 'pay_real', razorpay_signature: 'signed' }));
  view.unmount();await act(async () => vi.advanceTimersByTimeAsync(60000));expect(mocks.getPayment).not.toHaveBeenCalled();
});
