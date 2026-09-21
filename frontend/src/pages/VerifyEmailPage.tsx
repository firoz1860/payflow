import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { CheckCircle2, AlertCircle, Mail } from 'lucide-react';
import { verifyEmail } from '../services/authService';
import { extractError } from '../api';
import { Spinner } from '../components/Spinner';
import { motion } from 'framer-motion';

export function VerifyEmailPage() {
  const { token } = useParams<{ token: string }>();
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!token) { setStatus('error'); setMessage('No verification token provided'); return; }
    verifyEmail(token)
      .then(() => { setStatus('success'); })
      .catch((err) => { setStatus('error'); setMessage(extractError(err)); });
  }, [token]);

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
        <div className="text-center mb-8">
          <motion.div
            initial={{ scale: 0, rotate: -180 }}
            animate={{ scale: 1, rotate: 0 }}
            transition={{ type: 'spring', damping: 15, stiffness: 200, delay: 0.2 }}
            className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-brand-600 mb-4 shadow-lg"
          >
            <span className="text-white font-bold text-2xl">P</span>
          </motion.div>
          <h1 className="text-2xl font-bold text-white">Email Verification</h1>
          <p className="mt-1 text-sm text-slate-400">Confirming your email address</p>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}
          className="bg-white rounded-2xl shadow-xl p-8"
        >
          {status === 'loading' && (
            <div className="flex flex-col items-center py-8">
              <Spinner size="lg" />
              <p className="mt-4 text-sm text-slate-500">Verifying your email…</p>
            </div>
          )}

          {status === 'success' && (
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
              <h3 className="text-lg font-semibold text-slate-900">Email Verified</h3>
              <p className="mt-2 text-sm text-slate-500">Your email has been confirmed. You can now sign in.</p>
              <Link to="/login" className="btn-primary w-full mt-6">Go to sign in</Link>
            </motion.div>
          )}

          {status === 'error' && (
            <motion.div
              initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }}
              className="text-center py-4"
            >
              <motion.div
                initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: 'spring', damping: 15, delay: 0.2 }}
                className="w-14 h-14 rounded-full bg-red-100 flex items-center justify-center mx-auto mb-4"
              >
                <AlertCircle className="w-7 h-7 text-red-600" />
              </motion.div>
              <h3 className="text-lg font-semibold text-slate-900">Verification Failed</h3>
              <p className="mt-2 text-sm text-slate-500">{message || 'The verification link may have expired.'}</p>
              <Link to="/login" className="btn-secondary w-full mt-6">Back to sign in</Link>
            </motion.div>
          )}
        </motion.div>
      </motion.div>
    </div>
  );
}
