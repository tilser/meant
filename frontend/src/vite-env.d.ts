/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_MEANT_API_URL?: string
  readonly VITE_SUPABASE_URL?: string
  readonly VITE_SUPABASE_ANON_KEY?: string
  readonly VITE_EMBEDDED_CHECKOUT_ENABLED?: string
  readonly VITE_CHECKOUT_KIT_DEBUG?: string
  readonly VITE_POSTHOG_ENABLED?: string
  readonly VITE_POSTHOG_PROJECT_TOKEN?: string
  readonly VITE_POSTHOG_HOST?: string
  readonly VITE_POSTHOG_SESSION_REPLAY_ENABLED?: string
  readonly VITE_POSTHOG_SESSION_REPLAY_SAMPLE_RATE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
