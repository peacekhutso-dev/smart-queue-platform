import { useState, useRef, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Clock, CreditCard, Plane, Baby, Heart, FileText,
  ChevronRight, MapPin, Settings, Navigation2,
  Search, X, Loader2, ArrowLeft
} from 'lucide-react'
import { useJsApiLoader } from '@react-google-maps/api'
import { useAuthStore } from '@/store/AuthStore'
import { useCatalogStore } from '@/store/CatalogStore'
import type { ServiceOffering } from '@/types'

const LIBRARIES: ('places')[] = ['places']

// Presentational only — icon choice per service-code prefix, not DHA
// business data. Falls back to a generic icon for any unmapped/admin-added
// service so rendering never breaks.
const SERVICE_ICON = (code: string) => {
  if (code.startsWith('SMART_ID') || code === 'TIC') return CreditCard
  if (code.startsWith('PASSPORT') || code.includes('VISA') || code.includes('TRAVEL')) return Plane
  if (code.startsWith('BIRTH')) return Baby
  if (code.startsWith('MARRIAGE')) return Heart
  return FileText
}

interface Suggestion {
  placeId: string
  mainText: string
  secondaryText: string
}

export default function HomePage() {
  const navigate = useNavigate()
  const user     = useAuthStore((s) => s.user)
  const { selectedApplicantType, loaded, loading, loadCatalog, setSelectedService, servicesForSelectedApplicantType } = useCatalogStore()
  const services = servicesForSelectedApplicantType()

  useEffect(() => {
    loadCatalog()
  }, [loadCatalog])

  useEffect(() => {
    if (loaded && !selectedApplicantType) navigate('/welcome', { replace: true })
  }, [loaded, selectedApplicantType, navigate])

  const [selected, setSelected]       = useState<string | null>(null)
  const [location, setLocation]       = useState<string | null>(null)
  const [sheetOpen, setSheetOpen]     = useState(false)
  const [query, setQuery]             = useState('')
  const [suggestions, setSuggestions] = useState<Suggestion[]>([])
  const [gpsLoading, setGpsLoading]   = useState(false)

  const acRef       = useRef<google.maps.places.AutocompleteService | null>(null)
  const geocRef     = useRef<google.maps.Geocoder | null>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const inputRef    = useRef<HTMLInputElement>(null)

  const { isLoaded } = useJsApiLoader({
    id: 'google-map-script',
    googleMapsApiKey: import.meta.env.VITE_GOOGLE_MAPS_API_KEY,
    libraries: LIBRARIES,
  })

  useEffect(() => {
    if (isLoaded) {
      acRef.current   = new google.maps.places.AutocompleteService()
      geocRef.current = new google.maps.Geocoder()
    }
  }, [isLoaded])

  useEffect(() => {
    if (sheetOpen) setTimeout(() => inputRef.current?.focus(), 100)
  }, [sheetOpen])

  const handleQueryChange = (val: string) => {
    setQuery(val)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (!val.trim() || !acRef.current) { setSuggestions([]); return }
    debounceRef.current = setTimeout(() => {
      acRef.current!.getPlacePredictions(
        { input: val, componentRestrictions: { country: 'za' } },
        (results, status) => {
          if (status === google.maps.places.PlacesServiceStatus.OK && results) {
            setSuggestions(results.slice(0, 5).map((r) => ({
              placeId:       r.place_id,
              mainText:      r.structured_formatting.main_text,
              secondaryText: r.structured_formatting.secondary_text,
            })))
          } else setSuggestions([])
        }
      )
    }, 300)
  }

  const pickSuggestion = useCallback((s: Suggestion) => {
    setLocation(`${s.mainText}, ${s.secondaryText}`)
    setQuery('')
    setSuggestions([])
    setSheetOpen(false)
  }, [])

  const useGPS = () => {
    if (!navigator.geolocation) {
      import('react-hot-toast').then(({ default: toast }) => toast.error('Geolocation not supported on this device.'))
      return
    }
    setGpsLoading(true)
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const latlng = { lat: pos.coords.latitude, lng: pos.coords.longitude }
        if (geocRef.current) {
          geocRef.current.geocode({ location: latlng }, (results, status) => {
            setGpsLoading(false)
            if (status === 'OK' && results?.[0]) {
              const comps  = results[0].address_components
              const suburb = comps.find((c) => c.types.includes('sublocality_level_1'))?.long_name
              const city   = comps.find((c) => c.types.includes('locality'))?.long_name
              setLocation(suburb && city ? `${suburb}, ${city}` : results[0].formatted_address)
            } else {
              setLocation('Current location')
            }
            setSheetOpen(false)
          })
        } else {
          // Maps API not ready — still save raw coords label
          setGpsLoading(false)
          setLocation('Current location')
          setSheetOpen(false)
        }
      },
      (err) => {
        setGpsLoading(false)
        const msg = err.code === 1 ? 'Location permission denied. Please allow access in your browser.'
                  : err.code === 3 ? 'Location request timed out. Try again.'
                  : 'Could not get your location.'
        import('react-hot-toast').then(({ default: toast }) => toast.error(msg))
      },
      { timeout: 8000, enableHighAccuracy: true }
    )
  }

  const handleProceed = () => {
    const service = services.find((s: ServiceOffering) => s.code === selected)
    if (!service) return
    setSelectedService(service)
    navigate(`/documents?serviceType=${service.code}`)
  }

  return (
    <div className="flex flex-col min-h-0">

      {/* ── Header ── */}
      <div className="flex items-center justify-between px-5 md:px-8 h-20 border-b border-gray-100">
        <span className="text-base font-semibold text-gray-900">Department of Home Affairs</span>
        <button onClick={() => navigate('/settings')} className="p-1.5 rounded-full hover:bg-gray-100 transition-colors">
          <Settings className="w-5 h-5 text-gray-500" />
        </button>
      </div>

      <div className="h-px bg-gray-200" />

      {/* ── Location row ── */}
      <div className="px-5 md:px-8 py-4 border-b border-gray-100">
        <button
          onClick={() => setSheetOpen(true)}
          className="flex items-center gap-3 bg-white border-2 border-gray-200 hover:border-gray-300 transition-colors rounded-2xl px-4 py-3.5 w-full text-left"
        >
          <div className="w-8 h-8 rounded-full bg-green-500 flex items-center justify-center shrink-0">
            <MapPin className="w-4 h-4 text-white" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Where are you?</p>
            <p className={`text-sm font-bold truncate mt-0.5 ${location ? 'text-gray-900' : 'text-gray-400'}`}>
              {location ?? 'Set your location…'}
            </p>
          </div>
          <ChevronRight className="w-4 h-4 text-gray-400 shrink-0" />
        </button>
      </div>

      <div className="px-5 md:px-8 py-4 md:py-6 flex flex-col gap-5 pb-8 w-full">

        {/* ── Services grid ── */}
        <div>
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-xl font-bold text-gray-900">Choose a Service</h3>
            <button className="text-sm text-green-500 font-semibold">View Requirements</button>
          </div>

          {loading && services.length === 0 && (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              {[0, 1, 2, 3].map((i) => <div key={i} className="skeleton h-32 rounded-2xl" />)}
            </div>
          )}

          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {services.map((service: ServiceOffering) => {
              const Icon = SERVICE_ICON(service.code)
              return (
                <button
                  key={service.code}
                  onClick={() => setSelected(service.code)}
                  className={`text-left border-2 rounded-2xl p-4 transition-all duration-200 ${
                    selected === service.code ? 'border-green-500 bg-green-50' : 'border-gray-200 bg-white hover:border-gray-300'
                  }`}
                >
                  <div className="flex items-start justify-between mb-3">
                    <div className={`w-10 h-10 rounded-full flex items-center justify-center ${selected === service.code ? 'bg-green-100' : 'bg-gray-100'}`}>
                      <Icon className={`w-5 h-5 ${selected === service.code ? 'text-green-600' : 'text-gray-500'}`} />
                    </div>
                    <ChevronRight className="w-4 h-4 text-gray-400" />
                  </div>
                  <p className="text-sm font-bold text-gray-900">{service.name}</p>
                  {service.description && (
                    <p className="text-xs text-gray-500 mt-0.5 leading-snug">{service.description}</p>
                  )}
                  <div className="flex items-center justify-between mt-2">
                    {service.estimatedTurnaround ? (
                      <div className="flex items-center gap-1">
                        <Clock className="w-3 h-3 text-gray-400" />
                        <span className="text-xs text-gray-400">{service.estimatedTurnaround}</span>
                      </div>
                    ) : <span />}
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-green-100 text-green-700 border border-green-200">
                      {service.feeDisplay}
                    </span>
                  </div>
                </button>
              )
            })}
          </div>
        </div>

        {/* ── Proceed ── */}
        <button
          onClick={handleProceed}
          disabled={!selected}
          className={`w-full py-4 rounded-2xl text-base font-bold transition-all duration-200 ${
            selected ? 'bg-green-500 text-white hover:bg-green-600 active:scale-95' : 'bg-gray-100 text-gray-400 cursor-not-allowed'
          }`}
        >
          {selected ? `Continue with ${services.find((s: ServiceOffering) => s.code === selected)?.name}` : 'Please select a service'}
        </button>
      </div>

      {/* ── Location modal (Uber-style full overlay) ── */}
      {sheetOpen && (
        <div className="fixed inset-0 z-50 flex flex-col bg-white">

          {/* Top bar with search */}
          <div className="flex items-center gap-3 px-4 pt-12 pb-4 border-b border-gray-100">
            <button
              onClick={() => { setSheetOpen(false); setQuery(''); setSuggestions([]) }}
              className="w-9 h-9 flex items-center justify-center rounded-full hover:bg-gray-100 transition-colors shrink-0"
            >
              <ArrowLeft className="w-5 h-5 text-gray-700" />
            </button>
            <div className="flex-1 flex items-center gap-2 bg-gray-100 rounded-2xl px-4 py-2.5">
              <Search className="w-4 h-4 text-gray-400 shrink-0" />
              <input
                ref={inputRef}
                type="text"
                placeholder="Search suburb, city or address…"
                value={query}
                onChange={(e) => handleQueryChange(e.target.value)}
                className="flex-1 bg-transparent text-sm text-gray-800 placeholder-gray-400 focus:outline-none"
              />
              {query && (
                <button onClick={() => { setQuery(''); setSuggestions([]) }}>
                  <X className="w-4 h-4 text-gray-400" />
                </button>
              )}
            </div>
          </div>

          {/* Use my location row */}
          <button
            onClick={useGPS}
            disabled={gpsLoading}
            className="flex items-center gap-4 px-5 py-4 border-b border-gray-100 hover:bg-gray-50 transition-colors disabled:opacity-60"
          >
            <div className="w-10 h-10 rounded-full bg-green-100 flex items-center justify-center shrink-0">
              {gpsLoading
                ? <Loader2 className="w-5 h-5 text-green-600 animate-spin" />
                : <Navigation2 className="w-5 h-5 text-green-600" />}
            </div>
            <div className="text-left">
              <p className="text-sm font-bold text-gray-900">Use my current location</p>
              <p className="text-xs text-gray-400 mt-0.5">Auto-detect where you are now</p>
            </div>
          </button>

          {/* Suggestions */}
          <div className="flex-1 overflow-y-auto">
            {suggestions.length === 0 && !query && (
              <div className="flex flex-col items-center justify-center h-48 gap-2 text-gray-300">
                <MapPin className="w-8 h-8" />
                <p className="text-sm font-medium text-gray-400">Start typing to search</p>
              </div>
            )}

            {suggestions.map((s, i) => (
              <button
                key={s.placeId}
                onClick={() => pickSuggestion(s)}
                className={`w-full flex items-center gap-4 px-5 py-4 text-left hover:bg-gray-50 transition-colors ${
                  i > 0 ? 'border-t border-gray-100' : ''
                }`}
              >
                <div className="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center shrink-0">
                  <MapPin className="w-4 h-4 text-gray-500" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-gray-900 truncate">{s.mainText}</p>
                  <p className="text-xs text-gray-400 truncate mt-0.5">{s.secondaryText}</p>
                </div>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
