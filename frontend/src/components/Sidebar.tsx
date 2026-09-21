import { NavLink, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard, CreditCard, KeyRound, Building2, Settings, LogOut,
  ShieldCheck, Receipt, ChevronLeft, Menu,
} from 'lucide-react';
import { useAuthStore } from '../store/auth';
import { cn } from '../lib/utils';
import { useState } from 'react';

const merchantNav = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, perm: null },
  { to: '/payments', label: 'Payments', icon: CreditCard, perm: 'payments:read' },
  { to: '/payments/create', label: 'Create Payment', icon: Receipt, perm: 'payments:create' },
  { to: '/api-keys', label: 'API Keys', icon: KeyRound, perm: 'api_keys:manage' },
  { to: '/merchant', label: 'Merchant Profile', icon: Building2, perm: 'merchant:read' },
];

const adminNav = [
  { to: '/admin/merchants', label: 'Merchants', icon: ShieldCheck, perm: 'platform:admin' },
];

export function Sidebar() {
  const { user, logout, hasPermission, isAdmin } = useAuthStore();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const visibleMerchant = merchantNav.filter((item) => !item.perm || hasPermission(item.perm));
  const visibleAdmin = isAdmin() ? adminNav : [];

  return (
    <aside className={cn(
      'flex flex-col bg-slate-900 text-slate-300 transition-all duration-300 h-screen sticky top-0',
      collapsed ? 'w-16' : 'w-64'
    )}>
      <div className="flex items-center justify-between px-4 h-16 border-b border-slate-800">
        {!collapsed && (
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-brand-600 flex items-center justify-center">
              <span className="text-white font-bold text-sm">P</span>
            </div>
            <span className="text-white font-semibold text-lg">PayFlow</span>
          </div>
        )}
        <button onClick={() => setCollapsed(!collapsed)} className="p-1.5 rounded-lg hover:bg-slate-800 transition">
          {collapsed ? <Menu className="w-5 h-5" /> : <ChevronLeft className="w-5 h-5" />}
        </button>
      </div>

      <nav className="flex-1 overflow-y-auto py-4">
        <div className="space-y-1 px-2">
          {visibleMerchant.map((item) => (
            <NavItem key={item.to} {...item} collapsed={collapsed} />
          ))}
        </div>

        {visibleAdmin.length > 0 && (
          <>
            {!collapsed && <div className="px-4 mt-6 mb-2 text-xs font-medium text-slate-500 uppercase tracking-wider">Admin</div>}
            <div className="space-y-1 px-2 mt-2">
              {visibleAdmin.map((item) => (
                <NavItem key={item.to} {...item} collapsed={collapsed} />
              ))}
            </div>
          </>
        )}
      </nav>

      <div className="border-t border-slate-800 p-2">
        {!collapsed && user && (
          <div className="px-2 py-2 mb-1">
            <p className="text-sm font-medium text-white truncate">{user.fullName}</p>
            <p className="text-xs text-slate-500 truncate">{user.email}</p>
          </div>
        )}
        <button onClick={handleLogout} className={cn(
          'flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-sm text-slate-400 hover:bg-slate-800 hover:text-white transition',
          collapsed && 'justify-center'
        )}>
          <LogOut className="w-5 h-5 flex-shrink-0" />
          {!collapsed && 'Sign out'}
        </button>
      </div>
    </aside>
  );
}

function NavItem({ to, label, icon: Icon, collapsed }: { to: string; label: string; icon: any; collapsed: boolean }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) => cn(
        'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
        collapsed && 'justify-center',
        isActive
          ? 'bg-brand-600 text-white'
          : 'text-slate-400 hover:bg-slate-800 hover:text-white'
      )}
      title={collapsed ? label : undefined}
    >
      <Icon className="w-5 h-5 flex-shrink-0" />
      {!collapsed && label}
    </NavLink>
  );
}
