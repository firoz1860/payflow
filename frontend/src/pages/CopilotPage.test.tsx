import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { CopilotPage } from './CopilotPage';
import { ProtectedRoute } from '../components/ProtectedRoute';
import { useAuthStore } from '../store/auth';
import type { User } from '../types';

const mocks = vi.hoisted(() => ({
  getCapabilities: vi.fn(),
  listCredentials: vi.fn(),
  listConversations: vi.fn(),
  getConversation: vi.fn(),
  createConversation: vi.fn(),
  deleteConversation: vi.fn(),
  streamMessage: vi.fn(),
  submitFeedback: vi.fn(),
  connectCredential: vi.fn(),
  deleteCredential: vi.fn(),
}));

vi.mock('../services/copilotService', () => mocks);

function makeUser(permissions: string[]): User {
  return {
    id: 'u1',
    email: 'merchant@example.com',
    fullName: 'Merchant Owner',
    merchantId: 'm1',
    status: 'ACTIVE',
    emailVerified: true,
    roles: ['MERCHANT_OWNER'],
    permissions,
    createdAt: '2026-01-01T00:00:00.000Z',
    onboarding: { status: 'COMPLETED', lastStep: 0, dismissedAt: null, completedAt: null },
  };
}

function renderCopilot() {
  return render(
    <MemoryRouter initialEntries={['/ai']}>
      <Routes>
        <Route path="/ai" element={<CopilotPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.getCapabilities.mockResolvedValue({
    aiFeatureEnabled: true,
    defaultProvider: 'ANTHROPIC',
    services: [],
  });
  mocks.listCredentials.mockResolvedValue([]);
  mocks.listConversations.mockResolvedValue([]);
  mocks.getConversation.mockResolvedValue({ id: 'c1', messages: [] });
  mocks.createConversation.mockResolvedValue({
    id: 'c1',
    title: null,
    mode: 'DEFAULT',
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-01-01T00:00:00.000Z',
  });
  mocks.submitFeedback.mockResolvedValue(undefined);
  useAuthStore.setState({
    user: makeUser(['ai:use']),
    accessToken: 'token',
    refreshToken: 'refresh',
    isAuthenticated: true,
  });
});

afterEach(() => cleanup());

describe('Copilot route permission gating', () => {
  const gated = (
    <Routes>
      <Route
        path="/ai"
        element={
          <ProtectedRoute permission="ai:use">
            <div>copilot-workspace</div>
          </ProtectedRoute>
        }
      />
      <Route path="/dashboard" element={<div>dashboard-home</div>} />
      <Route path="/login" element={<div>login-screen</div>} />
    </Routes>
  );

  test('renders the workspace when the user has ai:use', async () => {
    useAuthStore.setState({ user: makeUser(['ai:use']), accessToken: 't', isAuthenticated: true });
    render(<MemoryRouter initialEntries={['/ai']}>{gated}</MemoryRouter>);
    expect(await screen.findByText('copilot-workspace')).toBeInTheDocument();
  });

  test('redirects to the dashboard when the user lacks ai:use', async () => {
    useAuthStore.setState({ user: makeUser(['payments:read']), accessToken: 't', isAuthenticated: true });
    render(<MemoryRouter initialEntries={['/ai']}>{gated}</MemoryRouter>);
    expect(await screen.findByText('dashboard-home')).toBeInTheDocument();
    expect(screen.queryByText('copilot-workspace')).toBeNull();
  });
});

describe('CopilotPage states', () => {
  test('AI_KEY_REQUIRED: shows the connect-provider prompt when no credential is configured', async () => {
    mocks.listCredentials.mockResolvedValue([]);
    renderCopilot();
    expect(await screen.findByText('Connect an AI provider')).toBeInTheDocument();
    expect(
      screen.getAllByRole('button', { name: /connect provider/i }).length,
    ).toBeGreaterThan(0);
  });

  test('AI_UNAVAILABLE: shows the feature-off banner when the AI feature is disabled', async () => {
    mocks.getCapabilities.mockResolvedValue({
      aiFeatureEnabled: false,
      defaultProvider: null,
      services: [],
    });
    renderCopilot();
    expect(await screen.findByText('Copilot is currently unavailable')).toBeInTheDocument();
  });

  test('composer send renders the mocked assistant answer', async () => {
    mocks.listCredentials.mockResolvedValue([
      {
        provider: 'ANTHROPIC',
        configured: true,
        source: 'USER',
        masked: 'sk-ant-••••3456',
        expiresAt: '2026-12-01T00:00:00.000Z',
      },
    ]);
    mocks.streamMessage.mockImplementation(async (_id, _payload, handlers) => {
      const answer = {
        messageId: 'm1',
        conversationId: 'c1',
        answer: 'The payment was captured successfully.',
        evidence: [],
        confidence: 'HIGH' as const,
        warnings: [],
        toolCalls: [],
      };
      handlers?.onMessage?.(answer);
      return answer;
    });

    renderCopilot();

    const composer = await screen.findByPlaceholderText(/ask about a payment/i);
    fireEvent.change(composer, { target: { value: 'What happened to this payment?' } });
    fireEvent.click(screen.getByRole('button', { name: /send message/i }));

    expect(await screen.findByText('The payment was captured successfully.')).toBeInTheDocument();
    await waitFor(() => expect(mocks.createConversation).toHaveBeenCalledTimes(1));
    expect(mocks.streamMessage).toHaveBeenCalledTimes(1);
  });
});
