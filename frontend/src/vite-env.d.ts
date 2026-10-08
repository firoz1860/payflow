/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Absolute base URL of the deployed PayFlow API gateway, including the
   * `/api/v1` suffix — e.g. `https://payflow-gateway.onrender.com/api/v1`.
   *
   * Leave unset in local development so requests fall back to the relative
   * `/api/v1` path, which the Vite dev server proxies to http://localhost:8000.
   */
  readonly VITE_API_URL?: string;
  /** Optional local Vite proxy target. Defaults to http://localhost:8000. */
  readonly VITE_DEV_API_PROXY?: string;
  /**
   * Optional published Spline scene URL (*.splinecode) for the landing / auth
   * hero. When unset, the hero renders its built-in animated fallback.
   */
  readonly VITE_SPLINE_SCENE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
