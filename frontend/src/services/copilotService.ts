import api, { API_BASE_URL } from '../api';
import { useAuthStore } from '../store/auth';

// ---------------------------------------------------------------------------
// PayFlow Copilot — typed client for the gateway AI surface (/api/v1/ai).
//
// Every request goes through the shared `api` axios instance so the JWT access
// token is attached by the request interceptor and 401s trigger the refresh
// flow. The only exception is the SSE stream, which the browser `fetch` API
// handles directly (EventSource cannot set an Authorization header); the token
// is read from the auth store and sent as a Bearer header there too.
//
// SECURITY: no provider API key is ever persisted here. Keys are passed to
// `connectCredential` for the single POST that registers them server-side and
// are never stored, logged, or echoed back by this module.
// ---------------------------------------------------------------------------

export type AiProvider =
  | 'ANTHROPIC'
  | 'OPENAI'
  | 'GEMINI'
  | 'XAI'
  | 'CUSTOM_OPENAI_COMPATIBLE';

export type MessageRole = 'USER' | 'ASSISTANT';
export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';
export type FeedbackRating = 'UP' | 'DOWN';
export type CredentialSource = 'USER' | 'SERVER' | null;
export type AiResourceType = 'PAYMENT';

export interface AiServiceInfo {
  key: string;
  name: string;
  status: 'IMPLEMENTED' | 'PLANNED';
  note: string;
}

export interface AiCapabilities {
  aiFeatureEnabled: boolean;
  defaultProvider: string | null;
  services: AiServiceInfo[];
}

export interface AiCredential {
  provider: AiProvider;
  configured: boolean;
  source: CredentialSource;
  masked: string;
  expiresAt: string | null;
}

export interface ConnectCredentialResponse {
  provider: AiProvider;
  configured: true;
  source: 'USER';
  masked: string;
  expiresAt: string;
}

export interface Conversation {
  id: string;
  title: string | null;
  mode: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationMessage {
  id: string;
  role: MessageRole;
  content: string;
  metadata: Record<string, unknown> | null;
  createdAt: string;
}

export interface ConversationDetail extends Conversation {
  messages: ConversationMessage[];
}

export interface Evidence {
  source: string;
  resourceType: string;
  resourceId: string;
  verified: boolean;
}

export interface ToolCall {
  name: string;
  status: string;
}

export interface AnswerResponse {
  messageId: string;
  conversationId: string;
  answer: string;
  evidence: Evidence[];
  confidence: Confidence;
  warnings: string[];
  toolCalls: ToolCall[];
}

export interface SendMessagePayload {
  content: string;
  resourceType?: AiResourceType;
  resourceReference?: string;
}

export interface ConnectCredentialInput {
  provider: AiProvider;
  apiKey: string;
  baseUrl?: string;
}

export interface FeedbackInput {
  messageId: string;
  rating: FeedbackRating;
  comment?: string;
}

/**
 * Error carrying the backend {code,message} contract from an SSE `event: error`
 * frame. Its `code` is read by `extractErrorCode` so the UI can branch on
 * AI_KEY_REQUIRED / AI_UNAVAILABLE exactly as it does for axios errors.
 */
export class CopilotError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'CopilotError';
    this.code = code;
  }
}

// --- Capabilities -----------------------------------------------------------

export async function getCapabilities(): Promise<AiCapabilities> {
  const res = await api.get<AiCapabilities>('/ai/capabilities');
  return res.data;
}

// --- Credentials ------------------------------------------------------------

export async function connectCredential(
  input: ConnectCredentialInput,
): Promise<ConnectCredentialResponse> {
  const body: Record<string, string> = {
    provider: input.provider,
    apiKey: input.apiKey,
  };
  if (input.baseUrl) body.baseUrl = input.baseUrl;
  const res = await api.post<ConnectCredentialResponse>('/ai/credentials', body);
  return res.data;
}

export async function listCredentials(): Promise<AiCredential[]> {
  const res = await api.get<AiCredential[]>('/ai/credentials');
  return res.data;
}

export async function deleteCredential(provider: AiProvider): Promise<void> {
  await api.delete(`/ai/credentials/${provider}`);
}

// --- Conversations ----------------------------------------------------------

export async function createConversation(title?: string): Promise<Conversation> {
  const res = await api.post<Conversation>('/ai/conversations', title ? { title } : {});
  return res.data;
}

export async function listConversations(): Promise<Conversation[]> {
  const res = await api.get<Conversation[]>('/ai/conversations');
  return res.data;
}

export async function getConversation(id: string): Promise<ConversationDetail> {
  const res = await api.get<ConversationDetail>(`/ai/conversations/${id}`);
  return res.data;
}

export async function deleteConversation(id: string): Promise<void> {
  await api.delete(`/ai/conversations/${id}`);
}

