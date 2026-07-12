/** Reusable skeleton building block */
function Sk({ className = '' }: { className?: string }) {
  return <div className={`skeleton ${className}`} />
}

/** Home page: hero card + service grid + recent activity */
export function HomePageSkeleton() {
  return (
    <div className="flex flex-col gap-5 p-5 animate-fade-in">
      {/* Hero card */}
      <div className="bg-white rounded-3xl p-5 border border-gray-100 flex flex-col gap-3">
        <Sk className="h-4 w-32" />
        <Sk className="h-8 w-48" />
        <Sk className="h-3 w-64" />
        <Sk className="h-10 w-full rounded-2xl mt-1" />
      </div>

      {/* Service grid */}
      <Sk className="h-3 w-24" />
      <div className="grid grid-cols-2 gap-3">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="bg-white rounded-2xl p-4 border border-gray-100 flex flex-col gap-2">
            <Sk className="w-10 h-10 rounded-xl" />
            <Sk className="h-3 w-20" />
            <Sk className="h-2.5 w-28" />
          </div>
        ))}
      </div>

      {/* Recent */}
      <Sk className="h-3 w-28" />
      <div className="flex flex-col gap-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="bg-white rounded-2xl p-4 border border-gray-100 flex items-center gap-3">
            <Sk className="w-10 h-10 rounded-full shrink-0" />
            <div className="flex-1 flex flex-col gap-2">
              <Sk className="h-3 w-32" />
              <Sk className="h-2.5 w-48" />
            </div>
            <Sk className="w-5 h-5 rounded" />
          </div>
        ))}
      </div>
    </div>
  )
}

/** Branch list cards shown while fetching nearby branches */
export function BranchListSkeleton() {
  return (
    <div className="flex flex-col gap-3 px-5 pt-1 animate-fade-in">
      {Array.from({ length: 4 }).map((_, i) => (
        <div key={i} className="bg-white rounded-2xl border border-gray-200 p-4 flex flex-col gap-3">
          <div className="flex items-start justify-between gap-3">
            <div className="flex-1 flex flex-col gap-2">
              <Sk className="h-4 w-40" />
              <Sk className="h-3 w-56" />
            </div>
            <Sk className="h-6 w-14 rounded-full" />
          </div>
          <div className="flex gap-3">
            <Sk className="h-3 w-16" />
            <Sk className="h-3 w-20" />
            <Sk className="h-3 w-16" />
          </div>
        </div>
      ))}
    </div>
  )
}

/** Document list rows shown while fetching docs in Settings */
export function DocumentListSkeleton() {
  return (
    <div className="flex flex-col gap-3 animate-fade-in">
      {Array.from({ length: 4 }).map((_, i) => (
        <div key={i} className="bg-white rounded-2xl border border-gray-200 p-4 flex items-center gap-3">
          <Sk className="w-10 h-10 rounded-xl shrink-0" />
          <div className="flex-1 flex flex-col gap-2">
            <Sk className="h-3.5 w-36" />
            <Sk className="h-2.5 w-24" />
          </div>
          <Sk className="h-6 w-16 rounded-full" />
        </div>
      ))}
    </div>
  )
}

/** Full-page app skeleton used as Suspense fallback */
export function AppSkeleton() {
  return (
    <div className="flex flex-col min-h-screen bg-gray-100">
      {/* Header */}
      <div className="bg-white border-b border-gray-100 px-5 h-20 flex items-center justify-between sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <Sk className="w-8 h-8 rounded-full" />
          <Sk className="h-4 w-32" />
        </div>
        <Sk className="w-8 h-8 rounded-full" />
      </div>

      {/* Body */}
      <HomePageSkeleton />

      {/* Bottom nav */}
      <div className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-100 flex items-center justify-around px-4 py-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="flex flex-col items-center gap-1">
            <Sk className="w-6 h-6 rounded" />
            <Sk className="h-2 w-10" />
          </div>
        ))}
      </div>
    </div>
  )
}
