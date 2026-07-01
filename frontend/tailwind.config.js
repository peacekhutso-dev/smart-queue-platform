/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // DHA Brand Colors - extracted from Visily designs
        primary: {
          DEFAULT: '#1DB954', // DHA green
          50:  '#f0fdf4',
          100: '#dcfce7',
          200: '#bbf7d0',
          300: '#86efac',
          400: '#4ade80',
          500: '#1DB954',
          600: '#16a34a',
          700: '#15803d',
          800: '#166534',
          900: '#14532d',
        },
        dha: {
          green:  '#1DB954',
          dark:   '#111827',
          gray:   '#6B7280',
          light:  '#F9FAFB',
          border: '#E5E7EB',
          error:  '#EF4444',
          warning:'#F59E0B',
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        'xl': '1rem',
        '2xl': '1.5rem',
      },
      boxShadow: {
        'card': '0 1px 3px 0 rgba(0,0,0,0.1), 0 1px 2px -1px rgba(0,0,0,0.1)',
        'modal': '0 20px 60px -10px rgba(0,0,0,0.3)',
      }
    },
  },
  plugins: [],
}