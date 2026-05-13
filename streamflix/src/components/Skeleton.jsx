import { cn } from '../lib/cn'

export function Skeleton({ className, ...props }) {
  return (
    <div
      className={cn('skeleton-shimmer rounded-lg', className)}
      aria-hidden="true"
      {...props}
    />
  )
}

export function MovieCardSkeleton() {
  return (
    <div className="shrink-0 w-[160px] sm:w-[180px] md:w-[200px] space-y-2" aria-hidden="true">
      <Skeleton className="aspect-[2/3] w-full rounded-lg" />
      <Skeleton className="h-3 w-3/4" />
      <Skeleton className="h-3 w-1/2" />
    </div>
  )
}

export function HeroBannerSkeleton() {
  return (
    <div className="relative w-full h-[70vh] sm:h-[80vh] min-h-[500px]" aria-hidden="true">
      <Skeleton className="absolute inset-0 w-full h-full rounded-none" />
      <div className="absolute bottom-[15%] left-8 md:left-16 space-y-4 w-full max-w-xl">
        <Skeleton className="h-10 w-3/4" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-2/3" />
        <div className="flex gap-3 pt-2">
          <Skeleton className="h-10 w-32 rounded-md" />
          <Skeleton className="h-10 w-40 rounded-md" />
        </div>
      </div>
    </div>
  )
}

export function CarouselSkeleton({ count = 6 }) {
  return (
    <div className="space-y-3 px-8 md:px-16">
      <Skeleton className="h-6 w-48" />
      <div className="flex gap-2 overflow-hidden">
        {Array.from({ length: count }).map((_, i) => (
          <MovieCardSkeleton key={i} />
        ))}
      </div>
    </div>
  )
}
