import { memo, useState, useEffect, useRef, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useNavigate } from 'react-router-dom'
import useStore from '../store/useStore'
import { fetchVideos, searchVideos } from '../api/api'

const overlayVariants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1 },
  exit: { opacity: 0, transition: { delay: 0.1 } },
}

const panelVariants = {
  hidden: { y: -20, opacity: 0, scaleY: 0.95 },
  visible: {
    y: 0, opacity: 1, scaleY: 1,
    transition: { duration: 0.3, ease: [0.4, 0, 0.2, 1] },
  },
  exit: {
    y: -10, opacity: 0, scaleY: 0.97,
    transition: { duration: 0.2, ease: [0.4, 0, 0.2, 1] },
  },
}

function SearchOverlayComponent() {
  const searchOpen = useStore((s) => s.searchOpen)
  const setSearchOpen = useStore((s) => s.setSearchOpen)
  const searchQuery = useStore((s) => s.searchQuery)
  const setSearchQuery = useStore((s) => s.setSearchQuery)
  const allVideos = useStore((s) => s.allVideos)
  const setAllVideos = useStore((s) => s.setAllVideos)
  const [results, setResults] = useState([])
  const inputRef = useRef(null)
  const navigate = useNavigate()

  useEffect(() => {
    if (searchOpen && inputRef.current) {
      inputRef.current.focus()
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
    return () => { document.body.style.overflow = '' }
  }, [searchOpen])

  useEffect(() => {
    const handleKey = (e) => {
      if (e.key === 'Escape' && searchOpen) {
        setSearchOpen(false)
      }
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault()
        setSearchOpen(!searchOpen)
      }
    }
    window.addEventListener('keydown', handleKey)
    return () => window.removeEventListener('keydown', handleKey)
  }, [searchOpen, setSearchOpen])

  useEffect(() => {
    if (!allVideos.length) {
      fetchVideos().then(setAllVideos).catch(() => {})
    }
  }, [allVideos.length, setAllVideos])

  const handleInputChange = useCallback((e) => {
    const val = e.target.value
    setSearchQuery(val)
    const filtered = searchVideos(allVideos, val)
    setResults(filtered)
  }, [setSearchQuery, allVideos])

  const handleClose = useCallback(() => {
    setSearchOpen(false)
    setSearchQuery('')
    setResults([])
  }, [setSearchOpen, setSearchQuery])

  const handleSelect = useCallback((videoId) => {
    handleClose()
    navigate(`/watch/${videoId}`)
  }, [handleClose, navigate])

  return (
    <AnimatePresence>
      {searchOpen && (
        <motion.div
          variants={overlayVariants}
          initial="hidden"
          animate="visible"
          exit="exit"
          transition={{ duration: 0.2 }}
          className="fixed inset-0 z-[70] bg-black/80 backdrop-blur-sm"
          onClick={handleClose}
          aria-modal="true"
          role="dialog"
          aria-label="Search"
        >
          <motion.div
            variants={panelVariants}
            initial="hidden"
            animate="visible"
            exit="exit"
            onClick={(e) => e.stopPropagation()}
            className="relative w-full max-w-2xl mx-auto mt-[15vh] px-4"
            style={{ transformOrigin: 'top center' }}
          >
            <div className="relative">
              <svg className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-surface-200 pointer-events-none" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              <input
                ref={inputRef}
                type="text"
                value={searchQuery}
                onChange={handleInputChange}
                placeholder="Search videos by title..."
                className="w-full h-14 pl-12 pr-12 bg-surface-800/95 border border-surface-400/30 rounded-xl text-white text-lg placeholder-surface-300 focus:outline-none focus:border-accent/50 focus:ring-1 focus:ring-accent/30 transition-all duration-200"
                aria-label="Search input"
              />
              <button
                onClick={handleClose}
                className="absolute right-4 top-1/2 -translate-y-1/2 p-1 text-surface-200 hover:text-white transition-colors duration-150"
                aria-label="Close search"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="mt-1 text-xs text-surface-300 text-right">
              <kbd className="px-1.5 py-0.5 bg-surface-700 rounded text-[10px]">ESC</kbd> to close
            </div>

            {searchQuery.trim() && results.length > 0 && (
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="mt-4 max-h-[50vh] overflow-y-auto rounded-xl bg-surface-800/95 border border-surface-400/20 divide-y divide-surface-400/10"
              >
                {results.map((item) => (
                  <button
                    key={item.videoId}
                    onClick={() => handleSelect(item.videoId)}
                    className="flex items-center gap-4 w-full p-3 hover:bg-surface-700/50 transition-colors duration-150 text-left"
                  >
                    <div className="w-12 h-16 rounded-md overflow-hidden bg-surface-700 shrink-0 flex items-center justify-center">
                      <svg className="w-5 h-5 text-surface-400" fill="currentColor" viewBox="0 0 24 24">
                        <path d="M8 5v14l11-7z" />
                      </svg>
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium text-white truncate">{item.title}</p>
                      {item.videoDescription && (
                        <p className="text-xs text-surface-300 truncate mt-0.5">{item.videoDescription}</p>
                      )}
                    </div>
                  </button>
                ))}
              </motion.div>
            )}

            {searchQuery.trim() && results.length === 0 && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="mt-8 text-center"
              >
                <p className="text-surface-300 text-sm">No videos found for "{searchQuery}"</p>
              </motion.div>
            )}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

export const SearchOverlay = memo(SearchOverlayComponent)
