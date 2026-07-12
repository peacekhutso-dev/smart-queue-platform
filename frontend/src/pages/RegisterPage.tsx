import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, ArrowLeft, ArrowRight, Check } from 'lucide-react'
import toast from 'react-hot-toast'
import { useAuthStore } from '../store/AuthStore'
import axios from '../api/Axios'

const FORM_KEY = 'dha_register_form'
const STEP_KEY = 'dha_register_step'

const STEPS = [
  { emoji: '👋', title: "What's your\nname?",   subtitle: "Let's start with the basics" },
  { emoji: '📧', title: "Your email\naddress",   subtitle: "We'll use this to keep you updated" },
  { emoji: '📱', title: "Your phone\nnumber",    subtitle: 'For SMS verification & security' },
  { emoji: '🔐', title: "Create a\npassword",   subtitle: 'Make it strong and memorable' },
]

interface Form {
  firstName: string; lastName: string; email: string
  phoneNumber: string; password: string; confirm: string
}
const BLANK: Form = { firstName: '', lastName: '', email: '', phoneNumber: '', password: '', confirm: '' }

function loadForm(): Form {
  try { return { ...BLANK, ...JSON.parse(sessionStorage.getItem(FORM_KEY) ?? '{}') } }
  catch { return BLANK }
}
function loadStep(): number {
  try { return Math.max(0, Math.min(3, Number(sessionStorage.getItem(STEP_KEY) ?? '0'))) }
  catch { return 0 }
}

