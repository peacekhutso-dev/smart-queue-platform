import { useState, useEffect } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Eye, EyeOff, Lock, ArrowRight, Phone } from 'lucide-react'
import toast from 'react-hot-toast'
import axios from '../api/Axios'
import { useAuthStore } from '../store/AuthStore'

export default function LoginPage() {
  const navigate  = useNavigate()
  const [params]  = useSearchParams()

  useEffect(() => {
    if (params.get('reason') === 'session_expired') {
      toast.error('Your session expired. Please log in again.')
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const [form, setForm]             = useState({ identifier: '', password: '' })
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading]       = useState(false)
  const [errors, setErrors]         = useState<Record<string, string>>({})

  const validate = () => {
    const e: Record<string, string> = {}
    if (!form.identifier.trim()) e.identifier = 'Phone or email is required.'
    if (!form.password)          e.password   = 'Password is required.'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleLogin = async () => {
    if (!validate()) return
    setLoading(true)
    try {
      const res  = await axios.post('/auth/login', { identifier: form.identifier, password: form.password })
      const data = res.data.data
      if (data?.token) {
        useAuthStore.getState().setAuth({
          id:         String(data.userId),
          name:       data.firstName && data.lastName ? `${data.firstName} ${data.lastName}` : 'Citizen',
          email:      data.email      ?? '',
          phone:      data.phoneNumber ?? '',
          saIdNumber: data.saIdNumber  ?? '',
          verified:   data.isVerified  ?? false,
          province:   data.province    ?? '',
          createdAt:  new Date().toISOString(),
        }, data.token)
        navigate('/home')
      }
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Login failed. Please check your credentials.'
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex flex-col bg-white">
      {/* Hero banner */}
      <div className="hero-gradient px-6 pt-14 pb-10 relative overflow-hidden">
        {/* Decorative circles */}
        <div className="absolute -top-10 -right-10 w-40 h-40 rounded-full bg-white/10" />
        <div className="absolute top-20 -right-6 w-24 h-24 rounded-full bg-white/10" />
        <div className="absolute -bottom-6 -left-6 w-32 h-32 rounded-full bg-white/10" />

        <div className="relative z-10">
          <div className="flex items-center gap-3 mb-6">
            <div className="w-12 h-12 rounded-2xl bg-white/20 flex items-center justify-center backdrop-blur-sm">
              <span className="text-2xl">🦅</span>
            </div>
            <div>
              <p className="text-white font-bold text-base leading-tight">Department of Home Affairs</p>
              <p className="text-white/70 text-xs">Republic of South Africa</p>
            </div>
          </div>
          <h1 className="text-3xl font-black text-white leading-tight">Welcome back,</h1>
          <h1 className="text-3xl font-black text-white/80 leading-tight">Citizen 👋</h1>
          <p className="text-white/70 text-sm mt-2">Sign in to manage your queue & services</p>
        </div>
      </div>

      {/* Form card */}
      <div className="flex-1 px-5 py-6 flex flex-col gap-4 -mt-4 bg-white rounded-t-3xl relative z-10">
        {/* Identifier */}
        <div>
          <label className="section-label mb-2 block">Phone or Email</label>
          <div className="relative">
            <Phone className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              placeholder="+27 82 000 0000 or email"
              value={form.identifier}
              onChange={(e) => setForm({ ...form, identifier: e.target.value })}
              className={`input-field pl-11 ${errors.identifier ? 'input-error' : ''}`}
            />
          </div>
          {errors.identifier && <p className="text-red-500 text-xs mt-1.5 ml-1">{errors.identifier}</p>}
        </div>

        {/* Password */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="section-label">Password</label>
            <Link to="/forgot-password" className="text-xs text-primary-500 font-semibold">Forgot Password?</Link>
          </div>
          <div className="relative">
            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              placeholder="Enter your password"
              type={showPassword ? 'text' : 'password'}
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              onKeyDown={(e) => e.key === 'Enter' && handleLogin()}
              className={`input-field pl-11 pr-11 ${errors.password ? 'input-error' : ''}`}
            />
            <button
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
            >
              {showPassword ? <Eye className="w-4 h-4" /> : <EyeOff className="w-4 h-4" />}
            </button>
          </div>
          {errors.password && <p className="text-red-500 text-xs mt-1.5 ml-1">{errors.password}</p>}
        </div>

        <button
          onClick={handleLogin}
          disabled={loading}
          className="btn-primary flex items-center justify-center gap-2 mt-1"
        >
          {loading ? (
            <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
            </svg>
          ) : (
            <>Sign In <ArrowRight className="w-4 h-4" /></>
          )}
        </button>

        <div className="flex items-center gap-3 my-1">
          <div className="flex-1 h-px bg-gray-100" />
          <span className="text-xs text-gray-400 font-medium">or continue with</span>
          <div className="flex-1 h-px bg-gray-100" />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <button className="flex items-center justify-center gap-2.5 border border-gray-200 rounded-2xl px-4 py-3.5 hover:bg-gray-50 transition-colors">
            <span className="text-base font-black" style={{ color: '#4285F4' }}>G</span>
            <span className="text-sm font-semibold text-gray-700">Google</span>
          </button>
          <button className="flex items-center justify-center gap-2.5 border border-gray-200 rounded-2xl px-4 py-3.5 hover:bg-gray-50 transition-colors">
            <span className="text-base">🍎</span>
            <span className="text-sm font-semibold text-gray-700">Apple</span>
          </button>
        </div>

        <p className="text-center text-gray-500 text-sm mt-2">
          Don't have an account?{' '}
          <Link to="/register" className="text-primary-500 font-bold">Create Account</Link>
        </p>
      </div>
    </div>
  )
}
