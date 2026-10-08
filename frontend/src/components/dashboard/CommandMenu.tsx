import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { ArrowRight, CornerDownLeft, Receipt, Search } from 'lucide-react';
import type { CommandDestination } from './navItems';
import { cn } from '../../lib/utils';

interface CommandMenuProps {
  open: boolean;
  onClose: () => void;
  destinations: CommandDestination[];
}

type Result =
  | { kind: 'payment'; id: string; to: string }
  | { kind: 'nav'; dest: CommandDestination };

/**
 * CommandMenu — ⌘K / Ctrl K palette for the dashboard.
 *
 * Does only real things: jumps to any navigation destination the user can
 * access, and preserves the original Topbar behaviour of opening a payment by
 * its `pay_…` reference. No placeholder/no-op actions.
 */
export function CommandMenu({ open, onClose, destinations }: CommandMenuProps) {
  const navigate = useNavigate();
  const reduce = useReducedMotion();
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      setQuery('');
      setActive(0);
      // Focus after the panel mounts.
      requestAnimationFrame(() => inputRef.current?.focus());
    }
  }, [open]);

  const results = useMemo<Result[]>(() => {
    const q = query.trim().toLowerCase();
    const out: Result[] = [];
    if (/^pay_/i.test(query.trim())) {
      const id = query.trim();
      out.push({ kind: 'payment', id, to: `/payments/${encodeURIComponent(id)}` });
    }
    const matches = destinations.filter((d) => {
      if (!q) return true;
      return (
        d.label.toLowerCase().includes(q) ||
        d.group.toLowerCase().includes(q) ||
        (d.hint ? d.hint.toLowerCase().includes(q) : false)
      );
    });
    for (const dest of matches) out.push({ kind: 'nav', dest });
    return out;
  }, [query, destinations]);

  useEffect(() => {
    setActive((a) => Math.min(a, Math.max(0, results.length - 1)));
  }, [results.length]);

  const select = (result: Result | undefined) => {
    if (!result) return;
    navigate(result.kind === 'payment' ? result.to : result.dest.to);
    onClose();
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((a) => (results.length ? (a + 1) % results.length : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((a) => (results.length ? (a - 1 + results.length) % results.length : 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      select(results[active]);
    }
  };

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: reduce ? 0 : 0.15 }}
          className="fixed inset-0 z-[60] flex items-start justify-center bg-slate-950/40 p-4 pt-[12vh] backdrop-blur-sm"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) onClose();
          }}
          role="presentation"
        >
          <motion.div
            initial={reduce ? { opacity: 0 } : { opacity: 0, y: -8, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={reduce ? { opacity: 0 } : { opacity: 0, y: -8, scale: 0.98 }}
            transition={{ duration: reduce ? 0 : 0.18, ease: [0.16, 1, 0.3, 1] }}
            className="w-full max-w-xl overflow-hidden rounded-2xl border border-slate-200/80 bg-white/95 shadow-2xl shadow-slate-900/20 backdrop-blur-2xl"
            role="dialog"
            aria-modal="true"
            aria-label="Command menu"
            onKeyDown={onKeyDown}
          >
            <div className="flex items-center gap-3 border-b border-slate-100 px-4">
              <Search className="h-4 w-4 shrink-0 text-slate-400" />
              <input
                ref={inputRef}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search pages or paste a payment ID (pay_…)"
                className="h-12 w-full bg-transparent text-sm text-slate-800 outline-none placeholder:text-slate-400"
                role="combobox"
                aria-expanded="true"
                aria-controls="command-results"
                aria-activedescendant={results.length ? `command-opt-${active}` : undefined}
                autoComplete="off"
                spellCheck={false}
              />
            </div>

            <ul id="command-results" role="listbox" className="max-h-[320px] overflow-y-auto p-2">
              {results.length === 0 && (
                <li className="px-3 py-6 text-center text-sm text-slate-400">No matches</li>
              )}
              {results.map((result, i) => {
                const isActive = i === active;
                if (result.kind === 'payment') {
                  return (
                    <li
                      key="payment-action"
                      id={`command-opt-${i}`}
                      role="option"
                      aria-selected={isActive}
                      onMouseEnter={() => setActive(i)}
                      onMouseDown={(e) => {
                        e.preventDefault();
                        select(result);
                      }}
                      className={cn(
                        'flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5 text-sm',
                        isActive ? 'bg-blue-600 text-white' : 'text-slate-700'
                      )}
                    >
                      <Receipt className={cn('h-4 w-4 shrink-0', isActive ? 'text-white' : 'text-blue-600')} />
                      <span className="flex-1 truncate">
                        Open payment <span className="font-mono font-semibold">{result.id}</span>
                      </span>
                      <ArrowRight className="h-3.5 w-3.5 shrink-0 opacity-70" />
                    </li>
                  );
                }
                const { dest } = result;
                const Icon = dest.icon;
                return (
                  <li
                    key={`${dest.to}-${dest.label}`}
                    id={`command-opt-${i}`}
                    role="option"
                    aria-selected={isActive}
                    onMouseEnter={() => setActive(i)}
                    onMouseDown={(e) => {
                      e.preventDefault();
                      select(result);
                    }}
                    className={cn(
                      'flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5 text-sm',
                      isActive ? 'bg-blue-600 text-white' : 'text-slate-700'
                    )}
                  >
                    <Icon className={cn('h-4 w-4 shrink-0', isActive ? 'text-white' : 'text-slate-400')} />
                    <span className="flex-1 truncate">
                      {dest.label}
                      {dest.hint && (
                        <span className={cn('ml-2 text-xs', isActive ? 'text-blue-100' : 'text-slate-400')}>
                          {dest.hint}
                        </span>
                      )}
                    </span>
                    <span
                      className={cn(
                        'rounded px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide',
                        isActive ? 'bg-white/20 text-white' : 'bg-slate-100 text-slate-500'
                      )}
                    >
                      {dest.group}
                    </span>
                  </li>
                );
              })}
            </ul>

            <div className="flex items-center justify-between border-t border-slate-100 px-4 py-2 text-[11px] text-slate-400">
              <span className="flex items-center gap-1.5">
                <CornerDownLeft className="h-3 w-3" /> to open
              </span>
              <span>↑ ↓ to navigate · Esc to close</span>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