export default function RegisterPage() {
  const navigate   = useNavigate()
  const setPending = useAuthStore((s) => s.setPending)

  const [step, setStep]     = useState<number>(loadStep)
  const [dir, setDir]       = useState<'fwd' | 'bck'>('fwd')
  const [animKey, setAnimKey] = useState(0)
  const [form, setForm]     = useState<Form>(loadForm)
  const [showPw, setShowPw] = useState(false)
  const [showCf, setShowCf] = useState(false)
  const [error, setError]   = useState('')
  const [loading, setLoading] = useState(false)
  const [success, setSuccess] = useState(false)

  useEffect(() => { sessionStorage.setItem(FORM_KEY, JSON.stringify(form)) }, [form])
  useEffect(() => { sessionStorage.setItem(STEP_KEY, String(step)) }, [step])

  const set = (field: keyof Form) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((f) => ({ ...f, [field]: e.target.value }))
    setError('')
  }

  const validate = (): boolean => {
    if (step === 0) {
      if (!form.firstName.trim()) { setError('Please enter your first name.'); return false }
      if (!form.lastName.trim())  { setError('Please enter your last name.'); return false }
    }
    if (step === 1) {
      if (!form.email.includes('@')) { setError('Please enter a valid email address.'); return false }
    }
    if (step === 2) {
      if (form.phoneNumber.replace(/\D/g, '').length < 10) {
        setError('Please enter a valid SA phone number.')
        return false
      }
    }
    if (step === 3) {
      if (form.password.length < 8) { setError('Password must be at least 8 characters.'); return false }
      if (form.password !== form.confirm) { setError('Passwords do not match.'); return false }
    }
    return true
  }

  const goNext = () => {
    if (!validate()) return
    setDir('fwd'); setAnimKey((k) => k + 1); setStep((s) => s + 1); setError('')
  }

  const goBack = () => {
    setDir('bck'); setAnimKey((k) => k + 1); setStep((s) => s - 1); setError('')
  }

  const handleSubmit = async () => {
    if (!validate()) return
    setLoading(true)
    try {
      const res  = await axios.post('/auth/register', {
        firstName:   form.firstName,
        lastName:    form.lastName,
        email:       form.email,
        phoneNumber: form.phoneNumber,
        password:    form.password,
      })
      const data = res.data.data
      if (data?.token) {
        useAuthStore.getState().setAuth({
          id:         String(data.userId),
          name:       `${form.firstName} ${form.lastName}`,
          email:      data.email,
          phone:      form.phoneNumber,
          saIdNumber: '',
          verified:   data.isVerified ?? false,
          province:   '',
          createdAt:  new Date().toISOString(),
        }, data.token)
        setPending(form.phoneNumber)
        sessionStorage.removeItem(FORM_KEY)
        sessionStorage.removeItem(STEP_KEY)
        setSuccess(true)
        setTimeout(() => navigate('/verify-otp'), 1800)
      } else {
        toast.error('Registration error. Please try again.')
      }
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Registration failed. Email or phone may already be in use.'
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  const handleKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') { step < 3 ? goNext() : handleSubmit() }
  }

  const pwStrength = (() => {
    const l = form.password.length
    if (l === 0) return 0
    if (l < 8)   return 1
    if (l < 10)  return 2
    if (l < 14)  return 3
    return 4
  })()

  if (success) {
    return (
      <div className="min-h-dvh md:min-h-0 flex flex-col items-center justify-center gap-6 px-8 text-center bg-white">
        <div className="relative">
          <div className="w-24 h-24 rounded-full bg-primary-500 flex items-center justify-center animate-celebrate shadow-[0_8px_40px_rgba(29,185,84,0.4)]">
            <Check className="w-12 h-12 text-white" strokeWidth={3} />
          </div>
          {['🎉','⭐','✨'].map((e, i) => (
            <span
              key={i}
              className="absolute text-xl animate-float-up"
              style={{
                top: i === 0 ? '-10px' : i === 1 ? '10px' : '-5px',
                left: i === 0 ? '-20px' : i === 1 ? '90px' : '40px',
                animationDelay: `${i * 120}ms`,
              }}
            >
              {e}
            </span>
          ))}
        </div>
        <div>
          <h2 className="text-2xl font-black text-gray-900">Welcome, {form.firstName}!</h2>
          <p className="text-gray-500 mt-2 text-sm">Account created. Sending verification code…</p>
        </div>
        <div className="flex gap-1.5">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="w-2 h-2 bg-primary-500 rounded-full animate-bounce"
              style={{ animationDelay: `${i * 150}ms` }}
            />
          ))}
        </div>
      </div>
    )
  }

  const animClass = animKey === 0
    ? 'animate-slide-up'
    : dir === 'fwd'
      ? 'animate-slide-in-right'
      : 'animate-slide-in-left'

  return (
    <div className="min-h-dvh md:min-h-0 flex flex-col bg-white overflow-x-hidden">
      {/* Top nav */}
      <div className="flex items-center justify-between px-5 pt-12 pb-4">
        <button
          onClick={step > 0 ? goBack : () => navigate('/login')}
          className="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center hover:bg-gray-200 transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-gray-700" />
        </button>
        <span className="text-sm font-semibold text-gray-400">Step {step + 1} of {STEPS.length}</span>
        <div className="w-10" />
      </div>
      {step === 0 && (
        <div className="flex justify-center px-8 pb-2">
          <img src="/dha-logo.webp" alt="Department of Home Affairs" className="w-full max-w-[220px] object-contain" />
        </div>
      )}

      {/* Progress segments */}
      <div className="px-5 flex gap-1.5">
        {STEPS.map((_, i) => (
          <div
            key={i}
            className={`h-1 flex-1 rounded-full transition-all duration-500 ${i <= step ? 'bg-primary-500' : 'bg-gray-200'}`}
          />
        ))}
      </div>

      {/* Animated step content */}
      <div key={animKey} className={`flex-1 flex flex-col px-6 pt-10 pb-8 ${animClass}`}>
        <span className="text-5xl mb-6 leading-none">{STEPS[step].emoji}</span>
        <h1 className="text-[2rem] font-black text-gray-900 leading-tight whitespace-pre-line mb-1.5">
          {STEPS[step].title}
        </h1>
        <p className="text-gray-400 text-sm mb-10">{STEPS[step].subtitle}</p>

        {/* Step 0 — Name */}
        {step === 0 && (
          <div className="flex flex-col gap-3">
            <input
              autoFocus
              placeholder="First name"
              value={form.firstName}
              onChange={set('firstName')}
              onKeyDown={handleKey}
              className="input-field text-lg font-semibold"
            />
            <input
              placeholder="Last name"
              value={form.lastName}
              onChange={set('lastName')}
              onKeyDown={handleKey}
              className="input-field text-lg font-semibold"
            />
            {error && <p className="text-red-500 text-sm font-medium">{error}</p>}
            <button onClick={goNext} className="btn-primary flex items-center justify-center gap-2">
              Continue <ArrowRight className="w-5 h-5" />
            </button>
            <p className="text-center text-gray-500 text-sm mt-2">
              Already have an account?{' '}
              <Link to="/login" className="text-primary-500 font-bold">Sign In</Link>
            </p>
          </div>
        )}

        {/* Step 1 — Email */}
        {step === 1 && (
          <div className="flex flex-col gap-3">
            <input
              autoFocus
              type="email"
              inputMode="email"
              placeholder="you@example.com"
              value={form.email}
              onChange={set('email')}
              onKeyDown={handleKey}
              className="input-field text-lg font-semibold"
            />
            {error && <p className="text-red-500 text-sm font-medium">{error}</p>}
            <button onClick={goNext} className="btn-primary flex items-center justify-center gap-2">
              Continue <ArrowRight className="w-5 h-5" />
            </button>
          </div>
        )}

        {/* Step 2 — Phone */}
        {step === 2 && (
          <div className="flex flex-col gap-3">
            <input
              autoFocus
              type="tel"
              inputMode="tel"
              placeholder="+27 82 000 0000"
              value={form.phoneNumber}
              onChange={set('phoneNumber')}
              onKeyDown={handleKey}
              className="input-field text-lg font-semibold"
            />
            {error && <p className="text-red-500 text-sm font-medium">{error}</p>}
            <button onClick={goNext} className="btn-primary flex items-center justify-center gap-2">
              Continue <ArrowRight className="w-5 h-5" />
            </button>
          </div>
        )}

        {/* Step 3 — Password */}
        {step === 3 && (
          <div className="flex flex-col gap-3">
            <div className="relative">
              <input
                autoFocus
                type={showPw ? 'text' : 'password'}
                placeholder="Password (min. 8 characters)"
                value={form.password}
                onChange={set('password')}
                onKeyDown={handleKey}
                className="input-field pr-12"
              />
              <button
                type="button"
                onClick={() => setShowPw((v) => !v)}
                className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              >
                {showPw ? <Eye className="w-5 h-5" /> : <EyeOff className="w-5 h-5" />}
              </button>
            </div>
            {form.password.length > 0 && (
              <div className="flex items-center gap-2">
                {(['bg-red-400', 'bg-amber-400', 'bg-yellow-400', 'bg-primary-500'] as const).map((c, i) => (
                  <div
                    key={i}
                    className={`h-1 flex-1 rounded-full transition-all duration-300 ${pwStrength > i ? c : 'bg-gray-200'}`}
                  />
                ))}
                <span className="text-xs font-medium text-gray-400 w-12 text-right">
                  {['', 'Weak', 'Fair', 'Good', 'Strong'][pwStrength]}
                </span>
              </div>
            )}
            <div className="relative">
              <input
                type={showCf ? 'text' : 'password'}
                placeholder="Confirm password"
                value={form.confirm}
                onChange={set('confirm')}
                onKeyDown={handleKey}
                className="input-field pr-12"
              />
              <button
                type="button"
                onClick={() => setShowCf((v) => !v)}
                className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              >
                {showCf ? <Eye className="w-5 h-5" /> : <EyeOff className="w-5 h-5" />}
              </button>
            </div>
            {error && <p className="text-red-500 text-sm font-medium">{error}</p>}
            <p className="text-xs text-gray-400 text-center leading-relaxed">
              By creating an account you agree to DHA's{' '}
              <span className="text-primary-500 font-semibold">Terms of Service</span> and{' '}
              <span className="text-primary-500 font-semibold">POPIA Policy</span>.
            </p>
            <button onClick={handleSubmit} disabled={loading} className="btn-primary flex items-center justify-center gap-2">
              {loading ? (
                <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                </svg>
              ) : (
                <><Check className="w-5 h-5" /> Create Account</>
              )}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
