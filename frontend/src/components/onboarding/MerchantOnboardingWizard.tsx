import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  BarChart3,
  BookOpen,
  CheckCircle2,
  Code2,
  CreditCard,
  KeyRound,
  Loader2,
  QrCode,
  Settings,
  Store,
  Webhook,
} from 'lucide-react';
import { AnimatePresence, motion } from 'framer-motion';
import { Modal } from '../Modal';
import { toast } from '../Toast';
import { extractError } from '../../api';
import { getMyMerchant, listApiKeys, updateMyMerchant } from '../../services/merchantService';
import { listPayments } from '../../services/paymentService';
import { updateOnboarding } from '../../services/onboardingService';
import { useAuthStore } from '../../store/auth';
import type { ApiKey, Merchant, UserOnboarding } from '../../types';
import { OnboardingProgress } from './OnboardingProgress';
import { OnboardingStep } from './OnboardingStep';
import { ApiKeySetupStep } from './ApiKeySetupStep';
import { FirstPaymentStep } from './FirstPaymentStep';

const TOTAL_STEPS = 5;

export function MerchantOnboardingWizard({
  open,
  initialOnboarding,
  tutorialMode = false,
  onClose,
  onOnboardingChange,
}: {
  open: boolean;
  initialOnboarding: UserOnboarding;
  tutorialMode?: boolean;
  onClose: () => void;
  onOnboardingChange: (state: UserOnboarding) => void;
}) {
  const user = useAuthStore((state) => state.user);
  const [step, setStep] = useState(1);
  const [direction, setDirection] = useState<'forward' | 'backward'>('forward');
  const [merchant, setMerchant] = useState<Merchant | null>(null);
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [hasPayment, setHasPayment] = useState(false);
  const [loadingData, setLoadingData] = useState(false);
  const [merchantError, setMerchantError] = useState<string | null>(null);
  const [keyError, setKeyError] = useState<string | null>(null);
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [secretPending, setSecretPending] = useState(false);
  const [savingStep, setSavingStep] = useState(false);
  const [finished, setFinished] = useState(false);
  const [editingProfile, setEditingProfile] = useState(false);
  const [profileForm, setProfileForm] = useState({ businessName: '', phone: '', defaultCurrency: '' });
  const [savingProfile, setSavingProfile] = useState(false);

  const canManageKeys = !!user?.permissions.includes('api_keys:manage');
  const canCreatePayments = !!user?.permissions.includes('payments:create');

  const loadData = useCallback(async () => {
    setLoadingData(true);
    const [merchantResult, keyResult, paymentResult] = await Promise.allSettled([
      getMyMerchant(),
      canManageKeys ? listApiKeys() : Promise.resolve([] as ApiKey[]),
      listPayments({ page: 0, size: 1 }),
    ]);

    if (merchantResult.status === 'fulfilled') {
      setMerchant(merchantResult.value);
      setProfileForm({
        businessName: merchantResult.value.businessName || '',
        phone: merchantResult.value.phone || '',
        defaultCurrency: merchantResult.value.defaultCurrency || '',
      });
      setMerchantError(null);
    } else {
      setMerchantError(extractError(merchantResult.reason));
    }

    if (keyResult.status === 'fulfilled') {
      setKeys(keyResult.value);
      setKeyError(null);
    } else {
      setKeyError(extractError(keyResult.reason));
    }

    if (paymentResult.status === 'fulfilled') {
      setHasPayment((paymentResult.value.data || []).length > 0);
      setPaymentError(null);
    } else {
      setPaymentError(extractError(paymentResult.reason));
    }
    setLoadingData(false);
  }, [canManageKeys]);

  const refreshKeys = useCallback(async () => {
    if (!canManageKeys) return;
    try {
      setKeys(await listApiKeys());
      setKeyError(null);
    } catch (error) {
      setKeyError(extractError(error));
      throw error;
    }
  }, [canManageKeys]);

  useEffect(() => {
    if (!open) return;
    setFinished(false);
    setSecretPending(false);
    setDirection('forward');
    const initialStep = tutorialMode
      ? 1
      : Math.min(TOTAL_STEPS, Math.max(1, initialOnboarding.lastStep || 1));
    setStep(initialStep);
    loadData();

    if (!tutorialMode && initialOnboarding.status === 'NOT_STARTED') {
      updateOnboarding('START')
        .then(onOnboardingChange)
        .catch((error) => toast('error', extractError(error)));
    } else if (!tutorialMode && initialOnboarding.status === 'DISMISSED') {
      updateOnboarding('RESUME')
        .then(onOnboardingChange)
        .catch((error) => toast('error', extractError(error)));
    }
  }, [open, tutorialMode]);

  const businessReady = !!merchant?.businessName && !!merchant?.merchantCode &&
    !!merchant?.email && !!merchant?.country && !!merchant?.defaultCurrency;
  const testKeyReady = keys.some((key) =>
    key.environment === 'TEST' && key.keyType === 'SECRET' && key.status === 'ACTIVE'
  );

  const persistStep = async (next: number) => {
    if (tutorialMode) return;
    const state = await updateOnboarding('ADVANCE', next);
    onOnboardingChange(state);
  };

  const next = async () => {
    if (step >= TOTAL_STEPS) return;
    if (step === 3 && secretPending) {
      toast('warning', 'Save and acknowledge the new API key before continuing');
      return;
    }
    const nextStep = step + 1;
    setSavingStep(true);
    try {
      await persistStep(nextStep);
      setDirection('forward');
      setStep(nextStep);
    } catch (error) {
      toast('error', extractError(error));
    } finally {
      setSavingStep(false);
    }
  };

  const back = () => {
    if (step <= 1) return;
    setDirection('backward');
    setStep((value) => value - 1);
  };

  const requestClose = async () => {
    if (secretPending) {
      const saved = window.confirm("You won't be able to view this secret again. Have you saved it?\n\nCancel = Go back\nOK = I saved it, close");
      if (!saved) return;
      setSecretPending(false);
    }
    if (tutorialMode || initialOnboarding.status === 'COMPLETED') {
      onClose();
      return;
    }
    setSavingStep(true);
    try {
      const state = await updateOnboarding('DISMISS');
      onOnboardingChange(state);
      onClose();
    } catch (error) {
      toast('error', `Could not save onboarding state: ${extractError(error)}`);
    } finally {
      setSavingStep(false);
    }
  };

  const finish = async () => {
    if (tutorialMode) {
      onClose();
      return;
    }
    setSavingStep(true);
    try {
      const state = await updateOnboarding('COMPLETE');
      onOnboardingChange(state);
      setFinished(true);
    } catch (error) {
      toast('error', extractError(error));
    } finally {
      setSavingStep(false);
    }
  };

  const saveProfile = async () => {
    setSavingProfile(true);
    try {
      const updated = await updateMyMerchant({
        businessName: profileForm.businessName.trim() || undefined,
        phone: profileForm.phone.trim() || undefined,
        defaultCurrency: profileForm.defaultCurrency.trim().toUpperCase() || undefined,
      });
      setMerchant(updated);
      setEditingProfile(false);
      toast('success', 'Business profile updated');
    } catch (error) {
      toast('error', extractError(error));
    } finally {
      setSavingProfile(false);
    }
  };

  const featureMap = useMemo(() => [
    ['/payments', 'Payments', 'View all payment attempts and statuses', CreditCard],
    ['/payments/qr', 'QR Payments', 'Generate test QR payment flows', QrCode],
    ['/api-keys', 'API Keys', 'Manage TEST and LIVE integration credentials', KeyRound],
    ['/ledger', 'Ledger', 'Read balanced financial postings', BookOpen],
    ['/analytics', 'Analytics', 'Review actual payment metrics', BarChart3],
    ['/developers', 'Developers', 'Copy integration examples', Code2],
    ['/webhooks', 'Webhooks', 'Provider/webhook integration status', Webhook],
    ['/settings', 'Settings', 'Manage your merchant profile', Settings],
  ] as const, []);

  if (!user) return null;

  return (
    <Modal
      open={open}
      onClose={requestClose}
      title={tutorialMode ? 'PayFlow Setup Guide' : 'Welcome to PayFlow'}
      size="xl"
    >
      {finished ? (
        <motion.div
          initial={{ opacity: 0, scale: 0.98 }}
          animate={{ opacity: 1, scale: 1 }}
          className="py-10 text-center"
          aria-live="polite"
        >
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-700">
            <CheckCircle2 className="h-8 w-8" />
          </div>
          <h3 className="mt-5 text-2xl font-bold text-slate-950">You're ready to use PayFlow</h3>
          <p className="mx-auto mt-2 max-w-lg text-sm leading-6 text-slate-600">
            Your TEST environment is ready. You can return to this guide anytime from Help.
          </p>
          <button type="button" onClick={onClose} className="btn-primary mt-6">Go to dashboard</button>
        </motion.div>
      ) : (
        <div className="flex max-h-[calc(100vh-10rem)] min-h-[34rem] flex-col">
          <div className="shrink-0 pb-5">
            <div className="mb-4 flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-blue-600 to-indigo-700 text-white shadow-sm">
                <span className="text-lg font-black">P</span>
              </div>
              <div>
                <p className="text-sm font-semibold text-slate-950">Merchant setup</p>
                <p className="text-xs text-slate-500">{tutorialMode ? 'Tutorial replay — completion state will not reset' : 'Persisted securely to your PayFlow account'}</p>
              </div>
            </div>
            <OnboardingProgress step={step} total={TOTAL_STEPS} />
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto pr-1">
            {loadingData && step > 1 ? (
              <div className="flex min-h-72 items-center justify-center text-sm text-slate-500">
                <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading your PayFlow setup…
              </div>
            ) : (
              <AnimatePresence mode="wait">
                <OnboardingStep key={step} direction={direction}>
                  {step === 1 && (
                    <WelcomeStep />
                  )}

                  {step === 2 && (
                    <div className="space-y-5">
                      <StepHeading title="Business profile" description="This information comes from your real Merchant Service record." />
                      {merchantError ? (
                        <RetryState message="Could not load merchant profile" detail={merchantError} onRetry={loadData} />
                      ) : merchant ? (
                        <>
                          <div className="rounded-2xl border border-slate-200 bg-white/65 p-5">
                            {businessReady && (
                              <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-emerald-700">
                                <CheckCircle2 className="h-4 w-4" /> Business profile ready
                              </div>
                            )}
                            {editingProfile ? (
                              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                                <Field label="Business name">
                                  <input className="input" value={profileForm.businessName} onChange={(e) => setProfileForm({ ...profileForm, businessName: e.target.value })} />
                                </Field>
                                <Field label="Phone">
                                  <input className="input" value={profileForm.phone} onChange={(e) => setProfileForm({ ...profileForm, phone: e.target.value })} />
                                </Field>
                                <Field label="Default currency">
                                  <input className="input" maxLength={3} value={profileForm.defaultCurrency} onChange={(e) => setProfileForm({ ...profileForm, defaultCurrency: e.target.value.toUpperCase() })} />
                                </Field>
                                <div className="flex items-end gap-2">
                                  <button type="button" onClick={() => setEditingProfile(false)} className="btn-secondary">Cancel</button>
                                  <button type="button" onClick={saveProfile} disabled={savingProfile} className="btn-primary">
                                    {savingProfile ? 'Saving…' : 'Save profile'}
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <>
                                <dl className="grid grid-cols-1 gap-4 text-sm sm:grid-cols-2">
                                  <Meta label="Business name" value={merchant.businessName} />
                                  <Meta label="Merchant code" value={merchant.merchantCode} mono />
                                  <Meta label="Email" value={merchant.email} />
                                  <Meta label="Country" value={merchant.country} />
                                  <Meta label="Default currency" value={merchant.defaultCurrency} />
                                  <Meta label="Account status" value={merchant.status} />
                                  <Meta label="TEST environment" value="Available" />
                                  <Meta label="LIVE environment" value={merchant.liveModeEnabled ? 'Enabled' : 'Not enabled yet'} />
                                </dl>
                                {user.permissions.includes('merchant:manage') && !tutorialMode && (
                                  <button type="button" onClick={() => setEditingProfile(true)} className="btn-secondary mt-5">Edit business profile</button>
                                )}
                              </>
                            )}
                          </div>
                        </>
                      ) : null}
                    </div>
                  )}

                  {step === 3 && (
                    <div className="space-y-5">
                      <StepHeading title="Your first TEST API key" description="Create a server-side TEST credential only when you are ready to save its one-time secret." />
                      {keyError ? (
                        <RetryState message="Could not load API keys" detail={keyError} onRetry={refreshKeys} />
                      ) : (
                        <ApiKeySetupStep
                          keys={keys}
                          canManage={canManageKeys}
                          tutorialMode={tutorialMode}
                          onKeysChanged={refreshKeys}
                          onSecretPendingChange={setSecretPending}
                        />
                      )}
                    </div>
                  )}

                  {step === 4 && (
                    <div className="space-y-5">
                      <StepHeading title="Create your first test payment" description="Use the sandbox to verify your integration without moving real money." />
                      {paymentError ? (
                        <RetryState message="Could not load payments" detail={paymentError} onRetry={loadData} />
                      ) : (
                        <FirstPaymentStep
                          hasPayment={hasPayment}
                          canCreate={canCreatePayments}
                          onOpenCreatePayment={() => window.open('/payments/create', '_blank', 'noopener,noreferrer')}
                        />
                      )}
                    </div>
                  )}

                  {step === 5 && (
                    <div className="space-y-5">
                      <StepHeading title="Where everything lives" description="Use these areas as your map of the PayFlow platform." />
                      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                        {featureMap.map(([path, title, description, Icon]) => (
                          <div key={path} className="rounded-2xl border border-slate-200 bg-white/65 p-4">
                            <div className="flex items-start gap-3">
                              <div className="rounded-xl bg-blue-100 p-2 text-blue-700"><Icon className="h-4 w-4" /></div>
                              <div className="min-w-0 flex-1">
                                <p className="font-semibold text-slate-950">{title}</p>
                                <p className="mt-1 text-xs leading-5 text-slate-500">{description}</p>
                                <button
                                  type="button"
                                  onClick={() => window.open(path, '_blank', 'noopener,noreferrer')}
                                  className="mt-2 text-xs font-semibold text-blue-700 hover:text-blue-800"
                                >
                                  Open page →
                                </button>
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </OnboardingStep>
              </AnimatePresence>
            )}
          </div>

          <div className="sticky bottom-0 mt-5 flex shrink-0 flex-wrap items-center justify-between gap-3 border-t border-slate-200 bg-white/90 pt-4 backdrop-blur">
            <button type="button" onClick={back} disabled={step === 1 || savingStep} className="btn-secondary disabled:opacity-40">
              Back
            </button>
            <div className="flex flex-wrap items-center gap-2">
              <button type="button" onClick={requestClose} disabled={savingStep} className="btn-secondary">
                {tutorialMode ? 'Close guide' : "I'll do this later"}
              </button>
              {step < TOTAL_STEPS ? (
                <motion.button
                  type="button"
                  whileTap={{ scale: 0.97 }}
                  onClick={next}
                  disabled={savingStep || (step === 3 && secretPending)}
                  className="btn-primary disabled:opacity-50"
                >
                  {savingStep ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                  {step === 1 ? 'Got it, continue' : 'Continue'}
                </motion.button>
              ) : (
                <motion.button
                  type="button"
                  whileTap={{ scale: 0.97 }}
                  onClick={finish}
                  disabled={savingStep}
                  className="btn-primary"
                >
                  {savingStep ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
                  {tutorialMode ? 'Close guide' : 'Finish setup'}
                </motion.button>
              )}
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}

function WelcomeStep() {
  const features = [
    ['Payments', CreditCard],
    ['QR Payments', QrCode],
    ['API Keys', KeyRound],
    ['Analytics', BarChart3],
    ['Ledger', BookOpen],
    ['Developers', Code2],
  ] as const;
  return (
    <div className="space-y-6">
      <div>
        <h3 className="text-2xl font-bold text-slate-950">Welcome to PayFlow</h3>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
          PayFlow gives your business one place to create payments, manage API keys, track payment status, inspect ledger activity, and integrate through APIs.
        </p>
      </div>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {features.map(([label, Icon]) => (
          <div key={label} className="rounded-2xl border border-slate-200 bg-white/65 p-4">
            <Icon className="h-5 w-5 text-blue-700" />
            <p className="mt-2 text-sm font-semibold text-slate-900">{label}</p>
          </div>
        ))}
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="rounded-2xl border border-amber-200 bg-amber-50/70 p-4">
          <p className="text-sm font-semibold text-amber-900">TEST environment</p>
          <p className="mt-1 text-xs leading-5 text-amber-800">Safe integration and sandbox payment testing. No real money.</p>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
          <p className="text-sm font-semibold text-slate-900">LIVE environment</p>
          <p className="mt-1 text-xs leading-5 text-slate-600">Real production payments only after live mode is explicitly enabled.</p>
        </div>
      </div>
    </div>
  );
}

function StepHeading({ title, description }: { title: string; description: string }) {
  return (
    <div>
      <h3 className="text-xl font-bold text-slate-950">{title}</h3>
      <p className="mt-1 text-sm leading-6 text-slate-600">{description}</p>
    </div>
  );
}

function Meta({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className={`mt-1 break-all font-medium text-slate-950 ${mono ? 'font-mono text-xs' : ''}`}>{value || '—'}</dd>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="label">{label}</label>
      {children}
    </div>
  );
}

function RetryState({
  message,
  detail,
  onRetry,
}: {
  message: string;
  detail: string;
  onRetry: () => void | Promise<void>;
}) {
  return (
    <div className="rounded-2xl border border-rose-200 bg-rose-50/70 p-5">
      <p className="font-semibold text-rose-900">{message}</p>
      <p className="mt-1 text-sm text-rose-700">{detail}</p>
      <button type="button" onClick={() => onRetry()} className="btn-secondary mt-4">Retry</button>
    </div>
  );
}
