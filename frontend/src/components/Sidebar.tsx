import { ReactNode, useState } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import {
  LayoutDashboard, CreditCard, KeyRound, Building2, LogOut,
  ShieldCheck, Receipt, ChevronLeft, Menu, X,
} from 'lucide-react';
import { useAuthStore } from '../store/auth';
import { cn } from '../lib/utils';
import { motion, AnimatePresence } from 'framer-motion';

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
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const visibleMerchant = merchantNav.filter((item) => !item.perm || hasPermission(item.perm));
  const visibleAdmin = isAdmin() ? adminNav : [];

  return (
    <>
      {/* Mobile menu button */}
      <button
        onClick={() => setMobileOpen(true)}
        className="fixed top-4 left-4 z-50 lg:hidden p-2 rounded-lg bg-white shadow-md border border-slate-200"
      >
        <Menu className="w-5 h-5 text-slate-700" />
      </button>

      {/* Mobile overlay */}
      <AnimatePresence>
        {mobileOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setMobileOpen(false)}
            className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-40 lg:hidden"
          />
        )}
      </AnimatePresence>

      {/* Mobile drawer */}
      <AnimatePresence>
        {mobileOpen && (
          <motion.aside
            initial={{ x: -300 }}
            animate={{ x: 0 }}
            exit={{ x: -300 }}
            transition={{ type: 'spring', damping: 30, stiffness: 300 }}
            className="fixed left-0 top-0 bottom-0 z-50 w-64 bg-slate-900 text-slate-300 flex flex-col lg:hidden"
          >
            <SidebarContent
              user={user}
              visibleMerchant={visibleMerchant}
              visibleAdmin={visibleAdmin}
              collapsed={false}
              onLogout={handleLogout}
              onNavigate={() => setMobileOpen(false)}
              onClose={() => setMobileOpen(false)}
              showClose
            />
          </motion.aside>
        )}
      </AnimatePresence>

      {/* Desktop sidebar */}
      <motion.aside
        animate={{ width: collapsed ? 64 : 256 }}
        transition={{ type: 'spring', damping: 30, stiffness: 300 }}
        className="hidden lg:flex flex-col bg-slate-900 text-slate-300 h-screen sticky top-0 overflow-hidden"
      >
        <SidebarContent
          user={user}
          visibleMerchant={visibleMerchant}
          visibleAdmin={visibleAdmin}
          collapsed={collapsed}
          onToggleCollapse={() => setCollapsed(!collapsed)}
          onLogout={handleLogout}
        />
      </motion.aside>
    </>
  );
}

interface SidebarContentProps {
  user: any;
  visibleMerchant: any[];
  visibleAdmin: any[];
  collapsed: boolean;
  onLogout: () => void;
  onNavigate?: () => void;
  onToggleCollapse?: () => void;
  onClose?: () => void;
  showClose?: boolean;
}

function SidebarContent({ user, visibleMerchant, visibleAdmin, collapsed, onLogout, onNavigate, onToggleCollapse, onClose, showClose }: SidebarContentProps) {
  const location = useLocation();

  return (
    <>
      <div className="flex items-center justify-between px-4 h-16 border-b border-slate-800 flex-shrink-0">
        <div className="flex items-center gap-2 overflow-hidden">
          <div className="w-8 h-8 rounded-lg bg-brand-600 flex items-center justify-center flex-shrink-0">
            <span className="text-white font-bold text-sm">P</span>
          </div>
          <AnimatePresence>
            {!collapsed && (
              <motion.span
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -10 }}
                className="text-white font-semibold text-lg whitespace-nowrap"
              >
                PayFlow
              </motion.span>
            )}
          </AnimatePresence>
        </div>
        {showClose ? (
          <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-800 transition">
            <X className="w-5 h-5" />
          </button>
        ) : (
          onToggleCollapse && (
            <button onClick={onToggleCollapse} className="p-1.5 rounded-lg hover:bg-slate-800 transition flex-shrink-0">
              <motion.div animate={{ rotate: collapsed ? 180 : 0 }} transition={{ duration: 0.3 }}>
                <ChevronLeft className="w-5 h-5" />
              </motion.div>
            </button>
          )
        )}
      </div>

      <nav className="flex-1 overflow-y-auto py-4">
        <div className="space-y-1 px-2">
          {visibleMerchant.map((item) => (
            <NavItem key={item.to} {...item} collapsed={collapsed} onClick={onNavigate} />
          ))}
        </div>

        {visibleAdmin.length > 0 && (
          <>
            <AnimatePresence>
              {!collapsed && (
                <motion.div
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="px-4 mt-6 mb-2 text-xs font-medium text-slate-500 uppercase tracking-wider"
                >
                  Admin
                </motion.div>
              )}
            </AnimatePresence>
            <div className="space-y-1 px-2 mt-2">
              {visibleAdmin.map((item) => (
                <NavItem key={item.to} {...item} collapsed={collapsed} onClick={onNavigate} />
              ))}
            </div>
          </>
        )}
      </nav>

      <div className="border-t border-slate-800 p-2 flex-shrink-0">
        <AnimatePresence>
          {!collapsed && user && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="px-2 py-2 mb-1"
            >
              <p className="text-sm font-medium text-white truncate">{user.fullName}</p>
              <p className="text-xs text-slate-500 truncate">{user.email}</p>
            </motion.div>
          )}
        </AnimatePresence>
        <button onClick={onLogout} className={cn(
          'flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-sm text-slate-400 hover:bg-slate-800 hover:text-white transition',
          collapsed && 'justify-center'
        )}>
          <LogOut className="w-5 h-5 flex-shrink-0" />
          {!collapsed && 'Sign out'}
        </button>
      </div>
    </>
  );
}

function NavItem({ to, label, icon: Icon, collapsed, onClick }: { to: string; label: string; icon: any; collapsed: boolean; onClick?: () => void }) {
  return (
    <NavLink
      to={to}
      onClick={onClick}
      className={({ isActive }) => cn(
        'relative flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
        collapsed && 'justify-center',
        isActive
          ? 'bg-brand-600 text-white'
          : 'text-slate-400 hover:bg-slate-800 hover:text-white'
      )}
      title={collapsed ? label : undefined}
    >
      {({ isActive }) => (
        <>
          {isActive && (
            <motion.div
              layoutId="active-nav-indicator"
              className="absolute inset-0 rounded-lg bg-brand-600"
              style={{ zIndex: -1 }}
              transition={{ type: 'spring', damping: 30, stiffness: 400 }}
            />
          )}
          <Icon className="w-5 h-5 flex-shrink-0 relative z-10" />
          {!collapsed && <span className="relative z-10 whitespace-nowrap">{label}</span>}
        </>
      )}
    </NavLink>
  );
}
