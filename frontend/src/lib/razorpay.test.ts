// @vitest-environment jsdom
import { afterEach, expect, test, vi } from 'vitest';
afterEach(() => { document.head.innerHTML = ''; delete window.Razorpay; vi.resetModules(); });
test('concurrent loads use one script and resolve the official constructor', async () => {
  const { loadRazorpay } = await import('./razorpay');
  const first = loadRazorpay(); const second = loadRazorpay();
  expect(document.querySelectorAll('script[src="https://checkout.razorpay.com/v1/checkout.js"]')).toHaveLength(1);
  const constructor = class { open() {} close() {} on() {} };
  window.Razorpay = constructor;
  document.querySelector('script')!.dispatchEvent(new Event('load'));
  expect(await first).toBe(constructor); expect(await second).toBe(constructor);
});
test('script failure removes failed script and allows a fresh retry', async () => {
  const { loadRazorpay } = await import('./razorpay');
  const first = loadRazorpay(); const rejected = expect(first).rejects.toThrow('load');
  const script = document.querySelector('script'); expect(script).not.toBeNull();
  script!.dispatchEvent(new Event('error')); await rejected;
  const retry = loadRazorpay(); expect(document.querySelectorAll('script')).toHaveLength(1);
  window.Razorpay = class { open() {} close() {} on() {} };
  document.querySelector('script')!.dispatchEvent(new Event('load')); expect(await retry).toBe(window.Razorpay);
});
