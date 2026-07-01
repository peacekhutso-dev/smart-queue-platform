import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Home, Map, MessageSquare, Clock, LogOut, Shield } from 'lucide-react';
import { useAuthStore } from '@/store/AuthStore';

// ─── DHA Logo Component ───────────────────────────────────────────────────────
export const DHALogo: React.FC<{ size?: 'sm' | 'md' | 'lg' }> = ({ size = 'md' }) => {
  const sizes = { sm: 'h-8', md: 'h-10', lg: 'h-14' };
  return (
    <div className={`flex items-center gap-3 ${sizes[size]}`}>
      <div className="flex-shrink-0">
        <div className="w-10 h-10 bg-dha-navy rounded-lg flex items-center justify-center">
          <Shield className="w-6 h-6 text-dha-green-light" />
        </div>
      </div>
      <div>
        <div className="text-dha-navy font-bold text-sm leading-tight">home affairs</div>
        <div className="text-dha-gray text-xs leading-tight">Department: Home Affairs</div>
        <div className="text-dha-navy font-semibold text-xs">REPUBLIC OF SOUTH AFRICA</div>
      </div>
    </div>
  );
};

// ─── Page Header ─────────────────────────────────────────────────────────────
export const PageHeader: React.FC<{
  title: string;
  showBack?: boolean;
  showLogo?: boolean;
  rightAction?: React.ReactNode;
}> = ({ title, showBack, showLogo, rightAction }) => {
  const navigate = useNavigate();
  return (
    <header className="bg-white border-b border-dha-gray-border sticky top-0 z-40">
      <div className="max-w-lg mx-auto px-4 py-3 flex items-center gap-3">
        {showBack && (
          <button
            onClick={() => navigate(-1)}
            className="p-2 -ml-2 rounded-lg hover:bg-dha-gray-light transition-colors"
          >
            <svg className="w-5 h-5 text-dha-navy" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
        )}
        {showLogo ? (
          <DHALogo size="sm" />
        ) : (
          <h1 className="flex-1 text-center font-semibold text-dha-navy text-base">{title}</h1>
        )}
        <div className="ml-auto">{rightAction}</div>
      </div>
    </header>
  );
};

// ─── Bottom Navigation ────────────────────────────────────────────────────────
export const BottomNav: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { logout } = useAuthStore();

  const navItems = [
    { path: '/home', icon: Home, label: 'Home' },
    { path: '/branches', icon: Map, label: 'Branches' },
    { path: '/assistant', icon: MessageSquare, label: 'Assistant' },
    { path: '/tickets', icon: Clock, label: 'Tickets' },
  ];

  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-dha-gray-border z-40 safe-bottom">
      <div className="max-w-lg mx-auto flex">
        {navItems.map(({ path, icon: Icon, label }) => {
          const active = location.pathname.startsWith(path);
          return (
            <button
              key={path}
              onClick={() => navigate(path)}
              className={`flex-1 flex flex-col items-center py-3 gap-0.5 transition-colors ${
                active ? 'text-dha-green' : 'text-dha-gray hover:text-dha-navy'
              }`}
            >
              <Icon className="w-5 h-5" strokeWidth={active ? 2.5 : 1.5} />
              <span className={`text-[10px] font-medium ${active ? 'font-semibold' : ''}`}>{label}</span>
            </button>
          );
        })}
      </div>
    </nav>
  );
};

// ─── Status Badge ─────────────────────────────────────────────────────────────
export const StatusBadge: React.FC<{ status: string }> = ({ status }) => {
  const configs: Record<string, { bg: string; text: string; label: string }> = {
    WAITING:   { bg: 'bg-blue-50',   text: 'text-blue-700',   label: 'Waiting' },
    CALLED:    { bg: 'bg-amber-50',  text: 'text-amber-700',  label: 'Called' },
    SERVING:   { bg: 'bg-dha-success-light', text: 'text-dha-success', label: 'Serving' },
    COMPLETE:  { bg: 'bg-gray-100',  text: 'text-gray-600',   label: 'Complete' },
    CANCELLED: { bg: 'bg-red-50',    text: 'text-red-600',    label: 'Cancelled' },
    VALID:     { bg: 'bg-dha-success-light', text: 'text-dha-success', label: 'Valid' },
    REJECTED:  { bg: 'bg-red-50',    text: 'text-red-600',    label: 'Rejected' },
    PENDING:   { bg: 'bg-amber-50',  text: 'text-amber-700',  label: 'Pending' },
    IN_REVIEW: { bg: 'bg-blue-50',   text: 'text-blue-700',   label: 'In Review' },
  };
  const cfg = configs[status] || { bg: 'bg-gray-100', text: 'text-gray-600', label: status };
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${cfg.bg} ${cfg.text}`}>
      {cfg.label}
    </span>
  );
};

// ─── Loading Spinner ──────────────────────────────────────────────────────────
export const Spinner: React.FC<{ size?: 'sm' | 'md' | 'lg'; className?: string }> = ({
  size = 'md',
  className = '',
}) => {
  const s = { sm: 'w-4 h-4', md: 'w-6 h-6', lg: 'w-10 h-10' };
  return (
    <div className={`${s[size]} border-2 border-dha-green border-t-transparent rounded-full animate-spin ${className}`} />
  );
};

// ─── Full Page Loader ─────────────────────────────────────────────────────────
export const PageLoader: React.FC = () => (
  <div className="min-h-screen flex flex-col items-center justify-center bg-white gap-4">
    <DHALogo size="md" />
    <Spinner size="lg" />
    <p className="text-dha-gray text-sm">Loading...</p>
  </div>
);

// ─── Empty State ──────────────────────────────────────────────────────────────
export const EmptyState: React.FC<{
  icon: React.ReactNode;
  title: string;
  description: string;
  action?: React.ReactNode;
}> = ({ icon, title, description, action }) => (
  <div className="flex flex-col items-center justify-center py-16 px-6 text-center">
    <div className="w-16 h-16 rounded-2xl bg-dha-gray-light flex items-center justify-center mb-4 text-dha-gray">
      {icon}
    </div>
    <h3 className="font-semibold text-dha-navy text-base mb-1">{title}</h3>
    <p className="text-dha-gray text-sm mb-6 max-w-xs">{description}</p>
    {action}
  </div>
);

// ─── Capacity Bar ─────────────────────────────────────────────────────────────
export const CapacityBar: React.FC<{ percent: number }> = ({ percent }) => {
  const color = percent < 50 ? 'bg-dha-green' : percent < 80 ? 'bg-amber-400' : 'bg-red-500';
  return (
    <div className="w-full h-1.5 bg-gray-100 rounded-full overflow-hidden">
      <div
        className={`h-full ${color} transition-all duration-700`}
        style={{ width: `${Math.min(percent, 100)}%` }}
      />
    </div>
  );
};

// ─── Info Row ─────────────────────────────────────────────────────────────────
export const InfoRow: React.FC<{
  icon: React.ReactNode;
  label: string;
  value: string | React.ReactNode;
}> = ({ icon, label, value }) => (
  <div className="flex items-center gap-3 py-2.5">
    <div className="text-dha-gray flex-shrink-0">{icon}</div>
    <div className="flex-1">
      <div className="text-xs text-dha-gray uppercase tracking-wide font-medium">{label}</div>
      <div className="text-sm font-semibold text-dha-navy mt-0.5">{value}</div>
    </div>
  </div>
);