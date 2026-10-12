import { afterEach, expect, test, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LandingPage } from './LandingPage';
import { DEFAULT_HERO_CONTENT } from '../services/heroContentService';
const mocks = vi.hoisted(() => ({ getPublicHeroContent: vi.fn() }));
vi.mock('../services/heroContentService', async importOriginal => ({
  ...await importOriginal<typeof import('../services/heroContentService')>(), ...mocks,
}));
vi.mock('../components/hero/HeroVisual', () => ({ HeroVisual: () => null }));
vi.mock('../components/landing/LandingNavbar', () => ({ LandingNavbar: () => null }));
vi.mock('../components/landing/LandingFooter', () => ({ LandingFooter: () => null }));
vi.mock('../components/landing/StorySections', () => Object.fromEntries(
  ['ArchitectureMap', 'CoreCapabilities', 'FaqSection', 'FinalCTA', 'HowItWorks', 'Reliability', 'SecuritySection', 'WhoFor', 'WhyPayFlow'].map(name => [name, () => null])
));
afterEach(cleanup);
test('renders published public hero copy as text', async () => {
  mocks.getPublicHeroContent.mockResolvedValue({ ...DEFAULT_HERO_CONTENT, title: 'Published merchant headline', primaryLabel: 'Start today', badge: '<script>alert(1)</script>' });
  const { container } = render(<MemoryRouter><LandingPage /></MemoryRouter>);
  expect(await screen.findByRole('heading', { level: 1 })).toBeInTheDocument();
  await screen.findByText('Published merchant headline', { exact: false });
  expect(screen.getByRole('link', { name: 'Start today' })).toHaveAttribute('href', '/register');
  expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument();
  expect(container.querySelector('script')).toBeNull();
});
test('keeps the default hero visible when the content service is unavailable', async () => {
  mocks.getPublicHeroContent.mockRejectedValue(new Error('Unavailable'));
  render(<MemoryRouter><LandingPage /></MemoryRouter>);
  expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(DEFAULT_HERO_CONTENT.title);
  expect(screen.getByRole('link', { name: DEFAULT_HERO_CONTENT.primaryLabel })).toBeInTheDocument();
});
