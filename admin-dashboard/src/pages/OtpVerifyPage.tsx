import { useEffect, useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { KeyRound } from 'lucide-react'
import { authService } from '../api/Api'
import { useAuthStore } from '../store/AuthStore'

const RESEND_COOLDOWN_SECONDS = 30

export default function OtpVerifyPage() {
  const navigate = useNavigate()
  const pendingIdentifier = useAuthStore((s) => s.pendingIdentifier)
  const set2faVerified = useAuthStore((s) => s.set2faVerified)
  const [otp, setOtp] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [resending, setResending] = useState(false)
  const [resendMessage, setResendMessage] = useState<string | null>(null)
  const [cooldown, setCooldown] = useState(0)

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setInterval(() => setCooldown((c) => c - 1), 1000)
    return () => clearInterval(timer)
  }, [cooldown])

  // pendingIdentifier only ever lives in memory (never persisted to
  // localStorage — see AuthStore), so it's empty after any fresh page load
  // on this route (a refresh, a lost tab, HMR full-reload). A declarative
  // <Navigate> element is required here rather than calling navigate()
  // imperatively during render — the latter is invalid mid-render and made
  // React bail out without committing anything, leaving a blank white page
  // with no console error.
  if (!pendingIdentifier) {
    return <Navigate to="/login" replace />
  }

  const resendOtp = async () => {
    setResending(true)
    setResendMessage(null)
    setError(null)
    try {
      await authService.sendOtp(pendingIdentifier)
      setResendMessage('A new code has been sent.')
      setCooldown(RESEND_COOLDOWN_SECONDS)
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Could not resend code. Try again.')
    } finally {
      setResending(false)
    }
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      const res = await authService.verifyOtp({ identifier: pendingIdentifier, otp })
      if (!res.data.data) {
        setError('Incorrect code. Try again.')
        return
      }
      set2faVerified()
      navigate('/verify-fingerprint')
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Verification failed.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-[#0f1f3d] to-slate-900 flex items-center justify-center p-4 relative overflow-hidden">
      <div className="absolute inset-0 overflow-hidden">
        <div className="absolute -top-40 -right-40 w-80 h-80 bg-blue-500/10 rounded-full blur-3xl"></div>
        <div className="absolute -bottom-40 -left-40 w-80 h-80 bg-blue-600/10 rounded-full blur-3xl"></div>
      </div>

      <form
        onSubmit={submit}
        className="bg-white/95 backdrop-blur-sm rounded-3xl shadow-2xl border border-slate-200 w-full max-w-md p-10 flex flex-col gap-5 relative animate-fade-in"
      >
        <div className="flex flex-col items-center gap-3 mb-2">
          <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-[#2563EB] to-[#1e40af] flex items-center justify-center shadow-lg">
            <KeyRound size={28} className="text-white" />
          </div>
          <div className="text-center">
            <h1 className="font-bold text-slate-900 text-2xl">Two-Factor Authentication</h1>
            <p className="text-sm text-slate-500 mt-1">Enter the 6-digit code sent to your email</p>
          </div>
        </div>

        <div className="w-full h-px bg-gradient-to-r from-transparent via-slate-200 to-transparent"></div>

        <div className="bg-blue-50 border border-blue-100 rounded-xl px-4 py-3">
          <p className="text-xs text-blue-700 text-center">
            Code sent to <span className="font-semibold">{pendingIdentifier}</span>
          </p>
        </div>

        <div>
          <label className="text-sm font-semibold text-slate-700 mb-2 block text-center">
            Verification Code
          </label>
          <input
            inputMode="numeric"
            maxLength={6}
            required
            autoFocus
            value={otp}
            onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
            className="text-center tracking-[0.5em] text-2xl font-bold w-full border-2 border-slate-200 rounded-xl px-4 py-4 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-blue-400 transition-all"
            placeholder="000000"
          />
        </div>

        <div className="text-center -mt-2">
          <button
            type="button"
            onClick={resendOtp}
            disabled={resending || cooldown > 0}
            className="text-xs font-semibold text-blue-600 hover:text-blue-700 disabled:text-slate-400 disabled:cursor-not-allowed transition-colors"
          >
            {resending
              ? 'Resending…'
              : cooldown > 0
                ? `Resend code in ${cooldown}s`
                : "Didn't get a code? Resend"}
          </button>
        </div>

        {resendMessage && (
          <div className="bg-green-50 border border-green-200 text-green-700 text-xs px-4 py-2.5 rounded-xl text-center">
            {resendMessage}
          </div>
        )}

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl flex items-start gap-2">
            <svg className="w-5 h-5 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
            </svg>
            <span>{error}</span>
          </div>
        )}

        <button
          type="submit"
          disabled={loading}
          className="btn-primary mt-2 flex items-center justify-center"
        >
          {loading ? (
            <>
              <svg className="animate-spin -ml-1 mr-2 h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              Verifying…
            </>
          ) : (
            'Verify Code'
          )}
        </button>
      </form>
    </div>
  )
}
