import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Fingerprint } from 'lucide-react'
import { startRegistration, startAuthentication } from '@simplewebauthn/browser'
import { useAuthStore } from '../store/AuthStore'
import { webauthnService } from '../api/Api'

type Mode = 'checking' | 'enroll' | 'verify' | 'working'

export default function FingerprintGatePage() {
  const navigate = useNavigate()
  const setFingerprintVerified = useAuthStore((s) => s.setFingerprintVerified)
  const [mode, setMode] = useState<Mode>('checking')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    checkEnrollment()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const checkEnrollment = async () => {
    try {
      // authenticate/options 409s with NO_ENROLLMENT if this user has no registered credential yet
      await webauthnService.authenticateOptions()
      setMode('verify')
    } catch (err: any) {
      if (err.response?.data?.message === 'NO_ENROLLMENT') {
        setMode('enroll')
      } else {
        setError('Could not reach the fingerprint service. Is the backend running?')
      }
    }
  }

  const enroll = async () => {
    setMode('working')
    setError(null)
    try {
      const optionsRes = await webauthnService.registerOptions()
      const optionsJSON = JSON.parse(optionsRes.data.data)
      const attestation = await startRegistration({ optionsJSON })
      await webauthnService.registerVerify(attestation)
      await verify()
    } catch (err) {
      console.error('[fingerprint] enrolment failed', err)
      setError('Fingerprint enrolment failed or was cancelled. Try again.')
      setMode('enroll')
    }
  }

  const verify = async () => {
    setMode('working')
    setError(null)
    try {
      const optionsRes = await webauthnService.authenticateOptions()
      const optionsJSON = JSON.parse(optionsRes.data.data)
      const assertion = await startAuthentication({ optionsJSON })
      await webauthnService.authenticateVerify(assertion)
      setFingerprintVerified()
      navigate('/', { replace: true })
    } catch (err) {
      console.error('[fingerprint] verification failed', err)
      setError('Fingerprint not recognised or was cancelled. Try again.')
      setMode('verify')
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-[#0f1f3d] to-slate-900 flex items-center justify-center p-4 relative overflow-hidden">
      <div className="absolute inset-0 overflow-hidden">
        <div className="absolute -top-40 -right-40 w-80 h-80 bg-blue-500/10 rounded-full blur-3xl"></div>
        <div className="absolute -bottom-40 -left-40 w-80 h-80 bg-blue-600/10 rounded-full blur-3xl"></div>
      </div>

      <div className="bg-white/95 backdrop-blur-sm rounded-3xl shadow-2xl border border-slate-200 w-full max-w-md p-10 flex flex-col items-center text-center gap-5 relative animate-fade-in">
        <div className="w-20 h-20 rounded-full bg-gradient-to-br from-[#2563EB] to-[#1e40af] flex items-center justify-center shadow-lg">
          <Fingerprint size={36} className="text-white" />
        </div>

        {mode === 'checking' && (
          <div className="flex flex-col items-center gap-3">
            <div className="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
            <p className="text-sm text-slate-600 font-medium">Checking fingerprint enrolment…</p>
          </div>
        )}

        {mode === 'enroll' && (
          <>
            <div className="text-center">
              <h2 className="font-bold text-slate-900 text-2xl mb-2">Enrol Your Fingerprint</h2>
              <p className="text-sm text-slate-600 leading-relaxed">
                First time on this device — register your fingerprint sensor (Touch ID / Windows
                Hello) to secure counter access.
              </p>
            </div>
            <button
              onClick={enroll}
              className="btn-primary w-full flex items-center justify-center gap-2"
            >
              <Fingerprint size={18} />
              Enrol Fingerprint
            </button>
          </>
        )}

        {mode === 'verify' && (
          <>
            <div className="text-center">
              <h2 className="font-bold text-slate-900 text-2xl mb-2">Verify Your Identity</h2>
              <p className="text-sm text-slate-600 leading-relaxed">
                Confirm it's you before opening the counter dashboard.
              </p>
            </div>
            <button
              onClick={verify}
              className="btn-primary w-full flex items-center justify-center gap-2"
            >
              <Fingerprint size={18} />
              Verify Fingerprint
            </button>
          </>
        )}

        {mode === 'working' && (
          <div className="flex flex-col items-center gap-3">
            <div className="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
            <p className="text-sm text-slate-600 font-medium">Waiting for sensor…</p>
          </div>
        )}

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl flex items-start gap-2 w-full">
            <svg className="w-5 h-5 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
            </svg>
            <span>{error}</span>
          </div>
        )}
      </div>
    </div>
  )
}
