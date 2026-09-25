import { useEffect, useMemo, useState } from 'react';
import { Bell, CheckCircle2, Clock3, Command, Search, XCircle } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { listPayments } from '../services/paymentService';
import { getMyMerchant } from '../services/merchantService';
import { useAuthStore } from '../store/auth';
import type { Merchant, Payment } from '../types';

export function Topbar() {
  const navigate = useNavigate();
  const { user, hasPermission } = useAuthStore();
  const [searchOpen, setSearchOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [payments, setPayments] = useState<Payment[]>([]);
  const [merchant, setMerchant] = useState<Merchant | null>(null);

  useEffect(() => {
    if (hasPermission('payments:read')) {
      listPayments({ page: 0, size: 100 }).then((r) => setPayments(r.data || [])).catch(() => setPayments([]));
    }
    if (hasPermission('merchant:read')) {
      getMyMerchant().then(setMerchant).catch(() => setMerchant(null));
    }
  }, [hasPermission]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const shortcut = (event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k';
      if (shortcut) {
        event.preventDefault();
        setSearchOpen(true);
      }
      if (event.key === 'Escape') {
        setSearchOpen(false);
        setNotificationsOpen(false);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const matches = useMemo(() => {
    const value = query.trim().toLowerCase();
    if (!value) return payments.slice(0, 6);
    return payments
      .filter((p) =>
        p.paymentReference.toLowerCase().includes(value) ||
        (p.merchantOrderId || '').toLowerCase().includes(value))
      .slice(0, 8);
  }, [payments, query]);

  const notifications = useMemo(() =>
    [...payments]
      .sort((a, b) => +new Date(b.updatedAt) - +new Date(a.updatedAt))
      .filter((p) => ['FAILED', 'CAPTURED', 'PENDING'].includes(p.status))
      .slice(0, 6), [payments]);

  const openPayment = (payment: Payment) => {
    setSearchOpen(false);
    setNotificationsOpen(false);
    navigate(`/payments/${payment.paymentReference}`);
  };

  return (
    <>
      <header className="sticky top-0 z-30 px-4 sm:px-6 pt-4">
        <div className="max-w-7xl mx-auto h-14 rounded-2xl border border-white/70 bg-white/64 backdrop-blur-2xl shadow-lg shadow-slate-900/5 flex items-center justify-between gap-4 px-4">
          <button
            onClick={() => setSearchOpen(true)}
            className="hidden sm:flex flex-1 max-w-xl items-center gap-3 text-left px-3 py-2 rounded-xl bg-white/50 hover:bg-white/75 border border-white/70 transition"
          >
            <Search className="w-4 h-4 text-slate-400" />
            <span className="text-sm text-slate-500">Search payments or order IDs</span>
            <span className="ml-auto inline-flex items-center gap-1 text-[11px] text-slate-400 border border-slate-200 rounded-md px-1.5 py-0.5">
              <Command className="w-3 h-3" /> K
            </span>
          </button>

          <div className="ml-auto flex items-center gap-2">
            <span className={`hidden sm:inline-flex badge px-3 py-1.5 ${merchant?.liveModeEnabled ? 'bg-red-100 text-red-700' : 'bg-blue-100 text-blue-700'}`}>
              {merchant?.liveModeEnabled ? 'LIVE enabled' : 'TEST mode'}
            </span>

            <button
              onClick={() => setSearchOpen(true)}
              className="sm:hidden p-2.5 rounded-xl hover:bg-white/60 text-slate-600"
              aria-label="Search"
            >
              <Search className="w-5 h-5" />
            </button>

            <button
              onClick={() => setNotificationsOpen((v) => !v)}
              className="relative p-2.5 rounded-xl hover:bg-white/60 text-slate-600"
              aria-label="Notifications"
            >
              <Bell className="w-5 h-5" />
              {notifications.some((p) => p.status === 'FAILED') && (
                <span className="absolute top-2 right-2 w-2 h-2 rounded-full bg-red-500 ring-2 ring-white" />
              )}
            </button>

            <div className="hidden md:block text-right">
              <p className="text-sm font-semibold text-slate-900">{user?.fullName || 'PayFlow User'}</p>
              <p className="text-[11px] text-slate-500">{user?.email || ''}</p>
            </div>
          </div>
        </div>
      </header>

      <AnimatePresence>
        {searchOpen && (
          <motion.div
            className="fixed inset-0 z-[70] bg-slate-950/28 backdrop-blur-sm p-4 flex items-start justify-center pt-[10vh]"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onMouseDown={(e) => { if (e.currentTarget === e.target) setSearchOpen(false); }}
          >
            <motion.div
              initial={{ opacity: 0, y: -8, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -8, scale: 0.98 }}
              className="w-full max-w-2xl rounded-3xl border border-white/70 bg-white/85 backdrop-blur-2xl shadow-2xl overflow-hidden"
            >
              <div className="flex items-center gap-3 px-5 py-4 border-b border-slate-200/70">
                <Search className="w-5 h-5 text-slate-400" />
                <input
                  autoFocus
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Search payment ID or merchant order ID..."
                  className="flex-1 bg-transparent outline-none text-sm text-slate-900 placeholder-slate-400"
                />
                <button onClick={() => setSearchOpen(false)} className="text-xs text-slate-400 border border-slate-200 rounded-lg px-2 py-1">ESC</button>
              </div>

              <div className="max-h-[55vh] overflow-y-auto p-2">
                <p className="px-3 pt-2 pb-1 text-[11px] uppercase tracking-wider text-slate-400">Payments</p>
                {matches.length === 0 ? (
                  <div className="px-4 py-8 text-center text-sm text-slate-500">No matching payment found in the latest loaded payments.</div>
                ) : matches.map((payment) => (
                  <button
                    key={payment.paymentReference}
                    onClick={() => openPayment(payment)}
                    className="w-full text-left p-3 rounded-xl hover:bg-blue-50/70 transition flex items-center justify-between gap-4"
                  >
                    <div>
                      <p className="font-mono text-xs font-semibold text-brand-700">{payment.paymentReference}</p>
                      <p className="text-xs text-slate-500 mt-1">{payment.merchantOrderId || 'No merchant order ID'}</p>
                    </div>
                    <span className={`badge ${statusClass(payment.status)}`}>{payment.status}</span>
                  </button>
                ))}
              </div>
            </motion.div>
          </motion.div>
        )}

        {notificationsOpen && (
          <motion.aside
            initial={{ opacity: 0, x: 24 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 24 }}
            className="fixed z-[65] right-4 top-20 w-[min(390px,calc(100vw-2rem))] rounded-3xl border border-white/70 bg-white/82 backdrop-blur-2xl shadow-2xl overflow-hidden"
          >
            <div className="p-5 border-b border-slate-200/70">
              <h2 className="font-semibold text-slate-950">Recent payment activity</h2>
              <p className="text-xs text-slate-500 mt-1">Derived from your latest PayFlow payment records.</p>
            </div>
            <div className="max-h-[65vh] overflow-y-auto p-2">
              {notifications.length === 0 ? (
                <div className="p-8 text-center text-sm text-slate-500">No recent payment activity.</div>
              ) : notifications.map((payment) => (
                <button
                  key={payment.paymentReference}
                  onClick={() => openPayment(payment)}
                  className="w-full p-3 rounded-xl hover:bg-white/75 text-left transition flex gap-3"
                >
                  <div className={`mt-0.5 w-8 h-8 rounded-full flex items-center justify-center ${statusIconClass(payment.status)}`}>
                    {payment.status === 'CAPTURED' ? <CheckCircle2 className="w-4 h-4" /> : payment.status === 'FAILED' ? <XCircle className="w-4 h-4" /> : <Clock3 className="w-4 h-4" />}
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-slate-900">Payment {payment.status.toLowerCase()}</p>
                    <p className="font-mono text-xs text-slate-500 truncate mt-0.5">{payment.paymentReference}</p>
                    <p className="text-[11px] text-slate-400 mt-1">{new Date(payment.updatedAt).toLocaleString('en-IN')}</p>
                  </div>
                </button>
              ))}
            </div>
          </motion.aside>
        )}
      </AnimatePresence>
    </>
  );
}

function statusClass(status: string) {
  if (status === 'CAPTURED') return 'bg-emerald-100 text-emerald-700';
  if (status === 'FAILED') return 'bg-red-100 text-red-700';
  if (status === 'PENDING') return 'bg-amber-100 text-amber-700';
  return 'bg-blue-100 text-blue-700';
}

function statusIconClass(status: string) {
  if (status === 'CAPTURED') return 'bg-emerald-100 text-emerald-700';
  if (status === 'FAILED') return 'bg-red-100 text-red-700';
  return 'bg-amber-100 text-amber-700';
}
