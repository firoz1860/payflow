import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import api from '../api';
import { guestLogin, login, register } from './authService';
vi.mock('../api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
beforeEach(() => { vi.resetAllMocks(); vi.useFakeTimers(); });
afterEach(() => { vi.useRealTimers(); });

test('waits for ready services before creating exactly one guest', async () => {
  vi.mocked(api.get).mockResolvedValueOnce({ data: { ready: false } }).mockResolvedValueOnce({ data: { ready: true } });
  vi.mocked(api.post).mockResolvedValue({ data: { accessToken: 'test-token' } });
  const progress = vi.fn();
  const result = guestLogin({ onProgress: progress });
  await vi.advanceTimersByTimeAsync(0);
  expect(api.post).not.toHaveBeenCalled();
  expect(progress).toHaveBeenCalledWith('starting');
  await vi.advanceTimersByTimeAsync(5000);
  expect(await result).toEqual({ accessToken: 'test-token' });
  expect(api.post).toHaveBeenCalledTimes(1);
  expect(progress).toHaveBeenLastCalledWith('submitting');
});

test('retries only the readiness GET after a transient startup failure', async () => {
  vi.mocked(api.get).mockRejectedValueOnce(new Error('Network Error')).mockResolvedValueOnce({ data: { ready: true } });
  vi.mocked(api.post).mockRejectedValue(new Error('Guest creation failed'));
  const result = guestLogin();
  const rejection = expect(result).rejects.toThrow('Guest creation failed');
  await vi.advanceTimersByTimeAsync(5000);
  await rejection;
  expect(api.get).toHaveBeenCalledTimes(2);
  expect(api.post).toHaveBeenCalledTimes(1);
});

test('cancelling startup prevents guest creation and further polls', async () => {
  vi.mocked(api.get).mockResolvedValue({ data: { ready: false } });
  const controller = new AbortController();
  const result = guestLogin({ signal: controller.signal });
  const rejection = expect(result).rejects.toMatchObject({ name: 'AbortError' });
  await vi.advanceTimersByTimeAsync(0);
  controller.abort();
  await rejection;
  await vi.advanceTimersByTimeAsync(20000);
  expect(api.get).toHaveBeenCalledTimes(1);
  expect(api.post).not.toHaveBeenCalled();
});

test('gives a useful bounded startup error without ever submitting a guest', async () => {
  vi.mocked(api.get).mockResolvedValue({ data: { ready: false } });
  const result = guestLogin();
  const rejection = expect(result).rejects.toThrow('Sign-in services are taking longer than expected');
  await vi.advanceTimersByTimeAsync(480000);
  await rejection;
  expect(api.post).not.toHaveBeenCalled();
});

test('ready services let existing login and registration submit once', async () => {
  vi.mocked(api.get).mockResolvedValue({ data: { ready: true } });
  vi.mocked(api.post).mockResolvedValue({ data: {} });
  await login('test@example.test', 'test-password');
  await register('test@example.test', 'test-password', 'Test', 'Test business');
  expect(api.get).toHaveBeenCalledTimes(2);
  expect(api.post).toHaveBeenCalledTimes(2);
});

test('a cancelled in-flight readiness response cannot create a guest when it arrives late', async () => {
  let respond!: (response: { data: { ready: boolean } }) => void;
  vi.mocked(api.get).mockImplementation(() => new Promise(resolve => { respond = resolve; }));
  const controller = new AbortController();
  const result = guestLogin({ signal: controller.signal });
  const rejection = expect(result).rejects.toMatchObject({ name: 'AbortError' });
  controller.abort();
  respond({ data: { ready: true } });
  await rejection;
  expect(api.post).not.toHaveBeenCalled();
});
