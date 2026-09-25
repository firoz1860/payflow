import { useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ChevronDown, Search, ShieldCheck, User } from 'lucide-react';
import { useAuthStore } from '../store/auth';

const titleByPath: Record<string, string> = {
  '/dashboard': 'Overview',
  '/payments': 'Payments',
  '/payments/create': 'Create Payment',
  '/api-keys': 'API Keys',
  '/merchant': 'Merchant Profile',
  '/admin/merchants': 'Merchants',
  '/admin/roles': 'User Roles',
};

export function Topbar() {
  const location = useLocation();
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);

  const title = useMemo(() => {
    if (location.pathname.startsWith('/payments/') && location.pathname !== '/payments/create') {
      return 'Payment Details';
    }
    if (location.pathname.startsWith('/admin/merchants/')) {
      return 'Merchant Details';
    }
    return titleByPath[location.pathname] || 'PayFlow';
  }, [location.pathname]);

  return (
    <header className="fixed left-4 right-4 top-4 z-30 h-16 rounded-2xl border border-white/80 bg-white/80 px-4 shadow-[0_4px_24px_-4px_rgba(15,23,42,0.07)] backdrop-blur-2xl lg:left-[18rem] lg:px-6">
      <div className="flex h-full items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-400">
            PayFlow Platform
          </p>
          <h2 className="truncate text-sm font-bold text-slate-900">{title}</h2>
        </div>

        <div className="flex items-center gap-2 sm:gap-3">
          <button
            type="button"
            onClick={() => navigate('/payments')}
            className="hidden items-center gap-2 rounded-xl border border-slate-200/80 bg-slate-50/80 px-3 py-2 text-xs text-slate-500 transition hover:border-slate-300 hover:bg-white hover:text-slate-800 sm:flex"
          >
            <Search className="h-4 w-4" />
            <span className="hidden md:inline">Search payments</span>
          </button>

          <div className="hidden items-center gap-1.5 rounded-full border border-emerald-200/70 bg-emerald-50 px-2.5 py-1.5 text-[11px] font-semibold text-emerald-700 md:flex">
            <ShieldCheck className="h-3.5 w-3.5" />
            Authenticated
          </div>

          <div className="flex items-center gap-2 border-l border-slate-200/80 pl-2 sm:pl-3">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-blue-600 to-indigo-700 text-white shadow-sm">
              <User className="h-4 w-4" />
            </div>
            <div className="hidden max-w-[160px] flex-col md:flex">
              <span className="truncate text-xs font-semibold text-slate-900">{user?.fullName || 'PayFlow User'}</span>
              <span className="truncate text-[10px] text-slate-500">{user?.roles?.[0] || 'Merchant'}</span>
            </div>
            <ChevronDown className="hidden h-3.5 w-3.5 text-slate-400 md:block" />
          </div>
        </div>
      </div>
    </header>
  );
}
