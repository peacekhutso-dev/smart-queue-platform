import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { GoogleMap, useJsApiLoader, Marker } from '@react-google-maps/api'
import {
  Navigation2, Search, X, ArrowLeft, MapPin,
  Clock, Users, ChevronRight, Wifi, Car, Accessibility, Loader2
} from 'lucide-react'
import { branchService } from '../api/Api'
import type { Branch } from '../types'

const LIBRARIES: ('places')[] = ['places']

type Screen = 'location' | 'branches'
type LatLng = { lat: number; lng: number }

interface Suggestion {
  placeId: string
  description: string
  mainText: string
  secondaryText: string
}

const CAPACITY_COLOR = (pct: number) =>
  pct < 50 ? 'bg-green-100 text-green-700 border-green-200'
  : pct < 80 ? 'bg-yellow-100 text-yellow-700 border-yellow-200'
  : 'bg-red-100 text-red-700 border-red-200'

const CAPACITY_LABEL = (pct: number) =>
  pct < 50 ? 'Low' : pct < 80 ? 'Busy' : 'Full'

export default function BranchPage() {
  const navigate    = useNavigate()
  const [screen, setScreen] = useState<Screen>('location')

  // ── location state ─────────────────────────────────────────────────────────
  const [query, setQuery]           = useState('')
  const [suggestions, setSuggestions] = useState<Suggestion[]>([])
  const [loadingLocation, setLoadingLocation] = useState(false)
  const [locationLabel, setLocationLabel] = useState('')
  const [userLatLng, setUserLatLng] = useState<LatLng | null>(null)

  // ── branches state ─────────────────────────────────────────────────────────
  const [branches, setBranches]       = useState<Branch[]>([])
  const [loadingBranches, setLoadingBranches] = useState(false)
  const [selectedBranch, setSelectedBranch]   = useState<Branch | null>(null)
  const [mapCenter, setMapCenter]     = useState<LatLng>({ lat: -25.7479, lng: 28.2293 })

  // ── Google Maps ────────────────────────────────────────────────────────────
  const { isLoaded } = useJsApiLoader({
    id: 'google-map-script',
    googleMapsApiKey: import.meta.env.VITE_GOOGLE_MAPS_API_KEY,
    libraries: LIBRARIES,
  })

  const autocompleteRef = useRef<google.maps.places.AutocompleteService | null>(null)
  const geocoderRef     = useRef<google.maps.Geocoder | null>(null)
  const debounceRef     = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (isLoaded) {
      autocompleteRef.current = new google.maps.places.AutocompleteService()
      geocoderRef.current     = new google.maps.Geocoder()
    }
  }, [isLoaded])

  // ── autocomplete input ─────────────────────────────────────────────────────
  const handleQueryChange = (val: string) => {
    setQuery(val)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (!val.trim() || !autocompleteRef.current) { setSuggestions([]); return }

    debounceRef.current = setTimeout(() => {
      autocompleteRef.current!.getPlacePredictions(
        { input: val, componentRestrictions: { country: 'za' } },
        (results, status) => {
          if (status === google.maps.places.PlacesServiceStatus.OK && results) {
            setSuggestions(results.map((r) => ({
              placeId:       r.place_id,
              description:   r.description,
              mainText:      r.structured_formatting.main_text,
              secondaryText: r.structured_formatting.secondary_text,
            })))
          } else {
            setSuggestions([])
          }
        }
      )
    }, 300)
  }

  // ── pick a suggestion ──────────────────────────────────────────────────────
  const selectSuggestion = useCallback((s: Suggestion) => {
    if (!geocoderRef.current) return
    setSuggestions([])
    setQuery(s.mainText)
    geocoderRef.current.geocode({ placeId: s.placeId }, (results, status) => {
      if (status === 'OK' && results?.[0]) {
        const loc = results[0].geometry.location
        goToBranches({ lat: loc.lat(), lng: loc.lng() }, s.description)
      }
    })
  }, [])

  // ── use current location ───────────────────────────────────────────────────
  const useMyLocation = () => {
    if (!navigator.geolocation) return
    setLoadingLocation(true)
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const latlng = { lat: pos.coords.latitude, lng: pos.coords.longitude }
        if (geocoderRef.current) {
          geocoderRef.current.geocode({ location: latlng }, (results, status) => {
            const label = status === 'OK' && results?.[0]
              ? results[0].formatted_address
              : 'Your location'
            setLoadingLocation(false)
            goToBranches(latlng, label)
          })
        } else {
          setLoadingLocation(false)
          goToBranches(latlng, 'Your location')
        }
      },
      () => setLoadingLocation(false),
      { timeout: 8000, enableHighAccuracy: true }
    )
  }

  // ── fetch branches + transition ────────────────────────────────────────────
  const goToBranches = async (latlng: LatLng, label: string) => {
    setUserLatLng(latlng)
    setLocationLabel(label)
    setMapCenter(latlng)
    setScreen('branches')
    setLoadingBranches(true)
    try {
      const res = await branchService.getNearby(latlng.lat, latlng.lng)
      setBranches(res.data.data ?? [])
    } catch {
      setBranches([])
    } finally {
      setLoadingBranches(false)
    }
  }

  const handleSelectBranch = (branch: Branch) => {
    setSelectedBranch(branch)
    setMapCenter({ lat: branch.latitude, lng: branch.longitude })
  }

  const handleConfirm = () => {
    if (!selectedBranch) return
    navigate('/ticket', { state: { branch: selectedBranch } })
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SCREEN 1 — location picker
  // ══════════════════════════════════════════════════════════════════════════
  if (screen === 'location') {
    return (
      <div className="flex flex-col min-h-screen bg-white">
        {/* Header */}
        <div className="flex items-center gap-3 px-5 py-4 border-b border-gray-100">
          <button
            onClick={() => navigate(-1)}
            className="w-9 h-9 flex items-center justify-center rounded-xl hover:bg-gray-100 transition-colors"
          >
            <ArrowLeft className="w-5 h-5 text-gray-600" />
          </button>
          <h1 className="text-base font-bold text-gray-900">Find a DHA Branch</h1>
        </div>

        <div className="flex flex-col gap-5 px-5 pt-6">
          {/* Use current location */}
          <button
            onClick={useMyLocation}
            disabled={!isLoaded || loadingLocation}
            className="flex items-center gap-4 p-4 border-2 border-green-500 rounded-2xl bg-green-50 hover:bg-green-100 transition-colors disabled:opacity-60"
          >
            <div className="w-11 h-11 rounded-full bg-green-500 flex items-center justify-center shrink-0">
              {loadingLocation
                ? <Loader2 className="w-5 h-5 text-white animate-spin" />
                : <Navigation2 className="w-5 h-5 text-white" />}
            </div>
            <div className="text-left">
              <p className="text-sm font-bold text-gray-900">Use my current location</p>
              <p className="text-xs text-gray-500 mt-0.5">Detect where you are now</p>
            </div>
          </button>

          <div className="flex items-center gap-3">
            <div className="flex-1 h-px bg-gray-200" />
            <span className="text-xs text-gray-400 font-medium">or search</span>
            <div className="flex-1 h-px bg-gray-200" />
          </div>

          {/* Search input */}
          <div className="relative">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search suburb, city or address…"
              value={query}
              onChange={(e) => handleQueryChange(e.target.value)}
              className="w-full border-2 border-gray-200 rounded-2xl px-4 py-4 pl-11 pr-10 text-sm text-gray-800 placeholder-gray-400 focus:outline-none focus:border-green-500 transition-colors"
              autoFocus
            />
            {query && (
              <button
                onClick={() => { setQuery(''); setSuggestions([]) }}
                className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          {/* Suggestions dropdown */}
          {suggestions.length > 0 && (
            <div className="flex flex-col border border-gray-200 rounded-2xl overflow-hidden shadow-lg -mt-2">
              {suggestions.map((s, i) => (
                <button
                  key={s.placeId}
                  onClick={() => selectSuggestion(s)}
                  className={`flex items-center gap-3 px-4 py-3.5 text-left hover:bg-gray-50 transition-colors ${
                    i > 0 ? 'border-t border-gray-100' : ''
                  }`}
                >
                  <div className="w-8 h-8 bg-gray-100 rounded-full flex items-center justify-center shrink-0">
                    <MapPin className="w-4 h-4 text-gray-500" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-gray-800 truncate">{s.mainText}</p>
                    <p className="text-xs text-gray-400 truncate">{s.secondaryText}</p>
                  </div>
                  <ChevronRight className="w-4 h-4 text-gray-300 shrink-0" />
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    )
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SCREEN 2 — map + nearby branches
  // ══════════════════════════════════════════════════════════════════════════
  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white flex items-center gap-3 px-5 py-4 border-b border-gray-100 z-10">
        <button
          onClick={() => { setScreen('location'); setSelectedBranch(null); setBranches([]) }}
          className="w-9 h-9 flex items-center justify-center rounded-xl hover:bg-gray-100 transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-gray-600" />
        </button>
        <div className="flex-1 min-w-0">
          <p className="text-xs text-gray-400 font-medium">Searching near</p>
          <p className="text-sm font-bold text-gray-900 truncate">{locationLabel}</p>
        </div>
      </div>

      {/* Map */}
      <div className="relative h-[280px] shrink-0">
        {isLoaded ? (
          <GoogleMap
            mapContainerStyle={{ width: '100%', height: '100%' }}
            center={mapCenter}
            zoom={selectedBranch ? 14 : 12}
            options={{ disableDefaultUI: true, zoomControl: false, streetViewControl: false }}
          >
            {/* User pin */}
            {userLatLng && (
              <Marker
                position={userLatLng}
                icon={{
                  path: google.maps.SymbolPath.CIRCLE,
                  scale: 10,
                  fillColor: '#22c55e',
                  fillOpacity: 1,
                  strokeColor: '#ffffff',
                  strokeWeight: 3,
                }}
              />
            )}
            {/* Branch pins */}
            {branches.map((b) => (
              <Marker
                key={b.id}
                position={{ lat: b.latitude, lng: b.longitude }}
                onClick={() => handleSelectBranch(b)}
                icon={{
                  path: google.maps.SymbolPath.BACKWARD_CLOSED_ARROW,
                  scale: selectedBranch?.id === b.id ? 8 : 6,
                  fillColor: selectedBranch?.id === b.id ? '#15803d' : '#16a34a',
                  fillOpacity: 1,
                  strokeColor: '#ffffff',
                  strokeWeight: 2,
                }}
              />
            ))}
          </GoogleMap>
        ) : (
          <div className="w-full h-full bg-gray-100 animate-pulse" />
        )}
      </div>

      {/* Branch list */}
      <div className="flex-1 overflow-y-auto pb-32">
        <div className="px-5 pt-4 pb-2">
          <p className="text-xs text-gray-400 font-semibold uppercase tracking-wider">
            {loadingBranches ? 'Finding branches…' : `${branches.length} branch${branches.length !== 1 ? 'es' : ''} nearby`}
          </p>
        </div>

        {loadingBranches && (
          <div className="flex items-center justify-center py-12 gap-2 text-gray-400">
            <Loader2 className="w-5 h-5 animate-spin" />
            <span className="text-sm">Searching for DHA branches…</span>
          </div>
        )}

        {!loadingBranches && branches.length === 0 && (
          <div className="flex flex-col items-center gap-3 py-12 text-center px-8">
            <div className="w-14 h-14 rounded-full bg-gray-100 flex items-center justify-center">
              <MapPin className="w-7 h-7 text-gray-400" />
            </div>
            <p className="text-sm font-semibold text-gray-700">No branches found nearby</p>
            <p className="text-xs text-gray-400">Try searching a different area or expanding your search.</p>
            <button
              onClick={() => setScreen('location')}
              className="text-sm text-green-600 font-bold mt-1"
            >
              Search again
            </button>
          </div>
        )}

        <div className="flex flex-col gap-3 px-5 pt-1">
          {branches.map((branch) => {
            const selected = selectedBranch?.id === branch.id
            return (
              <button
                key={branch.id}
                onClick={() => handleSelectBranch(branch)}
                className={`text-left bg-white rounded-2xl border-2 p-4 transition-all ${
                  selected ? 'border-green-500 shadow-md shadow-green-100' : 'border-gray-200'
                }`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="text-sm font-bold text-gray-900">{branch.name}</p>
                      {!branch.isOpen && (
                        <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 border border-gray-200">
                          Closed
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-gray-400 mt-0.5 truncate">{branch.address}</p>

                    <div className="flex items-center gap-3 mt-2.5 flex-wrap">
                      {branch.distanceKm != null && (
                        <span className="flex items-center gap-1 text-xs text-gray-500 font-medium">
                          <MapPin className="w-3 h-3" />
                          {branch.distanceKm < 1
                            ? `${Math.round(branch.distanceKm * 1000)} m`
                            : `${branch.distanceKm.toFixed(1)} km`}
                        </span>
                      )}
                      <span className="flex items-center gap-1 text-xs text-gray-500 font-medium">
                        <Clock className="w-3 h-3" />
                        ~{branch.estimatedWaitMinutes} min wait
                      </span>
                      <span className="flex items-center gap-1 text-xs text-gray-500 font-medium">
                        <Users className="w-3 h-3" />
                        {branch.currentQueueSize} in queue
                      </span>
                    </div>

                    <div className="flex items-center gap-1.5 mt-2">
                      {branch.hasWifi     && <Wifi          className="w-3.5 h-3.5 text-gray-300" />}
                      {branch.hasParking  && <Car           className="w-3.5 h-3.5 text-gray-300" />}
                      {branch.isAccessible && <Accessibility className="w-3.5 h-3.5 text-gray-300" />}
                    </div>
                  </div>

                  <span className={`text-[10px] font-bold px-2.5 py-1 rounded-full border shrink-0 ${CAPACITY_COLOR(branch.capacityPercent)}`}>
                    {CAPACITY_LABEL(branch.capacityPercent)}
                  </span>
                </div>

                {selected && (
                  <div className="mt-3 pt-3 border-t border-gray-100 flex items-center justify-between">
                    <div>
                      <p className="text-xs text-gray-400">Opens {branch.openingTime} · Closes {branch.closingTime}</p>
                      {branch.activeCounters > 0 && (
                        <p className="text-xs text-green-600 font-medium mt-0.5">{branch.activeCounters} counters active</p>
                      )}
                    </div>
                    <span className="text-xs text-green-600 font-bold">Selected ✓</span>
                  </div>
                )}
              </button>
            )
          })}
        </div>
      </div>

      {/* Confirm button */}
      {selectedBranch && (
        <div className="fixed bottom-20 left-1/2 -translate-x-1/2 w-full max-w-md px-5 z-20">
          <button
            onClick={handleConfirm}
            className="w-full bg-green-500 text-white font-bold py-4 rounded-2xl shadow-lg shadow-green-200 hover:bg-green-600 transition-all active:scale-95 flex items-center justify-center gap-2"
          >
            <MapPin className="w-4 h-4" />
            Confirm — {selectedBranch.name}
          </button>
        </div>
      )}
    </div>
  )
}
