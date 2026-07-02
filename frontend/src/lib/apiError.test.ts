import { describe, expect, test } from 'bun:test'

import { ApiError, parseErrorResponse, parseJsonResponse } from './apiError'

function jsonResponse(body: unknown, status = 400): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api error parsing', () => {
  test('preserves status, code, and detail precedence', async () => {
    const error = await parseErrorResponse(
      jsonResponse(
        {
          detail: 'Detailed failure',
          title: 'Problem title',
          message: 'Payload message',
          code: 'not_found',
        },
        404,
      ),
      'Fallback',
    )

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toBeInstanceOf(Error)
    expect(error.status).toBe(404)
    expect(error.code).toBe('not_found')
    expect(error.message).toBe('Detailed failure')
  })

  test('falls back through message, title, and missing code', async () => {
    const messageError = await parseErrorResponse(
      jsonResponse({ message: 'Payload message' }),
      'Fallback',
    )
    const titleError = await parseErrorResponse(
      jsonResponse({ title: 'Problem title' }),
      'Fallback',
    )

    expect(messageError.message).toBe('Payload message')
    expect(messageError.code).toBeNull()
    expect(titleError.message).toBe('Problem title')
    expect(titleError.code).toBeNull()
  })

  test('uses a safe fallback for non-json bodies', async () => {
    const error = await parseErrorResponse(new Response('not json', { status: 502 }), 'Fallback')

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(502)
    expect(error.code).toBeNull()
    expect(error.message).toBe('Fallback')
  })

  test('parseJsonResponse returns parsed 2xx bodies', async () => {
    await expect(
      parseJsonResponse<{ ok: true }>(jsonResponse({ ok: true }, 200), 'Fallback'),
    ).resolves.toEqual({ ok: true })
  })

  test('parseJsonResponse throws ApiError for non-2xx bodies', async () => {
    try {
      await parseJsonResponse(
        jsonResponse({ detail: 'Cart disappeared', code: 'not_found' }, 404),
        'Fallback',
      )
      throw new Error('Expected parseJsonResponse to throw')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(404)
      expect((error as ApiError).code).toBe('not_found')
      expect((error as ApiError).message).toBe('Cart disappeared')
    }
  })
})
