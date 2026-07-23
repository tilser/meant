const RETRYABLE_ACTION_CODES = new Set(['agent_action_in_progress', 'agent_action_uncertain'])
const STORAGE_PREFIX = 'meant:agent-action-request:v1:'

export interface AgentActionRequestIdentityStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

function browserSessionStorage(): AgentActionRequestIdentityStorage | undefined {
  try {
    return typeof window === 'undefined' ? undefined : window.sessionStorage
  } catch {
    return undefined
  }
}

/** Retains the original request identity whenever the server outcome is unknown or still in flight. */
export function retainAgentActionIdempotencyKey(error: unknown): boolean {
  if (!(error instanceof Error)) return true
  const failure = error as Error & { status?: unknown; code?: unknown }
  if (typeof failure.status !== 'number') return true
  return (
    (typeof failure.code === 'string' && RETRYABLE_ACTION_CODES.has(failure.code)) ||
    failure.status === 408 ||
    failure.status >= 500
  )
}

export class AgentActionRequestIdentityStore {
  private readonly keys = new Map<string, string>()

  constructor(
    private readonly storage:
      AgentActionRequestIdentityStorage | undefined = browserSessionStorage(),
  ) {}

  keyFor(logicalAction: string, create: () => string): string {
    const existing = this.keys.get(logicalAction)
    if (existing) return existing
    const persisted = this.read(logicalAction)
    if (persisted) {
      this.keys.set(logicalAction, persisted)
      return persisted
    }
    const created = create()
    this.keys.set(logicalAction, created)
    this.write(logicalAction, created)
    return created
  }

  completed(logicalAction: string): void {
    this.keys.delete(logicalAction)
    this.remove(logicalAction)
  }

  failed(logicalAction: string, error: unknown): void {
    if (!retainAgentActionIdempotencyKey(error)) {
      this.completed(logicalAction)
    }
  }

  private read(logicalAction: string): string | undefined {
    try {
      const value = this.storage?.getItem(this.storageKey(logicalAction))?.trim()
      return value || undefined
    } catch {
      return undefined
    }
  }

  private write(logicalAction: string, requestIdentity: string): void {
    try {
      this.storage?.setItem(this.storageKey(logicalAction), requestIdentity)
    } catch {
      // Privacy mode and storage quotas fall back to the in-memory copy.
    }
  }

  private remove(logicalAction: string): void {
    try {
      this.storage?.removeItem(this.storageKey(logicalAction))
    } catch {
      // The in-memory copy is still cleared even if browser storage is unavailable.
    }
  }

  private storageKey(logicalAction: string): string {
    return `${STORAGE_PREFIX}${logicalAction}`
  }
}
