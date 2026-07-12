import { NavLink } from 'react-router-dom'
import { Home, Map, MessageSquare, Clock } from 'lucide-react'
import { clsx } from 'clsx'

const navItems = [
  { to: '/home',      icon: Home,          label: 'Home'      },
  { to: '/branches',  icon: Map,           label: 'Map'       },
  { to: '/assistant', icon: MessageSquare, label: 'Support'   },
  { to: '/tickets',   icon: Clock,         label: 'History'   },
]

export default function BottomNav() {
  return (
    <nav className="md:hidden sticky bottom-0 bg-white border-t border-gray-100 px-2 py-2 z-10">
      <div className="flex items-center justify-around">
        {navItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end
            className={({ isActive }) =>
              clsx(
                'flex flex-col items-center gap-1 px-4 py-1 rounded-xl transition-colors',
                isActive ? 'text-primary-500' : 'text-gray-400 hover:text-gray-600'
              )
            }
          >
            {({ isActive }) => (
              <>
                <Icon className={clsx('w-5 h-5', isActive && 'stroke-2')} />
                <span className={clsx('text-xs font-medium', isActive && 'font-semibold')}>
                  {label}
                </span>
              </>
            )}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}