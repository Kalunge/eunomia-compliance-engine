/**
 * One workflow event as received over SSE.
 *
 * `sequenceNo` matches the server's `sequence_no` and is what the
 * browser's EventSource sends back as `Last-Event-ID` on auto-reconnect.
 */
export interface WorkflowEvent {
  id: number // sequence_no from the server
  type: string
  payload: Record<string, unknown>
  receivedAt: number // client-side wall-clock for ordering display
}

/**
 * Build the SSE URL for one regulation. Token rides in the query
 * string because EventSource can't send custom headers — see
 * docs/11-streaming-progress.md.
 */
export function workflowEventsUrl(regulationId: string, token: string, lastEventId?: number): string {
  const params = new URLSearchParams({ token })
  if (lastEventId && lastEventId > 0) {
    params.set('lastEventId', String(lastEventId))
  }
  return `/api/v1/regulations/${regulationId}/events?${params.toString()}`
}
