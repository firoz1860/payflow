import type { ReactNode } from 'react';
import { motion, useReducedMotion } from 'framer-motion';

export function OnboardingStep({
  direction,
  children,
}: {
  direction: 'forward' | 'backward';
  children: ReactNode;
}) {
  const reduceMotion = useReducedMotion();
  return (
    <motion.div
      initial={reduceMotion ? { opacity: 1 } : { opacity: 0, x: direction === 'forward' ? 30 : -30 }}
      animate={{ opacity: 1, x: 0 }}
      exit={reduceMotion ? { opacity: 0 } : { opacity: 0, x: direction === 'forward' ? -20 : 20 }}
      transition={{ duration: reduceMotion ? 0 : 0.2 }}
    >
      {children}
    </motion.div>
  );
}
