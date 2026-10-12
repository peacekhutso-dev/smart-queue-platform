import { useState, useEffect } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Eye, EyeOff, ArrowRight } from 'lucide-react'
import toast from 'react-hot-toast'
import axios from '../api/Axios'
import { useAuthStore } from '../store/AuthStore'

const ID_KEY = 'dha_login_id'

export default function LoginPage() {
  const navigate  = useNavigate()
  const [params]  = useSearchParams()

  useEffect(() => {
    if (params.get('reason') === 'session_expired') toast.error('Session expired. Please log in again.')
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Identity confirmation (Zero Trust gate 1.5) only appears after a
  // successful sign-in — it's a separate security step against the citizen's
  // Home Affairs record, not part of the sign-in form itself.
  const [confirmingIdentity, setConfirmingIdentity] = useState(false)

  const [identifier, setIdentifier] = useState(() => sessionStorage.getItem(ID_KEY) ?? '')
  const [password, setPassword]   = useState('')
  const [showPw, setShowPw]     = useState(false)
  const [error, setError]       = useState('')
  const [loading, setLoading]   = useState(false)

  const [saIdNumber, setSaIdNumber] = useState('')

  useEffect(() => { sessionStorage.setItem(ID_KEY, identifier) }, [identifier])

  const handleLogin = async () => {
    if (!identifier.trim()) { setError('Please enter your phone or email.'); return }
    if (!password.trim()) { setError('Please enter your password.'); return }
    setError('')
    setLoading(true)
    try {
      const res  = await axios.post('/auth/login', { identifier, password })
      const data = res.data.data
      // The JWT is set as an httpOnly cookie by the server now, not returned
      // in the body — userId presence is what confirms a successful login.
      if (data?.userId) {
        useAuthStore.getState().setAuth({
          id:         String(data.userId),
          name:       data.firstName && data.lastName ? `${data.firstName} ${data.lastName}` : 'Citizen',
          email:      data.email       ?? '',
          phone:      data.phoneNumber ?? '',
          saIdNumber: data.saIdNumber  ?? '',
          verified:   data.isVerified  ?? false,
          province:   data.province    ?? '',
          createdAt:  new Date().toISOString(),
        })
        sessionStorage.removeItem(ID_KEY)
        setConfirmingIdentity(true)
      }
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Login failed. Please check your credentials.'
      toast.error(msg)
      setError('Incorrect credentials. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  // Confirm SA ID against the Home Affairs record (name is checked
  // server-side against the account's own registered name)
  const confirmIdentity = async () => {
    if (!/^\d{13}$/.test(saIdNumber)) { setError('Please enter your 13-digit SA ID number.'); return }
    setLoading(true)
    try {
      const res = await axios.post('/auth/confirm-identity', { saIdNumber })
      const data = res.data.data
      if (data?.valid) {
        // Skip straight to face-verify if this device doesn't already have a
        // live session for it — ProtectedLayout would bounce them there
        // anyway, but routing directly avoids the flash of /welcome first.
        navigate(useAuthStore.getState().isFaceValid() ? '/welcome' : '/verify-face?returnTo=/welcome')
      } else {
        setError(data?.message ?? 'Identity could not be confirmed. Please try again.')
      }
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Could not confirm identity. Please try again.'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-dvh md:min-h-0 flex flex-col bg-white overflow-x-hidden">

      {/* ── Hero top strip — white bg, green text ── */}
      <div className="bg-white px-6 pt-14 pb-6 border-b border-gray-100 relative">
        {/* Logo — centered, large */}
        <div className="flex justify-center mb-6">
          <img
            src="/dha-logo.webp"
            alt="Department of Home Affairs"
            className="w-full max-w-[260px] object-contain"
          />
        </div>

        {/* Tagline + person */}
        <div className="flex items-end justify-between gap-3">
          <div className="flex-1">
            <h2 className="text-gray-900 text-lg font-black leading-snug mb-1">
              Skip the long queues<br />at Home Affairs offices.
            </h2>
            <p className="text-gray-500 text-xs leading-relaxed">
              Find your nearest branch and join the queue virtually — all from your phone.
            </p>
          </div>
          <span className="text-[68px] leading-none shrink-0">🤳🏾</span>
        </div>
      </div>

      {/* ── Form section ── */}
      <div className="flex-1 flex flex-col bg-white">
        <div className="flex flex-col px-6 pt-7 pb-8">

          {!confirmingIdentity ? (
            <>
              <h1 className="text-2xl font-black text-gray-900 mb-1">Welcome back</h1>
              <p className="text-gray-400 text-sm mb-8">Sign in to manage your queue & services</p>

              <form
                className="flex flex-col gap-3"
                onSubmit={(e) => { e.preventDefault(); handleLogin() }}
              >
                <input
                  autoFocus
                  placeholder="+27 82 000 0000 or email"
                  value={identifier}
                  onChange={(e) => { setIdentifier(e.target.value); setError('') }}
                  className="input-field text-base"
                />

                <div className="relative">
                  <input
                    type={showPw ? 'text' : 'password'}
                    placeholder="Your password"
                    value={password}
                    onChange={(e) => { setPassword(e.target.value); setError('') }}
                    className="input-field text-base pr-12"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPw((v) => !v)}
                    className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  >
                    {showPw ? <Eye className="w-5 h-5" /> : <EyeOff className="w-5 h-5" />}
                  </button>
                </div>

                <div className="text-right -mt-1">
                  <Link to="/forgot-password" className="text-sm text-primary-500 font-semibold">
                    Forgot password?
                  </Link>
                </div>

                {error && <p className="text-red-500 text-sm font-medium">{error}</p>}

                <button type="submit" disabled={loading} className="btn-primary flex items-center justify-center gap-2">
                  {loading ? (
                    <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                    </svg>
                  ) : (
                    <>Sign In <ArrowRight className="w-5 h-5" /></>
                  )}
                </button>
              </form>

              <div className="flex items-center gap-3 mt-7">
                <div className="flex-1 h-px bg-gray-100" />
                <span className="text-xs text-gray-400 font-medium">or continue with</span>
                <div className="flex-1 h-px bg-gray-100" />
              </div>

              <div className="grid grid-cols-2 gap-3 mt-4">
                <button className="flex items-center justify-center gap-2.5 border border-gray-200 rounded-2xl px-4 py-3.5 hover:bg-gray-50 transition-colors">
                  <span className="text-base font-black" style={{ color: '#4285F4' }}>G</span>
                  <span className="text-sm font-semibold text-gray-700">Google</span>
                </button>
                <button className="flex items-center justify-center gap-2.5 border border-gray-200 rounded-2xl px-4 py-3.5 hover:bg-gray-50 transition-colors">
                  <span className="text-base">🍎</span>
                  <span className="text-sm font-semibold text-gray-700">Apple</span>
                </button>
              </div>

              <p className="text-center text-gray-500 text-sm mt-6">
                No account?{' '}
                <Link to="/register" className="text-primary-500 font-bold">Create one</Link>
              </p>
            </>
          ) : (
            <>
              <h1 className="text-2xl font-black text-gray-900 mb-1">Confirm your identity</h1>
              <p className="text-gray-400 text-sm mb-8">
                Enter your SA ID number to confirm your identity against your Home Affairs record
              </p>

              <form
                className="flex flex-col gap-3"
                onSubmit={(e) => { e.preventDefault(); confirmIdentity() }}
              >
                <input
                  autoFocus
                  inputMode="numeric"
                  maxLength={13}
                  placeholder="13-digit SA ID number"
                  value={saIdNumber}
                  onChange={(e) => { setSaIdNumber(e.target.value.replace(/\D/g, '')); setError('') }}
                  className="input-field text-base font-mono tracking-widest"
                />
                {error && <p className="text-red-500 text-sm font-medium">{error}</p>}
                <button type="submit" disabled={loading} className="btn-primary flex items-center justify-center gap-2">
                  {loading ? (
                    <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                    </svg>
                  ) : (
                    <>Confirm Identity <ArrowRight className="w-5 h-5" /></>
                  )}
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
