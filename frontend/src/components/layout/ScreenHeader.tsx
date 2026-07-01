import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Settings } from 'lucide-react'

interface ScreenHeaderProps {
  title: string
  showBack?: boolean
  showSettings?: boolean
  onBack?: () => void
  rightElement?: React.ReactNode
}

export default function ScreenHeader({
  title, showBack = true, showSettings = true, onBack, rightElement
}: ScreenHeaderProps) {
  const navigate = useNavigate()
  return (
    <div className="screen-header bg-white sticky top-0 z-10">
      <div className="w-8">
        {showBack && (
          <button
            onClick={onBack ?? (() => navigate(-1))}
            className="p-1 rounded-full hover:bg-gray-100 transition-colors"
          >
            <ArrowLeft className="w-5 h-5 text-gray-700" />
          </button>
        )}
      </div>
      <span className="text-base font-semibold text-gray-900">{title}</span>
      <div className="w-8 flex justify-end">
        {rightElement ?? (showSettings && (
          <button
            onClick={() => navigate('/settings')}
            className="p-1 rounded-full hover:bg-gray-100 transition-colors"
            aria-label="Settings"
          >
            <Settings className="w-5 h-5 text-gray-500" />
          </button>
        ))}
      </div>
    </div>
  )
}
