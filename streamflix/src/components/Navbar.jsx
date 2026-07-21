import { memo, useState, useEffect, useRef, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import useStore from '../store/useStore'
import { cn } from '../lib/cn'

const NAV_ITEMS = ['Home', 'Series', 'Movies', 'New & Popular', 'My List', 'Browse by Languages']

function NavbarComponent() {
  const scrolledPast = useStore((s) => s.scrolledPast)
  const setSearchOpen = useStore((s) => s.setSearchOpen)
  const user = useStore((s) => s.user)
  const isAuthenticated = useStore((s) => s.isAuthenticated)
  const isAdmin = useStore((s) => s.isAdmin)
  const logout = useStore((s) => s.logout)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [activeItem, setActiveItem] = useState('Home')
  const [profileOpen, setProfileOpen] = useState(false)
  const menuRef = useRef(null)
  const profileRef = useRef(null)

  useEffect(() => {
    if (!mobileOpen) return
    const handler = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setMobileOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [mobileOpen])

  useEffect(() => {
    const handler = (e) => {
      if (profileRef.current && !profileRef.current.contains(e.target)) {
        setProfileOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  useEffect(() => {
    document.body.style.overflow = mobileOpen ? 'hidden' : ''
    return () => { document.body.style.overflow = '' }
  }, [mobileOpen])

  const handleSearchClick = useCallback(() => {
    setSearchOpen(true)
  }, [setSearchOpen])

  const handleLogin = useCallback(() => {
    window.location.href = '/login'
  }, [])

  const handleLogout = useCallback(() => {
    logout()
  }, [logout])

  const profileInitial = user?.email?.charAt(0)?.toUpperCase() || 'U'

  return (
    <>
      <motion.nav
        initial={false}
        animate={{
          backgroundColor: scrolledPast
            ? 'rgba(10, 10, 10, 0.96)'
            : 'rgba(10, 10, 10, 0)',
          backdropFilter: scrolledPast ? 'blur(12px)' : 'blur(0px)',
        }}
        transition={{ duration: 0.25, ease: [0.4, 0, 0.2, 1] }}
        className="fixed top-0 left-0 right-0 z-50 px-6 md:px-12"
        style={{ WebkitBackdropFilter: scrolledPast ? 'blur(12px)' : 'blur(0px)' }}
      >
        <div className="flex items-center justify-between h-16 md:h-[68px] max-w-[1920px] mx-auto">
          <div className="flex items-center gap-10">
            <a href="/" className="shrink-0" aria-label="StreamFlix Home">
              <span className="font-display text-2xl md:text-3xl font-bold tracking-tight text-accent select-none">
                STREAMFLIX
              </span>
            </a>
            <div className="hidden md:flex items-center gap-1">
              {NAV_ITEMS.map((item) => (
                <button
                  key={item}
                  onClick={() => setActiveItem(item)}
                  className={cn(
                    'relative px-3 py-1.5 text-sm font-medium rounded-md transition-colors duration-150',
                    activeItem === item
                      ? 'text-white'
                      : 'text-surface-100 hover:text-white'
                  )}
                >
                  {item}
                  {activeItem === item && (
                    <motion.div
                      layoutId="navActive"
                      className="absolute bottom-0 left-3 right-3 h-0.5 bg-accent rounded-full"
                      transition={{ type: 'spring', stiffness: 500, damping: 35 }}
                    />
                  )}
                </button>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-3 md:gap-5">
            {isAdmin && (
              <div className="hidden md:flex items-center gap-2">
                <a
                  href="/admin/analytics"
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold uppercase tracking-wider text-cyan-400 border border-cyan-400/30 rounded-md hover:bg-cyan-400/10 transition-colors duration-150"
                >
                  Analytics
                </a>
                <a
                  href="/admin.html"
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold uppercase tracking-wider text-accent border border-accent/30 rounded-md hover:bg-accent/10 transition-colors duration-150"
                >
                  Admin
                </a>
              </div>
            )}

            <button
              onClick={handleSearchClick}
              className="p-2 text-surface-100 hover:text-white transition-colors duration-150"
              aria-label="Search"
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </button>

            <button
              onClick={() => setMobileOpen(!mobileOpen)}
              className="md:hidden p-2 text-white"
              aria-label={mobileOpen ? 'Close menu' : 'Open menu'}
              aria-expanded={mobileOpen}
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                {mobileOpen ? (
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                ) : (
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
                )}
              </svg>
            </button>

            <div className="hidden md:flex items-center relative" ref={profileRef}>
              {isAuthenticated ? (
                <>
                  <button
                    onClick={() => setProfileOpen(!profileOpen)}
                    className="w-8 h-8 rounded-full bg-accent flex items-center justify-center text-sm font-bold text-white hover:ring-2 hover:ring-white/30 transition-all"
                    aria-label="Profile"
                  >
                    {profileInitial}
                  </button>
                  <AnimatePresence>
                    {profileOpen && (
                      <motion.div
                        initial={{ opacity: 0, y: -8 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -8 }}
                        transition={{ duration: 0.15 }}
                        className="absolute top-full right-0 mt-2 w-48 bg-surface-800 border border-surface-600/50 rounded-xl shadow-2xl overflow-hidden"
                      >
                        <div className="px-4 py-3 border-b border-surface-700">
                          <p className="text-xs text-surface-200">{user?.email}</p>
                          {isAdmin && <p className="text-[10px] text-accent font-semibold uppercase mt-0.5">Admin</p>}
                        </div>
                        {isAdmin && (
                          <>
                            <a
                              href="/admin/analytics"
                              className="block px-4 py-2.5 text-sm text-surface-100 hover:text-white hover:bg-surface-700 transition-colors"
                            >
                              Analytics Dashboard
                            </a>
                            <a
                              href="/admin.html"
                              className="block px-4 py-2.5 text-sm text-surface-100 hover:text-white hover:bg-surface-700 transition-colors"
                            >
                              Admin Panel
                            </a>
                          </>
                        )}
                        <button
                          onClick={handleLogout}
                          className="w-full text-left px-4 py-2.5 text-sm text-surface-100 hover:text-white hover:bg-surface-700 transition-colors"
                        >
                          Sign Out
                        </button>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </>
              ) : (
                <button
                  onClick={handleLogin}
                  className="px-4 py-1.5 text-sm font-semibold bg-accent text-white rounded-md hover:bg-accent-hover transition-colors duration-150"
                >
                  Sign In
                </button>
              )}
            </div>
          </div>
        </div>
      </motion.nav>

      <AnimatePresence>
        {mobileOpen && (
          <motion.div
            ref={menuRef}
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'spring', stiffness: 300, damping: 30 }}
            className="fixed inset-y-0 right-0 z-50 w-72 bg-surface-800 border-l border-surface-400/30 shadow-2xl md:hidden"
          >
            <div className="flex flex-col h-full pt-20 px-6">
              {NAV_ITEMS.map((item) => (
                <button
                  key={item}
                  onClick={() => {
                    setActiveItem(item)
                    setMobileOpen(false)
                  }}
                  className={cn(
                    'py-3 text-left text-lg font-medium transition-colors duration-150 border-b border-surface-400/20',
                    activeItem === item ? 'text-white' : 'text-surface-100 hover:text-white'
                  )}
                >
                  {item}
                </button>
              ))}
              {isAdmin && (
                <>
                  <a
                    href="/admin/analytics"
                    className="block py-3 text-lg font-medium text-cyan-400 border-b border-surface-400/20"
                  >
                    Analytics
                  </a>
                  <a
                    href="/admin.html"
                    className="block py-3 text-lg font-medium text-accent border-b border-surface-400/20"
                  >
                    Admin Panel
                  </a>
                </>
              )}
              <div className="mt-auto pb-8">
                {isAuthenticated ? (
                  <div className="space-y-3">
                    <div className="flex items-center gap-3 text-surface-100">
                      <div className="w-8 h-8 rounded-full bg-accent flex items-center justify-center text-sm font-bold text-white">
                        {profileInitial}
                      </div>
                      <div>
                        <span className="text-sm block">{user?.email}</span>
                        {isAdmin && <span className="text-[10px] text-accent font-semibold uppercase">Admin</span>}
                      </div>
                    </div>
                    <button
                      onClick={handleLogout}
                      className="w-full text-left py-2 text-sm text-surface-100 hover:text-white"
                    >
                      Sign Out
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={handleLogin}
                    className="flex items-center gap-3 text-surface-100 hover:text-white transition-colors duration-150"
                  >
                    <div className="w-8 h-8 rounded-full bg-accent flex items-center justify-center text-sm font-bold text-white">
                      U
                    </div>
                    <span className="text-sm">Sign In</span>
                  </button>
                )}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
}

export const Navbar = memo(NavbarComponent)
