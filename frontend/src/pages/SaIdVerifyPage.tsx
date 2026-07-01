import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CreditCard, ShieldCheck, HelpCircle } from 'lucide-react'

export default function SaIdVerifyPage() {
  const navigate = useNavigate()
  const [idNumber, setIdNumber] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const validateLuhn = (id: string): boolean => {
    if (!/^\d{13}$/.test(id)) return false
    let sum = 0
    for (let i = 0; i < 12; i++) {
      let d = parseInt(id[i])
      if (i % 2 !== 0) { d *= 2; if (d > 9) d -= 9 }
      sum += d
    }
    return (10 - (sum % 10)) % 10 === parseInt(id[12])
  }

  const handleContinue = async () => {
    setError('')
    if (!validateLuhn(idNumber)) {
      setError('The ID number provided is invalid. Please check the digits and try again.')
      return
    }
    setLoading(true)
    try {
      // POST SA ID to backend for Luhn validation + DB storage
      const token = localStorage.getItem('dha_token')
      await fetch('http://localhost:8080/api/auth/verify-id', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ saIdNumber: idNumber }),
      })
    } catch {
      // Continue even if the API call fails — backend validates later
    } finally {
      setLoading(false)
      // After ID verify, proceed to face enrolment (Zero Trust Gate 3 setup)
      navigate('/verify-face?mode=enroll')
    }
  }

  return (
    <div className="min-h-screen bg-white flex flex-col">
      <div className="flex items-center px-5 py-4 border-b border-gray-100">
        <button onClick={() => navigate(-1)} className="p-1 rounded-full hover:bg-gray-100 mr-3">←</button>
        <span className="flex-1 text-center text-base font-semibold text-gray-900">Department of Home Affairs</span>
      </div>

      <div className="flex-1 px-6 py-8 flex flex-col gap-6">
        <div>
          <h2 className="text-3xl font-bold text-gray-900">Verify your</h2>
          <h2 className="text-3xl font-bold text-green-500 italic">Identity</h2>
          <p className="text-gray-500 mt-2 text-sm">Please enter your 13-digit South African ID number to begin.</p>
        </div>

        <div>
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2 text-sm font-medium text-gray-700">
              <CreditCard className="w-4 h-4 text-green-500" />
              SA Identity Number
            </div>
            <span className="text-xs bg-gray-100 text-gray-500 px-2 py-1 rounded-full">13 Digits</span>
          </div>
          <input
            type="text"
            inputMode="numeric"
            maxLength={13}
            placeholder="YYMMDDSSSSCA Z"
            value={idNumber}
            onChange={e => { setIdNumber(e.target.value.replace(/\D/g, '')); setError('') }}
            className={`w-full border-2 rounded-2xl px-4 py-4 text-lg font-mono tracking-widest focus:outline-none transition-all
              ${error ? 'border-red-400 bg-red-50' : 'border-gray-200 focus:border-green-500'}`}
          />
          {error && (
            <div className="mt-2 bg-red-50 border border-red-200 rounded-xl px-4 py-3 flex items-start gap-2">
              <span className="text-red-500 mt-0.5">⊘</span>
              <p className="text-red-600 text-sm">{error}</p>
            </div>
          )}
        </div>

        <button
          onClick={handleContinue}
          disabled={loading || idNumber.length !== 13}
          className="w-full bg-green-500 text-white font-bold py-4 rounded-2xl hover:bg-green-600 transition-all active:scale-95 disabled:opacity-50"
        >
          {loading ? 'Verifying...' : 'Continue to Verification'}
        </button>

        <button className="w-full border border-gray-200 rounded-2xl py-3.5 flex items-center justify-center gap-2 text-sm text-gray-600 hover:bg-gray-50">
          <HelpCircle className="w-4 h-4 text-green-500" />
          Need help with your ID?
        </button>

        <div className="border border-gray-200 rounded-2xl p-4 flex items-start gap-3">
          <ShieldCheck className="w-5 h-5 text-gray-400 flex-shrink-0 mt-0.5" />
          <div>
            <p className="text-xs font-bold text-gray-500 uppercase tracking-wide mb-1">Secure Verification</p>
            <p className="text-xs text-gray-400 leading-relaxed">
              Your data is encrypted and protected by the Department of Home Affairs.
              By continuing, you agree to our{' '}
              <span className="text-green-500 underline">Privacy Policy</span> and{' '}
              <span className="text-green-500 underline">POPIA compliance</span> standards.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}