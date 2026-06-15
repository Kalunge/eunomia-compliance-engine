import {
  useEffect,
  useRef,
  useState,
  type DragEvent,
  type FormEvent,
} from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  BookOpen,
  Upload,
  X,
  AlertCircle,
  CheckCircle2,
  Sparkles,
} from 'lucide-react'
import { clsx } from 'clsx'
import { uploadPolicy } from '@/lib/policies'
import { apiErrorMessage } from '@/lib/api'
import { formatBytes } from '@/lib/format'

const ACCEPTED_MIME = new Set([
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/msword',
  'text/plain',
  'text/markdown',
  'text/x-markdown',
])

const ACCEPTED_EXTENSIONS = ['.pdf', '.docx', '.doc', '.txt', '.md']
const MAX_BYTES = 25 * 1024 * 1024

export default function UploadPolicyPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement>(null)

  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [isDragOver, setIsDragOver] = useState(false)
  const [validationError, setValidationError] = useState<string | null>(null)

  useEffect(() => {
    if (!file) return
    if (title.trim() === '') setTitle(file.name.replace(/\.[^/.]+$/, ''))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [file])

  const mutation = useMutation({
    mutationFn: uploadPolicy,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['policies'] })
      navigate('/policies', { replace: true })
    },
  })

  const handleFileSelected = (incoming: File | null) => {
    setValidationError(null)
    if (!incoming) {
      setFile(null)
      return
    }
    const allowed =
      ACCEPTED_MIME.has(incoming.type) ||
      ACCEPTED_EXTENSIONS.some((ext) => incoming.name.toLowerCase().endsWith(ext))
    if (!allowed) {
      setValidationError(`Unsupported file type. Accepted: ${ACCEPTED_EXTENSIONS.join(', ')}.`)
      return
    }
    if (incoming.size > MAX_BYTES) {
      setValidationError(`File is ${formatBytes(incoming.size)}. The 25 MB cap protects the free-tier container.`)
      return
    }
    setFile(incoming)
  }

  const onDragOver = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsDragOver(true)
  }
  const onDragLeave = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsDragOver(false)
  }
  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsDragOver(false)
    handleFileSelected(e.dataTransfer.files?.[0] ?? null)
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!file) return
    mutation.mutate({ file, title: title.trim() || undefined })
  }

  return (
    <div className="mx-auto max-w-3xl p-6 md:p-10">
      <Link
        to="/policies"
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to policies
      </Link>

      <header className="mt-6 mb-8">
        <h1 className="text-2xl font-bold text-slate-900">Upload an internal policy</h1>
        <p className="mt-1 text-sm text-slate-500 max-w-xl">
          Drop an internal policy document. Eunomia will chunk and embed it so
          incoming regulatory obligations can be matched against your existing
          controls.
        </p>
      </header>

      <form onSubmit={onSubmit} className="space-y-6">
        <div
          onDragOver={onDragOver}
          onDragLeave={onDragLeave}
          onDrop={onDrop}
          onClick={() => inputRef.current?.click()}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault()
              inputRef.current?.click()
            }
          }}
          className={clsx(
            'relative flex flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 py-14 text-center cursor-pointer transition-all focus-ring',
            isDragOver
              ? 'border-brand-500 bg-brand-50 scale-[1.01]'
              : file
                ? 'border-emerald-300 bg-emerald-50/40'
                : 'border-slate-300 bg-slate-50/40 hover:border-brand-400 hover:bg-brand-50/30',
          )}
        >
          <input
            ref={inputRef}
            type="file"
            className="sr-only"
            accept={ACCEPTED_EXTENSIONS.join(',')}
            onChange={(e) => handleFileSelected(e.target.files?.[0] ?? null)}
          />

          {file ? (
            <div className="flex items-center gap-4 animate-slide-up">
              <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-white shadow-sm">
                <CheckCircle2 className="h-7 w-7 text-emerald-500" />
              </div>
              <div className="text-left min-w-0">
                <p className="truncate font-medium text-slate-900">{file.name}</p>
                <p className="text-xs text-slate-500">
                  {formatBytes(file.size)} · {file.type || 'unknown type'}
                </p>
              </div>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation()
                  setFile(null)
                  setTitle('')
                  if (inputRef.current) inputRef.current.value = ''
                }}
                className="ml-auto rounded-full p-1.5 text-slate-400 hover:bg-white hover:text-slate-600 transition-colors focus-ring"
                aria-label="Remove file"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          ) : (
            <>
              <div
                className={clsx(
                  'flex h-14 w-14 items-center justify-center rounded-xl shadow-sm transition-colors',
                  isDragOver ? 'bg-brand-600 text-white' : 'bg-white text-brand-600',
                )}
              >
                {isDragOver ? <Upload className="h-7 w-7" /> : <BookOpen className="h-7 w-7" />}
              </div>
              <p className="mt-4 text-sm font-medium text-slate-700">
                {isDragOver ? 'Drop to upload' : 'Drag a file here, or click to browse'}
              </p>
              <p className="mt-1 text-xs text-slate-500">PDF, DOCX, DOC, TXT, or Markdown · up to 25 MB</p>
            </>
          )}
        </div>

        {validationError && (
          <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 flex items-start gap-2.5">
            <AlertCircle className="h-4 w-4 mt-0.5 flex-shrink-0" />
            <span>{validationError}</span>
          </div>
        )}

        {file && (
          <div className="animate-fade-in">
            <label className="block">
              <span className="text-sm font-medium text-slate-700">
                Title
                <span className="ml-1.5 text-xs font-normal text-slate-400">optional</span>
              </span>
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="e.g. Agent Operations Manual v4.2"
                maxLength={240}
                className="input-field mt-1.5"
              />
              <span className="mt-1 block text-xs text-slate-400">Defaults to the filename if left blank.</span>
            </label>
          </div>
        )}

        {mutation.isError && (
          <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 flex items-start gap-2.5">
            <AlertCircle className="h-4 w-4 mt-0.5 flex-shrink-0" />
            <span>{apiErrorMessage(mutation.error, 'Upload failed. Check your connection and try again.')}</span>
          </div>
        )}

        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <p className="text-xs text-slate-500 flex items-start gap-1.5">
            <Sparkles className="h-3.5 w-3.5 text-brand-500 mt-0.5 flex-shrink-0" />
            <span>
              Eunomia will chunk and embed the policy in the background. You can leave this page; the
              policy will appear in the list with a live status.
            </span>
          </p>
          <button
            type="submit"
            disabled={!file || mutation.isPending}
            className="btn-primary self-start sm:self-auto"
          >
            {mutation.isPending ? 'Uploading…' : (
              <>
                <Upload className="h-4 w-4" />
                Upload
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  )
}
