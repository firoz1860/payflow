import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Mail, Lock, Eye, EyeOff, ShieldCheck, ArrowRight, UserRound } from 'lucide-react';
import { login, guestLogin } from '../services/authService';
import { useAuthStore } from '../store/auth';
import { extractError } from '../api';
import { toast } from '../components/Toast';
import { Spinner } from '../components/Spinner';
import { AuthShell } from '../components/AuthShell';
import { motion } from 'framer-motion';

export function LoginPage() {
  const navigate = useNavigate();
  const loginStore = useAuthStore((s) => s.login);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [guestLoading, setGuestLoading] = useState(false);
  const [error, setError] = useState('');

  const handleGuest = async () => {
    setError('');
    setGuestLoading(true);
    try {
      const res = await guestLogin();
      loginStore(res.accessToken, res.refreshToken, res.user);
      toast('success', 'Signed in as a guest — explore the demo.');
      navigate('/dashboard');
    } catch (err) {
      setError(extractError(err));
    } finally {
      setGuestLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await login(email, password);
      loginStore(res.accessToken, res.refreshToken, res.user);
      toast('success', `Welcome back, ${res.user.fullName}!`);
      navigate('/dashboard');
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell>
      <motion.div
        initial={{ opacity: 0, y: 20, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.5, ease: [0.25, 0.1, 0.25, 1] }}
        className="relative w-full max-w-md"
      >
        <div className="text-center mb-8">
          <motion.div
            initial={{ scale: 0, rotate: -180 }}
            animate={{ scale: 1, rotate: 0 }}
            transition={{ type: 'spring', damping: 15, stiffness: 200, delay: 0.2 }}
            className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-brand-600 mb-4 shadow-lg"
          >
            <span className="text-white font-bold text-2xl">P</span>
          </motion.div>
          <motion.h1
            initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}
            className="text-2xl font-bold text-white"
          >PayFlow</motion.h1>
          <motion.p
            initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }}
            className="mt-1 text-sm text-slate-400"
          >Sign in to your merchant account</motion.p>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }}
          className="bg-white rounded-2xl shadow-xl p-8"
        >
          <form onSubmit={handleSubmit} className="space-y-5">
            {error && (
              <motion.div
                initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }}
                className="flex items-start gap-2 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700"
              >
                <ShieldCheck className="w-4 h-4 mt-0.5 flex-shrink-0" />
                <span>{error}</span>
              </motion.div>
            )}

            <div>
              <label className="label">Email address</label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                <input
                  type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@business.com" className="input pl-10" autoComplete="email"
                />
              </div>
            </div>

            <div>
              <label className="label">Password</label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                <input
                  type={showPassword ? 'text' : 'password'} required value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••" className="input pl-10 pr-10" autoComplete="current-password"
                />
                <button type="button" onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
                  {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                </button>
              </div>
            </div>

            <motion.button
              type="submit" disabled={loading}
              whileTap={{ scale: 0.98 }}
              className="btn-primary w-full"
            >
              {loading ? <Spinner size="sm" /> : (
                <>Sign in <ArrowRight className="w-4 h-4" /></>
              )}
            </motion.button>
          </form>

          <div className="relative my-5">
            <div className="absolute inset-0 flex items-center" aria-hidden="true">
              <div className="w-full border-t border-slate-200" />
            </div>
            <div className="relative flex justify-center">
              <span className="bg-white px-3 text-xs uppercase tracking-wider text-slate-400">or</span>
            </div>
          </div>

          <button
            type="button"
            onClick={handleGuest}
            disabled={guestLoading || loading}
            className="btn-secondary w-full"
          >
            {guestLoading ? <Spinner size="sm" /> : (
              <>Continue as guest <UserRound className="h-4 w-4" /></>
            )}
          </button>
          <p className="mt-2 text-center text-xs text-slate-400">
            Spins up a temporary demo merchant — no signup, no real money.
          </p>

          <div className="flex items-center justify-between mt-6 text-sm">
            <Link to="/register" className="font-medium text-brand-600 hover:text-brand-700">Create account</Link>
            <Link to="/forgot-password" className="font-medium text-slate-500 hover:text-brand-600">Forgot password?</Link>
          </div>
        </motion.div>
      </motion.div>
    </AuthShell>
  );
}
