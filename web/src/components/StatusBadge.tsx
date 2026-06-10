import { clsx } from 'clsx'
import { CheckCircle2, Clock, FileText, Sparkles, AlertCircle, BookOpen } from 'lucide-react'
import type { PolicyStatus, RegulationStatus } from '@/lib/types'

type AnyStatus = RegulationStatus | PolicyStatus

const STYLES: Record<AnyStatus, { label: string; classes: string; Icon: typeof Clock }> = {
  PARSING: {
    label: 'Parsing',
    classes: 'bg-slate-100 text-slate-700 ring-slate-200',
    Icon: Clock,
  },
  EXTRACTED: {
    label: 'Extracted',
    classes: 'bg-brand-50 text-brand-700 ring-brand-200',
    Icon: FileText,
  },
  MAPPED: {
    label: 'Mapped',
    classes: 'bg-violet-50 text-violet-700 ring-violet-200',
    Icon: Sparkles,
  },
  BRIEF_READY: {
    label: 'Brief ready',
    classes: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
    Icon: CheckCircle2,
  },
  INDEXED: {
    label: 'Indexed',
    classes: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
    Icon: BookOpen,
  },
  FAILED: {
    label: 'Failed',
    classes: 'bg-rose-50 text-rose-700 ring-rose-200',
    Icon: AlertCircle,
  },
}

interface Props {
  status: AnyStatus
  size?: 'sm' | 'md'
}

export default function StatusBadge({ status, size = 'md' }: Props) {
  const { label, classes, Icon } = STYLES[status]
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1.5 rounded-full font-medium ring-1 ring-inset',
        size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-xs',
        classes,
      )}
    >
      <Icon className={size === 'sm' ? 'h-3 w-3' : 'h-3.5 w-3.5'} />
      {label}
    </span>
  )
}
