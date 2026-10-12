import { useState } from 'react'
import { Fingerprint, X } from 'lucide-react'
import { startAuthentication } from '@simplewebauthn/browser'
import { useAuthStore } from '../store/AuthStore'
import { webauthnService } from '../api/Api'

export default function FingerprintModal() {
  const { showFingerprintModal, onFingerprintSuccess, onFingerprintCancel } = useAuthStore()
  const [scanning, setScanning] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!showFingerprintModal) return null

  const scan = async () => {
    setScanning(true)
    setError(null)
    try {
      const optionsRes = await webauthnService.authenticateOptions()
      const optionsJSON = JSON.parse(optionsRes.data.data)
      const assertion = await startAuthentication({ optionsJSON })
      await webauthnService.authenticateVerify(assertion)
      onFingerprintSuccess()
    } catch (err) {
      console.error('[fingerprint] verification failed', err)
      setError('Fingerprint not recognised. Touch the sensor again.')
    } finally {
      setScanning(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 animate-fade-in">
      <div className="bg-white rounded-3xl shadow-2xl border border-slate-200 w-full max-w-md p-8 relative animate-slide-up">
        <button
          onClick={onFingerprintCancel}
          className="absolute top-5 right-5 text-slate-400 hover:text-slate-600 transition-colors"
        >
          <X size={20} />
        </button>

        <div className="flex flex-col items-center text-center gap-5">
          <div
            className={`w-24 h-24 rounded-full flex items-center justify-center transition-all duration-300 ${
              scanning
                ? 'bg-gradient-to-br from-blue-100 to-blue-50 animate-pulse shadow-lg shadow-blue-200'
                : 'bg-gradient-to-br from-slate-100 to-slate-50'
            }`}
          >
            <Fingerprint
              size={44}
              className={`transition-colors duration-300 ${
                scanning ? 'text-blue-600' : 'text-slate-500'
              }`}
            />
          </div>

          <div>
            <h3 className="font-bold text-slate-900 text-xl mb-2">Session Security Check</h3>
            <p className="text-sm text-slate-600 leading-relaxed">
              Your fingerprint session has expired. Verify with Touch ID / Windows Hello to
              continue.
            </p>
          </div>

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl flex items-start gap-2 w-full">
              <svg className="w-5 h-5 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
              </svg>
              <span>{error}</span>
            </div>
          )}

          <button
            onClick={scan}
            disabled={scanning}
            className="btn-primary w-full flex items-center justify-center gap-2"
          >
            {scanning ? (
              <>
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                Waiting for sensor…
              </>
            ) : (
              <>
                <Fingerprint size={18} />
                Verify Fingerprint
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  )
}
