import { memo, useState, useEffect, useRef, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import useStore from '../store/useStore'
import { cn } from '../lib/cn'

const NAV_ITEMS = ['Home', 'Series', 'Movies', 'New & Popular', 'My List', 'Browse by Languages']

function NavbarComponent() {
  const scrolledPast = useStore((s) => s.scrolledPast)
  const setSearchOpen = useStore((s) => s.setSearchOpen)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [activeItem, setActiveItem] = useState('Home')
  const menuRef = useRef(null)

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
    document.body.style.overflow = mobileOpen ? 'hidden' : ''
    return () => { document.body.style.overflow = '' }
  }, [mobileOpen])

  const handleSearchClick = useCallback(() => {
    setSearchOpen(true)
  }, [setSearchOpen])

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

            <div className="hidden md:flex items-center">
              <button
                className="w-8 h-8 rounded-full bg-accent flex items-center justify-center text-sm font-bold text-white"
                aria-label="Profile"
              >
                U
              </button>
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
              <div className="mt-auto pb-8">
                <button className="flex items-center gap-3 text-surface-100 hover:text-white transition-colors duration-150">
                  <div className="w-8 h-8 rounded-full bg-accent flex items-center justify-center text-sm font-bold text-white">
                    U
                  </div>
                  <span className="text-sm">Account</span>
                </button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
}

export const Navbar = memo(NavbarComponent)
