import { useEffect, useRef, useState } from 'react'
import { Shield, X, RotateCcw } from 'lucide-react'
import toast from 'react-hot-toast'
import { useAuthStore } from '../store/AuthStore'
import api from '../api/Axios'

type Phase = 'idle' | 'streaming' | 'scanning' | 'success' | 'error'

export default function FaceScanModal() {
  const { showFaceModal, onFaceSuccess, onFaceCancel } = useAuthStore()

  const videoRef  = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const streamRef = useRef<MediaStream | null>(null)

  const [phase, setPhase]   = useState<Phase>('idle')
  const [message, setMessage] = useState('')

  // Start camera when modal opens
  useEffect(() => {
    if (showFaceModal) {
      setPhase('idle')
      setMessage('')
      startCamera()
    } else {
      stopCamera()
    }
    return () => stopCamera()
  }, [showFaceModal])

  const startCamera = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } },
      })
      streamRef.current = stream
      if (videoRef.current) {
        videoRef.current.srcObject = stream
        videoRef.current.play()
      }
      setPhase('streaming')
    } catch {
      setPhase('error')
      setMessage('Camera access denied. Please allow camera permissions.')
    }
  }

  const stopCamera = () => {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
  }

  const captureAndVerify = async () => {
    if (!videoRef.current || !canvasRef.current) return
    setPhase('scanning')

    const video  = videoRef.current
    const canvas = canvasRef.current
    canvas.width  = video.videoWidth
    canvas.height = video.videoHeight
    canvas.getContext('2d')!.drawImage(video, 0, 0)
    const imageBase64 = canvas.toDataURL('image/jpeg', 0.85)

    try {
      await api.post('/auth/face/verify', { imageBase64 })
      setPhase('success')
      stopCamera()
      setTimeout(() => {
        onFaceSuccess()
        toast.success('Face verified — access granted')
      }, 800)
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { data?: { code?: string }; message?: string } } })?.response?.data
      const code = data?.data?.code

      if (code === 'NO_ENROLLMENT') {
        stopCamera()
        onFaceCancel()
        toast.error('No face enrolled. Please complete face setup.')
        window.location.href = '/verify-face?mode=enroll'
        return
      }

      setPhase('error')
      setMessage(data?.message ?? 'Face not recognized. Please try again.')
    }
  }

  const retry = () => {
    setPhase('idle')
    setMessage('')
    startCamera()
  }

  if (!showFaceModal) return null

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/70">
      <div className="w-full max-w-md bg-black rounded-t-3xl overflow-hidden animate-slide-up">

        {/* Close */}
        <div className="flex justify-between items-center px-5 pt-5 pb-2">
          <div className="flex items-center gap-2">
            <Shield className="w-5 h-5 text-green-400" />
            <span className="text-white font-bold text-sm tracking-widest">ZERO TRUST — FACE CHECK</span>
          </div>
          <button onClick={onFaceCancel} className="text-gray-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Camera viewport */}
        <div className="relative mx-4 rounded-2xl overflow-hidden bg-gray-900" style={{ aspectRatio: '4/3' }}>
          <video
            ref={videoRef}
            className="w-full h-full object-cover scale-x-[-1]"
            playsInline
            muted
          />

          {/* Face oval overlay */}
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <div className={`w-44 h-56 rounded-full border-4 border-dashed transition-colors ${
              phase === 'success' ? 'border-green-400' :
              phase === 'error'   ? 'border-red-400'   :
                                    'border-green-400/70'
            }`} />
          </div>

          {/* Corner brackets */}
          {['tl','tr','bl','br'].map((c) => (
            <div key={c} className={`absolute w-6 h-6 border-green-400 ${
              c === 'tl' ? 'top-3 left-3 border-t-2 border-l-2 rounded-tl' :
              c === 'tr' ? 'top-3 right-3 border-t-2 border-r-2 rounded-tr' :
              c === 'bl' ? 'bottom-3 left-3 border-b-2 border-l-2 rounded-bl' :
                           'bottom-3 right-3 border-b-2 border-r-2 rounded-br'
            }`} />
          ))}

          {/* Status badge */}
          {phase === 'scanning' && (
            <div className="absolute top-4 inset-x-0 flex justify-center">
              <div className="bg-green-500 rounded-full px-4 py-1.5 flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-white animate-ping" />
                <span className="text-white text-xs font-bold">SCANNING…</span>
              </div>
            </div>
          )}
          {phase === 'success' && (
            <div className="absolute inset-0 flex items-center justify-center bg-green-500/20">
              <span className="text-6xl">✓</span>
            </div>
          )}
        </div>

        {/* Hidden canvas for frame capture */}
        <canvas ref={canvasRef} className="hidden" />

        {/* Bottom controls */}
        <div className="px-5 py-5 space-y-3">
          {phase === 'error' && message && (
            <p className="text-red-400 text-sm text-center">{message}</p>
          )}

          {(phase === 'streaming' || phase === 'scanning') && (
            <p className="text-gray-400 text-xs text-center">
              Position your face within the oval and look directly at the camera
            </p>
          )}

          {phase === 'error' ? (
            <button
              onClick={retry}
              className="w-full flex items-center justify-center gap-2 bg-gray-800 text-white font-bold py-3.5 rounded-2xl"
            >
              <RotateCcw className="w-4 h-4" /> Try Again
            </button>
          ) : (
            <button
              onClick={captureAndVerify}
              disabled={phase !== 'streaming'}
              className="w-full bg-green-500 text-white font-bold py-3.5 rounded-2xl disabled:opacity-40 transition-all"
            >
              {phase === 'scanning' ? 'Verifying…' :
               phase === 'success'  ? 'Verified ✓'  :
               phase === 'idle'     ? 'Starting camera…' :
                                      'Verify My Face'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
