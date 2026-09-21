import { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { Lock, Eye, EyeOff, ArrowLeft, CheckCircle2, AlertCircle } from 'lucide-react';
import { completePasswordReset } from '../services/authService';
import { extractError } from '../api';
import { Spinner } from '../components/Spinner';
import { motion } from 'framer-motion';

export function ResetPasswordPage() {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (password !== confirmPassword) { setError('Passwords do not match'); return; }
    if (password.length < 12) { setError('Password must be at least 12 characters'); return; }
    if (!/(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/.test(password)) {
      setError('Password must contain lowercase, uppercase, and a digit'); return;
    }

    setLoading(true);
    try {
      await completePasswordReset(token || '', password);
      setSuccess(true);
      setTimeout(() => navigate('/login'), 3000);
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-900 via-slate-800 to-brand-900 p-4 relative overflow-hidden">
      <motion.div
        initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 1 }}
        className="absolute inset-0 overflow-hidden"
      >
        <motion.div
          animate={{ x: [0, 30, 0], y: [0, -20, 0] }}
          transition={{ duration: 20, repeat: Infinity, ease: 'easeInOut' }}
          className="absolute -top-40 -right-40 w-96 h-96 bg-brand-500/20 rounded-full blur-3xl"
        />
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 20, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.5 }}
        className="relative w-full max-w-md"
      >
        <Link to="/login" className="inline-flex items-center gap-2 text-sm text-slate-400 hover:text-white mb-6 transition">
          <ArrowLeft className="w-4 h-4" /> Back to sign in
        </Link>

        <div className="text-center mb-8">
          <motion.div
            initial={{ scale: 0, rotate: -180 }}
            animate={{ scale: 1, rotate: 0 }}
            transition={{ type: 'spring', damping: 15, stiffness: 200, delay: 0.2 }}
            className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-brand-600 mb-4 shadow-lg"
          >
            <span className="text-white font-bold text-2xl">P</span>
          </motion.div>
          <h1 className="text-2xl font-bold text-white">Reset Password</h1>
          <p className="mt-1 text-sm text-slate-400">Set a new password for your account</p>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}
          className="bg-white rounded-2xl shadow-xl p-8"
        >
          {success ? (
            <motion.div
              initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }}
              className="text-center py-4"
            >
              <motion.div
                initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: 'spring', damping: 15, delay: 0.2 }}
                className="w-14 h-14 rounded-full bg-emerald-100 flex items-center justify-center mx-auto mb-4"
              >
                <CheckCircle2 className="w-7 h-7 text-emerald-600" />
              </motion.div>
              <h3 className="text-lg font-semibold text-slate-900">Password Updated</h3>
              <p className="mt-2 text-sm text-slate-500">
                All previous sessions have been revoked. Redirecting to sign in…
              </p>
            </motion.div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-5">
              {error && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }}
                  className="flex items-start gap-2 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700"
                >
                  <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" /> {error}
                </motion.div>
              )}

              <div>
                <label className="label">New Password</label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                  <input type={showPassword ? 'text' : 'password'} required value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Min 12 characters" className="input pl-10 pr-10" />
                  <button type="button" onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
                    {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                  </button>
                </div>
              </div>

              <div>
                <label className="label">Confirm Password</label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                  <input type={showPassword ? 'text' : 'password'} required value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="Repeat password" className="input pl-10" />
                </div>
              </div>

              <div className="p-3 rounded-lg bg-slate-50 border border-slate-100">
                <p className="text-xs text-slate-500">
                  Password must be 12+ characters with at least one lowercase letter, one uppercase letter, and one digit.
                </p>
              </div>

              <motion.button type="submit" disabled={loading} whileTap={{ scale: 0.98 }} className="btn-primary w-full">
                {loading ? <Spinner size="sm" /> : 'Update Password'}
              </motion.button>
            </form>
          )}
        </motion.div>
      </motion.div>
    </div>
  );
}
