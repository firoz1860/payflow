import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  AlertTriangle,
  Bot,
  KeyRound,
  ListTree,
  MessageSquarePlus,
  PanelRightOpen,
  Plus,
  Sparkles,
  Trash2,
} from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { CardSpinner, EmptyState } from '../components/Spinner';
import { toast } from '../components/Toast';
import { extractError, extractErrorCode } from '../api';
import { cn } from '../lib/utils';
import { timeAgo } from '../lib/utils';
import { FadeIn } from '../lib/motion';
import {
  createConversation,
  deleteConversation,
  getCapabilities,
  getConversation,
  listConversations,
  listCredentials,
  streamMessage,
  type AiCapabilities,
  type AiCredential,
  type Conversation,
  type SendMessagePayload,
} from '../services/copilotService';
import { ConnectProviderModal } from '../components/copilot/ConnectProviderModal';
import { CredentialStatus } from '../components/copilot/CredentialStatus';
import { EvidenceCards } from '../components/copilot/EvidenceCards';
import { MessageComposer } from '../components/copilot/MessageComposer';
import {
  MessageList,
  toDisplayMessage,
  type DisplayMessage,
} from '../components/copilot/MessageList';

interface SeedContext {
  resourceType: 'PAYMENT';
  resourceReference: string;
  autoPrompt: string;
}

let tempCounter = 0;
const tempId = (prefix: string) => `${prefix}_${Date.now()}_${tempCounter++}`;

