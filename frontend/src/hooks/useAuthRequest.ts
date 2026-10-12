import { useEffect, useRef, useState } from 'react';
import type { AuthPhase, AuthRequestOptions } from '../services/authReadiness';

export function useAuthRequest() {
  const controller = useRef<AbortController | null>(null);
  const mounted = useRef(true);
  const [kind, setKind] = useState<'guest' | 'account' | null>(null);
  const [phase, setPhase] = useState<AuthPhase>();
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; controller.current?.abort(); };
  }, []);

  async function run<T>(action: 'guest' | 'account', request: (options: AuthRequestOptions) => Promise<T>): Promise<T | undefined> {
    if (controller.current) return undefined;
    const current = new AbortController();
    controller.current = current;
    setKind(action);
    setPhase('starting');
    try {
      const result = await request({ signal: current.signal,
        onProgress: next => { if (mounted.current && !current.signal.aborted) setPhase(next); } });
      return mounted.current && !current.signal.aborted ? result : undefined;
    } catch (error) {
      if (!current.signal.aborted) throw error;
      return undefined;
    } finally {
      if (controller.current === current) controller.current = null;
      if (mounted.current) { setKind(null); setPhase(undefined); }
    }
  }
  return { run, kind, phase, pending: kind !== null, cancel: () => controller.current?.abort() };
}
