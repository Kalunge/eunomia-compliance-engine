// Display formatters. Keep one source of truth so dates and sizes look the
// same across every page.

const dateFormatter = new Intl.DateTimeFormat('en-GB', {
  year: 'numeric',
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

const relativeFormatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

export function formatDateTime(isoString: string | null | undefined): string {
  if (!isoString) return '—'
  return dateFormatter.format(new Date(isoString))
}

export function formatRelative(isoString: string | null | undefined): string {
  if (!isoString) return '—'
  const then = new Date(isoString).getTime()
  const now = Date.now()
  const diffSeconds = Math.round((then - now) / 1000)
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 60 * 60 * 24 * 365],
    ['month', 60 * 60 * 24 * 30],
    ['week', 60 * 60 * 24 * 7],
    ['day', 60 * 60 * 24],
    ['hour', 60 * 60],
    ['minute', 60],
    ['second', 1],
  ]
  for (const [unit, secondsPerUnit] of units) {
    if (Math.abs(diffSeconds) >= secondsPerUnit) {
      return relativeFormatter.format(Math.round(diffSeconds / secondsPerUnit), unit)
    }
  }
  return 'just now'
}

export function formatBytes(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = bytes
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return `${value.toFixed(value >= 10 || i === 0 ? 0 : 1)} ${units[i]}`
}
