import { useState, useRef, useEffect, KeyboardEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ShieldCheck, RotateCcw } from 'lucide-react'
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

  // Session mode uses the logged-in user's phone; registration uses pendingPhone
  const phone = sessionMode ? (user?.phone ?? '') : (pendingPhone ?? '')

  const [digits, setDigits]     = useState<string[]>(Array(OTP_LENGTH).fill(''))
  const [loading, setLoading]   = useState(false)
  const [hasError, setHasError] = useState(false)
  const [countdown, setCountdown] = useState(OTP_TTL)
  const [resending, setResending] = useState(false)

  const inputRefs = useRef<(HTMLInputElement | null)[]>([])

  useEffect(() => {
    if (countdown <= 0) return
    const id = setInterval(() => setCountdown((c) => c - 1), 1000)
    return () => clearInterval(id)
  }, [countdown])

  // Redirect if no phone available
  useEffect(() => {
    if (!phone) navigate('/login', { replace: true })
  }, [phone, navigate])

  // In session mode, auto-send OTP to the user's registered phone on mount
  useEffect(() => {
    if (sessionMode && phone) {
      sendOtp()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const sendOtp = async () => {
    try {
      await axios.post('/auth/send-otp', { identifier: phone })
    } catch {
      // non-fatal — user can manually resend
    }
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

      // Grant 2FA gate on client side (mirrors what backend stores in Redis)
      set2faVerified()

      toast.success('Phone verified!')

      if (sessionMode) {
        // Session renewal — go back to face verify so the user passes Gate 3 too
        navigate('/verify-face?mode=verify', { replace: true })
      } else {
        // Registration flow — next step is SA ID verification
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

  return (
    <div className="min-h-screen flex flex-col bg-white">
      <div className="flex items-center px-5 py-4 border-b border-gray-100">
        <button onClick={() => navigate(-1)} className="p-1 rounded-full hover:bg-gray-100 mr-3 text-lg">←</button>
        <span className="flex-1 text-center text-base font-semibold text-gray-900">
          {sessionMode ? 'Re-Authenticate' : 'Verification'}
        </span>
        <div className="w-8" />
      </div>

      <div className="flex-1 flex flex-col items-center gap-6 px-6 py-8">
        <div className="w-20 h-20 rounded-full bg-green-50 flex items-center justify-center">
          <ShieldCheck className="w-10 h-10 text-green-600" />
        </div>

        <div className="text-center">
          <h2 className="text-2xl font-bold text-gray-900">Enter the 6-digit code</h2>
          <p className="text-gray-500 mt-2 text-sm leading-relaxed">
            {sessionMode
              ? 'Your session expired. Enter the code sent to'
              : 'We\'ve sent a secure verification code to'}
            <br />
            <span className="font-semibold text-gray-800">{maskedPhone}</span>
          </p>
        </div>

        <div className={`flex gap-2.5 ${hasError ? 'animate-shake' : ''}`} onPaste={handlePaste}>
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
                    ? 'border-gray-300 bg-white text-gray-900'
                    : i === digits.findIndex((x) => !x)
                      ? 'border-green-500 ring-2 ring-green-100 bg-white'
                      : 'border-gray-200 bg-white text-gray-400'
                }
              `}
            />
          ))}
        </div>

        <div className="w-full border-2 border-dashed border-gray-200 rounded-2xl p-4 text-center">
          {countdown > 0 && (
            <p className="text-sm text-gray-500">
              Request new code in{' '}
              <span className="text-green-600 font-bold">{formatCountdown(countdown)}</span>
            </p>
          )}
          <button
            onClick={handleResend}
            disabled={resending || countdown > 0}
            className="flex items-center gap-1.5 mx-auto mt-1.5 text-sm text-gray-400 disabled:opacity-40 hover:text-green-600 transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            {resending ? 'Sending…' : 'Resend OTP via SMS'}
          </button>
        </div>

        <p className="text-xs text-gray-400 font-medium tracking-widest flex items-center gap-1.5">
          <ShieldCheck className="w-3.5 h-3.5" />
          OFFICIAL DHA VERIFICATION
        </p>
      </div>

      <div className="px-6 pb-8 space-y-3">
        <button
          onClick={handleVerify}
          disabled={loading || digits.join('').length !== OTP_LENGTH}
          className="w-full bg-green-500 text-white font-bold py-4 rounded-2xl hover:bg-green-600 transition-all active:scale-95 disabled:opacity-50"
        >
          {loading ? 'Verifying…' : 'Verify & Continue'}
        </button>
        <p className="text-xs text-gray-400 text-center leading-relaxed">
          By continuing, you agree to the Department of Home Affairs<br />
          Terms of Service and Privacy Policy.
        </p>
      </div>
    </div>
  )
}
