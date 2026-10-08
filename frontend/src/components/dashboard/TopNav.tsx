import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import {
  BookOpen,
  ChevronDown,
  HelpCircle,
  LogOut,
  Menu,
  Search,
  User,
  X,
} from 'lucide-react';
import { useAuthStore } from '../../store/auth';
import { logout as logoutSession } from '../../services/authService';
import { cn } from '../../lib/utils';
import {
  ACCOUNT_LINKS,
  getCommandDestinations,
  getVisibleNav,
  isNodeActive,
  type NavGroup,
  type NavLeaf,
  type NavNode,
} from './navItems';
import { CommandMenu } from './CommandMenu';

function Mark({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        'flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-blue-600 to-indigo-700 text-white shadow-md shadow-blue-500/20',
        className
      )}
    >
      <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M7 16V4h8a4 4 0 0 1 0 8H7" />
        <path d="M12 12l5 8" className="stroke-blue-200" />
      </svg>
    </span>
  );
}

export function TopNav() {
  const { user, hasPermission, isAdmin, refreshToken, logout: clearAuth } = useAuthStore();
  const admin = isAdmin();
  const location = useLocation();
  const navigate = useNavigate();

  const nav = useMemo(
    () => getVisibleNav((p) => !p || hasPermission(p), admin),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [user, admin]
  );
  const destinations = useMemo(
    () => getCommandDestinations((p) => !p || hasPermission(p), admin),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [user, admin]
  );
  const accountLinks = ACCOUNT_LINKS.filter((l) => !l.perm || hasPermission(l.perm));

  const [cmdOpen, setCmdOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setCmdOpen((o) => !o);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const handleLogout = async () => {
    try {
      if (refreshToken) await logoutSession(refreshToken);
    } catch {
      // Local sign-out must still complete if the session/network is gone.
    } finally {
      clearAuth();
      navigate('/login');
    }
  };

  const isMac = typeof navigator !== 'undefined' && /mac|iphone|ipad/i.test(navigator.platform);
  const reduce = useReducedMotion();

  // Elevate the header once the page scrolls, for subtle depth.
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 4);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <>
      <header
        className={cn(
          'sticky top-0 z-40 border-b backdrop-blur-xl transition-[background-color,box-shadow,border-color] duration-300',
          scrolled
            ? 'border-slate-200/80 bg-white/90 shadow-[0_6px_24px_-12px_rgba(15,23,42,0.18)]'
            : 'border-slate-200/60 bg-white/70'
        )}
      >
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-2 px-4 sm:px-6 lg:px-8">
          <Link
            to="/dashboard"
            className="group flex shrink-0 items-center gap-2.5"
            aria-label="PayFlow dashboard"
          >
            <Mark className="transition-transform duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] group-hover:scale-105" />
            <span className="text-lg font-bold tracking-tight text-slate-900">PayFlow</span>
          </Link>

          <span className="mx-1 hidden h-6 w-px bg-slate-200 lg:block" aria-hidden="true" />

          {/* Primary navigation (desktop) */}
          <nav className="hidden items-center gap-0.5 lg:flex" aria-label="Primary">
            {nav.map((node) =>
              node.kind === 'leaf' ? (
                <LeafLink
                  key={node.to}
                  node={node}
                  active={isNodeActive(node, location.pathname)}
                  reduce={!!reduce}
                />
              ) : (
                <GroupMenu
                  key={node.label}
                  group={node}
                  active={isNodeActive(node, location.pathname)}
                  reduce={!!reduce}
                />
              )
            )}
          </nav>

          {/* Right controls */}
          <div className="ml-auto flex items-center gap-1.5 sm:gap-2">
            <button
              type="button"
              onClick={() => setCmdOpen(true)}
              className="hidden items-center gap-2 rounded-xl border border-slate-200/80 bg-slate-50/70 py-2 pl-3 pr-2 text-xs text-slate-500 transition hover:border-slate-300 hover:bg-white md:flex"
            >
              <Search className="h-4 w-4" />
              <span className="pr-6">Search</span>
              <kbd className="rounded-md border border-slate-200 bg-white px-1.5 py-0.5 font-sans text-[10px] font-semibold text-slate-400">
                {isMac ? '⌘' : 'Ctrl'} K
              </kbd>
            </button>

            <button
              type="button"
              onClick={() => setCmdOpen(true)}
              aria-label="Search"
              className="rounded-xl border border-slate-200/80 bg-white/80 p-2 text-slate-500 transition hover:bg-slate-50 hover:text-slate-900 md:hidden"
            >
              <Search className="h-4 w-4" />
            </button>

            <HelpMenu onNavigate={(to) => navigate(to)} isOwner={!!user?.roles?.includes('MERCHANT_OWNER')} />

            <div className="hidden lg:block">
              <AccountMenu user={user} accountLinks={accountLinks} onLogout={handleLogout} />
            </div>

            <button
              type="button"
              onClick={() => setMobileOpen(true)}
              aria-label="Open menu"
              aria-expanded={mobileOpen}
              className="rounded-xl border border-slate-200/80 bg-white/80 p-2 text-slate-600 transition hover:bg-slate-50 lg:hidden"
            >
              <Menu className="h-5 w-5" />
            </button>
          </div>
        </div>
      </header>

      <CommandMenu open={cmdOpen} onClose={() => setCmdOpen(false)} destinations={destinations} />
      <MobileNav
        open={mobileOpen}
        onClose={() => setMobileOpen(false)}
        nav={nav}
        accountLinks={accountLinks}
        user={user}
        onLogout={handleLogout}
      />
    </>
  );
}

