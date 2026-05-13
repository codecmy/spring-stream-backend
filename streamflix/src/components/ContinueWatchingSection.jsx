import { memo } from 'react'
import { motion } from 'framer-motion'
import { cn } from '../lib/cn'

const ContinueWatchingCard = memo(({ item, index }) => {
  const progress = item.progress || 0

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{
        duration: 0.3,
        ease: [0.4, 0, 0.2, 1],
        delay: Math.min(index * 0.05, 0.25),
      }}
      className="group/cw relative shrink-0 w-[240px] sm:w-[260px] md:w-[280px] cursor-pointer"
      role="button"
      tabIndex={0}
      aria-label={`${item.title}${item.episode ? `, ${item.episode}` : ''}, ${progress}% complete`}
    >
      <div className="relative aspect-video w-full rounded-lg overflow-hidden bg-surface-700">
        <img
          src={item.backdrop || item.poster}
          alt={item.title}
          loading="lazy"
          className="w-full h-full object-cover transition-transform duration-300 ease-[cubic-bezier(0.4,0,0.2,1)] group-hover/cw:scale-105"
          style={{ willChange: 'transform' }}
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent opacity-0 group-hover/cw:opacity-100 transition-opacity duration-200" />
        <div className="absolute bottom-0 left-0 right-0 h-1 bg-surface-600">
          <div
            className="h-full bg-accent transition-all duration-300"
            style={{ width: `${progress}%` }}
          />
        </div>
        <div className="absolute top-2 right-2 opacity-0 group-hover/cw:opacity-100 transition-opacity duration-200">
          <button
            className="w-8 h-8 rounded-full bg-white/90 flex items-center justify-center shadow-lg"
            aria-label={`Continue watching ${item.title}`}
            onClick={(e) => e.stopPropagation()}
          >
            <svg className="w-3.5 h-3.5 text-black ml-0.5" fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 5v14l11-7z" />
            </svg>
          </button>
        </div>
      </div>
      <div className="mt-2 px-0.5">
        <h3 className="text-sm font-medium text-white truncate">{item.title}</h3>
        {item.episode && (
          <p className="text-xs text-surface-300 mt-0.5">{item.episode}</p>
        )}
      </div>
    </motion.div>
  )
})

ContinueWatchingCard.displayName = 'ContinueWatchingCard'

function ContinueWatchingSectionComponent({ items, isLoading = false }) {
  if (isLoading) {
    return (
      <section>
        <div className="px-8 md:px-16 mb-3">
          <div className="h-6 w-48 rounded skeleton-shimmer" />
        </div>
        <div className="flex gap-3 px-8 md:px-16">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="shrink-0 w-[240px] sm:w-[260px] md:w-[280px] space-y-2">
              <div className="aspect-video w-full rounded-lg skeleton-shimmer" />
              <div className="h-3 w-2/3 rounded skeleton-shimmer" />
              <div className="h-2 w-1/3 rounded skeleton-shimmer" />
            </div>
          ))}
        </div>
      </section>
    )
  }

  if (!items || items.length === 0) return null

  return (
    <section aria-label="Continue watching">
      <div className="px-8 md:px-16 mb-3">
        <h2 className="font-display text-lg sm:text-xl md:text-2xl font-bold text-white">
          Continue Watching
        </h2>
      </div>
      <div className="flex gap-3 overflow-x-auto no-scrollbar px-8 md:px-16 py-2">
        {items.map((item, i) => (
          <ContinueWatchingCard key={item.id} item={item} index={i} />
        ))}
      </div>
    </section>
  )
}

export const ContinueWatchingSection = memo(ContinueWatchingSectionComponent)
