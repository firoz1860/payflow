import { useState } from 'react';
import {
  Bot,
  Check,
  CheckCircle2,
  Copy,
  Loader2,
  ThumbsDown,
  ThumbsUp,
  User,
  XCircle,
} from 'lucide-react';
import { motion } from 'framer-motion';
import { cn } from '../../lib/utils';
import { timeAgo } from '../../lib/utils';
import { toast } from '../Toast';
import { extractError } from '../../api';
import {
  submitFeedback,
  type AnswerResponse,
  type Confidence,
  type Evidence,
  type FeedbackRating,
  type MessageRole,
  type ToolCall,
} from '../../services/copilotService';
import { ConfidenceBadge } from './EvidenceCards';

export interface DisplayMessage {
  id: string;
  role: MessageRole;
  content: string;
  createdAt?: string;
  pending?: boolean;
  stopped?: boolean;
  error?: boolean;
  evidence?: Evidence[];
  confidence?: Confidence;
  warnings?: string[];
  toolCalls?: ToolCall[];
}

export function toDisplayMessage(answer: AnswerResponse): DisplayMessage {
  return {
    id: answer.messageId,
    role: 'ASSISTANT',
    content: answer.answer,
    evidence: answer.evidence,
    confidence: answer.confidence,
    warnings: answer.warnings,
    toolCalls: answer.toolCalls,
  };
}

function toolTone(status: string) {
  const s = status?.toLowerCase?.() ?? '';
  if (['ok', 'done', 'success', 'completed', 'complete'].some((v) => s.includes(v))) {
    return { className: 'bg-emerald-100 text-emerald-700', icon: <Check className="h-3 w-3" /> };
  }
  if (['error', 'failed', 'fail'].some((v) => s.includes(v))) {
    return { className: 'bg-rose-100 text-rose-700', icon: <XCircle className="h-3 w-3" /> };
  }
  return {
    className: 'bg-blue-100 text-blue-700',
    icon: <Loader2 className="h-3 w-3 animate-spin" />,
  };
}

function ToolChips({ toolCalls }: { toolCalls: ToolCall[] }) {
  if (!toolCalls.length) return null;
  return (
    <div className="mb-2 flex flex-wrap gap-1.5" aria-label="Tool execution status">
      {toolCalls.map((tool, index) => {
        const tone = toolTone(tool.status);
        return (
          <span
            key={`${tool.name}-${index}`}
            className={cn('badge font-mono text-[11px]', tone.className)}
          >
            {tone.icon}
            {tool.name}
          </span>
        );
      })}
    </div>
  );
}

function FeedbackButtons({ messageId }: { messageId: string }) {
  const [rating, setRating] = useState<FeedbackRating | null>(null);
  const [saving, setSaving] = useState<FeedbackRating | null>(null);

  const rate = async (next: FeedbackRating) => {
    if (saving) return;
    setSaving(next);
    try {
      await submitFeedback({ messageId, rating: next });
      setRating(next);
    } catch (err) {
      toast('error', extractError(err));
    } finally {
      setSaving(null);
    }
  };

  return (
    <div className="flex items-center gap-1">
      <button
        type="button"
        onClick={() => rate('UP')}
        className={cn(
          'rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-emerald-600 focus:outline-none focus:ring-2 focus:ring-blue-500',
          rating === 'UP' && 'bg-emerald-50 text-emerald-600',
        )}
        aria-label="Helpful answer"
        aria-pressed={rating === 'UP'}
      >
        <ThumbsUp className="h-3.5 w-3.5" />
      </button>
      <button
        type="button"
        onClick={() => rate('DOWN')}
        className={cn(
          'rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-rose-600 focus:outline-none focus:ring-2 focus:ring-blue-500',
          rating === 'DOWN' && 'bg-rose-50 text-rose-600',
        )}
        aria-label="Unhelpful answer"
        aria-pressed={rating === 'DOWN'}
      >
        <ThumbsDown className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

function AssistantMessage({ message }: { message: DisplayMessage }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard?.writeText(message.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast('error', 'Could not copy to clipboard.');
    }
  };

  const showAnswer = message.content.length > 0;
  const confidence = message.confidence;

  return (
    <div className="flex gap-3">
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-blue-600 to-indigo-700 text-white shadow-sm">
        <Bot className="h-4 w-4" />
      </div>
      <div className="min-w-0 flex-1">
        {message.toolCalls && message.toolCalls.length > 0 && (
          <ToolChips toolCalls={message.toolCalls} />
        )}

        <div className="card p-3.5">
          {message.pending && !showAnswer ? (
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <Loader2 className="h-4 w-4 animate-spin text-blue-600" />
              Copilot is gathering evidence…
            </div>
          ) : (
            <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-slate-800">
              {message.content}
            </p>
          )}

          {message.stopped && (
            <p className="mt-2 text-xs italic text-slate-400">Generation stopped.</p>
          )}

          {(showAnswer || confidence) && !message.pending && (
            <div className="mt-3 flex items-center justify-between gap-2 border-t border-slate-100 pt-2.5">
              <div className="flex items-center gap-2">
                {confidence && <ConfidenceBadge confidence={confidence} />}
              </div>
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={copy}
                  className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  aria-label="Copy response"
                >
                  {copied ? (
                    <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" />
                  ) : (
                    <Copy className="h-3.5 w-3.5" />
                  )}
                </button>
                {!message.error && !message.stopped && <FeedbackButtons messageId={message.id} />}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function UserMessage({ message }: { message: DisplayMessage }) {
  return (
    <div className="flex justify-end gap-3">
      <div className="min-w-0 max-w-[85%]">
        <div className="rounded-2xl rounded-tr-sm bg-blue-600 px-4 py-2.5 text-white shadow-sm">
          <p className="whitespace-pre-wrap break-words text-sm leading-relaxed">
            {message.content}
          </p>
        </div>
        {message.createdAt && (
          <p className="mt-1 text-right text-[11px] text-slate-400">{timeAgo(message.createdAt)}</p>
        )}
      </div>
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-500">
        <User className="h-4 w-4" />
      </div>
    </div>
  );
}

export function MessageList({ messages }: { messages: DisplayMessage[] }) {
  return (
    <div className="space-y-5">
      {messages.map((message) => (
        <motion.div
          key={message.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.25 }}
        >
          {message.role === 'ASSISTANT' ? (
            <AssistantMessage message={message} />
          ) : (
            <UserMessage message={message} />
          )}
        </motion.div>
      ))}
    </div>
  );
}
