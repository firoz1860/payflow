import { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  Activity,
  BarChart3,
  BookOpen,
  Building2,
  Code2,
  CreditCard,
  KeyRound,
  LayoutDashboard,
  LockKeyhole,
  LogOut,
  Menu,
  QrCode,
  Receipt,
  Settings,
  ShieldCheck,
  Users,
  Webhook,
  X,
} from 'lucide-react';
import { AnimatePresence, motion } from 'framer-motion';
import { useAuthStore } from '../store/auth';
import { logout as logoutSession } from '../services/authService';
import { cn } from '../lib/utils';

const paymentNav = [
  { to: '/dashboard', label: 'Overview', icon: LayoutDashboard, perm: null },
  { to: '/payments', label: 'Payments', icon: CreditCard, perm: 'payments:read' },
  { to: '/payments/create', label: 'Create Payment', icon: Receipt, perm: 'payments:create' },
  { to: '/payments/qr', label: 'QR Payments', icon: QrCode, perm: 'payments:create' },
];

const platformNav = [
  { to: '/ledger', label: 'Ledger', icon: BookOpen, perm: 'ledger:read' },
  { to: '/analytics', label: 'Analytics', icon: BarChart3, perm: 'payments:read' },
  { to: '/api-keys', label: 'API Keys', icon: KeyRound, perm: 'api_keys:manage' },
  { to: '/webhooks', label: 'Webhooks', icon: Webhook, perm: 'webhooks:manage' },
  { to: '/developers', label: 'Developers', icon: Code2, perm: null },
  { to: '/settings', label: 'Settings', icon: Settings, perm: 'merchant:read' },
];

const adminNav = [
  { to: '/monitoring', label: 'Monitoring', icon: Activity, perm: 'platform:admin' },
  { to: '/admin/merchants', label: 'Merchants', icon: ShieldCheck, perm: 'platform:admin' },
  { to: '/admin/roles', label: 'User Roles', icon: Users, perm: 'platform:admin' },
];

export function Sidebar() {
  const { user, refreshToken, logout: clearAuth, hasPermission, isAdmin } = useAuthStore();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleLogout = async () => {
    try {
      if (refreshToken) {
        await logoutSession(refreshToken);
      }
    } catch {
      // Local sign-out must still complete if the network/session is already gone.
    } finally {
      clearAuth();
      navigate('/login');
    }
  };

  const visiblePayments = paymentNav.filter((item) => !item.perm || hasPermission(item.perm));
  const visiblePlatform = platformNav.filter((item) => !item.perm || hasPermission(item.perm));
  const visibleAdmin = isAdmin() ? adminNav : [];

  return (
    <>
      <button
        type="button"
        onClick={() => setMobileOpen(true)}
        className="fixed left-4 top-5 z-40 rounded-xl border border-white/80 bg-white/90 p-2 text-slate-700 shadow-lg backdrop-blur-xl lg:hidden"
        aria-label="Open navigation"
      >
        <Menu className="h-5 w-5" />
      </button>

      <AnimatePresence>
        {mobileOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setMobileOpen(false)}
              className="fixed inset-0 z-40 bg-slate-950/30 backdrop-blur-sm lg:hidden"
            />
            <motion.aside
              initial={{ x: -300, opacity: 0 }}
              animate={{ x: 0, opacity: 1 }}
              exit={{ x: -300, opacity: 0 }}
              transition={{ type: 'spring', damping: 30, stiffness: 320 }}
              className="fixed bottom-4 left-4 top-4 z-50 flex w-64 flex-col overflow-hidden rounded-2xl border border-white/80 bg-white/95 shadow-2xl backdrop-blur-2xl lg:hidden"
            >
              <SidebarContent
                user={user}
                visiblePayments={visiblePayments}
                visiblePlatform={visiblePlatform}
                visibleAdmin={visibleAdmin}
                onNavigate={() => setMobileOpen(false)}
                onLogout={handleLogout}
                onClose={() => setMobileOpen(false)}
              />
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      <aside className="fixed bottom-4 left-4 top-4 z-40 hidden w-64 flex-col overflow-hidden rounded-2xl border border-white/80 bg-white/82 shadow-[0_4px_24px_-4px_rgba(15,23,42,0.08)] backdrop-blur-2xl lg:flex">
        <SidebarContent
          user={user}
          visiblePayments={visiblePayments}
          visiblePlatform={visiblePlatform}
          visibleAdmin={visibleAdmin}
          onLogout={handleLogout}
        />
      </aside>
    </>
  );
}

