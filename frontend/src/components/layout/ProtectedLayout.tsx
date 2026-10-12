import { Outlet, Navigate, NavLink } from 'react-router-dom'
import { Home, Map, MessageSquare, Clock, Settings } from 'lucide-react'
import { clsx } from 'clsx'
import BottomNav from './BottomNav'
import { useAuthStore } from '../../store/AuthStore'
import FaceScanModal from '../FaceScanModal'

const NAV_ITEMS = [
  { to: '/home',      icon: Home,          label: 'Home'    },
  { to: '/branches',  icon: Map,           label: 'Map'     },
  { to: '/assistant', icon: MessageSquare, label: 'Support' },
  { to: '/tickets',   icon: Clock,         label: 'History' },
]

function SidebarLink({ to, icon: Icon, label }: { to: string; icon: React.ElementType; label: string }) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        clsx(
          'flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-medium transition-colors',
          isActive
            ? 'bg-primary-50 text-primary-600 font-semibold'
            : 'text-gray-500 hover:bg-gray-50 hover:text-gray-800'
        )
      }
    >
      {({ isActive }) => (
        <>
          <Icon className={clsx('w-5 h-5 shrink-0', isActive && 'stroke-[2.5]')} />
          {label}
        </>
      )}
    </NavLink>
  )
}

export default function ProtectedLayout() {
  const { isAuthenticated, isTwoFaValid, isFaceValid } = useAuthStore()

  if (!isAuthenticated) return <Navigate to="/login" replace />
  // isTwoFaValid stays unenforced here: it mirrors a server-side Redis gate
  // that's only ever granted by /verify-otp during registration (an 8h TTL —
  // see identity-service's twoFaTtlHours) and login never re-grants it, so a
  // returning citizen past that window would get bounced into an OTP step
  // the login flow doesn't actually offer. That's a real gap, but a separate
  // one from face enforcement — flagging rather than silently gating on it.
  void isTwoFaValid
  if (!isFaceValid()) return <Navigate to="/verify-face" replace />

  return (
    <div className="min-h-screen bg-gray-100 flex overscroll-none">

      {/* ── Desktop Sidebar ── */}
      <aside className="hidden md:flex md:flex-col md:w-64 bg-white border-r border-gray-100 fixed inset-y-0 left-0 z-30 shrink-0">
        {/* Brand */}
        <div className="flex items-center gap-3 px-5 py-5 border-b border-gray-100">
          <div className="w-10 h-10 rounded-2xl bg-primary-500 flex items-center justify-center shrink-0">
            <span className="text-xl">🦅</span>
          </div>
          <div className="min-w-0">
            <p className="text-sm font-bold text-gray-900 leading-tight">DHA Smart Queue</p>
            <p className="text-xs text-gray-400 truncate">Dept. of Home Affairs</p>
          </div>
        </div>

        {/* Nav links */}
        <nav className="flex-1 px-3 py-4 flex flex-col gap-1 overflow-y-auto">
          {NAV_ITEMS.map((item) => (
            <SidebarLink key={item.to} {...item} />
          ))}
        </nav>

        {/* Bottom */}
        <div className="px-3 py-4 border-t border-gray-100">
          <SidebarLink to="/settings" icon={Settings} label="Settings" />
        </div>
      </aside>

      {/* ── Content ── */}
      <div className="flex flex-col flex-1 min-w-0 md:ml-64">
        <main className="flex-1 pb-16 md:pb-0">
          <Outlet />
        </main>
        <BottomNav />
      </div>

      <FaceScanModal />
    </div>
  )
}