/* ---------------------------------------------------------------- desktop */

const linkBase = 'flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors';

/** Shared-layout pill that slides between nav items as the route changes. */
function ActiveIndicator({ reduce }: { reduce: boolean }) {
  if (reduce) return <span aria-hidden="true" className="absolute inset-0 rounded-lg bg-blue-50" />;
  return (
    <motion.span
      aria-hidden="true"
      layoutId="topnav-active"
      className="absolute inset-0 rounded-lg bg-blue-50"
      transition={{ type: 'spring', stiffness: 380, damping: 32 }}
    />
  );
}

function LeafLink({
  node,
  active,
  reduce,
}: {
  node: Extract<NavNode, { kind: 'leaf' }>;
  active: boolean;
  reduce: boolean;
}) {
  const Icon = node.icon;
  return (
    <NavLink
      to={node.to}
      end={node.exact}
      className={cn(
        linkBase,
        'relative',
        active ? 'text-blue-700' : 'text-slate-600 hover:bg-slate-100/70 hover:text-slate-900'
      )}
    >
      {active && <ActiveIndicator reduce={reduce} />}
      <span className="relative z-10 flex items-center gap-2">
        <Icon className="h-4 w-4" />
        {node.label}
      </span>
    </NavLink>
  );
}

function GroupMenu({ group, active, reduce }: { group: NavGroup; active: boolean; reduce: boolean }) {
  const Icon = group.icon;
  return (
    <Popover
      align="left"
      trigger={(open, toggle) => (
        <button
          type="button"
          onClick={toggle}
          aria-expanded={open}
          aria-haspopup="menu"
          className={cn(
            linkBase,
            'relative',
            active
              ? 'text-blue-700'
              : open
                ? 'bg-slate-100/70 text-slate-900'
                : 'text-slate-600 hover:bg-slate-100/70 hover:text-slate-900'
          )}
        >
          {active && <ActiveIndicator reduce={reduce} />}
          <span className="relative z-10 flex items-center gap-2">
            <Icon className="h-4 w-4" />
            {group.label}
            <ChevronDown className={cn('h-3.5 w-3.5 transition-transform duration-200', open && 'rotate-180')} />
          </span>
        </button>
      )}
    >
      {(close) => (
        <div className="w-64 p-1.5">
          {group.children.map((child) => (
            <DropdownLink key={child.to} leaf={child} onClick={close} />
          ))}
        </div>
      )}
    </Popover>
  );
}

function DropdownLink({ leaf, onClick }: { leaf: NavLeaf; onClick?: () => void }) {
  const Icon = leaf.icon;
  return (
    <NavLink
      to={leaf.to}
      onClick={onClick}
      className={({ isActive }) =>
        cn('flex items-start gap-3 rounded-lg px-3 py-2 transition-colors', isActive ? 'bg-blue-50' : 'hover:bg-slate-50')
      }
    >
      {({ isActive }) => (
        <>
          <Icon className={cn('mt-0.5 h-4 w-4 shrink-0', isActive ? 'text-blue-600' : 'text-slate-400')} />
          <span className="min-w-0">
            <span className={cn('block text-sm font-medium', isActive ? 'text-blue-700' : 'text-slate-800')}>
              {leaf.label}
            </span>
            {leaf.hint && <span className="block truncate text-xs text-slate-400">{leaf.hint}</span>}
          </span>
        </>
      )}
    </NavLink>
  );
}

