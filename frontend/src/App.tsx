import { Route, Routes } from 'react-router'
import PublicLayout from './components/PublicLayout'
import DashboardLayout from './components/dashboard/DashboardLayout'
import DashboardPage from './pages/DashboardPage'
import MyDecksPage from './pages/MyDecksPage'
import CreateDeckPage from './pages/CreateDeckPage'
import EditDeckPage from './pages/EditDeckPage'
import ProtectedRoute from './auth/ProtectedRoute'
import AuthPage from './pages/AuthPage'
import EmailVerificationPage from './pages/EmailVerificationPage'
import HomePage from './pages/HomePage'
import NotFoundPage from './pages/NotFoundPage'
import StatusPage from './pages/StatusPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import './App.css'

function App() {
  return (
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route element={<DashboardLayout />}>
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="decks" element={<MyDecksPage />} />
          <Route path="decks/create" element={<CreateDeckPage />} />
          <Route path="decks/:deckId/edit" element={<EditDeckPage />} />
        </Route>
      </Route>
      <Route element={<PublicLayout />}>
        <Route index element={<HomePage />} />
        <Route path="login" element={<AuthPage key="login" mode="login" />} />
        <Route path="register" element={<AuthPage key="register" mode="register" />} />
        <Route path="forgot-password" element={<ForgotPasswordPage />} />
        <Route path="reset-password" element={<ResetPasswordPage />} />
        <Route path="verify-email" element={<EmailVerificationPage />} />
        <Route path="status" element={<StatusPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
