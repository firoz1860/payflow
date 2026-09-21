import { useEffect, useState } from 'react';
import { Plus, KeyRound, Copy, CheckCircle2, AlertTriangle, Trash2 } from 'lucide-react';
import { listApiKeys, createApiKey, revokeApiKey } from '../services/merchantService';
import { PageHeader } from '../components/PageHeader';
import { Modal } from '../components/Modal';
import { CardSpinner, EmptyState } from '../components/Spinner';
import { toast } from '../components/Toast';
import { extractError } from '../api';
import { formatDateTime, timeAgo } from '../lib/utils';
import { motion, AnimatePresence } from 'framer-motion';
import type { ApiKey, CreateApiKeyResponse } from '../types';

export function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [newKey, setNewKey] = useState<CreateApiKeyResponse | null>(null);
  const [copied, setCopied] = useState(false);
  const [form, setForm] = useState({ environment: 'TEST', keyType: 'SECRET', label: '' });
  const [creating, setCreating] = useState(false);
  const [revokingId, setRevokingId] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    try { setKeys(await listApiKeys()); }
    catch { setKeys([]); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = async () => {
    setCreating(true);
    try {
      const res = await createApiKey({
        environment: form.environment, keyType: form.keyType, label: form.label || undefined,
      });
      setNewKey(res);
      setCreateOpen(false);
      setForm({ environment: 'TEST', keyType: 'SECRET', label: '' });
      toast('success', 'API key created');
      load();
    } catch (err) { toast('error', extractError(err)); }
    finally { setCreating(false); }
  };

  const handleRevoke = async (keyId: string) => {
    if (!confirm('Revoke this API key? This cannot be undone.')) return;
    setRevokingId(keyId);
    try { await revokeApiKey(keyId); toast('success', 'API key revoked'); load(); }
    catch (err) { toast('error', extractError(err)); }
    finally { setRevokingId(null); }
  };

  const copySecret = () => {
    navigator.clipboard.writeText(newKey?.secret || '');
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <>
      <PageHeader title="API Keys" description="Manage your TEST and LIVE API keys"
        actions={
          <button onClick={() => setCreateOpen(true)} className="btn-primary">
            <Plus className="w-4 h-4" /> Create Key
          </button>
        }
      />

      <div className="card overflow-hidden">
        {loading ? <CardSpinner /> : keys.length === 0 ? (
          <EmptyState icon={<KeyRound className="w-6 h-6" />} title="No API keys yet"
            description="Create an API key to start integrating payments"
            action={<button onClick={() => setCreateOpen(true)} className="btn-primary"><Plus className="w-4 h-4" /> Create Key</button>} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-slate-50">
                <tr className="text-xs text-slate-500 uppercase tracking-wider">
                  <th className="text-left font-medium px-6 py-3">Label</th>
                  <th className="text-left font-medium px-6 py-3">Key</th>
                  <th className="text-left font-medium px-6 py-3">Environment</th>
                  <th className="text-left font-medium px-6 py-3">Type</th>
                  <th className="text-left font-medium px-6 py-3">Status</th>
                  <th className="text-left font-medium px-6 py-3">Created</th>
                  <th className="text-left font-medium px-6 py-3">Last Used</th>
                  <th className="text-right font-medium px-6 py-3">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                <AnimatePresence>
                  {keys.map((k, i) => (
                    <motion.tr
                      key={k.keyId}
                      initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, x: -20 }}
                      transition={{ delay: i * 0.05, duration: 0.2 }}
                      className="table-row-hover"
                    >
                      <td className="px-6 py-4 text-sm font-medium text-slate-900">{k.label || '—'}</td>
                      <td className="px-6 py-4"><span className="font-mono text-sm text-slate-600">{k.maskedKey}</span></td>
                      <td className="px-6 py-4">
                        <span className={`badge ${k.environment === 'LIVE' ? 'bg-red-100 text-red-700' : 'bg-amber-100 text-amber-700'}`}>{k.environment}</span>
                      </td>
                      <td className="px-6 py-4 text-sm text-slate-600">{k.keyType}</td>
                      <td className="px-6 py-4">
                        <span className={`badge ${k.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>{k.status}</span>
                      </td>
                      <td className="px-6 py-4 text-sm text-slate-500">{formatDateTime(k.createdAt)}</td>
                      <td className="px-6 py-4 text-sm text-slate-500">{k.lastUsedAt ? timeAgo(k.lastUsedAt) : 'Never'}</td>
                      <td className="px-6 py-4 text-right">
                        <motion.button
                          whileTap={{ scale: 0.9 }}
                          onClick={() => handleRevoke(k.keyId)} disabled={revokingId === k.keyId}
                          className="text-red-500 hover:text-red-700 p-1.5 rounded-lg hover:bg-red-50 transition disabled:opacity-50"
                          title="Revoke"
                        ><Trash2 className="w-4 h-4" /></motion.button>
                      </td>
                    </motion.tr>
                  ))}
                </AnimatePresence>
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Create API Key" size="sm">
        <div className="space-y-4">
          <div>
            <label className="label">Label (optional)</label>
            <input value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })}
              placeholder="Production server" className="input" />
          </div>
          <div>
            <label className="label">Environment</label>
            <select value={form.environment} onChange={(e) => setForm({ ...form, environment: e.target.value })} className="input">
              <option value="TEST">Test</option>
              <option value="LIVE">Live</option>
            </select>
          </div>
          <div>
            <label className="label">Key Type</label>
            <select value={form.keyType} onChange={(e) => setForm({ ...form, keyType: e.target.value })} className="input">
              <option value="SECRET">Secret (sk_)</option>
              <option value="PUBLISHABLE">Publishable (pk_)</option>
            </select>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button onClick={() => setCreateOpen(false)} className="btn-secondary">Cancel</button>
            <button onClick={handleCreate} disabled={creating} className="btn-primary">
              {creating ? 'Creating…' : 'Create Key'}
            </button>
          </div>
        </div>
      </Modal>

      {newKey && (
        <Modal open={!!newKey} onClose={() => { setNewKey(null); setCopied(false); }} title="API Key Created" size="md">
          <div className="space-y-4">
            <motion.div
              initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} transition={{ type: 'spring', damping: 20 }}
              className="flex items-start gap-3 p-4 rounded-lg bg-amber-50 border border-amber-200"
            >
              <AlertTriangle className="w-5 h-5 text-amber-500 flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-medium text-amber-900">Save this key now</p>
                <p className="text-xs text-amber-700 mt-1">The secret is shown only once. You won't be able to see it again.</p>
              </div>
            </motion.div>

            <div>
              <label className="label">API Key Secret</label>
              <div className="flex gap-2">
                <input readOnly value={newKey.secret} className="input font-mono text-sm bg-slate-50" />
                <motion.button whileTap={{ scale: 0.95 }} onClick={copySecret} className="btn-secondary flex-shrink-0">
                  {copied ? <CheckCircle2 className="w-4 h-4 text-emerald-500" /> : <Copy className="w-4 h-4" />}
                </motion.button>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <p className="text-xs text-slate-500">Key ID</p>
                <p className="font-mono text-slate-900">{newKey.keyId}</p>
              </div>
              <div>
                <p className="text-xs text-slate-500">Environment</p>
                <p className="text-slate-900">{newKey.environment}</p>
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <button onClick={() => { setNewKey(null); setCopied(false); }} className="btn-primary">Done</button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}
