import { Link } from 'react-router-dom';
import { PayFlowMark } from './PayFlowMark';

export function LandingFooter() {
  return (
    <footer className="border-t border-slate-200/70 bg-white/60 backdrop-blur-sm">
      <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-4 px-4 py-8 sm:flex-row sm:px-6 lg:px-8">
        <div className="flex items-center gap-2.5">
          <PayFlowMark className="h-7 w-7" />
          <span className="text-sm font-semibold text-slate-700">PayFlow</span>
        </div>

        <nav className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-sm text-slate-500">
          <a href="#platform" className="transition-colors hover:text-brand-600">Platform</a>
          <a href="#security" className="transition-colors hover:text-brand-600">Security</a>
          <a href="#developers" className="transition-colors hover:text-brand-600">Developers</a>
          <Link to="/login" className="transition-colors hover:text-brand-600">Sign in</Link>
        </nav>

        <p className="text-xs text-slate-400">
          © {new Date().getFullYear()} PayFlow. Payments infrastructure.
        </p>
      </div>
    </footer>
  );
}
