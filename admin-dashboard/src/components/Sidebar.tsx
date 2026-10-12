import { NavLink, useNavigate } from 'react-router-dom'
import { Shield, MapPin, LayoutDashboard, UserCircle, LogOut } from 'lucide-react'
import { useAuthStore } from '../store/AuthStore'
import { useBranchStore } from '../store/BranchStore'

const navItems = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/profile', label: 'Profile', icon: UserCircle, end: false },
]

export default function Sidebar() {
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const { branches, selectedBranchId } = useBranchStore()

  const branch = branches.find((b) => b.id === selectedBranchId)
  const initials = user ? `${user.firstName[0] ?? ''}${user.lastName[0] ?? ''}` : ''

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <aside className="w-64 shrink-0 bg-[#0f1f3d] text-white flex flex-col h-screen sticky top-0 shadow-xl border-r border-slate-800">
      <div className="flex items-center gap-3 px-5 py-5 border-b border-slate-800">
        <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-[#2563EB] to-[#1e40af] flex items-center justify-center shadow-md shrink-0">
          <Shield size={18} className="text-white" />
        </div>
        <div className="leading-tight min-w-0">
          <p className="font-bold text-sm truncate">DHA Smart Queue</p>
          <p className="text-[11px] text-slate-400">Staff & Admin Portal</p>
        </div>
      </div>

      <div className="flex items-center gap-2 px-5 py-4 border-b border-slate-800">
        <MapPin size={14} className="text-blue-400 shrink-0" />
        <span className="font-semibold text-sm tracking-wide truncate">
          {branch?.name ?? 'Loading branch…'}
        </span>
      </div>

      <nav className="flex-1 px-3 py-4 flex flex-col gap-1">
        {navItems.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition-all ${
                isActive
                  ? 'bg-blue-600/20 text-white ring-1 ring-blue-500/40'
                  : 'text-slate-300 hover:bg-white/5 hover:text-white'
              }`
            }
          >
            <Icon size={17} />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="px-3 py-4 border-t border-slate-800 flex flex-col gap-3">
        <NavLink
          to="/profile"
          className="flex items-center gap-2.5 px-2 py-1.5 rounded-xl hover:bg-white/5 transition-colors"
        >
          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center text-xs font-bold shadow-md ring-2 ring-blue-400/30 shrink-0">
            {initials}
          </div>
          <div className="leading-tight min-w-0">
            <p className="text-sm font-semibold truncate">
              {user ? `${user.firstName} ${user.lastName}` : ''}
            </p>
            <p className="text-xs text-slate-400">
              {user?.role === 'ADMIN' ? 'Administrator' : 'Counter Staff'}
            </p>
          </div>
        </NavLink>

        <button
          onClick={handleLogout}
          className="flex items-center justify-center gap-1.5 bg-red-600 hover:bg-red-700 text-white text-xs font-semibold px-3.5 py-2.5 rounded-lg transition-all hover:shadow-md"
        >
          <LogOut size={13} />
          Logout
        </button>
      </div>
    </aside>
  )
}
