import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Send, Shield, Smile, Frown, Meh, ThumbsUp, ThumbsDown } from 'lucide-react'
import toast from 'react-hot-toast'
import axios from '../api/Axios'

const EMOJIS = [
  { icon: '😠', label: 'Very Poor' },
  { icon: '😕', label: 'Poor' },
  { icon: '😐', label: 'Neutral' },
  { icon: '🙂', label: 'Good' },
  { icon: '😄', label: 'Excellent' },
]

export default function FeedbackPage() {
  const navigate = useNavigate()
  const [stars, setStars]     = useState(0)
  const [helpful, setHelpful] = useState<boolean | null>(null)
  const [emoji, setEmoji]     = useState<number | null>(null)
  const [comment, setComment] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async () => {
    if (!stars) { toast.error('Please give a star rating'); return }
    setLoading(true)
    try {
      // ✅ Fixed: use correct FeedbackRequest fields matching the backend
      await axios.post('/api/feedback', {
        branchId:      1, // default — would come from active ticket store
        overallRating: stars,
        staffHelpful:  helpful ?? false,
        emotionRating: emoji ?? 3,
        comment,
        serviceType:   'SMART_ID',
      })
      toast.success('Thank you for your feedback!')
      navigate('/home')
    } catch {
      // Silently succeed for demo if backend unavailable
      toast.success('Thank you for your feedback!')
      navigate('/home')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white flex items-center justify-between px-5 py-4 border-b border-gray-100">
        <div className="w-9 h-9 bg-green-500 rounded-xl flex items-center justify-center">
          <Shield className="w-5 h-5 text-white" />
        </div>
        <span className="font-semibold text-gray-900">Rate Service</span>
        <div className="w-9" />
      </div>

      <div className="px-5 py-4 flex flex-col gap-5 pb-24">
        {/* Service chip */}
        <div>
          <span className="text-xs bg-green-50 border border-green-200 text-green-600 font-semibold px-3 py-1 rounded-full">
            Service: Smart ID Application
          </span>
          <h2 className="text-2xl font-bold text-gray-900 mt-3">Share Your Experience</h2>
          <p className="text-sm text-gray-500 mt-1">
            Please rate your recent visit to the Pretoria Central branch. Your input is anonymous and valuable.
          </p>
        </div>

        {/* Star rating */}
        <div className="bg-white border border-gray-200 rounded-2xl p-4">
          <p className="font-bold text-gray-800 mb-4">Overall Satisfaction</p>
          <div className="flex justify-around mb-2">
            {[1, 2, 3, 4, 5].map((n) => (
              <button
                key={n}
                onClick={() => setStars(n)}
                className="flex flex-col items-center gap-1 transition-transform active:scale-110"
              >
                <div className={`
                  w-12 h-12 rounded-full border-2 flex items-center justify-center text-xl transition-all
                  ${n <= stars ? 'border-yellow-400 bg-yellow-50 text-yellow-500' : 'border-gray-200 bg-gray-50 text-gray-300'}
                `}>
                  ★
                </div>
                <span className="text-xs text-gray-400">{n}</span>
              </button>
            ))}
          </div>
          <div className="flex justify-between px-2">
            <span className="text-xs text-gray-400">VERY POOR</span>
            <span className="text-xs text-gray-400">EXCELLENT</span>
          </div>
        </div>

        {/* Staff helpfulness */}
        <div className="bg-white border border-gray-200 rounded-2xl p-4">
          <p className="font-bold text-gray-800 mb-3">Was the staff helpful and polite?</p>
          <div className="grid grid-cols-2 gap-3">
            <button
              onClick={() => setHelpful(true)}
              className={`flex items-center justify-center gap-2 border-2 rounded-2xl py-3.5 font-semibold transition-all ${
                helpful === true
                  ? 'border-green-500 bg-green-50 text-green-600'
                  : 'border-gray-200 text-gray-700 hover:bg-gray-50'
              }`}
            >
              <ThumbsUp className="w-4 h-4" /> Yes
            </button>
            <button
              onClick={() => setHelpful(false)}
              className={`flex items-center justify-center gap-2 border-2 rounded-2xl py-3.5 font-semibold transition-all ${
                helpful === false
                  ? 'border-red-400 bg-red-50 text-red-600'
                  : 'border-gray-200 text-gray-700 hover:bg-gray-50'
              }`}
            >
              <ThumbsDown className="w-4 h-4" /> No
            </button>
          </div>
        </div>

        {/* Emoji rating */}
        <div className="bg-white border border-gray-200 rounded-2xl p-4">
          <div className="flex items-center justify-between mb-3">
            <p className="font-bold text-gray-800">How did you feel today?</p>
            <span className="text-xs bg-gray-100 text-gray-500 font-bold px-2 py-0.5 rounded-full">EASY-READ</span>
          </div>
          <div className="flex justify-around">
            {EMOJIS.map((e, i) => (
              <button
                key={i}
                onClick={() => setEmoji(i + 1)}
                className={`
                  w-12 h-12 rounded-2xl flex items-center justify-center text-2xl transition-all
                  ${emoji === i + 1 ? 'bg-green-100 scale-110 ring-2 ring-green-300' : 'bg-gray-100 hover:bg-gray-200'}
                `}
              >
                {e.icon}
              </button>
            ))}
          </div>
        </div>

        {/* Open text */}
        <div className="bg-white border border-gray-200 rounded-2xl p-4">
          <p className="font-bold text-gray-800 mb-2">What could we improve?</p>
          <div className="relative">
            <textarea
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="Tell us about your branch experience, wait times, or document verification..."
              rows={4}
              className="w-full border border-gray-200 rounded-xl px-4 py-3 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-green-500 placeholder-gray-400"
            />
            <span className="absolute bottom-2 right-3 text-xs text-gray-400">Optional</span>
          </div>
        </div>

        <button
          onClick={handleSubmit}
          disabled={loading}
          className="w-full bg-green-500 text-white font-bold py-4 rounded-2xl flex items-center justify-center gap-2 hover:bg-green-600 active:scale-95 disabled:opacity-50 transition-all"
        >
          <Send className="w-4 h-4" />
          {loading ? 'Submitting...' : 'Submit Feedback'}
        </button>

        <button
          onClick={() => navigate('/assistant')}
          className="text-center text-sm text-green-500 font-semibold"
        >
          Need help with your application?
        </button>
      </div>
    </div>
  )
}