// --- Messages ---------------------------------------------------------------

export async function sendMessage(
  id: string,
  payload: SendMessagePayload,
): Promise<AnswerResponse> {
  const res = await api.post<AnswerResponse>(`/ai/conversations/${id}/messages`, payload);
  return res.data;
}

export async function submitFeedback(input: FeedbackInput): Promise<void> {
  await api.post('/ai/feedback', input);
}

// --- Streaming (SSE over fetch) --------------------------------------------

export interface StreamHandlers {
  onTool?: (tool: ToolCall) => void;
  onMessage?: (answer: AnswerResponse) => void;
}

interface SseEvent {
  event: string;
  data: string;
}

/**
 * Parse a Server-Sent-Events body into discrete {event, data} records.
 * Reads the raw byte stream via a ReadableStream reader so an Authorization
 * header can be attached by the caller (EventSource cannot do this).
 */
async function* parseEventStream(
  body: ReadableStream<Uint8Array>,
): AsyncGenerator<SseEvent> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
      let boundary = buffer.indexOf('\n\n');
      while (boundary !== -1) {
        const chunk = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        let event = 'message';
        const dataLines: string[] = [];
        for (const line of chunk.split('\n')) {
          if (line.startsWith('event:')) event = line.slice(6).trim();
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''));
        }
        if (dataLines.length) yield { event, data: dataLines.join('\n') };
        boundary = buffer.indexOf('\n\n');
      }
    }
  } finally {
    try {
      reader.releaseLock();
    } catch {
      /* reader already released */
    }
  }
}

function parseSsePayload<T>(data: string): T | null {
  try {
    return JSON.parse(data) as T;
  } catch {
    return null;
  }
}

/**
 * Send a message and surface the assistant's reply.
 *
 * The streaming GET endpoint is SELF-CONTAINED: it runs the agent and persists
 * both the user and assistant messages on its own. The message content (and
 * optional resource context) are passed as URL-encoded query params, since a
 * GET carries no body. It emits `event: tool` {name,status} per tool, then a
 * terminal `event: message` {AnswerResponse}, or `event: error` {code,message}.
 *
 * Crucially, streaming mode does NOT also POST /messages — doing so would run
 * the agent (and persist the exchange) twice. The POST path is used only as the
 * non-streaming fallback when the stream cannot be opened (response not ok,
 * no body, or a network failure before the first byte). An explicit caller can
 * also opt out of streaming by calling `sendMessage` directly.
 *
 * Pass an AbortSignal to support "stop generation" / disconnect.
 */
export async function streamMessage(
  conversationId: string,
  payload: SendMessagePayload,
  handlers: StreamHandlers = {},
  signal?: AbortSignal,
): Promise<AnswerResponse> {
  const token = useAuthStore.getState().accessToken;

  const params = new URLSearchParams({ content: payload.content });
  if (payload.resourceType) params.set('resourceType', payload.resourceType);
  if (payload.resourceReference) params.set('resourceReference', payload.resourceReference);
  const streamUrl = `${API_BASE_URL}/ai/conversations/${conversationId}/stream?${params.toString()}`;

  let streamBody: ReadableStream<Uint8Array> | null = null;
  try {
    const streamRes = await fetch(streamUrl, {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      signal,
    });
    if (streamRes.ok && streamRes.body) {
      streamBody = streamRes.body;
    }
  } catch (err) {
    if (signal?.aborted) throw err;
    // Could not open the stream — fall back to the non-streaming POST below.
    streamBody = null;
  }

  if (streamBody) {
    // Streaming mode: the GET already ran the agent and persisted the messages,
    // so consume its events and NEVER also POST. An abort rejects the reader,
    // which propagates out for the caller's "stop generation" handling.
    for await (const sse of parseEventStream(streamBody)) {
      if (sse.event === 'tool') {
        const tool = parseSsePayload<ToolCall>(sse.data);
        if (tool) handlers.onTool?.(tool);
      } else if (sse.event === 'message') {
        const final = parseSsePayload<AnswerResponse>(sse.data);
        if (final) {
          handlers.onMessage?.(final);
          return final;
        }
      } else if (sse.event === 'error') {
        const detail = parseSsePayload<{ code?: string; message?: string }>(sse.data);
        throw new CopilotError(
          detail?.code ?? '',
          detail?.message ?? 'Copilot streaming failed.',
        );
      }
    }
    // The stream opened but ended without a terminal message/error. Surface
    // this rather than re-running the agent via POST (which would double-persist).
    throw new CopilotError('AI_STREAM_INCOMPLETE', 'Copilot stream ended without a response.');
  }

  // Non-streaming fallback: the POST runs and persists the exchange exactly once.
  const answer = await sendMessage(conversationId, payload);
  handlers.onMessage?.(answer);
  return answer;
}
