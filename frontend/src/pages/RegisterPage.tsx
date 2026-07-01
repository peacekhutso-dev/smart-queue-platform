import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { User, Mail, Phone, Lock, MapPin } from 'lucide-react'
import toast from 'react-hot-toast'
import { useAuthStore } from '../store/AuthStore'
import axios from '../api/Axios'

export default function RegisterPage() {
  const navigate   = useNavigate()
  const setPending = useAuthStore((s) => s.setPending)

  const [form, setForm] = useState({
    firstName: '', lastName: '', email: '',
    phoneNumber: '', password: '', confirm: '', province: ''
  })
  const [loading, setLoading] = useState(false)
  const [errors, setErrors]   = useState<Record<string, string>>({})

  const validate = () => {
    const e: Record<string, string> = {}
    if (!form.firstName.trim())          e.firstName   = 'First name is required.'
    if (!form.lastName.trim())           e.lastName    = 'Last name is required.'
    if (!form.email.includes('@'))       e.email       = 'Valid email is required.'
    if (form.phoneNumber.length < 10)    e.phoneNumber = 'Valid SA phone number required.'
    if (form.password.length < 8)        e.password    = 'Password must be at least 8 characters.'
    if (form.password !== form.confirm)  e.confirm     = 'Passwords do not match.'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleRegister = async () => {
    if (!validate()) return
    setLoading(true)
    try {
      const res = await axios.post('/auth/register', {
        firstName:   form.firstName,
        lastName:    form.lastName,
        email:       form.email,
        phoneNumber: form.phoneNumber,
        password:    form.password,
        province:    form.province,
      })

      // 🔍 DEBUG — paste what this prints in the console
      console.log('RAW RESPONSE:', JSON.stringify(res.data, null, 2))

      const data = res.data.data

      console.log('data.token:', data?.token)
      console.log('data.userId:', data?.userId)

      if (data?.token) {
        useAuthStore.getState().setAuth({
          id:         String(data.userId),
          name:       `${form.firstName} ${form.lastName}`,
          email:      data.email,
          phone:      form.phoneNumber,
          saIdNumber: '',
          verified:   data.isVerified ?? false,
          province:   form.province,
          createdAt:  new Date().toISOString(),
        }, data.token)

        setPending(form.phoneNumber)
        console.log('Navigating to /verify-otp...')
        navigate('/verify-otp')
      } else {
        console.error('No token in response — not navigating. Full data:', data)
        toast.error('Registration error: no token received. Check console.')
      }
    } catch (err: unknown) {
      console.error('REGISTER ERROR:', err)
      const axiosErr = err as { response?: { data?: { message?: string }, status?: number } }
      console.error('Status:', axiosErr?.response?.status)
      console.error('Body:', axiosErr?.response?.data)
      const msg = axiosErr?.response?.data?.message
        ?? 'Registration failed. Email or phone may already be in use.'
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  const set = (field: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [field]: e.target.value }))

  const inputClass = "w-full border border-gray-200 rounded-2xl px-4 py-4 text-base placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-green-500"

  return (
    <div className="min-h-screen flex flex-col bg-white">
      {/* Header */}
      <div className="flex items-center px-5 py-4 border-b border-gray-100">
        <button onClick={() => navigate(-1)} className="p-1 rounded-full hover:bg-gray-100 mr-3 text-lg">←</button>
        <span className="text-base font-semibold text-gray-900">Create Account</span>
      </div>

      <div className="px-5 py-4 flex flex-col gap-4 pb-8">
        <div className="mb-2">
          <h2 className="text-2xl font-bold text-gray-900">Join DHA Smart Queue</h2>
          <p className="text-sm text-gray-500 mt-1">Create your verified citizen account</p>
        </div>

        {/* First Name */}
        <div>
          <label className="text-sm font-medium text-gray-700 mb-1.5 block">First Name</label>
          <div className="relative">
            <User className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input placeholder="e.g. Thabo" value={form.firstName} onChange={set('firstName')} className={`${inputClass} pl-11`} />
          </div>
          {errors.firstName && <p className="text-red-500 text-xs mt-1">{errors.firstName}</p>}
        </div>

        {/* Last Name */}
        <div>
          <label className="text-sm font-medium text-gray-700 mb-1.5 block">Last Name</label>
          <div className="relative">
            <User className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input placeholder="e.g. Nkosi" value={form.lastName} onChange={set('lastName')} className={`${inputClass} pl-11`} />
          </div>
          {errors.lastName && <p className="text-red-500 text-xs mt-1">{errors.lastName}</p>}
        </div>

        {/* Email */}
        <div>
          <label className="text-sm font-medium text-gray-700 mb-1.5 block">Email Address</label>
          <div className="relative">
            <Mail className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input placeholder="you@example.com" type="email" value={form.email} onChange={set('email')} className={`${inputClass} pl-11`} />
          </div>
          {errors.email && <p className="text-red-500 text-xs mt-1">{errors.email}</p>}
        </div>

        {/* Phone */}
        <div>
          <label className="text-sm font-medium text-gray-700 mb-1.5 block">Phone Number</label>
          <div className="relative">
            <Phone className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input placeholder="+27 82 000 0000" type="tel" value={form.phoneNumber} onChange={set('phoneNumber')} className={`${inputClass} pl-11`} />
          </div>
          {errors.phoneNumber && <p className="text-red-500 text-xs mt-1">{errors.phoneNumber}</p>}
        </div>

        {/* Province */}
        <div>
          <label className="text-sm font-medium text-gray-700 mb-1.5 block">Province</label>
          <div className="relative">
            <MapPin className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <select value={form.province} onChange={set('province')} className={`${inputClass} pl-11 appearance-none`}>
              <option value="">Select province...</option>
              <option>Gauteng</option>
              <option>Western Cape</option>
              <option>KwaZulu-Natal</option>
              <option>Eastern Cape</option>
              <option>Limpopo</option>
              <option>Mpumalanga</option>
              <option>North West</option>
              <option>Free State</option>
              <option>Northern Cape</option>
            </select>
          </div>
        </div>

        {/* Password */}
        <div>
          <label className="text-sm font-medium text-gray-700 mb-1.5 block">Password</label>
          <div className="relative">
            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input placeholder="Min. 8 characters" type="password" value={form.password} onChange={set('password')} className={`${inputClass} pl-11`} />
          </div>
          {errors.password && <p className="text-red-500 text-xs mt-1">{errors.password}</p>}
        </div>

        {/* Confirm Password */}
        <div>
          <label className="text-sm font-medium text-gray-700 mb-1.5 block">Confirm Password</label>
          <div className="relative">
            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input placeholder="Repeat your password" type="password" value={form.confirm} onChange={set('confirm')} className={`${inputClass} pl-11`} />
          </div>
          {errors.confirm && <p className="text-red-500 text-xs mt-1">{errors.confirm}</p>}
        </div>

        <p className="text-xs text-gray-400 text-center">
          By registering you agree to the DHA Terms of Service and POPIA Policy.
        </p>

        <button
          onClick={handleRegister}
          disabled={loading}
          className="w-full bg-green-500 text-white font-bold py-4 rounded-2xl hover:bg-green-600 transition-all active:scale-95 disabled:opacity-50"
        >
          {loading ? 'Creating account...' : 'Create Account'}
        </button>

        <p className="text-center text-sm text-gray-500">
          Already have an account?{' '}
          <Link to="/login" className="text-green-500 font-bold">Login</Link>
        </p>
      </div>
    </div>
  )
}