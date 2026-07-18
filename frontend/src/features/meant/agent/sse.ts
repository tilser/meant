export interface ServerSentEvent {
  data: string
  event: string
  id: string | null
  retry: number | null
}

interface PendingServerSentEvent {
  dataLines: string[]
  event: string
  retry: number | null
}

function readLine(buffer: string, final: boolean): { line: string; remainder: string } | null {
  for (let index = 0; index < buffer.length; index += 1) {
    const character = buffer[index]
    if (character !== '\r' && character !== '\n') {
      continue
    }
    if (character === '\r' && index === buffer.length - 1 && !final) {
      return null
    }
    const separatorLength = character === '\r' && buffer[index + 1] === '\n' ? 2 : 1
    return {
      line: buffer.slice(0, index),
      remainder: buffer.slice(index + separatorLength),
    }
  }
  if (final && buffer.length > 0) {
    return { line: buffer, remainder: '' }
  }
  return null
}

function applySseLine(
  line: string,
  pending: PendingServerSentEvent,
  lastEventId: string | null,
): { event?: ServerSentEvent; lastEventId: string | null } {
  if (line === '') {
    if (pending.dataLines.length === 0) {
      pending.event = ''
      pending.retry = null
      return { lastEventId }
    }
    const event: ServerSentEvent = {
      data: pending.dataLines.join('\n'),
      event: pending.event || 'message',
      id: lastEventId,
      retry: pending.retry,
    }
    pending.dataLines = []
    pending.event = ''
    pending.retry = null
    return { event, lastEventId }
  }
  if (line.startsWith(':')) {
    return { lastEventId }
  }

  const separator = line.indexOf(':')
  const field = separator < 0 ? line : line.slice(0, separator)
  let value = separator < 0 ? '' : line.slice(separator + 1)
  if (value.startsWith(' ')) {
    value = value.slice(1)
  }
  switch (field) {
    case 'data':
      pending.dataLines.push(value)
      break
    case 'event':
      pending.event = value
      break
    case 'id':
      if (!value.includes('\0')) {
        lastEventId = value
      }
      break
    case 'retry':
      if (/^\d+$/.test(value)) {
        pending.retry = Number(value)
      }
      break
  }
  return { lastEventId }
}

/** Parses SSE framing across arbitrary UTF-8 chunk and CR/LF boundaries. */
export async function* parseServerSentEventStream(
  stream: ReadableStream<Uint8Array>,
): AsyncGenerator<ServerSentEvent> {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  const pending: PendingServerSentEvent = { dataLines: [], event: '', retry: null }
  let buffer = ''
  let lastEventId: string | null = null
  let firstDecodedText = true
  let completed = false

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        completed = true
        buffer += decoder.decode()
        break
      }
      let decoded = decoder.decode(value, { stream: true })
      if (firstDecodedText) {
        firstDecodedText = false
        if (decoded.startsWith('\uFEFF')) {
          decoded = decoded.slice(1)
        }
      }
      buffer += decoded

      while (true) {
        const next = readLine(buffer, false)
        if (!next) {
          break
        }
        buffer = next.remainder
        const applied = applySseLine(next.line, pending, lastEventId)
        lastEventId = applied.lastEventId
        if (applied.event) {
          yield applied.event
        }
      }
    }

    while (true) {
      const next = readLine(buffer, true)
      if (!next) {
        break
      }
      buffer = next.remainder
      const applied = applySseLine(next.line, pending, lastEventId)
      lastEventId = applied.lastEventId
      if (applied.event) {
        yield applied.event
      }
    }
    const finalEvent = applySseLine('', pending, lastEventId).event
    if (finalEvent) {
      yield finalEvent
    }
  } finally {
    if (!completed) {
      await reader.cancel()
    }
    reader.releaseLock()
  }
}
