import { useAuthStore } from '@/stores/authStore'

/**
 * Stream an answer to a question about Eunomia from the in-app help
 * assistant. Uses fetch + ReadableStream because EventSource doesn't
 * support Authorization headers and we don't want to pass the JWT in
 * a query string for a POST endpoint.
 *
 * Callbacks fire as events arrive:
 *  - onToken: called for each token chunk
 *  - onDone: called once when the backend signals completion
 *  - onError: called on transport error OR backend-emitted error
 *
 * Returns an abort function the caller can invoke to cancel the
 * in-flight stream (e.g. on component unmount).
 */
export interface AskHelpHandlers {
  onToken: (chunk: string) => void
  onDone: () => void
  onError: (message: string) => void
}

export function askHelpStream(question: string, handlers: AskHelpHandlers): () => void {
  const controller = new AbortController()
  const token = useAuthStore.getState().token

  void (async () => {
    let resp: Response
    try {
      resp = await fetch('/api/v1/help/ask', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ question }),
        signal: controller.signal,
      })
    } catch (e) {
      if (controller.signal.aborted) return
      handlers.onError(toMessage(e, 'Network error while contacting the help assistant.'))
      return
    }

    if (!resp.ok) {
      handlers.onError(`Help assistant returned HTTP ${resp.status}.`)
      return
    }
    if (!resp.body) {
      handlers.onError('Help assistant returned no body.')
      return
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        // SSE event boundary is a blank line (\n\n). Split on that.
        let sep = buffer.indexOf('\n\n')
        while (sep !== -1) {
          const block = buffer.slice(0, sep)
          buffer = buffer.slice(sep + 2)
          dispatchEventBlock(block, handlers)
          sep = buffer.indexOf('\n\n')
        }
      }
    } catch (e) {
      if (controller.signal.aborted) return
      handlers.onError(toMessage(e, 'Stream interrupted.'))
    }
  })()

  return () => controller.abort()
}

/**
 * Parse one SSE event block. Each block is a set of lines like
 *   event: error
 *   data: <payload>
 * separated by `\n`. We only care about `event` and `data`. Multi-line
 * `data:` blocks are concatenated with newlines per the SSE spec.
 */
function dispatchEventBlock(block: string, handlers: AskHelpHandlers): void {
  if (block.trim() === '') return

  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      // SSE spec: strip one leading space if present.
      const raw = line.slice(5)
      dataLines.push(raw.startsWith(' ') ? raw.slice(1) : raw)
    }
    // Ignore other field types (id:, retry:, comments starting with `:`).
  }

  const data = dataLines.join('\n')

  switch (eventName) {
    case 'done':
      handlers.onDone()
      return
    case 'error':
      handlers.onError(data || 'Help assistant reported an unknown error.')
      return
    default:
      // Default 'message' event = a token chunk.
      handlers.onToken(data)
  }
}

function toMessage(e: unknown, fallback: string): string {
  if (e instanceof Error) return e.message
  if (typeof e === 'string') return e
  return fallback
}
