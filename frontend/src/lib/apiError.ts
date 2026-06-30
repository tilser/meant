export class ApiError extends Error {
  readonly status: number
  readonly code: string | null

  constructor(message: string, status: number, code: string | null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export async function parseJsonResponse<T>(response: Response, message: string): Promise<T> {
  if (!response.ok) {
    throw await parseErrorResponse(response, message)
  }
  return (await response.json()) as T
}

export async function parseErrorResponse(response: Response, fallback: string): Promise<ApiError> {
  try {
    const payload = await response.json() as {
      detail?: unknown
      title?: unknown
      message?: unknown
      code?: unknown
    }
    const detail = typeof payload.detail === 'string' ? payload.detail : null
    const title = typeof payload.title === 'string' ? payload.title : null
    const payloadMessage = typeof payload.message === 'string' ? payload.message : null
    const code = typeof payload.code === 'string' ? payload.code : null
    return new ApiError(detail || payloadMessage || title || fallback, response.status, code)
  } catch {
    return new ApiError(fallback, response.status, null)
  }
}
