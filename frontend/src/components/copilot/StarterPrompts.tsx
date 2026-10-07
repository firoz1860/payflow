import {
  AlertOctagon,
  BookOpenCheck,
  Clock3,
  Plug,
  Search,
  type LucideIcon,
} from 'lucide-react';

export interface StarterPrompt {
  key: string;
  label: string;
  prompt: string;
  icon: LucideIcon;
}

export const STARTER_PROMPTS: StarterPrompt[] = [
  {
    key: 'investigate',
    label: 'Investigate payment',
    prompt: 'Investigate this payment and summarise its current state with verified evidence.',
    icon: Search,
  },
  {
    key: 'explain-failure',
    label: 'Explain payment failure',
    prompt: 'Explain why this payment failed, citing the failure code and provider evidence.',
    icon: AlertOctagon,
  },
  {
    key: 'explain-pending',
    label: 'Explain pending payment',
    prompt: 'Explain why this payment is still pending and what the next expected transition is.',
    icon: Clock3,
  },
  {
    key: 'provider-evidence',
    label: 'Check provider evidence',
    prompt: 'Check the provider-side evidence for this payment and whether it reconciles.',
    icon: Plug,
  },
  {
    key: 'ledger-consistency',
    label: 'Check ledger consistency',
    prompt: 'Check that the ledger postings for this payment are balanced and consistent.',
    icon: BookOpenCheck,
  },
];

interface StarterPromptsProps {
  onSelect: (prompt: string) => void;
  disabled?: boolean;
}

export function StarterPrompts({ onSelect, disabled }: StarterPromptsProps) {
  return (
    <div className="flex flex-wrap gap-2">
      {STARTER_PROMPTS.map(({ key, label, prompt, icon: Icon }) => (
        <button
          key={key}
          type="button"
          onClick={() => onSelect(prompt)}
          disabled={disabled}
          className="btn-secondary px-3 py-1.5 text-xs"
        >
          <Icon className="h-3.5 w-3.5 text-blue-600" />
          {label}
        </button>
      ))}
    </div>
  );
}
