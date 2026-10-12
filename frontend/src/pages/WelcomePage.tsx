import { useNavigate } from 'react-router-dom'
import { CreditCard, FileText, ClipboardCheck, Timer, Star, ArrowRight } from 'lucide-react'
import { useAuthStore } from '@/store/AuthStore'

const FEATURES = [
  {
    icon: CreditCard,
    title: 'Apply for DHA services',
    description: 'Smart ID cards, passports, birth, marriage and death certificates, visas and more.',
  },
  {
    icon: FileText,
    title: 'See what applies to you',
    description: 'Fees and required documents are tailored to your applicant type — no guesswork.',
  },
  {
    icon: ClipboardCheck,
    title: 'Upload documents in advance',
    description: 'Our AI checks your documents are valid before you travel to a branch.',
  },
  {
    icon: Timer,
    title: 'Book & track your queue',
    description: 'Reserve a slot at a nearby branch and follow your position in real time.',
  },
  {
    icon: Star,
    title: 'Rate your visit',
    description: 'Tell us how it went so we can keep improving the service.',
  },
]

export default function WelcomePage() {
  const navigate  = useNavigate()
  const user      = useAuthStore((s) => s.user)
  const firstName = user?.name?.split(' ')[0]

  return (
    <div className="flex flex-col min-h-screen">
      <div className="flex items-center justify-center px-5 h-20 border-b border-gray-100">
        <span className="text-base font-semibold text-gray-900">Department of Home Affairs</span>
      </div>

      <div className="px-5 md:px-8 py-6 flex flex-col gap-6 w-full max-w-2xl mx-auto">
        <div>
          <h3 className="text-xl font-bold text-gray-900">
            Hi{firstName ? ` ${firstName}` : ''}, welcome to the Department of Home Affairs 🇿🇦
          </h3>
          <p className="text-sm text-gray-500 mt-1">
            Here's what you can do on this platform:
          </p>
        </div>

        <div className="flex flex-col gap-3">
          {FEATURES.map(({ icon: Icon, title, description }) => (
            <div
              key={title}
              className="flex items-start gap-4 border-2 border-gray-200 bg-white rounded-2xl p-4"
            >
              <div className="w-12 h-12 rounded-full bg-gray-100 flex items-center justify-center shrink-0">
                <Icon className="w-6 h-6 text-gray-600" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-bold text-gray-900">{title}</p>
                <p className="text-xs text-gray-500 mt-0.5 leading-snug">{description}</p>
              </div>
            </div>
          ))}
        </div>

        <button
          onClick={() => navigate('/applicant-type')}
          className="w-full py-4 rounded-2xl text-base font-bold bg-green-500 text-white hover:bg-green-600 active:scale-95 transition-all duration-200 flex items-center justify-center gap-2"
        >
          Continue
          <ArrowRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  )
}
