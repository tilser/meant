interface TurnstileApi {
  render(
    container: HTMLElement,
    options: {
      sitekey: string
      action: string
      appearance: 'interaction-only'
      theme: 'auto'
      callback: (token: string) => void
      'error-callback': () => void
      'expired-callback': () => void
      'unsupported-callback': () => void
    },
  ): string
  remove(widgetId: string): void
}

declare global {
  interface Window {
    turnstile?: TurnstileApi
  }
}

const SCRIPT_URL = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
let scriptPromise: Promise<TurnstileApi> | null = null

export type AuthCaptchaAction =
  'anonymous-sign-in' | 'password-sign-in' | 'sign-up' | 'magic-link' | 'password-reset'

function loadTurnstile(): Promise<TurnstileApi> {
  if (window.turnstile) return Promise.resolve(window.turnstile)
  if (scriptPromise) return scriptPromise
  scriptPromise = new Promise<TurnstileApi>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT_URL}"]`)
    const script = existing ?? document.createElement('script')
    const loaded = () => {
      if (window.turnstile) resolve(window.turnstile)
      else reject(new Error('Security verification did not load.'))
    }
    const failed = () => reject(new Error('Security verification could not start.'))
    script.addEventListener('load', loaded, { once: true })
    script.addEventListener('error', failed, { once: true })
    if (!existing) {
      script.src = SCRIPT_URL
      script.defer = true
      script.dataset.meantTurnstile = 'true'
      document.head.append(script)
    }
  }).catch((error) => {
    scriptPromise = null
    throw error
  })
  return scriptPromise
}

/** Runs a fresh managed challenge only when a production Turnstile site key is configured. */
export async function resolveAuthCaptchaToken(action: AuthCaptchaAction): Promise<string | null> {
  const sitekey = import.meta.env.VITE_TURNSTILE_SITE_KEY?.trim()
  if (!sitekey || typeof window === 'undefined') return null
  const turnstile = await loadTurnstile()
  const container = document.createElement('div')
  container.className = 'mt-turnstile-challenge'
  container.setAttribute('aria-label', 'Security verification')
  document.body.append(container)

  return new Promise<string>((resolve, reject) => {
    let widgetId: string | null = null
    const finish = (result: { token?: string; error?: Error }) => {
      if (widgetId) turnstile.remove(widgetId)
      container.remove()
      if (result.token) resolve(result.token)
      else reject(result.error ?? new Error('Security verification expired. Please try again.'))
    }
    widgetId = turnstile.render(container, {
      sitekey,
      action,
      appearance: 'interaction-only',
      theme: 'auto',
      callback: (token) => finish({ token }),
      'error-callback': () => finish({ error: new Error('Security verification failed.') }),
      'expired-callback': () => finish({ error: new Error('Security verification expired.') }),
      'unsupported-callback': () =>
        finish({ error: new Error('This browser cannot complete security verification.') }),
    })
  })
}