function HelpMenu({ onNavigate, isOwner }: { onNavigate: (to: string) => void; isOwner: boolean }) {
  return (
    <Popover
      align="right"
      trigger={(open, toggle) => (
        <button
          type="button"
          onClick={toggle}
          aria-label="Help"
          aria-expanded={open}
          className={cn(
            'rounded-xl border border-slate-200/80 bg-white/80 p-2 text-slate-500 transition hover:bg-slate-50 hover:text-slate-900',
            open && 'bg-slate-50 text-slate-900'
          )}
        >
          <HelpCircle className="h-4 w-4" />
        </button>
      )}
    >
      {(close) => (
        <div className="w-56 p-1.5">
          {isOwner && (
            <button
              type="button"
              onClick={() => {
                close();
                onNavigate('/dashboard?setup=guide');
              }}
              className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-50"
            >
              <HelpCircle className="h-4 w-4 text-blue-600" /> Setup guide
            </button>
          )}
          <button
            type="button"
            onClick={() => {
              close();
              onNavigate('/developers');
            }}
            className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-50"
          >
            <BookOpen className="h-4 w-4 text-blue-600" /> Developer documentation
          </button>
        </div>
      )}
    </Popover>
  );
}

interface AuthUser {
  fullName?: string;
  email?: string;
  roles?: string[];
}

function AccountMenu({
  user,
  accountLinks,
  onLogout,
}: {
  user: AuthUser | null;
  accountLinks: NavLeaf[];
  onLogout: () => void;
}) {
  return (
    <Popover
      align="right"
      trigger={(open, toggle) => (
        <button
          type="button"
          onClick={toggle}
          aria-label="Account menu"
          aria-expanded={open}
          className={cn(
            'flex items-center gap-2 rounded-xl border border-slate-200/80 bg-white/80 py-1.5 pl-1.5 pr-2 transition hover:bg-slate-50',
            open && 'bg-slate-50'
          )}
        >
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-br from-blue-600 to-indigo-700 text-white">
            <User className="h-4 w-4" />
          </span>
          <span className="hidden max-w-[140px] flex-col text-left xl:flex">
            <span className="truncate text-xs font-semibold text-slate-900">{user?.fullName || 'PayFlow user'}</span>
            <span className="truncate text-[10px] text-slate-500">{user?.roles?.[0] || 'Merchant'}</span>
          </span>
          <ChevronDown className="h-3.5 w-3.5 text-slate-400" />
        </button>
      )}
    >
      {(close) => (
        <div className="w-64 p-1.5">
          <div className="border-b border-slate-100 px-3 py-2.5">
            <p className="truncate text-sm font-semibold text-slate-900">{user?.fullName || 'PayFlow user'}</p>
            {user?.email && <p className="truncate text-xs text-slate-500">{user.email}</p>}
          </div>
          <div className="py-1.5">
            {accountLinks.map((link) => (
              <DropdownLink key={link.to} leaf={link} onClick={close} />
            ))}
          </div>
          <div className="border-t border-slate-100 pt-1.5">
            <button
              type="button"
              onClick={() => {
                close();
                onLogout();
              }}
              className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm font-medium text-slate-600 transition hover:bg-rose-50 hover:text-rose-700"
            >
              <LogOut className="h-4 w-4" /> Sign out
            </button>
          </div>
        </div>
      )}
    </Popover>
  );
}

