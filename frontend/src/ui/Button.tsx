import { ButtonHTMLAttributes, forwardRef } from 'react'
import { clsx } from 'clsx'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  loading?: boolean
  size?: 'sm' | 'md' | 'lg'
}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = 'primary', loading, size = 'lg', className, children, disabled, ...props }, ref) => {
    const base = 'flex items-center justify-center gap-2 font-semibold rounded-2xl transition-all duration-200 active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed'

    const variants = {
      primary:   'bg-primary-500 text-white hover:bg-primary-600',
      secondary: 'bg-gray-100 text-gray-800 hover:bg-gray-200',
      ghost:     'bg-transparent text-primary-500 hover:bg-primary-50',
      danger:    'bg-red-500 text-white hover:bg-red-600',
    }

    const sizes = {
      sm: 'px-4 py-2 text-sm',
      md: 'px-5 py-3 text-base',
      lg: 'w-full px-6 py-4 text-base',
    }

    return (
      <button
        ref={ref}
        disabled={disabled || loading}
        className={clsx(base, variants[variant], sizes[size], className)}
        {...props}
      >
        {loading && (
          <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
          </svg>
        )}
        {children}
      </button>
    )
  }
)
Button.displayName = 'Button'
export default Button