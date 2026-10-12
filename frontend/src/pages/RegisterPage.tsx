import { useState, useEffect, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, ArrowLeft, ArrowRight, Check, Loader2, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { useAuthStore } from '../store/AuthStore'
import axios from '../api/Axios'

type Availability = 'idle' | 'checking' | 'taken' | 'available'

// Cheap unique-index lookup on identity-service — safe to call on every
// pause in typing rather than only finding out "already registered" at
// final submit.
async function checkTaken(params: { email?: string; phoneNumber?: string }): Promise<boolean> {
  const res = await axios.get('/auth/check-availability', { params })
  const data = res.data.data as { emailTaken?: boolean; phoneTaken?: boolean }
  return params.email ? !!data.emailTaken : !!data.phoneTaken
}

const FORM_KEY = 'dha_register_form'

interface Form {
  firstName: string; lastName: string; email: string
  phoneNumber: string; password: string; confirm: string
}
const BLANK: Form = { firstName: '', lastName: '', email: '', phoneNumber: '', password: '', confirm: '' }

function AvailabilityIcon({ status }: { status: Availability }) {
  if (status === 'checking') return <Loader2 className="w-5 h-5 text-gray-400 animate-spin absolute right-3 top-1/2 -translate-y-1/2" />
  if (status === 'available') return <Check className="w-5 h-5 text-primary-500 absolute right-3 top-1/2 -translate-y-1/2" />
  if (status === 'taken') return <X className="w-5 h-5 text-red-500 absolute right-3 top-1/2 -translate-y-1/2" />
  return null
}

function loadForm(): Form {
  try { return { ...BLANK, ...JSON.parse(sessionStorage.getItem(FORM_KEY) ?? '{}') } }
  catch { return BLANK }
}

export default function RegisterPage() {
  const navigate   = useNavigate()
  const setPending = useAuthStore((s) => s.setPending)

  const [form, setForm]     = useState<Form>(loadForm)
  const [showPw, setShowPw] = useState(false)
  const [showCf, setShowCf] = useState(false)
  const [error, setError]   = useState('')
  const [loading, setLoading] = useState(false)
  const [success, setSuccess] = useState(false)

  const [emailStatus, setEmailStatus] = useState<Availability>('idle')
  const [phoneStatus, setPhoneStatus] = useState<Availability>('idle')
  const emailCheckId = useRef(0)
  const phoneCheckId = useRef(0)

  useEffect(() => { sessionStorage.setItem(FORM_KEY, JSON.stringify(form)) }, [form])

  // Debounced instant check — fires as the citizen types, not just on submit.
  useEffect(() => {
    if (!form.email.includes('@')) { setEmailStatus('idle'); return }
    setEmailStatus('checking')
    const id = ++emailCheckId.current
    const t = setTimeout(() => {
      checkTaken({ email: form.email })
        .then((taken) => { if (emailCheckId.current === id) setEmailStatus(taken ? 'taken' : 'available') })
        .catch(() => { if (emailCheckId.current === id) setEmailStatus('idle') })
    }, 350)
    return () => clearTimeout(t)
  }, [form.email])

  useEffect(() => {
    if (form.phoneNumber.replace(/\D/g, '').length < 10) { setPhoneStatus('idle'); return }
    setPhoneStatus('checking')
    const id = ++phoneCheckId.current
    const t = setTimeout(() => {
      checkTaken({ phoneNumber: form.phoneNumber })
        .then((taken) => { if (phoneCheckId.current === id) setPhoneStatus(taken ? 'taken' : 'available') })
        .catch(() => { if (phoneCheckId.current === id) setPhoneStatus('idle') })
    }, 350)
    return () => clearTimeout(t)
  }, [form.phoneNumber])

  const set = (field: keyof Form) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((f) => ({ ...f, [field]: e.target.value }))
    setError('')
  }

  const validate = (): boolean => {
    if (!form.firstName.trim()) { setError('Please enter your first name.'); return false }
    if (!form.lastName.trim())  { setError('Please enter your last name.'); return false }
    if (!form.email.includes('@')) { setError('Please enter a valid email address.'); return false }
    if (emailStatus === 'taken') { setError('This email is already registered.'); return false }
    if (form.phoneNumber.replace(/\D/g, '').length < 10) {
      setError('Please enter a valid SA phone number.')
      return false
    }
    if (phoneStatus === 'taken') { setError('This phone number is already registered.'); return false }
    if (form.password.length < 8) { setError('Password must be at least 8 characters.'); return false }
    if (form.password !== form.confirm) { setError('Passwords do not match.'); return false }
    return true
  }

  const handleSubmit = async () => {
    if (!validate()) return

    // A fast typer can submit before the debounced availability check
    // lands — never let them through on an unconfirmed result.
    if (emailStatus !== 'available') {
      setLoading(true)
      const taken = await checkTaken({ email: form.email }).catch(() => false)
      setLoading(false)
      setEmailStatus(taken ? 'taken' : 'available')
      if (taken) { setError('This email is already registered.'); return }
    }
    if (phoneStatus !== 'available') {
      setLoading(true)
      const taken = await checkTaken({ phoneNumber: form.phoneNumber }).catch(() => false)
      setLoading(false)
      setPhoneStatus(taken ? 'taken' : 'available')
      if (taken) { setError('This phone number is already registered.'); return }
    }

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
      // The JWT is set as an httpOnly cookie by the server now, not returned
      // in the body — userId presence is what confirms a successful registration.
      if (data?.userId) {
        useAuthStore.getState().setAuth({
          id:         String(data.userId),
          name:       `${form.firstName} ${form.lastName}`,
          email:      data.email,
          phone:      form.phoneNumber,
          saIdNumber: '',
          verified:   data.isVerified ?? false,
          province:   '',
          createdAt:  new Date().toISOString(),
        })
        setPending(form.phoneNumber)
        sessionStorage.removeItem(FORM_KEY)
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

  return (
    <div className="min-h-dvh md:min-h-0 flex flex-col bg-white overflow-x-hidden">
      {/* Top nav */}
      <div className="flex items-center justify-between px-5 pt-12 pb-4">
        <button
          onClick={() => navigate('/login')}
          className="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center hover:bg-gray-200 transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-gray-700" />
        </button>
        <div className="w-10" />
      </div>
      <div className="flex justify-center px-8 pb-2">
        <img src="/dha-logo.webp" alt="Department of Home Affairs" className="w-full max-w-[220px] object-contain" />
      </div>

      <div className="flex-1 flex flex-col px-6 pt-6 pb-8">
        <h1 className="text-[2rem] font-black text-gray-900 leading-tight mb-1.5">
          Create your account
        </h1>
        <p className="text-gray-400 text-sm mb-8">Join Home Affairs' virtual queue in a few details</p>

        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => { e.preventDefault(); handleSubmit() }}
        >
          <div className="grid grid-cols-2 gap-3">
            <input
              autoFocus
              placeholder="First name"
              value={form.firstName}
              onChange={set('firstName')}
              className="input-field text-base font-semibold"
            />
            <input
              placeholder="Last name"
              value={form.lastName}
              onChange={set('lastName')}
              className="input-field text-base font-semibold"
            />
          </div>

          <div className="relative">
            <input
              type="email"
              inputMode="email"
              placeholder="you@example.com"
              value={form.email}
              onChange={set('email')}
              className="input-field text-base font-semibold pr-10"
            />
            <AvailabilityIcon status={emailStatus} />
          </div>
          {emailStatus === 'taken' && (
            <p className="text-red-500 text-sm font-medium -mt-2">This email is already registered.</p>
          )}
          {emailStatus === 'available' && (
            <p className="text-primary-500 text-sm font-medium -mt-2">Email available</p>
          )}

          <div className="relative">
            <input
              type="tel"
              inputMode="tel"
              placeholder="+27 82 000 0000"
              value={form.phoneNumber}
              onChange={set('phoneNumber')}
              className="input-field text-base font-semibold pr-10"
            />
            <AvailabilityIcon status={phoneStatus} />
          </div>
          {phoneStatus === 'taken' && (
            <p className="text-red-500 text-sm font-medium -mt-2">This phone number is already registered.</p>
          )}
          {phoneStatus === 'available' && (
            <p className="text-primary-500 text-sm font-medium -mt-2">Phone number available</p>
          )}

          <div className="relative">
            <input
              type={showPw ? 'text' : 'password'}
              placeholder="Password (min. 8 characters)"
              value={form.password}
              onChange={set('password')}
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
            <div className="flex items-center gap-2 -mt-1">
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

          <button type="submit" disabled={loading} className="btn-primary flex items-center justify-center gap-2">
            {loading ? (
              <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
              </svg>
            ) : (
              <><Check className="w-5 h-5" /> Create Account</>
            )}
          </button>

          <p className="text-center text-gray-500 text-sm mt-1">
            Already have an account?{' '}
            <Link to="/login" className="text-primary-500 font-bold">Sign In</Link>
          </p>
        </form>
      </div>
    </div>
  )
}
