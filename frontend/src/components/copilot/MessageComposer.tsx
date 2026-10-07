import { useRef, useState } from 'react';
import { Send, Square } from 'lucide-react';
import { StarterPrompts } from './StarterPrompts';

interface MessageComposerProps {
  onSend: (content: string) => void;
  onStop: () => void;
  streaming: boolean;
  disabled?: boolean;
  showStarters?: boolean;
}

export function MessageComposer({
  onSend,
  onStop,
  streaming,
  disabled = false,
  showStarters = false,
}: MessageComposerProps) {
  const [value, setValue] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const submit = () => {
    const content = value.trim();
    if (!content || streaming || disabled) return;
    onSend(content);
    setValue('');
    textareaRef.current?.focus();
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      submit();
    }
  };

  const handleStarter = (prompt: string) => {
    if (streaming || disabled) return;
    onSend(prompt);
  };

  return (
    <div className="space-y-2.5">
      {showStarters && (
        <StarterPrompts onSelect={handleStarter} disabled={streaming || disabled} />
      )}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
        className="flex items-end gap-2"
      >
        <label htmlFor="copilot-composer" className="sr-only">
          Message Copilot
        </label>
        <textarea
          id="copilot-composer"
          ref={textareaRef}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          rows={1}
          placeholder={disabled ? 'Copilot is unavailable' : 'Ask about a payment, failure, or ledger…'}
          className="input max-h-40 min-h-[46px] flex-1 resize-none"
        />
        {streaming ? (
          <button
            type="button"
            onClick={onStop}
            className="btn-secondary h-[46px] shrink-0 px-3.5"
            aria-label="Stop generation"
          >
            <Square className="h-4 w-4 fill-current" />
            Stop
          </button>
        ) : (
          <button
            type="submit"
            disabled={disabled || !value.trim()}
            className="btn-primary h-[46px] shrink-0 px-3.5"
            aria-label="Send message"
          >
            <Send className="h-4 w-4" />
            Send
          </button>
        )}
      </form>
      <p className="px-1 text-[11px] text-slate-400">
        Enter to send · Shift + Enter for a new line
      </p>
    </div>
  );
}
