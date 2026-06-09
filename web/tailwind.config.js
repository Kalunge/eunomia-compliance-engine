/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Brand palette. Slate-blue for primary chrome, warm amber for the
        // "gap" / attention states. Locks in early so we don't redo it.
        brand: {
          50: '#f0f4ff',
          100: '#dfe7ff',
          500: '#3656c8',
          600: '#2c46a8',
          700: '#23398a',
        },
        gap: {
          50: '#fff7ed',
          500: '#f59e0b',
          700: '#b45309',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
    },
  },
  plugins: [],
}
