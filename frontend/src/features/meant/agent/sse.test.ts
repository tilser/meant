import { describe, expect, test } from 'bun:test'

import { parseServerSentEventStream, type ServerSentEvent } from './sse'

function chunkedStream(source: string, oneByteAtATime = false): ReadableStream<Uint8Array> {
  const encoded = new TextEncoder().encode(source)
  const chunks = oneByteAtATime
    ? Array.from(encoded, (_, index) => encoded.slice(index, index + 1))
    : [encoded]
  return new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(chunk)
      }
      controller.close()
    },
  })
}

async function collect(stream: ReadableStream<Uint8Array>): Promise<ServerSentEvent[]> {
  const events: ServerSentEvent[] = []
  for await (const event of parseServerSentEventStream(stream)) {
    events.push(event)
  }
  return events
}

describe('SSE parser', () => {
  test('parses fields across byte, UTF-8, CRLF, and event boundaries', async () => {
    const source =
      '\uFEFF: keepalive\r\nid: cursor-7\r\nevent: agent.event\r\ndata: first\r\ndata: second 😀\r\nretry: 1500\r\n\r\ndata: final\r\n\r\n'

    expect(await collect(chunkedStream(source, true))).toEqual([
      {
        data: 'first\nsecond 😀',
        event: 'agent.event',
        id: 'cursor-7',
        retry: 1500,
      },
      {
        data: 'final',
        event: 'message',
        id: 'cursor-7',
        retry: null,
      },
    ])
  })

  test('dispatches the final data event at EOF and ignores invalid fields', async () => {
    const source = 'id: good\nid: bad\0id\nretry: soon\nunknown: value\ndata: final'

    expect(await collect(chunkedStream(source))).toEqual([
      {
        data: 'final',
        event: 'message',
        id: 'good',
        retry: null,
      },
    ])
  })

  test('cancels the fetch body when a consumer stops reading early', async () => {
    let cancelled = false
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('data: one\n\n'))
      },
      cancel() {
        cancelled = true
      },
    })
    const events = parseServerSentEventStream(stream)

    expect((await events.next()).value?.data).toBe('one')
    await events.return(undefined)

    expect(cancelled).toBe(true)
  })
})
