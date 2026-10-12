import { Routes, Route, Navigate } from 'react-router-dom'
import ProtectedLayout from './components/layout/ProtectedLayout'
import LoginPage from './pages/LoginPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import OtpVerifyPage from './pages/OtpVerifyPage'
import FingerprintGatePage from './pages/FingerprintGatePage'
import DashboardPage from './pages/DashboardPage'
import ProfilePage from './pages/ProfilePage'
import CitizenRecordsPage from './pages/CitizenRecordsPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/verify-otp" element={<OtpVerifyPage />} />
      <Route path="/verify-fingerprint" element={<FingerprintGatePage />} />

      <Route element={<ProtectedLayout />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        {/* DEV ONLY — stand-in for DHA's population register */}
        <Route path="/citizen-records" element={<CitizenRecordsPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
