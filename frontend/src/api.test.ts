import axios, { AxiosError } from 'axios';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import api from './api';
import { useAuthStore } from './store/auth';
const adapter = api.defaults.adapter;
const originalState = useAuthStore.getState();
let attempts = 0;
beforeEach(() => {
  attempts = 0;
  useAuthStore.setState({ accessToken: 'old-test-access', refreshToken: 'old-test-refresh' });
  vi.spyOn(axios, 'post').mockResolvedValue({ data: { accessToken: 'refreshed-test-access', refreshToken: 'refreshed-test-refresh' } });
  api.defaults.adapter = async config => {
    attempts++;
    throw new AxiosError('Invalid sign-in', 'ERR_BAD_REQUEST', config, undefined,
      { data: { message: 'Invalid sign-in' }, status: 401, statusText: 'Unauthorized', headers: {}, config });
  };
});
afterEach(() => { api.defaults.adapter = adapter; useAuthStore.setState(originalState); vi.restoreAllMocks(); });
test.each(['/auth/login', '/auth/register', '/auth/guest'])('does not refresh or replay public authentication POST %s', async path => {
  await expect(api.post(path, {})).rejects.toThrow('Invalid sign-in');
  expect(axios.post).not.toHaveBeenCalled();
  expect(attempts).toBe(1);
});
