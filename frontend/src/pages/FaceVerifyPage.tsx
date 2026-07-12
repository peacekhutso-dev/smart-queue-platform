import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Shield, RotateCcw, Lock, AlertCircle, CheckCircle } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../api/Axios'
import { useAuthStore } from '../store/AuthStore'

type Phase = 'idle' | 'starting' | 'streaming' | 'capturing' | 'processing' | 'success' | 'error'
type Mode  = 'enroll' | 'verify'

export default function FaceVerifyPage() {
  const navigate       = useNavigate()
  const [params]       = useSearchParams()
  const mode: Mode     = (params.get('mode') as Mode) ?? 'enroll'
  const returnTo       = params.get('returnTo') ?? '/home'

  const { setFaceVerified } = useAuthStore()

  const videoRef  = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const streamRef = useRef<MediaStream | null>(null)

  const [phase,   setPhase]   = useState<Phase>('idle')
  const [message, setMessage] = useState<string>('')
  const [cameraFacing, setCameraFacing] = useState<'user' | 'environment'>('user')

  useEffect(() => {
    startCamera()
    return () => stopCamera()
  }, [cameraFacing])

  const startCamera = async () => {
    stopCamera()
    setPhase('starting')
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: cameraFacing,
          width: { ideal: 640 },
          height: { ideal: 480 },
        },
      })
      streamRef.current = stream
      if (videoRef.current) {
        videoRef.current.srcObject = stream
        await videoRef.current.play()
      }
      setPhase('streaming')
    } catch {
      setPhase('error')
      setMessage('Camera access denied. Please allow camera permissions and try again.')
    }
  }

  const stopCamera = () => {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
  }

  const captureFrame = (): string | null => {
    if (!videoRef.current || !canvasRef.current) return null
    const video  = videoRef.current
    const canvas = canvasRef.current
    canvas.width  = video.videoWidth
    canvas.height = video.videoHeight
    canvas.getContext('2d')!.drawImage(video, 0, 0)
    return canvas.toDataURL('image/jpeg', 0.85)
  }

  const handleAction = async () => {
    const imageBase64 = captureFrame()
    if (!imageBase64) return

    setPhase('processing')
    setMessage('')

    try {
      const endpoint = mode === 'enroll' ? '/auth/face/enroll' : '/auth/face/verify'
      await api.post(endpoint, { imageBase64 })

      setPhase('success')
      stopCamera()

      if (mode === 'verify') {
        setFaceVerified()
        toast.success('Face verified — access granted')
        setTimeout(() => navigate(returnTo, { replace: true }), 800)
      } else {
        toast.success('Face enrolled successfully!')
        setTimeout(() => navigate('/applicant-type', { replace: true }), 800)
      }
    } catch (err: unknown) {
      const errData = (err as { response?: { data?: { data?: { code?: string }; message?: string } } })?.response?.data
      const code    = errData?.data?.code
      const msg     = errData?.message

      if (code === 'NO_ENROLLMENT' && mode === 'verify') {
        // Shouldn't happen here, but handle gracefully
        navigate('/verify-face?mode=enroll', { replace: true })
        return
      }

      setPhase('error')
      setMessage(msg ?? `Face ${mode === 'enroll' ? 'enrollment' : 'verification'} failed. Please try again.`)
    }
  }

  const retry = () => {
    setPhase('idle')
    setMessage('')
    startCamera()
  }

  const isEnroll = mode === 'enroll'

  return (
    <div className="min-h-screen bg-black flex flex-col">

      {/* Camera viewport */}
      <div className="relative flex-1 bg-gray-900 flex items-center justify-center overflow-hidden">
        <video
          ref={videoRef}
          className="absolute inset-0 w-full h-full object-cover"
          style={{ transform: cameraFacing === 'user' ? 'scaleX(-1)' : 'none' }}
          playsInline
          muted
        />
        <canvas ref={canvasRef} className="hidden" />

        {/* Dark overlay when not streaming */}
        {(phase === 'idle' || phase === 'starting') && (
          <div className="absolute inset-0 bg-gray-900 flex items-center justify-center">
            <div className="w-8 h-8 border-4 border-green-400 border-t-transparent rounded-full animate-spin" />
          </div>
        )}

        {/* Corner brackets */}
        <div className="absolute top-6 left-6 w-8 h-8 border-t-4 border-l-4 border-green-400 rounded-tl-lg" />
        <div className="absolute top-6 right-6 w-8 h-8 border-t-4 border-r-4 border-green-400 rounded-tr-lg" />
        <div className="absolute bottom-52 left-6 w-8 h-8 border-b-4 border-l-4 border-green-400 rounded-bl-lg" />
        <div className="absolute bottom-52 right-6 w-8 h-8 border-b-4 border-r-4 border-green-400 rounded-br-lg" />

        {/* Face oval */}
        <div className={`relative w-52 h-64 rounded-full border-4 border-dashed transition-colors ${
          phase === 'success' ? 'border-green-400' :
          phase === 'error'   ? 'border-red-400'   :
                                'border-green-400/80'
        }`} />

        {/* Top bar */}
        <div className="absolute top-4 left-0 right-0 flex items-center justify-between px-4">
          <button
            onClick={() => navigate(-1)}
            className="w-10 h-10 rounded-full bg-black/50 flex items-center justify-center text-white text-lg"
          >
            ←
          </button>
          <div className="bg-black/60 rounded-full px-3 py-1.5 flex items-center gap-1.5">
            <Lock className="w-3 h-3 text-green-400" />
            <span className="text-white text-xs font-bold">ZERO TRUST</span>
          </div>
          <button
            onClick={() => setCameraFacing((f) => f === 'user' ? 'environment' : 'user')}
            className="w-10 h-10 rounded-full bg-black/50 flex items-center justify-center text-white text-xs"
          >
            ⇄
          </button>
        </div>

        {/* Status badge */}
        {phase === 'processing' && (
          <div className="absolute top-24 inset-x-0 flex justify-center">
            <div className="bg-green-500 rounded-full px-4 py-2 flex items-center gap-2">
              <div className="w-2 h-2 rounded-full bg-white animate-ping" />
              <span className="text-white text-sm font-bold">SCANNING…</span>
            </div>
          </div>
        )}

        {phase === 'success' && (
          <div className="absolute inset-0 flex items-center justify-center bg-green-500/20 backdrop-blur-sm">
            <CheckCircle className="w-24 h-24 text-green-400" />
          </div>
        )}

        {/* Side labels */}
        <div className="absolute left-4 top-1/2 -translate-y-1/2 text-white/60 text-xs font-bold tracking-widest writing-vertical">
          STAY STILL
        </div>
      </div>

      {/* Bottom sheet */}
      <div className="bg-white rounded-t-3xl px-6 pt-6 pb-8 flex flex-col gap-4">
        <div className="text-center">
          <h2 className="text-2xl font-bold text-gray-900">
            {isEnroll ? 'Enrol Your Face' : 'Verify Your Identity'}
          </h2>
          <p className="text-gray-500 text-sm mt-1">
            {isEnroll
              ? 'Position your face in the frame. This becomes your biometric reference.'
              : 'Look directly at the camera to verify your identity.'}
          </p>
        </div>

        {/* Error message */}
        {phase === 'error' && message && (
          <div className="flex items-start gap-2 bg-red-50 border border-red-200 rounded-2xl p-3">
            <AlertCircle className="w-5 h-5 text-red-500 flex-shrink-0 mt-0.5" />
            <p className="text-red-700 text-sm">{message}</p>
          </div>
        )}

        {phase === 'error' ? (
          <button
            onClick={retry}
            className="w-full flex items-center justify-center gap-2 bg-gray-900 text-white font-bold py-4 rounded-2xl"
          >
            <RotateCcw className="w-4 h-4" /> Retry
          </button>
        ) : (
          <button
            onClick={handleAction}
            disabled={phase !== 'streaming'}
            className="w-full bg-green-500 text-white font-bold py-4 rounded-2xl hover:bg-green-600 transition-all active:scale-95 disabled:opacity-50"
          >
            {phase === 'processing' ? 'Processing…' :
             phase === 'success'    ? isEnroll ? 'Enrolled ✓' : 'Verified ✓' :
             phase === 'starting' || phase === 'idle' ? 'Starting camera…' :
             isEnroll ? 'Enrol Face' : 'Verify Identity'}
          </button>
        )}

        <div className="border border-gray-100 rounded-2xl p-4 flex items-start gap-3">
          <Shield className="w-5 h-5 text-gray-400 flex-shrink-0" />
          <div>
            <p className="text-xs font-bold text-gray-500 uppercase tracking-wide">
              {isEnroll ? 'Biometric Enrolment' : 'Secure Verification'}
            </p>
            <p className="text-xs text-gray-400 mt-0.5">
              {isEnroll
                ? 'Your face embedding is encrypted and stored securely. Never shared.'
                : 'Zero Trust: every session requires a fresh face scan. Data stays on-device during capture.'}
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
