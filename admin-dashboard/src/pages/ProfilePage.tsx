import { UserCircle, Mail, Phone, MapPinned, ShieldCheck, Building2, BadgeCheck } from 'lucide-react'
import { useAuthStore } from '../store/AuthStore'
import { useBranchStore } from '../store/BranchStore'

export default function ProfilePage() {
  const user = useAuthStore((s) => s.user)
  const { branches, selectedBranchId } = useBranchStore()
  const branch = branches.find((b) => b.id === selectedBranchId)
  const initials = user ? `${user.firstName[0] ?? ''}${user.lastName[0] ?? ''}` : ''

  const fields = [
    { label: 'Email', value: user?.email, icon: Mail },
    { label: 'Phone Number', value: user?.phoneNumber ?? '—', icon: Phone },
    { label: 'Province', value: user?.province ?? '—', icon: MapPinned },
    { label: 'Assigned Branch', value: branch?.name ?? '—', icon: Building2 },
  ]

  return (
    <main className="flex-1 p-6">
      <div className="max-w-2xl mx-auto flex flex-col gap-5 animate-fade-in">
        <h1 className="text-xl font-bold text-slate-900">My Profile</h1>

        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-6 flex items-center gap-4">
          <div className="w-16 h-16 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center text-xl font-bold text-white shadow-md ring-2 ring-blue-400/30 shrink-0">
            {initials}
          </div>
          <div className="min-w-0">
            <p className="text-lg font-bold text-slate-900 truncate">
              {user ? `${user.firstName} ${user.lastName}` : ''}
            </p>
            <div className="flex items-center gap-1.5 text-sm text-slate-500 mt-0.5">
              <ShieldCheck size={14} className="text-blue-500" />
              {user?.role === 'ADMIN' ? 'Administrator' : 'Counter Staff'}
            </div>
          </div>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 divide-y divide-slate-100">
          {fields.map(({ label, value, icon: Icon }) => (
            <div key={label} className="flex items-center gap-3 px-6 py-4">
              <div className="bg-blue-50 p-2 rounded-lg shrink-0">
                <Icon size={16} className="text-blue-500" />
              </div>
              <div className="min-w-0">
                <p className="text-xs text-slate-500">{label}</p>
                <p className="text-sm font-semibold text-slate-800 truncate">{value}</p>
              </div>
            </div>
          ))}
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 divide-y divide-slate-100">
          <div className="flex items-center gap-3 px-6 py-4">
            <div className={`p-2 rounded-lg shrink-0 ${user?.isVerified ? 'bg-green-50' : 'bg-slate-100'}`}>
              <BadgeCheck size={16} className={user?.isVerified ? 'text-green-600' : 'text-slate-400'} />
            </div>
            <div>
              <p className="text-xs text-slate-500">Two-Factor Verification</p>
              <p className="text-sm font-semibold text-slate-800">
                {user?.isVerified ? 'Verified' : 'Not verified'}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3 px-6 py-4">
            <div className={`p-2 rounded-lg shrink-0 ${user?.isIdVerified ? 'bg-green-50' : 'bg-slate-100'}`}>
              <UserCircle size={16} className={user?.isIdVerified ? 'text-green-600' : 'text-slate-400'} />
            </div>
            <div>
              <p className="text-xs text-slate-500">ID Verification</p>
              <p className="text-sm font-semibold text-slate-800">
                {user?.isIdVerified ? 'Verified' : 'Not verified'}
              </p>
            </div>
          </div>
        </div>

        <p className="text-xs text-slate-400 text-center">
          To update your details, contact your branch administrator.
        </p>
      </div>
    </main>
  )
}
