import { clsx } from 'clsx'

/**
 * Brand mark. A simple monogram with a gradient — enough identity for
 * the app shell without paying a designer.
 */
interface LogoProps {
  size?: 'sm' | 'md' | 'lg'
  className?: string
  showWordmark?: boolean
}

export default function Logo({ size = 'md', className, showWordmark = true }: LogoProps) {
  const dim = size === 'sm' ? 'h-8 w-8 text-sm' : size === 'lg' ? 'h-12 w-12 text-xl' : 'h-10 w-10 text-base'
  const word = size === 'sm' ? 'text-base' : size === 'lg' ? 'text-2xl' : 'text-lg'

  return (
    <span className={clsx('inline-flex items-center gap-2.5', className)}>
      <span
        className={clsx(
          'inline-flex items-center justify-center rounded-xl font-display font-bold text-white shadow-sm',
          'bg-gradient-to-br from-brand-500 via-brand-600 to-brand-800',
          dim,
        )}
        aria-hidden
      >
        E
      </span>
      {showWordmark && (
        <span className={clsx('font-display font-bold tracking-tight text-slate-900', word)}>
          Eunomia
        </span>
      )}
    </span>
  )
}