export function CopilotPage() {
  const location = useLocation();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [capabilities, setCapabilities] = useState<AiCapabilities | null>(null);
  const [featureOff, setFeatureOff] = useState(false);
  const [credentials, setCredentials] = useState<AiCredential[]>([]);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [activeContext, setActiveContext] = useState<Omit<SeedContext, 'autoPrompt'> | null>(null);
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [connectOpen, setConnectOpen] = useState(false);
  const [mobilePanel, setMobilePanel] = useState<'list' | 'evidence' | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const pendingAssistantRef = useRef<string | null>(null);
  const seedRef = useRef<SeedContext | null>(null);
  const seedConsumed = useRef(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  const hasCredential = credentials.some((c) => c.configured);

  // --- data loading ---------------------------------------------------------

  const refreshCredentials = useCallback(async () => {
    try {
      setCredentials(await listCredentials());
    } catch {
      /* non-fatal: the connect prompt still covers the empty case */
    }
  }, []);

  const loadConversationMessages = useCallback(
    async (conversation: Conversation) => {
      try {
        const detail = await getConversation(conversation.id);
        setMessages(
          detail.messages.map((m) => ({
            id: m.id,
            role: m.role,
            content: m.content,
            createdAt: m.createdAt,
          })),
        );
      } catch (err) {
        toast('error', extractError(err));
        setMessages([]);
      }
    },
    [],
  );

  useEffect(() => {
    const state = (location.state ?? null) as
      | { resourceType?: 'PAYMENT'; resourceReference?: string }
      | null;
    const params = new URLSearchParams(location.search);
    const reference = state?.resourceReference ?? params.get('ref') ?? undefined;
    if (reference && !seedConsumed.current) {
      seedRef.current = {
        resourceType: 'PAYMENT',
        resourceReference: reference,
        autoPrompt:
          'Investigate this payment and summarise its current state with verified evidence.',
      };
      // Clear navigation state so a refresh does not re-seed.
      navigate(location.pathname, { replace: true, state: null });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const [caps, creds, convos] = await Promise.all([
          getCapabilities(),
          listCredentials().catch(() => [] as AiCredential[]),
          listConversations().catch(() => [] as Conversation[]),
        ]);
        if (cancelled) return;
        setCapabilities(caps);
        setFeatureOff(!caps.aiFeatureEnabled);
        setCredentials(creds);
        setConversations(convos);

        const credentialReady = creds.some((c) => c.configured);
        const seed = seedRef.current;

        if (caps.aiFeatureEnabled && seed && !seedConsumed.current) {
          seedConsumed.current = true;
          if (credentialReady) {
            await startSeededConversation(seed);
          } else {
            setConnectOpen(true);
          }
        } else if (convos.length > 0) {
          setActiveId(convos[0].id);
          await loadConversationMessages(convos[0]);
        }
      } catch (err) {
        if (cancelled) return;
        if (extractErrorCode(err) === 'AI_UNAVAILABLE') {
          setFeatureOff(true);
        } else {
          toast('error', extractError(err));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    scrollRef.current?.scrollTo?.({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  useEffect(
    () => () => {
      abortRef.current?.abort();
    },
    [],
  );

  // --- message plumbing ------------------------------------------------------

  const patchMessage = (id: string, patch: Partial<DisplayMessage>) => {
    setMessages((prev) => prev.map((m) => (m.id === id ? { ...m, ...patch } : m)));
  };

  const mergeToolCall = (id: string, tool: { name: string; status: string }) => {
    setMessages((prev) =>
      prev.map((m) => {
        if (m.id !== id) return m;
        const existing = m.toolCalls ?? [];
        const idx = existing.findIndex((t) => t.name === tool.name);
        const next = idx === -1 ? [...existing, tool] : existing.map((t, i) => (i === idx ? tool : t));
        return { ...m, toolCalls: next };
      }),
    );
  };

  const runMessage = useCallback(
    async (conversationId: string, payload: SendMessagePayload) => {
      const assistantId = tempId('assistant');
      pendingAssistantRef.current = assistantId;
      setMessages((prev) => [
        ...prev,
        { id: assistantId, role: 'ASSISTANT', content: '', pending: true, toolCalls: [] },
      ]);

      const controller = new AbortController();
      abortRef.current = controller;
      setStreaming(true);

      try {
        await streamMessage(
          conversationId,
          payload,
          {
            onTool: (tool) => mergeToolCall(assistantId, tool),
            onMessage: (answer) => {
              const finalized = toDisplayMessage(answer);
              patchMessage(assistantId, { ...finalized, pending: false });
            },
          },
          controller.signal,
        );
      } catch (err) {
        if (controller.signal.aborted) {
          patchMessage(assistantId, { pending: false, stopped: true });
        } else {
          const code = extractErrorCode(err);
          if (code === 'AI_KEY_REQUIRED') {
            setMessages((prev) => prev.filter((m) => m.id !== assistantId));
            setConnectOpen(true);
            toast('warning', 'Connect an AI provider to continue.');
          } else if (code === 'AI_UNAVAILABLE') {
            setMessages((prev) => prev.filter((m) => m.id !== assistantId));
            setFeatureOff(true);
          } else {
            patchMessage(assistantId, {
              pending: false,
              error: true,
              content: extractError(err),
            });
            toast('error', extractError(err));
          }
        }
      } finally {
        setStreaming(false);
        abortRef.current = null;
        pendingAssistantRef.current = null;
      }
    },
    [],
  );

  const handleSend = useCallback(
    async (content: string) => {
      if (featureOff || streaming) return;
      if (!hasCredential) {
        setConnectOpen(true);
        return;
      }

      let conversationId = activeId;
      let context = activeContext;
      if (!conversationId) {
        try {
          const created = await createConversation();
          conversationId = created.id;
          context = null;
          setActiveId(created.id);
          setActiveContext(null);
          setConversations((prev) => [created, ...prev]);
        } catch (err) {
          toast('error', extractError(err));
          return;
        }
      }

      setMessages((prev) => [
        ...prev,
        {
          id: tempId('user'),
          role: 'USER',
          content,
          createdAt: new Date().toISOString(),
        },
      ]);

      const payload: SendMessagePayload = context
        ? { content, resourceType: context.resourceType, resourceReference: context.resourceReference }
        : { content };

      await runMessage(conversationId, payload);
    },
    [activeContext, activeId, featureOff, hasCredential, runMessage, streaming],
  );

  async function startSeededConversation(seed: SeedContext) {
    try {
      const created = await createConversation(`Payment ${seed.resourceReference}`);
      setConversations((prev) => [created, ...prev]);
      setActiveId(created.id);
      setActiveContext({ resourceType: seed.resourceType, resourceReference: seed.resourceReference });
      setMessages([
        {
          id: tempId('user'),
          role: 'USER',
          content: seed.autoPrompt,
          createdAt: new Date().toISOString(),
        },
      ]);
      await runMessage(created.id, {
        content: seed.autoPrompt,
        resourceType: seed.resourceType,
        resourceReference: seed.resourceReference,
      });
    } catch (err) {
      toast('error', extractError(err));
    }
  }

  const handleStop = () => {
    abortRef.current?.abort();
  };

  const handleNewConversation = () => {
    if (streaming) return;
    setActiveId(null);
    setActiveContext(null);
    setMessages([]);
    setMobilePanel(null);
  };

  const handleSelectConversation = async (conversation: Conversation) => {
    if (streaming || conversation.id === activeId) return;
    setActiveId(conversation.id);
    setActiveContext(null);
    setMobilePanel(null);
    await loadConversationMessages(conversation);
  };

  const handleDeleteConversation = async (conversation: Conversation) => {
    try {
      await deleteConversation(conversation.id);
      setConversations((prev) => prev.filter((c) => c.id !== conversation.id));
      if (conversation.id === activeId) {
        setActiveId(null);
        setMessages([]);
        setActiveContext(null);
      }
      toast('success', 'Conversation deleted.');
    } catch (err) {
      toast('error', extractError(err));
    }
  };

  // Evidence for the right panel = latest assistant message that carries any.
  const latestWithEvidence = [...messages]
    .reverse()
    .find((m) => m.role === 'ASSISTANT' && ((m.evidence?.length ?? 0) > 0 || Boolean(m.confidence)));

  // --- render ----------------------------------------------------------------

  if (loading) {
    return (
      <>
        <PageHeader title="PayFlow Copilot" description="Your evidence-grounded payments assistant." />
        <div className="card p-6">
          <CardSpinner />
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="PayFlow Copilot"
        description="Ask grounded questions about payments, failures, and ledger postings."
        actions={
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setMobilePanel((p) => (p === 'list' ? null : 'list'))}
              className="btn-secondary lg:hidden"
              aria-pressed={mobilePanel === 'list'}
            >
              <ListTree className="h-4 w-4" /> Chats
            </button>
            <button
              type="button"
              onClick={() => setMobilePanel((p) => (p === 'evidence' ? null : 'evidence'))}
              className="btn-secondary lg:hidden"
              aria-pressed={mobilePanel === 'evidence'}
            >
              <PanelRightOpen className="h-4 w-4" /> Evidence
            </button>
            <button type="button" onClick={handleNewConversation} className="btn-primary" disabled={streaming}>
              <Plus className="h-4 w-4" /> New chat
            </button>
          </div>
        }
      />

      {featureOff && (
        <FadeIn>
          <div className="card mb-6 flex items-start gap-3 border-amber-200/80 bg-amber-50/70 p-4">
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
            <div>
              <h3 className="text-sm font-semibold text-amber-900">Copilot is currently unavailable</h3>
              <p className="mt-0.5 text-sm text-amber-800/90">
                The AI feature is turned off for your account. Contact your administrator to enable
                PayFlow Copilot.
              </p>
            </div>
          </div>
        </FadeIn>
      )}

      <div className="grid gap-4 lg:grid-cols-[260px_minmax(0,1fr)_320px]">
        {/* Left — conversations */}
        <aside
          className={cn(
            'card flex-col p-3',
            mobilePanel === 'list' ? 'flex' : 'hidden',
            'lg:flex',
          )}
        >
          <button
            type="button"
            onClick={handleNewConversation}
            disabled={streaming}
            className="btn-secondary mb-3 w-full justify-start"
          >
            <MessageSquarePlus className="h-4 w-4 text-blue-600" /> New conversation
          </button>
          <div className="space-y-1 overflow-y-auto">
            {conversations.length === 0 ? (
              <p className="px-2 py-4 text-center text-xs text-slate-400">No conversations yet.</p>
            ) : (
              conversations.map((conversation) => (
                <div
                  key={conversation.id}
                  className={cn(
                    'group flex items-center gap-1 rounded-lg px-1 transition',
                    conversation.id === activeId ? 'bg-blue-50' : 'hover:bg-slate-100/80',
                  )}
                >
                  <button
                    type="button"
                    onClick={() => handleSelectConversation(conversation)}
                    className="min-w-0 flex-1 py-2 pl-2 text-left"
                    aria-current={conversation.id === activeId}
                  >
                    <p
                      className={cn(
                        'truncate text-sm font-medium',
                        conversation.id === activeId ? 'text-blue-700' : 'text-slate-700',
                      )}
                    >
                      {conversation.title || 'Untitled conversation'}
                    </p>
                    <p className="truncate text-[11px] text-slate-400">
                      {timeAgo(conversation.updatedAt)}
                    </p>
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDeleteConversation(conversation)}
                    className="rounded-lg p-1.5 text-slate-300 opacity-0 transition hover:bg-rose-50 hover:text-rose-600 focus:opacity-100 group-hover:opacity-100"
                    aria-label={`Delete ${conversation.title || 'conversation'}`}
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              ))
            )}
          </div>
        </aside>

        {/* Center — conversation */}
        <section className="card flex min-h-[60vh] flex-col overflow-hidden">
          <div ref={scrollRef} className="flex-1 overflow-y-auto p-4">
            {featureOff ? (
              <EmptyState
                icon={<Bot className="h-6 w-6" />}
                title="Copilot is unavailable"
                description="The AI feature is disabled for your account."
              />
            ) : !hasCredential ? (
              <EmptyState
                icon={<KeyRound className="h-6 w-6" />}
                title="Connect an AI provider"
                description="PayFlow Copilot needs an AI provider key before it can investigate payments. Your key is sent only to the backend and never stored in this browser."
                action={
                  <button type="button" onClick={() => setConnectOpen(true)} className="btn-primary">
                    <Sparkles className="h-4 w-4" /> Connect provider
                  </button>
                }
              />
            ) : messages.length === 0 ? (
              <EmptyState
                icon={<Sparkles className="h-6 w-6" />}
                title="Ask PayFlow Copilot"
                description="Start with a prompt below or type your own question. Every answer is grounded in verifiable backend evidence."
              />
            ) : (
              <MessageList messages={messages} />
            )}
          </div>

          <div className="border-t border-slate-100 bg-white/60 p-4">
            <MessageComposer
              onSend={handleSend}
              onStop={handleStop}
              streaming={streaming}
              disabled={featureOff}
              showStarters={!featureOff && hasCredential}
            />
          </div>
        </section>

        {/* Right — context & evidence */}
        <aside
          className={cn(
            'flex-col gap-4',
            mobilePanel === 'evidence' ? 'flex' : 'hidden',
            'lg:flex',
          )}
        >
          <div className="card p-4">
            <h3 className="mb-3 text-sm font-semibold text-slate-900">Provider connection</h3>
            {hasCredential ? (
              <div className="space-y-2">
                {credentials
                  .filter((c) => c.configured)
                  .map((credential) => (
                    <CredentialStatus
                      key={credential.provider}
                      credential={credential}
                      onRemoved={refreshCredentials}
                    />
                  ))}
                <button
                  type="button"
                  onClick={() => setConnectOpen(true)}
                  className="btn-ghost w-full justify-start text-xs"
                >
                  <Plus className="h-3.5 w-3.5" /> Connect another provider
                </button>
              </div>
            ) : (
              <button type="button" onClick={() => setConnectOpen(true)} className="btn-primary w-full">
                <KeyRound className="h-4 w-4" /> Connect provider
              </button>
            )}
          </div>

          <div className="card p-4">
            <h3 className="mb-3 text-sm font-semibold text-slate-900">Evidence &amp; confidence</h3>
            <EvidenceCards
              evidence={latestWithEvidence?.evidence ?? []}
              confidence={latestWithEvidence?.confidence}
              warnings={latestWithEvidence?.warnings ?? []}
            />
          </div>

          {capabilities?.services && capabilities.services.length > 0 && (
            <div className="card p-4">
              <h3 className="mb-3 text-sm font-semibold text-slate-900">Copilot services</h3>
              <ul className="space-y-2">
                {capabilities.services.map((service) => (
                  <li key={service.key} className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-slate-700">{service.name}</p>
                      {service.note && (
                        <p className="truncate text-[11px] text-slate-400">{service.note}</p>
                      )}
                    </div>
                    <span
                      className={cn(
                        'badge shrink-0',
                        service.status === 'IMPLEMENTED'
                          ? 'bg-emerald-100 text-emerald-700'
                          : 'bg-slate-100 text-slate-500',
                      )}
                    >
                      {service.status === 'IMPLEMENTED' ? 'Live' : 'Planned'}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </aside>
      </div>

      <ConnectProviderModal
        open={connectOpen}
        onClose={() => setConnectOpen(false)}
        onConnected={async () => {
          await refreshCredentials();
          const seed = seedRef.current;
          if (seed && seedConsumed.current && !activeId) {
            await startSeededConversation(seed);
          }
        }}
      />
    </>
  );
}
