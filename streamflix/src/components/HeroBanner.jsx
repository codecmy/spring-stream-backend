import { memo } from 'react'
import { Link } from 'react-router-dom'

function HeroBannerComponent({ videos, isLoading }) {
  if (isLoading) {
    return (
      <div className="relative w-full h-[50vh] sm:h-[60vh] min-h-[400px] bg-surface-900">
        <div className="absolute inset-0 skeleton-shimmer" />
        <div className="absolute bottom-[15%] left-8 md:left-16 space-y-4 w-full max-w-xl">
          <div className="h-8 w-3/4 rounded skeleton-shimmer" />
          <div className="h-4 w-full rounded skeleton-shimmer" />
          <div className="h-4 w-2/3 rounded skeleton-shimmer" />
        </div>
      </div>
    )
  }

  if (!videos?.length) return null

  const recent = videos[videos.length - 1]

  return (
    <section className="relative w-full h-[50vh] sm:h-[60vh] min-h-[400px]" aria-label="Latest video">
      <div className="absolute inset-0 bg-gradient-to-br from-surface-800 via-surface-900 to-black">
        <div className="absolute inset-0 opacity-20">
          <div className="absolute inset-0" style={{
            backgroundImage: 'radial-gradient(circle at 25% 50%, rgba(220,3,10,0.3) 0%, transparent 50%), radial-gradient(circle at 75% 50%, rgba(245,197,24,0.1) 0%, transparent 50%)'
          }} />
        </div>
      </div>

      <div className="absolute inset-0 bg-gradient-to-t from-surface-900 via-surface-900/30 to-transparent" />

      <div className="relative h-full flex items-center px-8 md:px-16 lg:px-20">
        <div className="max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 bg-accent/20 border border-accent/30 rounded-full text-accent text-xs font-semibold mb-4">
            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            {videos.length} video{videos.length !== 1 ? 's' : ''} available
          </div>

          <h1 className="font-display text-3xl sm:text-4xl md:text-5xl lg:text-6xl font-bold text-white leading-tight mb-3 text-balance">
            {recent.title}
          </h1>

          {recent.videoDescription && (
            <p className="text-sm sm:text-base text-surface-200 leading-relaxed mb-6 max-w-lg line-clamp-2 text-pretty">
              {recent.videoDescription}
            </p>
          )}

          <Link
            to={`/watch/${recent.videoId}`}
            className="inline-flex items-center gap-2 px-6 py-3 bg-accent text-white font-semibold rounded-lg hover:bg-accent/80 transition-colors duration-150 text-sm sm:text-base"
          >
            <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 5v14l11-7z" />
            </svg>
            Watch Now
          </Link>
        </div>
      </div>

      <div className="absolute bottom-0 left-0 right-0 h-16 bg-gradient-to-t from-surface-900 to-transparent pointer-events-none" />
    </section>
  )
}

export const HeroBanner = memo(HeroBannerComponent)
