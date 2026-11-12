import { Route, Routes } from 'react-router'
import PublicLayout from './components/PublicLayout'
import DashboardLayout from './components/dashboard/DashboardLayout'
import DashboardPage from './pages/DashboardPage'
import AuthPage from './pages/AuthPage'
import EmailVerificationPage from './pages/EmailVerificationPage'
import HomePage from './pages/HomePage'
import NotFoundPage from './pages/NotFoundPage'
import StatusPage from './pages/StatusPage'
import './App.css'

function App() {
  return (
    <Routes>
      <Route element={<DashboardLayout />}>
        <Route path="dashboard" element={<DashboardPage />} />
      </Route>
      <Route element={<PublicLayout />}>
        <Route index element={<HomePage />} />
        <Route path="login" element={<AuthPage key="login" mode="login" />} />
        <Route path="register" element={<AuthPage key="register" mode="register" />} />
        <Route path="verify-email" element={<EmailVerificationPage />} />
        <Route path="status" element={<StatusPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
