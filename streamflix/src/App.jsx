import { lazy, Suspense, useEffect } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from './api/queryClient'
import { Navbar } from './components/Navbar'
import { SearchOverlay } from './components/SearchOverlay'
import useStore from './store/useStore'

const HomePage = lazy(() => import('./pages/HomePage'))
const PlayerPage = lazy(() => import('./pages/PlayerPage'))
const LoginPage = lazy(() => import('./pages/LoginPage'))
const AdminAnalyticsPage = lazy(() => import('./pages/AdminAnalyticsPage'))

function LoadingFallback() {
  return (
    <div className="min-h-screen bg-surface-900 flex items-center justify-center">
      <div className="flex flex-col items-center gap-4">
        <div className="w-10 h-10 border-2 border-accent border-t-transparent rounded-full animate-spin" />
        <p className="text-surface-300 text-sm">Loading...</p>
      </div>
    </div>
  )
}

export default function App() {
  const checkSession = useStore((s) => s.checkSession)

  useEffect(() => {
    checkSession()
  }, [checkSession])

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <div className="min-h-screen bg-surface-900 text-white antialiased">
          <Navbar />
          <SearchOverlay />

          <Suspense fallback={<LoadingFallback />}>
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/watch/:videoId" element={<PlayerPage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/admin/analytics" element={<AdminAnalyticsPage />} />
              <Route path="*" element={<HomePage />} />
            </Routes>
          </Suspense>
        </div>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
