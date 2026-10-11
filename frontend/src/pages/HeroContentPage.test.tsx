import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { HeroContentPage } from './HeroContentPage';
import { DEFAULT_HERO_CONTENT } from '../services/heroContentService';
const mocks = vi.hoisted(() => ({ getHeroContent: vi.fn(), saveHeroContent: vi.fn() }));
vi.mock('../services/heroContentService', async importOriginal => ({
  ...await importOriginal<typeof import('../services/heroContentService')>(), ...mocks,
}));
beforeEach(() => { vi.clearAllMocks(); mocks.getHeroContent.mockResolvedValue({ ...DEFAULT_HERO_CONTENT, version: 7 }); });
afterEach(cleanup);
function open() { render(<MemoryRouter><HeroContentPage /></MemoryRouter>); }
test('publishes edits with the loaded version and uses the next version for another save', async () => {
  mocks.saveHeroContent.mockImplementation(async content => ({ ...content, version: content.version + 1 }));
  open();
  const headline = await screen.findByLabelText('Headline');
  fireEvent.change(headline, { target: { value: 'My new headline' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save and publish' }));
  await screen.findByRole('status');
  expect(mocks.saveHeroContent).toHaveBeenCalledWith(expect.objectContaining({ title: 'My new headline', version: 7 }));
  fireEvent.change(headline, { target: { value: 'Second edit' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save and publish' }));
  await waitFor(() => expect(mocks.saveHeroContent).toHaveBeenLastCalledWith(expect.objectContaining({ title: 'Second edit', version: 8 })));
});
test('preserves the draft when publishing fails and reloads only on explicit action', async () => {
  mocks.saveHeroContent.mockRejectedValue(new Error('Content changed; reload before saving'));
  open();
  const headline = await screen.findByLabelText('Headline');
  fireEvent.change(headline, { target: { value: 'Keep my draft' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save and publish' }));
  await screen.findByRole('alert');
  expect(headline).toHaveValue('Keep my draft');
  expect(mocks.getHeroContent).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: 'Reload saved content' }));
  await waitFor(() => expect(screen.getByLabelText('Headline')).toHaveValue(DEFAULT_HERO_CONTENT.title));
});
