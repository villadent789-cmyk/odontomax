/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Prefijo de la API del backend. Por defecto "/api/v1" (en desarrollo, Vite lo redirige a localhost:8080). */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
