import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Mail, ArrowLeft } from 'lucide-react'
import toast from 'react-hot-toast'

export default function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async () => {
    if (!email.includes('@')) {
      toast.error('Please enter a valid email address.')
      return
    }
    setLoading(true)
    // Simulate network call — replace with real API when endpoint is ready
    await new Promise((r) => setTimeout(r, 1000))
    setLoading(false)
    setSent(true)
  }

  return (
    <div className="min-h-screen flex flex-col px-6 pt-12 pb-8 bg-white">
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-2 text-gray-500 mb-8 self-start"
      >
        <ArrowLeft className="w-4 h-4" />
        <span className="text-sm font-medium">Back to Login</span>
      </button>

      {sent ? (
        <div className="flex flex-col items-center text-center gap-4 mt-10">
          <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center">
            <Mail className="w-8 h-8 text-green-600" />
          </div>
          <h2 className="text-xl font-bold text-gray-900">Check your email</h2>
          <p className="text-sm text-gray-500 max-w-xs">
            If an account with <span className="font-semibold text-gray-700">{email}</span> exists,
            you will receive a password reset link shortly.
          </p>
          <button
            onClick={() => navigate('/login')}
            className="mt-4 w-full bg-green-500 text-white font-bold py-4 rounded-2xl hover:bg-green-600 transition-all"
          >
            Return to Login
          </button>
        </div>
      ) : (
        <>
          <h2 className="text-2xl font-bold text-gray-900 mb-1">Forgot Password?</h2>
          <p className="text-sm text-gray-500 mb-8">
            Enter your email and we'll send you a reset link.
          </p>

          <div className="flex flex-col gap-4">
            <div className="relative">
              <Mail className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                type="email"
                placeholder="Email address"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full border border-gray-200 rounded-2xl px-4 py-4 pl-11 text-base placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-green-500"
              />
            </div>

            <button
              onClick={handleSubmit}
              disabled={loading}
              className="w-full bg-green-500 text-white font-bold py-4 rounded-2xl hover:bg-green-600 transition-all active:scale-95 disabled:opacity-50"
            >
              {loading ? 'Sending…' : 'Send Reset Link'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}
