import { useEffect } from 'react'
import { Outlet, Navigate } from 'react-router-dom'
import { useAuthStore } from '../../store/AuthStore'
import { useBranchStore } from '../../store/BranchStore'
import FingerprintModal from '../FingerprintModal'
import Sidebar from '../Sidebar'

/**
 * Mirrors smart-queue-platform/frontend's ProtectedLayout Zero Trust gates, with the
 * citizen face check replaced by the staff/admin fingerprint (WebAuthn) check:
 *   isAuthenticated? -> NO -> /login
 *   isTwoFaValid()?  -> NO -> /verify-otp
 *   isFingerprintValid()? -> NO -> /verify-fingerprint
 */
export default function ProtectedLayout() {
  const { isAuthenticated, isTwoFaValid, isFingerprintValid, user } = useAuthStore()
  const loadBranches = useBranchStore((s) => s.loadBranches)

  useEffect(() => {
    if (isAuthenticated) loadBranches(user?.assignedBranchId ?? null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated])

  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (!isTwoFaValid()) return <Navigate to="/verify-otp" replace />
  if (!isFingerprintValid()) return <Navigate to="/verify-fingerprint" replace />

  return (
    <div className="min-h-screen flex bg-gradient-to-br from-slate-50 to-slate-100">
      <Sidebar />
      <div className="flex-1 flex flex-col min-w-0">
        <Outlet />
      </div>
      <FingerprintModal />
    </div>
  )
}
