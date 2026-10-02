import api from '../api';
import type { UserOnboarding } from '../types';

export type OnboardingAction = 'START' | 'ADVANCE' | 'DISMISS' | 'RESUME' | 'COMPLETE';

export async function getOnboarding() {
  const res = await api.get<UserOnboarding>('/auth/me/onboarding');
  return res.data;
}

export async function updateOnboarding(action: OnboardingAction, step?: number) {
  const res = await api.patch<UserOnboarding>('/auth/me/onboarding', {
    action,
    ...(step !== undefined ? { step } : {}),
  });
  return res.data;
}
