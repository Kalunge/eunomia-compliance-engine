import { useEffect, useRef, useState } from 'react'
import { useAuthStore } from '@/stores/authStore'
import { workflowEventsUrl, type WorkflowEvent } from '@/lib/events'

interface UseWorkflowEventsOptions {
  /** Stop streaming once `enabled` flips to false (e.g., saga reached a terminal state). */
  enabled?: boolean
}

interface UseWorkflowEventsResult {
  events: WorkflowEvent[]
  connectionState: 'idle' | 'connecting' | 'open' | 'closed' | 'error'
}

/**
 * Subscribe to a regulation's SSE event stream and accumulate events
 * into local state.
 *
 * The browser's native `EventSource` handles automatic reconnect with
 * `Last-Event-ID` for us — we just need to tear down on unmount and
 * filter out events we've already seen (by `sequenceNo`) if a manual
 * re-subscribe happens.
 */
export function useWorkflowEvents(
  regulationId: string | undefined,
  opts: UseWorkflowEventsOptions = {},
): UseWorkflowEventsResult {
  const { enabled = true } = opts
  const token = useAuthStore((s) => s.token)
  const [events, setEvents] = useState<WorkflowEvent[]>([])
  const [connectionState, setConnectionState] =
    useState<UseWorkflowEventsResult['connectionState']>('idle')

  // Track the highest sequence number we've seen, so a re-subscribe
  // (e.g. enabled flips false → true again) can resume from there.
  const lastSeqRef = useRef<number>(0)

  useEffect(() => {
    if (!regulationId || !token || !enabled) {
      setConnectionState('idle')
      return
    }

    setConnectionState('connecting')
    const url = workflowEventsUrl(regulationId, token, lastSeqRef.current)
    const es = new EventSource(url)

    es.onopen = () => setConnectionState('open')
    es.onerror = () => {
      // EventSource auto-reconnects unless we close — let it try.
      setConnectionState('error')
    }

    // SSE delivers events by `event:` name, not via `message`. Listen
    // to every type the backend emits.
    const handle = (e: MessageEvent) => {
      const seq = Number(e.lastEventId)
      if (!Number.isFinite(seq) || seq <= lastSeqRef.current) return
      lastSeqRef.current = seq

      let parsedPayload: Record<string, unknown> = {}
      try {
        parsedPayload = e.data ? JSON.parse(e.data as string) : {}
      } catch {
        // Tolerate malformed payloads — show the raw string under a fallback key.
        parsedPayload = { raw: e.data }
      }

      setEvents((prev) => [
        ...prev,
        {
          id: seq,
          type: e.type,
          payload: parsedPayload,
          receivedAt: Date.now(),
        },
      ])
    }

    const eventTypes = [
      'regulation.uploaded',
      'extractor.start',
      'extractor.parsed',
      'extractor.identified',
      'mapper.start',
      'mapper.obligation.start',
      'mapper.obligation.candidates',
      'mapper.obligation.matched',
      'mapper.obligation.gap',
      'mapper.complete',
      'impact.start',
      'impact.tagged',
      'impact.complete',
      'impact.skipped',
      'summarizer.start',
      'summarizer.complete',
      'workflow.failed',
    ]
    eventTypes.forEach((t) => es.addEventListener(t, handle as EventListener))

    return () => {
      eventTypes.forEach((t) => es.removeEventListener(t, handle as EventListener))
      es.close()
      setConnectionState('closed')
    }
    // Reset the event buffer whenever the regulation changes; the
    // server's replay will repopulate.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [regulationId, token, enabled])

  // Clear the buffered events when switching regulations.
  useEffect(() => {
    setEvents([])
    lastSeqRef.current = 0
  }, [regulationId])

  return { events, connectionState }
}
