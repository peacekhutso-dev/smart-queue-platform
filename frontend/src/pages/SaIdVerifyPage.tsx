import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CreditCard, ShieldCheck, HelpCircle } from 'lucide-react'
import axios from '../api/Axios'

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
      // POST SA ID to backend for Luhn validation + DB storage. Goes through
      // the shared axios instance (relative /api path via nginx's same-origin
      // proxy, withCredentials for the httpOnly cookie) like every other
      // page — a hardcoded absolute http://localhost:8080 URL here previously
      // bypassed that proxy and got blocked by the browser at the CORS
      // preflight stage, since api-gateway has no CORS config of its own
      // (it's only ever meant to be reached same-origin, through nginx).
      await axios.post('/auth/verify-id', { saIdNumber: idNumber })
      // Also grants the "identity confirmed" Zero Trust gate right away using
      // the ID number just entered — otherwise a brand-new registration would
      // sail through every step here only to get silently logged back out at
      // the first FULL-tier page it hits, since that gate is normally granted
      // by Login's own confirm-identity step, which a fresh registration
      // never passes through. Best-effort: if it fails (e.g. name doesn't
      // match Home Affairs' record), the gate stays ungranted and the citizen
      // is correctly asked for it on next login instead — never a hard block
      // here, same as verify-id above.
      await axios.post('/auth/confirm-identity', { saIdNumber: idNumber })
    } catch {
      // Continue even if either API call fails — backend validates later
    } finally {
      setLoading(false)
      // After ID verify, run the live face check (Zero Trust Gate 3 setup).
      // There's no separate "enroll" step — DHA is simulated as already
      // having your photo on file, and this scan verifies against it.
      // Goes to /home — HomePage itself still redirects to /welcome first
      // for a citizen who hasn't picked an applicant type yet, so this isn't
      // a way to skip that step, just the default landing spot afterward.
      navigate('/verify-face?returnTo=/home')
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