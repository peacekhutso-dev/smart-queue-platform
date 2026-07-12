import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, ArrowRight, Check } from 'lucide-react'
import toast from 'react-hot-toast'

export default function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [email, setEmail]   = useState('')
  const [sent, setSent]     = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError]   = useState('')

  const handleSubmit = async () => {
    if (!email.includes('@')) { setError('Please enter a valid email address.'); return }
    setLoading(true)
    await new Promise((r) => setTimeout(r, 1000))
    setLoading(false)
    setSent(true)
  }

  if (sent) {
    return (
      <div className="min-h-dvh md:min-h-0 flex flex-col bg-white animate-slide-up px-8">
        <div className="flex-1 flex flex-col items-center justify-center gap-6 text-center">
          <div className="w-20 h-20 rounded-full bg-primary-500 flex items-center justify-center shadow-[0_8px_32px_rgba(29,185,84,0.35)] animate-celebrate">
            <Check className="w-10 h-10 text-white" strokeWidth={3} />
          </div>
          <div>
            <h2 className="text-2xl font-black text-gray-900">Check your email</h2>
            <p className="text-gray-500 mt-2 text-sm leading-relaxed max-w-xs">
              If <span className="font-semibold text-gray-700">{email}</span> is linked to an account,
              you'll receive a reset link shortly.
            </p>
          </div>
        </div>
        <div className="pb-10">
          <button
            onClick={() => navigate('/login')}
            className="btn-primary"
          >
            Back to Sign In
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-dvh md:min-h-0 flex flex-col bg-white overflow-x-hidden animate-slide-up">
      {/* Header */}
      <div className="flex items-center justify-between px-5 pt-12 pb-4">
        <button
          onClick={() => navigate(-1)}
          className="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center hover:bg-gray-200 transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-gray-700" />
        </button>
        <div className="w-10" />
      </div>

      <div className="flex-1 flex flex-col px-6 pt-8 pb-8">
        <span className="text-5xl mb-6 leading-none">🔒</span>
        <h1 className="text-[2rem] font-black text-gray-900 leading-tight mb-1.5">
          Forgot your<br />password?
        </h1>
        <p className="text-gray-400 text-sm mb-10">
          No worries. Enter your email and we'll send you a reset link.
        </p>

        <input
          autoFocus
          type="email"
          inputMode="email"
          placeholder="you@example.com"
          value={email}
          onChange={(e) => { setEmail(e.target.value); setError('') }}
          onKeyDown={(e) => e.key === 'Enter' && handleSubmit()}
          className="input-field text-lg font-semibold"
        />

        {error && <p className="text-red-500 text-sm mt-3 font-medium">{error}</p>}

        <div className="flex-1 min-h-8" />

        <button onClick={handleSubmit} disabled={loading} className="btn-primary flex items-center justify-center gap-2">
          {loading ? (
            <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
            </svg>
          ) : (
            <>Send Reset Link <ArrowRight className="w-5 h-5" /></>
          )}
        </button>
      </div>
    </div>
  )
}
