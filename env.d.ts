/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_BACKEND: string;
  readonly VITE_OIDC_AUTHORITY: string;
  readonly VITE_CLIENT_ID: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
