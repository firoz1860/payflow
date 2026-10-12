import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { LoginPage } from './LoginPage';
import { RegisterPage } from './RegisterPage';
const mocks = vi.hoisted(() => ({ guestLogin: vi.fn(), login: vi.fn(), register: vi.fn(), storeLogin: vi.fn(), navigate: vi.fn() }));
vi.mock('../services/authService', () => mocks);
vi.mock('../store/auth', () => ({ useAuthStore: (selector: (state: { login: typeof mocks.storeLogin }) => unknown) => selector({ login: mocks.storeLogin }) }));
vi.mock('react-router-dom', async importOriginal => ({
  ...await importOriginal<typeof import('react-router-dom')>(), useNavigate: () => mocks.navigate,
}));
vi.mock('../components/AuthShell', () => ({ AuthShell: ({ children }: { children: ReactNode }) => <>{children}</> }));
beforeEach(() => {
  vi.resetAllMocks();
  mocks.guestLogin.mockImplementation(options => new Promise((_resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(new DOMException('Cancelled', 'AbortError')));
  }));
});
afterEach(cleanup);
test.each([
  [LoginPage, 'Continue as guest', 'Sign in'],
  [RegisterPage, 'Explore as a guest', 'Create account'],
] as const)('shows startup progress, blocks competing submission, and cancels on both auth pages', async (Page, guestLabel, accountLabel) => {
  const view = render(<MemoryRouter><Page /></MemoryRouter>);
  const guest = screen.getByRole('button', { name: guestLabel });
  fireEvent.click(guest);
  expect(screen.getByRole('status')).toHaveTextContent('Connecting to PayFlow');
  expect(guest).toBeDisabled();
  expect(screen.getByRole('button', { name: accountLabel })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
  await waitFor(() => expect(guest).toBeEnabled());
  expect(mocks.guestLogin.mock.calls[0][0].signal.aborted).toBe(true);
  fireEvent.click(guest);
  view.unmount();
  expect(mocks.guestLogin.mock.calls[1][0].signal.aborted).toBe(true);
});

test('leaving during an in-flight guest POST ignores a late success without storing a session or navigating', async () => {
  let respond!: (value: unknown) => void;
  mocks.guestLogin.mockImplementation(options => {
    options.onProgress('submitting');
    return new Promise(resolve => { respond = resolve; });
  });
  const view = render(<MemoryRouter><LoginPage /></MemoryRouter>);
  fireEvent.click(screen.getByRole('button', { name: 'Continue as guest' }));
  expect(screen.getByRole('status')).toHaveTextContent('Creating your demo workspace');
  expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument();
  view.unmount();
  await act(async () => { respond({ accessToken: 'test-access', refreshToken: 'test-refresh', user: {} }); });
  expect(mocks.guestLogin.mock.calls[0][0].signal.aborted).toBe(true);
  expect(mocks.storeLogin).not.toHaveBeenCalled();
  expect(mocks.navigate).not.toHaveBeenCalled();
});
