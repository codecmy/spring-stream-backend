import { useEffect, useRef } from 'react'
import useStore from '../store/useStore'

export function useScrollPosition() {
  const setScrolledPast = useStore((s) => s.setScrolledPast)
  const rafRef = useRef(null)

  useEffect(() => {
    const handleScroll = () => {
      if (rafRef.current) return
      rafRef.current = requestAnimationFrame(() => {
        setScrolledPast(window.scrollY > 64)
        rafRef.current = null
      })
    }

    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => {
      window.removeEventListener('scroll', handleScroll)
      if (rafRef.current) cancelAnimationFrame(rafRef.current)
    }
  }, [setScrolledPast])
}
