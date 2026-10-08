import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { PayFlowMark } from './PayFlowMark';

const NAV_LINKS = [
  { label: 'Platform', href: '#platform' },
  { label: 'Security', href: '#security' },
  { label: 'Developers', href: '#developers' },
];

export function LandingNavbar() {
  return (
    <header className="sticky top-0 z-40 w-full">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
        <Link to="/" className="flex items-center gap-2.5" aria-label="PayFlow home">
          <PayFlowMark className="h-9 w-9" />
          <span className="text-lg font-bold tracking-tight text-slate-900">PayFlow</span>
        </Link>

        <nav className="hidden items-center gap-8 md:flex" aria-label="Primary">
          {NAV_LINKS.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="text-sm font-medium text-slate-600 transition-colors hover:text-brand-600"
            >
              {link.label}
            </a>
          ))}
        </nav>

        <div className="flex items-center gap-2 sm:gap-3">
          <Link to="/login" className="btn-ghost hidden sm:inline-flex">
            Sign in
          </Link>
          <Link to="/register" className="btn-primary">
            Get started <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </div>
    </header>
  );
}
