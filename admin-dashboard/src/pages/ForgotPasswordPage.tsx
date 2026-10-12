import { useState, type FormEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { MailQuestion } from 'lucide-react'
import { authService } from '../api/Api'

export default function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [identifier, setIdentifier] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      await authService.forgotPassword({ identifier })
      navigate('/reset-password', { state: { identifier } })
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Something went wrong. Try again.')
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
            <MailQuestion size={28} className="text-white" />
          </div>
          <div className="text-center">
            <h1 className="font-bold text-slate-900 text-2xl">Forgot Password</h1>
            <p className="text-sm text-slate-500 mt-1">
              We'll send a reset code to your email or phone
            </p>
          </div>
        </div>

        <div className="w-full h-px bg-gradient-to-r from-transparent via-slate-200 to-transparent"></div>

        <div>
          <label className="text-sm font-semibold text-slate-700 mb-1.5 block">
            Email or Phone Number
          </label>
          <input
            required
            autoFocus
            value={identifier}
            onChange={(e) => setIdentifier(e.target.value)}
            className="input-field"
            placeholder="staff@dha.gov.za"
          />
        </div>

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
          {loading ? 'Sending…' : 'Send Reset Code'}
        </button>

        <Link
          to="/login"
          className="text-xs text-center text-slate-500 mt-1 hover:text-blue-600 transition-colors"
        >
          Back to sign in
        </Link>
      </form>
    </div>
  )
}