/** Generic click popover used by the group/help/account menus. */
function Popover({
  trigger,
  children,
  align = 'left',
}: {
  trigger: (open: boolean, toggle: () => void) => ReactNode;
  children: (close: () => void) => ReactNode;
  align?: 'left' | 'right';
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const reduce = useReducedMotion();

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div ref={ref} className="relative">
      {trigger(open, () => setOpen((o) => !o))}
      <AnimatePresence>
        {open && (
          <motion.div
            initial={reduce ? { opacity: 0 } : { opacity: 0, y: 6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={reduce ? { opacity: 0 } : { opacity: 0, y: 6, scale: 0.98 }}
            transition={{ duration: reduce ? 0 : 0.15, ease: [0.16, 1, 0.3, 1] }}
            className={cn(
              'absolute top-[calc(100%+8px)] z-50 overflow-hidden rounded-xl border border-slate-200/80 bg-white/95 shadow-xl shadow-slate-900/10 backdrop-blur-xl',
              align === 'right' ? 'right-0' : 'left-0'
            )}
            role="menu"
          >
            {children(() => setOpen(false))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

/* ----------------------------------------------------------------- mobile */

function MobileNav({
  open,
  onClose,
  nav,
  accountLinks,
  user,
  onLogout,
}: {
  open: boolean;
  onClose: () => void;
  nav: NavNode[];
  accountLinks: NavLeaf[];
  user: AuthUser | null;
  onLogout: () => void;
}) {
  const reduce = useReducedMotion();
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    requestAnimationFrame(() => closeRef.current?.focus());
    return () => {
      document.body.style.overflow = prev;
      document.removeEventListener('keydown', onKey);
    };
  }, [open, onClose]);

  const flatLeaves = (node: NavNode): NavLeaf[] => (node.kind === 'leaf' ? [node] : node.children);

  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-[55] lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: reduce ? 0 : 0.2 }}
            className="absolute inset-0 bg-slate-950/40 backdrop-blur-sm"
            onClick={onClose}
          />
          <motion.aside
            initial={reduce ? { opacity: 0 } : { x: '100%' }}
            animate={reduce ? { opacity: 1 } : { x: 0 }}
            exit={reduce ? { opacity: 0 } : { x: '100%' }}
            transition={{ type: reduce ? 'tween' : 'spring', damping: 32, stiffness: 320 }}
            className="absolute right-0 top-0 flex h-full w-[86%] max-w-sm flex-col border-l border-slate-200 bg-white shadow-2xl"
          >
            <div className="flex h-16 shrink-0 items-center justify-between border-b border-slate-100 px-4">
              <span className="flex items-center gap-2.5">
                <Mark />
                <span className="text-lg font-bold tracking-tight text-slate-900">PayFlow</span>
              </span>
              <button
                ref={closeRef}
                type="button"
                onClick={onClose}
                aria-label="Close menu"
                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <nav className="flex-1 overflow-y-auto px-3 py-3" aria-label="Mobile">
              {nav.map((node) => (
                <div key={node.kind === 'leaf' ? node.to : node.label} className="mb-3">
                  {node.kind === 'group' && (
                    <p className="px-3 pb-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-400">
                      {node.label}
                    </p>
                  )}
                  <div className="space-y-0.5">
                    {flatLeaves(node).map((leaf) => (
                      <MobileLink
                        key={leaf.to}
                        leaf={leaf}
                        onNavigate={onClose}
                        exact={node.kind === 'leaf' && node.exact}
                      />
                    ))}
                  </div>
                </div>
              ))}

              {accountLinks.length > 0 && (
                <div className="mb-3">
                  <p className="px-3 pb-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-400">Account</p>
                  <div className="space-y-0.5">
                    {accountLinks.map((leaf) => (
                      <MobileLink key={leaf.to} leaf={leaf} onNavigate={onClose} />
                    ))}
                  </div>
                </div>
              )}
            </nav>

            <div className="shrink-0 space-y-2 border-t border-slate-100 bg-slate-50/50 p-3">
              {user && (
                <div className="rounded-xl border border-slate-200/70 bg-white px-3 py-2">
                  <p className="truncate text-xs font-semibold text-slate-900">{user.fullName}</p>
                  {user.email && <p className="truncate text-[10px] text-slate-500">{user.email}</p>}
                </div>
              )}
              <button
                type="button"
                onClick={() => {
                  onClose();
                  onLogout();
                }}
                className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-semibold text-slate-600 transition hover:bg-rose-50 hover:text-rose-700"
              >
                <LogOut className="h-4 w-4" /> Sign out
              </button>
            </div>
          </motion.aside>
        </div>
      )}
    </AnimatePresence>
  );
}

function MobileLink({ leaf, onNavigate, exact }: { leaf: NavLeaf; onNavigate: () => void; exact?: boolean }) {
  const Icon = leaf.icon;
  return (
    <NavLink
      to={leaf.to}
      end={exact}
      onClick={onNavigate}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
          isActive ? 'bg-blue-600 text-white' : 'text-slate-700 hover:bg-slate-100'
        )
      }
    >
      <Icon className="h-[18px] w-[18px] shrink-0" />
      {leaf.label}
    </NavLink>
  );
}
