import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import './index.css'
import './theme/theme.css'
import App from './App.tsx'
import AuthProvider from './auth/AuthProvider'
import ThemeProvider from './theme/ThemeProvider'
import LanguageProvider from './i18n/LanguageProvider'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <ThemeProvider><LanguageProvider><AuthProvider><App /></AuthProvider></LanguageProvider></ThemeProvider>
    </BrowserRouter>
  </StrictMode>,
)
