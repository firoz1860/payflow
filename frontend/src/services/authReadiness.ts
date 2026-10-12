import api from '../api';

export type AuthPhase = 'starting' | 'submitting';
export type AuthRequestOptions = {
  signal?: AbortSignal;
  onProgress?: (phase: AuthPhase) => void;
};

function cancelled() { return new DOMException('Sign-in cancelled', 'AbortError'); }
function checkCancelled(signal?: AbortSignal) { if (signal?.aborted) throw cancelled(); }
function pause(milliseconds: number, signal?: AbortSignal) {
  checkCancelled(signal);
  return new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => { signal?.removeEventListener('abort', abort); resolve(); }, milliseconds);
    const abort = () => { clearTimeout(timer); signal?.removeEventListener('abort', abort); reject(cancelled()); };
    signal?.addEventListener('abort', abort, { once: true });
  });
}

export async function waitForAuthReady(options: AuthRequestOptions = {}) {
  const deadline = Date.now() + 8 * 60 * 1000;
  while (Date.now() < deadline) {
    checkCancelled(options.signal);
    try {
      const response = await api.get<{ ready: boolean }>('/auth/readiness', {
        signal: options.signal, timeout: Math.min(5000, deadline - Date.now()),
      });
      checkCancelled(options.signal);
      if (response.data?.ready === true && Date.now() < deadline) {
        options.onProgress?.('submitting');
        return;
      }
    } catch {
      checkCancelled(options.signal);
      // Only the read-only startup check is retried. Never replay an auth POST.
    }
    options.onProgress?.('starting');
    await pause(Math.min(5000, Math.max(0, deadline - Date.now())), options.signal);
  }
  throw new Error('Sign-in services are taking longer than expected. Please try again shortly.');
}
