import { useState, useRef, useEffect, KeyboardEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, RotateCcw, ShieldCheck } from 'lucide-react'
import toast from 'react-hot-toast'
import { useAuthStore } from '@/store/AuthStore'
import axios from '../api/Axios'

const OTP_LENGTH = 6
const OTP_TTL    = 120

function formatCountdown(s: number) {
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

export default function OtpVerifyPage() {
  const navigate         = useNavigate()
  const [params]         = useSearchParams()
  const sessionMode      = params.get('session') === 'true'

  const { pendingPhone, user, set2faVerified } = useAuthStore()
  const phone = sessionMode ? (user?.phone ?? '') : (pendingPhone ?? '')

  const [digits, setDigits]       = useState<string[]>(Array(OTP_LENGTH).fill(''))
  const [loading, setLoading]     = useState(false)
  const [hasError, setHasError]   = useState(false)
  const [countdown, setCountdown] = useState(OTP_TTL)
  const [resending, setResending] = useState(false)
  const inputRefs = useRef<(HTMLInputElement | null)[]>([])

  useEffect(() => {
    if (countdown <= 0) return
    const id = setInterval(() => setCountdown((c) => c - 1), 1000)
    return () => clearInterval(id)
  }, [countdown])

  useEffect(() => {
    if (!phone) navigate('/login', { replace: true })
  }, [phone, navigate])

  useEffect(() => {
    if (sessionMode && phone) sendOtp()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const sendOtp = async () => {
    try { await axios.post('/auth/send-otp', { identifier: phone }) } catch { /* non-fatal */ }
  }

  const focusNext = (idx: number) => inputRefs.current[idx + 1]?.focus()
  const focusPrev = (idx: number) => inputRefs.current[idx - 1]?.focus()

  const handleChange = (idx: number, val: string) => {
    if (!/^\d?$/.test(val)) return
    const next = [...digits]
    next[idx] = val
    setDigits(next)
    setHasError(false)
    if (val) focusNext(idx)
  }

  const handleKeyDown = (idx: number, e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !digits[idx]) focusPrev(idx)
  }

  const handlePaste = (e: React.ClipboardEvent) => {
    const text = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, OTP_LENGTH)
    const next  = [...digits]
    text.split('').forEach((ch, i) => { next[i] = ch })
    setDigits(next)
    inputRefs.current[Math.min(text.length, OTP_LENGTH - 1)]?.focus()
  }

  const handleVerify = async () => {
    const code = digits.join('')
    if (code.length < OTP_LENGTH) { setHasError(true); return }
    if (!phone) return
    setLoading(true)
    try {
      await axios.post('/auth/verify-otp', { identifier: phone, otp: code })
      set2faVerified()
      toast.success('Phone verified!')
      if (sessionMode) {
        navigate('/verify-face', { replace: true })
      } else {
        navigate('/verify-id')
      }
    } catch {
      setHasError(true)
      setDigits(Array(OTP_LENGTH).fill(''))
      inputRefs.current[0]?.focus()
      toast.error('Invalid code. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleResend = async () => {
    if (!phone || countdown > 0) return
    setResending(true)
    try {
      await axios.post('/auth/send-otp', { identifier: phone })
      setCountdown(OTP_TTL)
      setDigits(Array(OTP_LENGTH).fill(''))
      toast.success('New code sent!')
    } catch {
      toast.error('Failed to resend. Please try again.')
    } finally {
      setResending(false)
    }
  }

  const maskedPhone = phone
    ? `+27 ••• ••• ${phone.slice(-4)}`
    : '+27 ••• ••• ????'

  const filled = digits.join('').length

  return (
    <div className="min-h-dvh md:min-h-0 flex flex-col bg-white animate-slide-up">
      {/* Header */}
      <div className="flex items-center justify-between px-5 pt-12 pb-4">
        <button
          onClick={() => navigate(-1)}
          className="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center hover:bg-gray-200 transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-gray-700" />
        </button>
        <span className="text-sm font-semibold text-gray-400">
          {sessionMode ? 'Re-authenticate' : 'Verification'}
        </span>
        <div className="w-10" />
      </div>

      <div className="flex-1 flex flex-col px-6 pt-8 pb-8">
        {/* Icon */}
        <div className="w-16 h-16 rounded-2xl bg-primary-50 flex items-center justify-center mb-6">
          <ShieldCheck className="w-8 h-8 text-primary-500" />
        </div>

        <h1 className="text-[2rem] font-black text-gray-900 leading-tight mb-1.5">
          Check your<br />phone
        </h1>
        <p className="text-gray-400 text-sm mb-10">
          {sessionMode ? 'Session expired. We sent a code to' : 'We sent a 6-digit code to'}{' '}
          <span className="font-semibold text-gray-700">{maskedPhone}</span>
        </p>

        {/* OTP inputs */}
        <div
          className={`flex gap-2 justify-between ${hasError ? 'animate-shake' : ''}`}
          onPaste={handlePaste}
        >
          {digits.map((d, i) => (
            <input
              key={i}
              ref={(el) => { inputRefs.current[i] = el }}
              value={d}
              maxLength={1}
              inputMode="numeric"
              onChange={(e) => handleChange(i, e.target.value)}
              onKeyDown={(e) => handleKeyDown(i, e)}
              className={`
                w-12 h-14 text-center text-xl font-bold border-2 rounded-2xl
                focus:outline-none transition-all duration-150
                ${hasError
                  ? 'border-red-400 bg-red-50 text-red-600'
                  : d
                    ? 'border-primary-400 bg-primary-50 text-primary-600'
                    : i === digits.findIndex((x) => !x)
                      ? 'border-primary-500 ring-2 ring-primary-100 bg-white'
                      : 'border-gray-200 bg-gray-50'
                }
              `}
            />
          ))}
        </div>

        {/* Progress dots under inputs */}
        <div className="flex gap-1 mt-4 justify-center">
          {Array.from({ length: OTP_LENGTH }).map((_, i) => (
            <div
              key={i}
              className={`w-1.5 h-1.5 rounded-full transition-all duration-200 ${
                i < filled ? 'bg-primary-500' : 'bg-gray-200'
              }`}
            />
          ))}
        </div>

        {hasError && <p className="text-red-500 text-sm text-center mt-3 font-medium">Incorrect code. Please try again.</p>}

        <div className="flex-1 min-h-8" />

        {/* Resend */}
        <div className="bg-gray-50 rounded-2xl p-4 text-center mb-6">
          {countdown > 0 ? (
            <p className="text-sm text-gray-500">
              Resend code in{' '}
              <span className="text-primary-500 font-bold tabular-nums">{formatCountdown(countdown)}</span>
            </p>
          ) : (
            <p className="text-sm text-gray-500">Didn't receive it?</p>
          )}
          <button
            onClick={handleResend}
            disabled={resending || countdown > 0}
            className="flex items-center gap-1.5 mx-auto mt-1.5 text-sm font-semibold text-gray-400 disabled:opacity-40 hover:text-primary-500 transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            {resending ? 'Sending…' : 'Resend via SMS'}
          </button>
        </div>

        <button
          onClick={handleVerify}
          disabled={loading || filled !== OTP_LENGTH}
          className="btn-primary flex items-center justify-center gap-2"
        >
          {loading ? (
            <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
            </svg>
          ) : (
            'Verify & Continue'
          )}
        </button>

        <p className="text-xs text-gray-400 text-center mt-4 leading-relaxed">
          By continuing you agree to the DHA Terms of Service and Privacy Policy.
        </p>
      </div>
    </div>
  )
}
