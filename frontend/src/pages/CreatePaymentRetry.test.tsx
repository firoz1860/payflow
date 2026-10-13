import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CreatePaymentPage } from './CreatePaymentPage';
import type { Payment } from '../types';

const service = vi.hoisted(() => ({ createPayment: vi.fn() }));
vi.mock('../services/paymentService', () => service);
vi.mock('../components/Toast', () => ({ toast: vi.fn() }));
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

const result: Payment = {
  paymentReference: 'pay_original', merchantOrderId: null, amount: 10, currency: 'INR',
  refundedAmount: 0, refundableAmount: 10, status: 'PENDING', environment: 'TEST',
  description: null, provider: 'sandbox', providerPaymentId: 'sbx_original', checkoutUrl: null,
  qrCode: null, failureCode: null, failureMessage: null, metadata: null, attempts: [],
  createdAt: '2026-10-13T03:00:00Z', updatedAt: '2026-10-13T03:00:00Z', expiresAt: null,
};

function form() {
  const view = render(<MemoryRouter><CreatePaymentPage /></MemoryRouter>);
  fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '10' } });
  return view.container.querySelector('form')!;
}

test('a lost response retries the original logical payment rather than accepting another one', async () => {
  const accepted = new Map<string, Payment>();
  let responseLost = true;
  service.createPayment.mockImplementation(async (_body, key: string) => {
    if (!accepted.has(key)) accepted.set(key, { ...result, paymentReference: `pay_${accepted.size + 1}` });
    if (responseLost) { responseLost = false; throw new Error('Response lost after acceptance'); }
    return accepted.get(key);
  });
  const node = form();
  fireEvent.submit(node);
  await waitFor(() => expect(screen.getByRole('button', { name: /Create Payment|Retry same request/ })).toBeEnabled());
  fireEvent.submit(node);
  await screen.findByRole('button', { name: 'Create Another' });
  expect(accepted.size).toBe(1);
  expect(service.createPayment.mock.calls[1]).toEqual(service.createPayment.mock.calls[0]);
});

test('an uncertain result stays visible and checking history does not submit another payment', async () => {
  service.createPayment.mockRejectedValue(new Error('timeout'));
  form();
  fireEvent.click(screen.getByRole('button', { name: 'Create Payment' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('may already exist');
  const check = screen.getByRole('link', { name: 'View payments in new tab' });
  expect(check).toHaveAttribute('href', '/payments');
  expect(check).toHaveAttribute('target', '_blank');
  expect(check).toHaveAttribute('rel', 'noopener noreferrer');
  fireEvent.click(check);
  expect(service.createPayment).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('spinbutton')).toHaveValue(10);
});

test('synchronous repeated submits send only one creation request', async () => {
  let finish!: (payment: Payment) => void;
  service.createPayment.mockReturnValue(new Promise<Payment>(resolve => { finish = resolve; }));
  const node = form();
  act(() => { fireEvent.submit(node); fireEvent.submit(node); });
  expect(service.createPayment).toHaveBeenCalledTimes(1);
  await act(async () => { finish(result); });
  await screen.findByRole('button', { name: 'Create Another' });
});

test('Create Another after a verified result starts a new logical request', async () => {
  service.createPayment.mockResolvedValue(result);
  const node = form();
  fireEvent.submit(node);
  fireEvent.click(await screen.findByRole('button', { name: 'Create Another' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Create Payment' }));
  await waitFor(() => expect(service.createPayment).toHaveBeenCalledTimes(2));
  expect(service.createPayment.mock.calls[1][1]).not.toEqual(service.createPayment.mock.calls[0][1]);
});
