/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_MEANT_API_URL?: string
  readonly VITE_SUPABASE_URL?: string
  readonly VITE_SUPABASE_ANON_KEY?: string
  readonly VITE_EMBEDDED_CHECKOUT_ENABLED?: string
  readonly VITE_CHECKOUT_KIT_DEBUG?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
