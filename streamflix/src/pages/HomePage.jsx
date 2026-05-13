import { useQuery } from '@tanstack/react-query'
import { fetchVideos } from '../api/api'
import { HeroBanner } from '../components/HeroBanner'
import { MovieCarousel } from '../components/MovieCarousel'
import { CarouselSkeleton } from '../components/Skeleton'
import { useScrollPosition } from '../hooks/useScrollPosition'

export default function HomePage() {
  useScrollPosition()

  const { data: videos, isLoading } = useQuery({
    queryKey: ['videos'],
    queryFn: fetchVideos,
    refetchInterval: 30000,
  })

  return (
    <main className="min-h-screen bg-surface-900 overflow-x-hidden">
      <HeroBanner videos={videos} isLoading={isLoading} />

      <div className="relative z-10 -mt-16 space-y-6 pb-16">
        {isLoading ? (
          <>
            <CarouselSkeleton />
            <CarouselSkeleton />
          </>
        ) : videos?.length > 0 ? (
          <>
            <section className="pt-4">
              <MovieCarousel title="All Videos" items={videos} />
            </section>
            {videos.length > 4 && (
              <section>
                <MovieCarousel
                  title="Recently Added"
                  items={[...videos].reverse().slice(0, 8)}
                />
              </section>
            )}
          </>
        ) : (
          <div className="flex flex-col items-center justify-center pt-32 px-4">
            <svg className="w-16 h-16 text-surface-300 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
            </svg>
            <h2 className="text-xl font-display font-semibold text-surface-100 mb-2">No videos yet</h2>
            <p className="text-surface-300 text-sm text-center max-w-md">
              Upload a video using the API at <code className="text-accent">POST /api/v1/videos</code> to get started.
            </p>
          </div>
        )}
      </div>
    </main>
  )
}