interface SidebarContentProps {
  user: any;
  visiblePayments: any[];
  visiblePlatform: any[];
  visibleAdmin: any[];
  onLogout: () => void;
  onNavigate?: () => void;
  onClose?: () => void;
}

function SidebarContent({
  user,
  visiblePayments,
  visiblePlatform,
  visibleAdmin,
  onLogout,
  onNavigate,
  onClose,
}: SidebarContentProps) {
  return (
    <>
      <div className="flex h-16 shrink-0 items-center justify-between border-b border-slate-100/80 px-4">
        <NavLink to="/dashboard" onClick={onNavigate} className="group flex items-center gap-2.5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-blue-600 to-indigo-700 text-white shadow-md shadow-blue-500/20 transition-transform group-hover:scale-105">
            <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M7 16V4h8a4 4 0 0 1 0 8H7" />
              <path d="M12 12l5 8" className="stroke-blue-200" />
            </svg>
          </div>
          <span className="text-lg font-bold tracking-tight text-slate-900">PayFlow</span>
        </NavLink>
        {onClose && (
          <button type="button" onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700">
            <X className="h-5 w-5" />
          </button>
        )}
      </div>

      <div className="flex-1 overflow-y-auto px-3 py-3">
        <NavSection label="Payments" items={visiblePayments} onNavigate={onNavigate} />
        <div className="mt-5">
          <NavSection label="Platform" items={visiblePlatform} onNavigate={onNavigate} />
        </div>

        {visibleAdmin.length > 0 && (
          <div className="mt-5">
            <NavSection label="Administration" items={visibleAdmin} onNavigate={onNavigate} />
          </div>
        )}
      </div>

      <div className="space-y-2 border-t border-slate-100/80 bg-slate-50/45 p-3">
        <div className="flex items-center gap-2 rounded-xl border border-emerald-200/60 bg-emerald-50/80 px-2.5 py-2 text-[11px] font-medium text-emerald-800">
          <LockKeyhole className="h-4 w-4 shrink-0 text-emerald-600" />
          <div className="min-w-0">
            <p className="font-semibold leading-tight">Protected session</p>
            <p className="truncate text-[10px] text-emerald-700/80">JWT + permission checks active</p>
          </div>
        </div>

        {user && (
          <div className="rounded-xl border border-slate-200/70 bg-white/80 p-2.5">
            <p className="truncate text-xs font-semibold text-slate-900">{user.fullName}</p>
            <p className="truncate text-[10px] text-slate-500">{user.email}</p>
          </div>
        )}

        <button
          type="button"
          onClick={onLogout}
          className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-semibold text-slate-500 transition hover:bg-rose-50 hover:text-rose-700"
        >
          <LogOut className="h-4 w-4" />
          Sign out
        </button>
      </div>
    </>
  );
}

function NavSection({ label, items, onNavigate }: { label: string; items: any[]; onNavigate?: () => void }) {
  return (
    <div>
      <div className="px-3 pb-1.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-400">
        {label}
      </div>
      <nav className="space-y-0.5">
        {items.map((item) => (
          <NavItem key={item.to} {...item} onClick={onNavigate} />
        ))}
      </nav>
    </div>
  );
}

function NavItem({
  to,
  label,
  icon: Icon,
  onClick,
}: {
  to: string;
  label: string;
  icon: any;
  onClick?: () => void;
}) {
  return (
    <NavLink
      to={to}
      onClick={onClick}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all',
          isActive
            ? 'bg-blue-600 text-white shadow-sm shadow-blue-500/20'
            : 'text-slate-600 hover:bg-slate-100/80 hover:text-slate-900'
        )
      }
    >
      <Icon className="h-[18px] w-[18px] shrink-0" />
      <span>{label}</span>
    </NavLink>
  );
}
