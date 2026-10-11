import axios from 'axios';
import api, { API_BASE_URL } from '../api';

export interface HeroContent {
  version: number;
  badge: string;
  title: string;
  accent: string;
  description: string;
  supportingText: string;
  primaryLabel: string;
  secondaryLabel: string;
  trustPoints: string[];
}

export const DEFAULT_HERO_CONTENT: HeroContent = {
  version: 0,
  badge: 'Payment processing with a clear audit trail',
  title: 'Accept payments.',
  accent: 'Keep your records in balance.',
  description: 'PayFlow brings payment collection, transaction tracking, and ledger records into one merchant workspace. Create payments through an API, offer hosted or UPI QR checkout, and follow each transaction from initiation to its recorded outcome.',
  supportingText: 'Built to prevent duplicate payment creation during retries, verify provider updates, and keep financial entries traceable.',
  primaryLabel: 'Create merchant account',
  secondaryLabel: 'See how payments work',
  trustPoints: ['Retry-safe payment APIs', 'Verified provider updates', 'Balanced ledger entries', 'Merchant dashboard'],
};

export async function getPublicHeroContent(signal?: AbortSignal): Promise<HeroContent> {
  const response = await axios.get<HeroContent>(`${API_BASE_URL}/auth/site-content/hero`, {
    signal, timeout: 10000,
  });
  const data = response.data;
  if (!data || !Array.isArray(data.trustPoints) || data.trustPoints.length < 1 || data.trustPoints.length > 4
    || !data.trustPoints.every(p => typeof p === 'string')
    || !['badge', 'title', 'accent', 'description', 'supportingText', 'primaryLabel', 'secondaryLabel']
      .every(field => typeof data[field as keyof HeroContent] === 'string')) {
    throw new Error('Invalid hero content');
  }
  return data;
}

export async function getHeroContent(): Promise<HeroContent> {
  return (await api.get<HeroContent>('/auth/admin/site-content/hero')).data;
}

export async function saveHeroContent(content: HeroContent): Promise<HeroContent> {
  return (await api.put<HeroContent>('/auth/admin/site-content/hero', content)).data;
}
