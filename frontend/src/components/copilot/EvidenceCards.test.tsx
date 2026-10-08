import { describe, expect, test } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EvidenceCards, evidenceKind } from './EvidenceCards';
import type { Evidence } from '../../services/copilotService';

const fact: Evidence = {
  source: 'Payment record',
  resourceType: 'PAYMENT',
  resourceId: 'pay_123',
  verified: true,
};
const inference: Evidence = {
  source: 'Status inference',
  resourceType: 'PAYMENT',
  resourceId: 'pay_123',
  verified: false,
};
const missing: Evidence = {
  source: 'Ledger posting',
  resourceType: 'LEDGER_POSTING',
  resourceId: '',
  verified: false,
};

describe('EvidenceCards', () => {
  test('classifies evidence into FACT / INFERENCE / MISSING', () => {
    expect(evidenceKind(fact)).toBe('FACT');
    expect(evidenceKind(inference)).toBe('INFERENCE');
    expect(evidenceKind(missing)).toBe('MISSING');
  });

  test('renders each evidence kind distinctly', () => {
    const { container } = render(
      <EvidenceCards evidence={[fact, inference, missing]} confidence="MEDIUM" />,
    );

    expect(screen.getByText('Fact')).toBeInTheDocument();
    expect(screen.getByText('Inference')).toBeInTheDocument();
    expect(screen.getByText('Missing')).toBeInTheDocument();
    expect(screen.getByText('Medium confidence')).toBeInTheDocument();

    // Each card is tagged with its kind so the three are visually separable.
    expect(container.querySelector('[data-evidence-kind="FACT"]')).not.toBeNull();
    expect(container.querySelector('[data-evidence-kind="INFERENCE"]')).not.toBeNull();
    expect(container.querySelector('[data-evidence-kind="MISSING"]')).not.toBeNull();
  });

  test('renders warnings as notes', () => {
    render(<EvidenceCards evidence={[]} warnings={['Provider evidence is stale.']} />);
    expect(screen.getByText('Provider evidence is stale.')).toBeInTheDocument();
  });
});
