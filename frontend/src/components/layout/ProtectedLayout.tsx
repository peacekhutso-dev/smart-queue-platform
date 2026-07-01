import { Outlet, Navigate } from 'react-router-dom'
import BottomNav from './BottomNav'
import { useAuthStore } from '../../store/AuthStore'
import FaceScanModal from '../FaceScanModal'

export default function ProtectedLayout() {
  const { isAuthenticated, isTwoFaValid, isFaceValid } = useAuthStore()

  // DEV BYPASS — remove these comments to re-enable auth gates
  // if (!isAuthenticated) return <Navigate to="/login" replace />
  // if (!isTwoFaValid()) return <Navigate to="/verify-otp?session=true" replace />
  // if (!isFaceValid()) return <Navigate to="/verify-face?mode=verify" replace />
  void isAuthenticated; void isTwoFaValid; void isFaceValid

  return (
    <div className="mobile-shell flex flex-col min-h-screen">
      <div className="flex-1 overflow-y-auto pb-20">
        <Outlet />
      </div>
      <BottomNav />

      {/* Global face-scan modal — triggered by Axios interceptor on 403 REQUIRES_FACE */}
      <FaceScanModal />
    </div>
  )
}
