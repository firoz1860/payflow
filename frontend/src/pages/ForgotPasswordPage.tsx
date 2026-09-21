import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Mail, ArrowLeft, CheckCircle2, Send } from 'lucide-react';
import { initiatePasswordReset } from '../services/authService';
import { extractError } from '../api';
import { Spinner } from '../components/Spinner';
import { motion } from 'framer-motion';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await initiatePasswordReset(email);
      setSent(true);
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
        <motion.div
          animate={{ x: [0, -30, 0], y: [0, 20, 0] }}
          transition={{ duration: 25, repeat: Infinity, ease: 'easeInOut' }}
          className="absolute -bottom-40 -left-40 w-96 h-96 bg-brand-700/20 rounded-full blur-3xl"
        />
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 20, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.5, ease: [0.25, 0.1, 0.25, 1] }}
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
          <h1 className="text-2xl font-bold text-white">Forgot Password</h1>
          <p className="mt-1 text-sm text-slate-400">Enter your email to receive a reset link</p>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}
          className="bg-white rounded-2xl shadow-xl p-8"
        >
          {sent ? (
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
              <h3 className="text-lg font-semibold text-slate-900">Check your inbox</h3>
              <p className="mt-2 text-sm text-slate-500">
                If an account exists for <span className="font-medium text-slate-700">{email}</span>,
                a reset link has been sent.
              </p>
              <Link to="/login" className="btn-primary w-full mt-6">Back to sign in</Link>
            </motion.div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-5">
              {error && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }}
                  className="p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700"
                >{error}</motion.div>
              )}

              <div>
                <label className="label">Email address</label>
                <div className="relative">
                  <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                  <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
                    placeholder="you@business.com" className="input pl-10" autoComplete="email" />
                </div>
              </div>

              <motion.button type="submit" disabled={loading} whileTap={{ scale: 0.98 }} className="btn-primary w-full">
                {loading ? <Spinner size="sm" /> : <>Send Reset Link <Send className="w-4 h-4" /></>}
              </motion.button>
            </form>
          )}
        </motion.div>
      </motion.div>
    </div>
  );
}
