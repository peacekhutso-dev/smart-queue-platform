import { Routes, Route, Navigate } from 'react-router-dom'
import { lazy, Suspense, Component, ReactNode } from 'react'
import ProtectedLayout from './components/layout/ProtectedLayout'
import FaceScanModal from './components/FaceScanModal'

// ✅ Auth pages loaded eagerly — no lazy/Suspense blocking navigation
import LoginPage      from './pages/LoginPage'
import RegisterPage   from './pages/RegisterPage'
import OtpVerifyPage  from './pages/OtpVerifyPage'
import SaIdVerifyPage from './pages/SaIdVerifyPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'

// App screens — lazy is fine for these
const FaceVerifyPage  = lazy(() => import('./pages/FaceVerifyPage'))
const HomePage        = lazy(() => import('./pages/HomePage'))
const DocumentsPage   = lazy(() => import('./pages/DocumentsPage'))
const BranchesPage    = lazy(() => import('./pages/BranchPage'))
const AssistantPage   = lazy(() => import('./pages/AssistantPage'))
const TicketPage      = lazy(() => import('./pages/TicketPage'))
const QueueStatusPage = lazy(() => import('./pages/QueueStatusPage'))
const FeedbackPage    = lazy(() => import('./pages/FeedbackPage'))
const SettingsPage    = lazy(() => import('./pages/SettingsPage'))

const Loader = () => (
  <div className="mobile-shell flex items-center justify-center min-h-screen">
    <div className="flex flex-col items-center gap-3">
      <div className="w-10 h-10 border-4 border-green-500 border-t-transparent rounded-full animate-spin" />
      <p className="text-sm text-gray-400">Loading…</p>
    </div>
  </div>
)

// ✅ Error boundary catches silent lazy-load crashes and shows the actual error
interface EBState { hasError: boolean; error: Error | null }
class ErrorBoundary extends Component<{ children: ReactNode }, EBState> {
  state: EBState = { hasError: false, error: null }
  static getDerivedStateFromError(error: Error): EBState {
    return { hasError: true, error }
  }
  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center p-6 bg-white">
          <div className="max-w-md w-full">
            <h2 className="text-red-600 font-bold text-lg mb-2">Page Load Error</h2>
            <pre className="text-xs bg-red-50 border border-red-200 rounded-xl p-4 overflow-auto text-red-700 whitespace-pre-wrap">
              {this.state.error?.message}
              {'\n\n'}
              {this.state.error?.stack}
            </pre>
            <button
              onClick={() => { this.setState({ hasError: false, error: null }); window.history.back() }}
              className="mt-4 w-full bg-green-500 text-white font-bold py-3 rounded-xl"
            >
              Go Back
            </button>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}

export default function App() {
  return (
    <div className="min-h-screen bg-gray-100 flex items-start justify-center">
      <ErrorBoundary>
        {/* Global face modal — shown by Axios interceptor when face session expires mid-session */}
        <FaceScanModal />
        <Suspense fallback={<Loader />}>
          <Routes>
            {/* Auth — eagerly imported, no lazy blocking */}
            <Route path="/login"       element={<div className="mobile-shell"><LoginPage /></div>} />
            <Route path="/register"    element={<div className="mobile-shell"><RegisterPage /></div>} />
            <Route path="/verify-otp"  element={<div className="mobile-shell"><OtpVerifyPage /></div>} />
            <Route path="/verify-id"   element={<div className="mobile-shell"><SaIdVerifyPage /></div>} />
            <Route path="/verify-face"      element={<div className="mobile-shell"><FaceVerifyPage /></div>} />
            <Route path="/forgot-password"  element={<div className="mobile-shell"><ForgotPasswordPage /></div>} />

            {/* Protected app screens */}
            <Route element={<ProtectedLayout />}>
              <Route path="/home"         element={<HomePage />} />
              <Route path="/documents"    element={<DocumentsPage />} />
              <Route path="/branches"     element={<BranchesPage />} />
              <Route path="/assistant"    element={<AssistantPage />} />
              <Route path="/ticket"       element={<TicketPage />} />
              <Route path="/tickets"      element={<TicketPage />} />
              <Route path="/queue-status" element={<QueueStatusPage />} />
              <Route path="/feedback"     element={<FeedbackPage />} />
              <Route path="/settings"     element={<SettingsPage />} />
            </Route>

            <Route path="/"  element={<Navigate to="/home" replace />} />
            <Route path="*"  element={<Navigate to="/home" replace />} />
          </Routes>
        </Suspense>
      </ErrorBoundary>
    </div>
  )
}