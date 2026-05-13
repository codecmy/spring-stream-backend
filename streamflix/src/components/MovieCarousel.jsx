import { memo, useRef, useState, useCallback, useEffect } from 'react'
import { motion } from 'framer-motion'
import { MovieCard } from './MovieCard'
import { MovieCardSkeleton } from './Skeleton'
import useStore from '../store/useStore'

function MovieCarouselComponent({ title, items, isLoading = false }) {
  const scrollRef = useRef(null)
  const [canScrollLeft, setCanScrollLeft] = useState(false)
  const [canScrollRight, setCanScrollRight] = useState(true)
  const setAllItems = useStore((s) => s.setAllItems)

  useEffect(() => {
    if (items?.length) {
      setAllItems(items)
    }
  }, [items, setAllItems])

  const updateScrollButtons = useCallback(() => {
    const el = scrollRef.current
    if (!el) return
    setCanScrollLeft(el.scrollLeft > 4)
    setCanScrollRight(el.scrollLeft < el.scrollWidth - el.clientWidth - 4)
  }, [])

  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    el.addEventListener('scroll', updateScrollButtons, { passive: true })
    updateScrollButtons()
    return () => el.removeEventListener('scroll', updateScrollButtons)
  }, [updateScrollButtons, items])

  const scroll = useCallback((direction) => {
    const el = scrollRef.current
    if (!el) return
    const cardWidth = 200
    const gap = 8
    const scrollAmount = (cardWidth + gap) * Math.floor(el.clientWidth / (cardWidth + gap))
    el.scrollBy({
      left: direction === 'left' ? -scrollAmount : scrollAmount,
      behavior: 'smooth',
    })
  }, [])

  return (
    <section className="relative group/carousel">
      <div className="px-8 md:px-16 mb-3">
        <h2 className="font-display text-lg sm:text-xl md:text-2xl font-bold text-white">
          {title}
        </h2>
      </div>

      <div className="relative">
        {canScrollLeft && (
          <button
            onClick={() => scroll('left')}
            className="absolute left-0 top-0 bottom-0 z-10 w-12 md:w-16 bg-gradient-to-r from-surface-900/90 to-transparent flex items-center justify-start pl-2 opacity-0 group-hover/carousel:opacity-100 transition-opacity duration-200"
            aria-label="Scroll left"
          >
            <svg className="w-6 h-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
        )}

        <div
          ref={scrollRef}
          className="flex gap-2 overflow-x-auto no-scrollbar px-8 md:px-16 py-2"
          style={{ scrollBehavior: 'smooth' }}
        >
          {isLoading
            ? Array.from({ length: 7 }).map((_, i) => <MovieCardSkeleton key={i} />)
            : items?.map((item, i) => (
                <motion.div
                  key={item.videoId || item.id}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{
                    duration: 0.3,
                    ease: [0.4, 0, 0.2, 1],
                    delay: Math.min(i * 0.04, 0.3),
                  }}
                >
                  <MovieCard item={item} index={i} />
                </motion.div>
              ))}
        </div>

        {canScrollRight && (
          <button
            onClick={() => scroll('right')}
            className="absolute right-0 top-0 bottom-0 z-10 w-12 md:w-16 bg-gradient-to-l from-surface-900/90 to-transparent flex items-center justify-end pr-2 opacity-0 group-hover/carousel:opacity-100 transition-opacity duration-200"
            aria-label="Scroll right"
          >
            <svg className="w-6 h-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        )}
      </div>
    </section>
  )
}

export const MovieCarousel = memo(MovieCarouselComponent)
