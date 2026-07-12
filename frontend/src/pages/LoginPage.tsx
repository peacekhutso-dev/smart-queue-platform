import { useState, useEffect } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Eye, EyeOff, ArrowLeft, ArrowRight } from 'lucide-react'
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

  const [step, setStep]     = useState(0)
  const [dir, setDir]       = useState<'fwd' | 'bck'>('fwd')
  const [animKey, setAnimKey] = useState(0)
  const [identifier, setIdentifier] = useState(() => sessionStorage.getItem(ID_KEY) ?? '')
  const [password, setPassword]   = useState('')
  const [showPw, setShowPw]     = useState(false)
  const [error, setError]       = useState('')
  const [loading, setLoading]   = useState(false)

  useEffect(() => { sessionStorage.setItem(ID_KEY, identifier) }, [identifier])

  const goNext = () => {
    if (!identifier.trim()) { setError('Please enter your phone or email.'); return }
    setError(''); setDir('fwd'); setAnimKey((k) => k + 1); setStep(1)
  }

  const goBack = () => {
    setError(''); setDir('bck'); setAnimKey((k) => k + 1); setStep(0)
  }

  const handleLogin = async () => {
    if (!password.trim()) { setError('Please enter your password.'); return }
    setLoading(true)
    try {
      const res  = await axios.post('/auth/login', { identifier, password })
      const data = res.data.data
      if (data?.token) {
        useAuthStore.getState().setAuth({
          id:         String(data.userId),
          name:       data.firstName && data.lastName ? `${data.firstName} ${data.lastName}` : 'Citizen',
          email:      data.email       ?? '',
          phone:      data.phoneNumber ?? '',
          saIdNumber: data.saIdNumber  ?? '',
          verified:   data.isVerified  ?? false,
          province:   data.province    ?? '',
          createdAt:  new Date().toISOString(),
        }, data.token)
        sessionStorage.removeItem(ID_KEY)
        navigate('/applicant-type')
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

  const animClass = animKey === 0
    ? 'animate-slide-up'
    : dir === 'fwd'
      ? 'animate-slide-in-right'
      : 'animate-slide-in-left'

  return (
    <div className="min-h-dvh md:min-h-0 flex flex-col bg-white overflow-x-hidden">

      {/* ── Hero top strip — white bg, green text ── */}
      <div className="bg-white px-6 pt-14 pb-6 border-b border-gray-100 relative">
        {step === 1 && (
          <button
            onClick={goBack}
            className="absolute top-12 left-5 w-9 h-9 rounded-full bg-gray-100 flex items-center justify-center hover:bg-gray-200 transition-colors"
          >
            <ArrowLeft className="w-4 h-4 text-gray-700" />
          </button>
        )}

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
        <div key={animKey} className={`flex flex-col px-6 pt-7 pb-8 ${animClass}`}>

          {/* Step 0 — Identifier */}
          {step === 0 && (
            <>
              <h1 className="text-2xl font-black text-gray-900 mb-1">Welcome back</h1>
              <p className="text-gray-400 text-sm mb-8">Sign in to manage your queue & services</p>

              <div className="flex flex-col gap-3">
                <input
                  autoFocus
                  placeholder="+27 82 000 0000 or email"
                  value={identifier}
                  onChange={(e) => { setIdentifier(e.target.value); setError('') }}
                  onKeyDown={(e) => e.key === 'Enter' && goNext()}
                  className="input-field text-base"
                />
                {error && <p className="text-red-500 text-sm font-medium">{error}</p>}
                <button onClick={goNext} className="btn-primary flex items-center justify-center gap-2">
                  Continue <ArrowRight className="w-5 h-5" />
                </button>
              </div>

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
          )}

          {/* Step 1 — Password */}
          {step === 1 && (
            <>
              <h1 className="text-2xl font-black text-gray-900 mb-1">Enter your password</h1>
              <p className="text-gray-400 text-sm mb-8">Almost there — keep it secure</p>

              <div className="flex flex-col gap-3">
                <div className="relative">
                  <input
                    autoFocus
                    type={showPw ? 'text' : 'password'}
                    placeholder="Your password"
                    value={password}
                    onChange={(e) => { setPassword(e.target.value); setError('') }}
                    onKeyDown={(e) => e.key === 'Enter' && handleLogin()}
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
                <button onClick={handleLogin} disabled={loading} className="btn-primary flex items-center justify-center gap-2">
                  {loading ? (
                    <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                    </svg>
                  ) : (
                    <>Sign In <ArrowRight className="w-5 h-5" /></>
                  )}
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
