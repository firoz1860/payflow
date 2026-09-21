import { useState } from 'react';
import { Users, Shield, Search, UserCog, Crown, Code, Briefcase, Headphones } from 'lucide-react';
import { assignRole } from '../services/authService';
import { PageHeader } from '../components/PageHeader';
import { CardSpinner, EmptyState } from '../components/Spinner';
import { toast } from '../components/Toast';
import { extractError } from '../api';
import { motion, AnimatePresence } from 'framer-motion';
import type { RoleName, User } from '../types';

const ROLES: { value: RoleName; label: string; icon: any; desc: string; color: string }[] = [
  { value: 'PAYFLOW_ADMIN', label: 'Platform Admin', icon: Crown, desc: 'Full platform access', color: 'text-red-600 bg-red-50' },
  { value: 'MERCHANT_OWNER', label: 'Merchant Owner', icon: Shield, desc: 'Full merchant access', color: 'text-brand-600 bg-brand-50' },
  { value: 'MERCHANT_DEVELOPER', label: 'Developer', icon: Code, desc: 'API keys and payments', color: 'text-emerald-600 bg-emerald-50' },
  { value: 'MERCHANT_FINANCE', label: 'Finance', icon: Briefcase, desc: 'Ledger and settlements', color: 'text-amber-600 bg-amber-50' },
  { value: 'MERCHANT_SUPPORT', label: 'Support', icon: Headphones, desc: 'Read-only access', color: 'text-slate-600 bg-slate-50' },
];

export function AdminRolesPage() {
  const [userId, setUserId] = useState('');
  const [selectedRole, setSelectedRole] = useState<RoleName | null>(null);
  const [assigning, setAssigning] = useState(false);
  const [assignedUser, setAssignedUser] = useState<User | null>(null);

  const handleAssign = async () => {
    if (!userId || !selectedRole) return;
    setAssigning(true);
    try {
      const user = await assignRole(userId, selectedRole);
      setAssignedUser(user);
      toast('success', `Role assigned: ${ROLES.find(r => r.value === selectedRole)?.label}`);
      setUserId('');
      setSelectedRole(null);
    } catch (err) {
      toast('error', extractError(err));
    } finally {
      setAssigning(false);
    }
  };

  return (
    <>
      <PageHeader title="User Roles" description="Assign roles to users in your organization" />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <motion.div
          initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}
          className="card p-6 lg:col-span-2"
        >
          <h3 className="text-sm font-semibold text-slate-900 mb-4">Assign a Role</h3>

          <div className="space-y-5">
            <div>
              <label className="label">User ID</label>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                <input
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  placeholder="Enter user UUID (e.g. 8f14e45f-…)"
                  className="input pl-10 font-mono text-sm"
                />
              </div>
            </div>

            <div>
              <label className="label">Select Role</label>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {ROLES.map((r, i) => {
                  const Icon = r.icon;
                  const selected = selectedRole === r.value;
                  return (
                    <motion.button
                      key={r.value}
                      type="button"
                      initial={{ opacity: 0, scale: 0.9 }}
                      animate={{ opacity: 1, scale: 1 }}
                      transition={{ delay: i * 0.05 }}
                      whileHover={{ y: -2 }}
                      whileTap={{ scale: 0.97 }}
                      onClick={() => setSelectedRole(r.value)}
                      className={`relative p-4 rounded-xl border-2 text-left transition-all overflow-hidden ${
                        selected ? 'border-brand-500 bg-brand-50' : 'border-slate-200 hover:border-slate-300'
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <div className={`w-9 h-9 rounded-lg flex items-center justify-center ${r.color}`}>
                          <Icon className="w-5 h-5" />
                        </div>
                        <div>
                          <p className="text-sm font-medium text-slate-900">{r.label}</p>
                          <p className="text-xs text-slate-500">{r.desc}</p>
                        </div>
                      </div>
                    </motion.button>
                  );
                })}
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={handleAssign}
                disabled={!userId || !selectedRole || assigning}
                className="btn-primary"
              >
                {assigning ? 'Assigning…' : 'Assign Role'}
              </button>
            </div>
          </div>

          <AnimatePresence>
            {assignedUser && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                className="mt-5 p-4 rounded-lg bg-emerald-50 border border-emerald-200"
              >
                <div className="flex items-start gap-3">
                  <UserCog className="w-5 h-5 text-emerald-600 flex-shrink-0 mt-0.5" />
                  <div className="flex-1">
                    <p className="text-sm font-medium text-emerald-900">{assignedUser.fullName}</p>
                    <p className="text-xs text-emerald-700 mt-0.5">{assignedUser.email}</p>
                    <div className="flex flex-wrap gap-1.5 mt-2">
                      {assignedUser.roles.map((r) => (
                        <span key={r} className="badge bg-emerald-100 text-emerald-700 text-xs">{r}</span>
                      ))}
                    </div>
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1, duration: 0.3 }}
          className="card p-6"
        >
          <h3 className="text-sm font-semibold text-slate-900 mb-4 flex items-center gap-2">
            <Users className="w-4 h-4" /> Role Permissions
          </h3>
          <div className="space-y-3">
            {ROLES.map((r) => (
              <div key={r.value} className="flex items-start gap-3 p-3 rounded-lg bg-slate-50">
                <div className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 ${r.color}`}>
                  <r.icon className="w-4 h-4" />
                </div>
                <div>
                  <p className="text-sm font-medium text-slate-900">{r.label}</p>
                  <p className="text-xs text-slate-500 mt-0.5">{r.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </motion.div>
      </div>
    </>
  );
}
