import { memo, useState } from 'react'
import { Link } from 'react-router-dom'

function MovieCardComponent({ item, index = 0 }) {
  const [imgError, setImgError] = useState(false)
  const colorIndex = (item.videoId || item.id || '').charCodeAt(0) % 6
  const gradientColors = [
    'from-crimson-600/30 via-surface-800 to-surface-900',
    'from-gold/20 via-surface-800 to-surface-900',
    'from-blue-600/30 via-surface-800 to-surface-900',
    'from-green-600/30 via-surface-800 to-surface-900',
    'from-purple-600/30 via-surface-800 to-surface-900',
    'from-orange-600/30 via-surface-800 to-surface-900',
  ]

  return (
    <Link
      to={`/watch/${item.videoId || item.id}`}
      className="group/card relative shrink-0 w-[160px] sm:w-[180px] md:w-[200px] cursor-pointer"
      aria-label={`Watch ${item.title}`}
    >
      <div className="relative aspect-[2/3] w-full rounded-lg overflow-hidden bg-surface-700 will-change-transform">
        {imgError || !item.videoId ? (
          <div className={`absolute inset-0 bg-gradient-to-br ${gradientColors[colorIndex]}`}>
            <div className="absolute inset-0 flex items-center justify-center">
              <svg className="w-12 h-12 text-white/20 group-hover/card:text-white/40 transition-colors duration-300" fill="currentColor" viewBox="0 0 24 24">
                <path d="M8 5v14l11-7z" />
              </svg>
            </div>
          </div>
        ) : (
          <img
            src={`/api/v1/videos/${item.videoId}/thumbnail`}
            alt={item.title}
            className="absolute inset-0 w-full h-full object-cover group-hover/card:scale-105 transition-transform duration-300"
            onError={() => setImgError(true)}
          />
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover/card:opacity-100 transition-opacity duration-200" />
        <div className="absolute bottom-0 left-0 right-0 p-3 translate-y-2 group-hover/card:translate-y-0 opacity-0 group-hover/card:opacity-100 transition-all duration-200">
          <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-accent text-white text-xs font-semibold rounded-lg">
            <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 5v14l11-7z" />
            </svg>
            Play
          </span>
        </div>
      </div>
      <div className="mt-2 px-0.5">
        <h3 className="text-sm font-medium text-white truncate leading-tight group-hover/card:text-accent transition-colors duration-150">
          {item.title || 'Untitled'}
        </h3>
        <p className="text-xs text-surface-300 mt-0.5 truncate">
          {item.contentType?.replace('video/', '') || 'video'}
        </p>
      </div>
    </Link>
  )
}

export const MovieCard = memo(MovieCardComponent)
