import { Route, Routes } from 'react-router'
import PublicLayout from './components/PublicLayout'
import DashboardLayout from './components/dashboard/DashboardLayout'
import DashboardPage from './pages/DashboardPage'
import MyDecksPage from './pages/MyDecksPage'
import ExploreDecksPage from './pages/ExploreDecksPage'
import CreateDeckPage from './pages/CreateDeckPage'
import EditDeckPage from './pages/EditDeckPage'
import DeckDetailsPage from './pages/DeckDetailsPage'
import StudyPage from './pages/StudyPage'
import SettingsPage from './pages/SettingsPage'
import StatisticsPage from './pages/StatisticsPage'
import DeckStatisticsPage from './pages/DeckStatisticsPage'
import MasteryReportPage from './pages/MasteryReportPage'
import ConfirmEmailChangePage from './pages/ConfirmEmailChangePage'
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
        <Route path="decks/:deckId/study" element={<StudyPage />} />
        <Route path="decks/:deckId/study/:sessionId" element={<StudyPage />} />
        <Route element={<DashboardLayout />}>
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="explore" element={<ExploreDecksPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="statistics" element={<StatisticsPage />} />
          <Route path="statistics/decks" element={<DeckStatisticsPage />} />
          <Route path="statistics/decks/:reportDeckId" element={<MasteryReportPage />} />
          <Route path="decks" element={<MyDecksPage />} />
          <Route path="decks/create" element={<CreateDeckPage />} />
          <Route path="decks/:deckId/edit" element={<EditDeckPage />} />
          <Route path="decks/:deckId" element={<DeckDetailsPage />} />
        </Route>
      </Route>
      <Route element={<PublicLayout />}>
        <Route index element={<HomePage />} />
        <Route path="login" element={<AuthPage key="login" mode="login" />} />
        <Route path="register" element={<AuthPage key="register" mode="register" />} />
        <Route path="forgot-password" element={<ForgotPasswordPage />} />
        <Route path="reset-password" element={<ResetPasswordPage />} />
        <Route path="verify-email" element={<EmailVerificationPage />} />
        <Route path="confirm-email-change" element={<ConfirmEmailChangePage />} />
        <Route path="status" element={<StatusPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
