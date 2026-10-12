import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { User, BadgeCheck, Globe2, LifeBuoy, ChevronRight, ChevronLeft, FileQuestion } from 'lucide-react'
import { useCatalogStore } from '@/store/CatalogStore'

// Presentational only — icon choice per applicant-type code, not DHA business
// data. Falls back to a generic icon so a new admin-added applicant type
// never breaks rendering.
const ICONS: Record<string, typeof User> = {
  SA_CITIZEN: User,
  PERMANENT_RESIDENT: BadgeCheck,
  FOREIGN_NATIONAL: Globe2,
  REFUGEE_ASYLUM_SEEKER: LifeBuoy,
}

export default function ApplicantTypePage() {
  const navigate = useNavigate()
  const { applicantTypes, loaded, loading, loadCatalog, setSelectedApplicantType } = useCatalogStore()

  useEffect(() => {
    loadCatalog()
  }, [loadCatalog])

  const handleSelect = (code: string) => {
    setSelectedApplicantType(code)
    navigate('/home')
  }

  return (
    <div className="flex flex-col min-h-screen">
      <div className="flex items-center px-5 h-20 border-b border-gray-100">
        <button onClick={() => navigate(-1)} className="p-1 rounded-full hover:bg-gray-100 shrink-0">
          <ChevronLeft className="w-5 h-5 text-gray-600" />
        </button>
        <div className="flex-1 text-center">
          <span className="text-base font-semibold text-gray-900">Department of Home Affairs</span>
          <p className="text-xs text-gray-400 mt-0.5">Step 1 of 3 · Who are you applying as?</p>
        </div>
        <div className="w-7 shrink-0" />
      </div>

      <div className="px-5 md:px-8 py-6 flex flex-col gap-5 w-full max-w-2xl mx-auto">
        <div>
          <h3 className="text-xl font-bold text-gray-900">Select Applicant Type</h3>
          <p className="text-sm text-gray-500 mt-1">
            This determines which services, fees, and documents apply to you.
          </p>
        </div>

        {(!loaded && loading) && (
          <div className="grid grid-cols-1 gap-3">
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="skeleton h-20 rounded-2xl" />
            ))}
          </div>
        )}

        {loaded && applicantTypes.length === 0 && (
          <p className="text-sm text-gray-500">
            No applicant types are configured yet. Please try again later.
          </p>
        )}

        <div className="grid grid-cols-1 gap-3">
          {applicantTypes.map((at) => {
            const Icon = ICONS[at.code] ?? FileQuestion
            return (
              <button
                key={at.code}
                onClick={() => handleSelect(at.code)}
                className="flex items-center gap-4 text-left border-2 border-gray-200 bg-white hover:border-green-400 hover:bg-green-50 rounded-2xl p-4 transition-all duration-200"
              >
                <div className="w-12 h-12 rounded-full bg-gray-100 flex items-center justify-center shrink-0">
                  <Icon className="w-6 h-6 text-gray-600" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-bold text-gray-900">{at.displayName}</p>
                  {at.description && (
                    <p className="text-xs text-gray-500 mt-0.5 leading-snug">{at.description}</p>
                  )}
                </div>
                <ChevronRight className="w-4 h-4 text-gray-400 shrink-0" />
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}
