import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ConnectProviderModal } from './ConnectProviderModal';
import { useAuthStore } from '../../store/auth';

const mocks = vi.hoisted(() => ({ connectCredential: vi.fn() }));
vi.mock('../../services/copilotService', () => ({
  connectCredential: mocks.connectCredential,
}));

const RAW_KEY = 'sk-super-secret-value-123456';

function storageSnapshot(): string {
  const dump: string[] = [];
  for (const store of [window.localStorage, window.sessionStorage]) {
    for (let i = 0; i < store.length; i += 1) {
      const key = store.key(i);
      if (key) dump.push(`${key}=${store.getItem(key)}`);
    }
  }
  return dump.join('\n');
}

beforeEach(() => {
  vi.clearAllMocks();
  window.localStorage.clear();
  window.sessionStorage.clear();
  useAuthStore.setState({
    user: null,
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    isAuthenticated: true,
  });
  mocks.connectCredential.mockResolvedValue({
    provider: 'ANTHROPIC',
    configured: true,
    source: 'USER',
    masked: 'sk-ant-••••3456',
    expiresAt: '2026-12-01T00:00:00.000Z',
  });
});

afterEach(() => cleanup());

describe('ConnectProviderModal', () => {
  test('submits the key to the service and never persists it client-side', async () => {
    const onConnected = vi.fn();
    const onClose = vi.fn();
    render(<ConnectProviderModal open onClose={onClose} onConnected={onConnected} />);

    fireEvent.change(screen.getByLabelText('API key'), { target: { value: RAW_KEY } });
    fireEvent.click(screen.getByRole('button', { name: /connect & continue/i }));

    await waitFor(() => expect(mocks.connectCredential).toHaveBeenCalledTimes(1));
    expect(mocks.connectCredential).toHaveBeenCalledWith({
      provider: 'ANTHROPIC',
      apiKey: RAW_KEY,
      baseUrl: undefined,
    });
    await waitFor(() => expect(onConnected).toHaveBeenCalledTimes(1));

    // The raw key must not leak into browser storage or the auth store.
    expect(storageSnapshot()).not.toContain(RAW_KEY);
    expect(JSON.stringify(useAuthStore.getState())).not.toContain(RAW_KEY);

    // And the input is cleared from the DOM after submission.
    expect((screen.getByLabelText('API key') as HTMLInputElement).value).toBe('');
  });

  test('reveals the base URL field only for a custom provider', () => {
    render(<ConnectProviderModal open onClose={vi.fn()} onConnected={vi.fn()} />);
    expect(screen.queryByLabelText('Base URL')).toBeNull();

    fireEvent.change(screen.getByLabelText('Provider'), {
      target: { value: 'CUSTOM_OPENAI_COMPATIBLE' },
    });
    expect(screen.getByLabelText('Base URL')).toBeInTheDocument();
  });
});
