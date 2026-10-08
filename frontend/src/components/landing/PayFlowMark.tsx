/**
 * PayFlowMark — the PayFlow logo mark: a brand-blue rounded square with the
 * descending "flow" lines, matching public/favicon.svg. Shared across the
 * landing navbar/footer and the auth marketing panel so the brand stays
 * consistent.
 */
export function PayFlowMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} role="img" aria-label="PayFlow">
      <rect width="32" height="32" rx="7" fill="#2563eb" />
      <path
        d="M9 11h14M9 16h10M9 21h7"
        stroke="#fff"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
    </svg>
  );
}
