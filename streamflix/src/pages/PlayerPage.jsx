import { useEffect, useRef, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import Hls from 'hls.js'
import { fetchVideos, getMasterPlaylistUrl, getQualityPlaylistUrl } from '../api/api'

const QUALITIES = [
  { label: 'Auto', value: '' },
  { label: '720p', value: 'v3' },
  { label: '360p', value: 'v2' },
  { label: '240p', value: 'v1' },
  { label: '144p', value: 'v0' },
]

export default function PlayerPage() {
  const { videoId } = useParams()
  const navigate = useNavigate()
  const videoRef = useRef(null)
  const hlsRef = useRef(null)
  const [video, setVideo] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [quality, setQuality] = useState('')

  useEffect(() => {
    let cancelled = false
    fetchVideos()
      .then((videos) => {
        if (cancelled) return
        const found = videos.find((v) => v.videoId === videoId)
        if (found) {
          setVideo(found)
        } else {
          setError('Video not found')
        }
        setLoading(false)
      })
      .catch(() => {
        if (!cancelled) {
          setError('Failed to load video info')
          setLoading(false)
        }
      })
    return () => { cancelled = true }
  }, [videoId])

  const setupHls = useCallback((videoElement, url) => {
    if (hlsRef.current) {
      hlsRef.current.destroy()
      hlsRef.current = null
    }

    if (!Hls.isSupported()) {
      if (videoElement.canPlayType('application/vnd.apple.mpegurl')) {
        videoElement.src = url
      } else {
        setError('HLS is not supported in this browser')
        return
      }
      return
    }

    const hls = new Hls({
      enableWorker: true,
      lowLatencyMode: false,
    })
    hlsRef.current = hls

    hls.on(Hls.Events.ERROR, (_, data) => {
      if (data.fatal) {
        hls.destroy()
        hlsRef.current = null
        setError('This video is not available yet — transcoding may still be in progress.')
      }
    })

    hls.attachMedia(videoElement)
    hls.loadSource(url)
  }, [])

  useEffect(() => {
    const el = videoRef.current
    if (!el || !videoId || !video) return

    if (video.contentType === 'video/mp4') {
      setLoading(false)
      return
    }

    const url = quality ? getQualityPlaylistUrl(videoId, quality) : getMasterPlaylistUrl(videoId)
    setupHls(el, url)

    return () => {
      if (hlsRef.current) {
        hlsRef.current.destroy()
        hlsRef.current = null
      }
    }
  }, [videoId, quality, setupHls, video])

  const handleQualityChange = (e) => {
    setQuality(e.target.value)
  }

  const fileName = video?.filepath
    ? decodeURIComponent(video.filepath.split('_').slice(1).join('_') || '')
    : ''

  if (loading) {
    return (
      <div className="min-h-screen bg-black flex items-center justify-center">
        <div className="w-10 h-10 border-2 border-accent border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen bg-surface-900 flex flex-col items-center justify-center gap-4 px-4">
        <p className="text-surface-300 text-lg">{error}</p>
        <button
          onClick={() => navigate('/')}
          className="px-6 py-2 bg-accent text-white rounded-lg hover:bg-accent/80 transition-colors"
        >
          Back to Home
        </button>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-black flex flex-col">
      <div className="relative w-full bg-black flex items-center justify-center" style={{ minHeight: '50vh', maxHeight: '80vh' }}>
        {video?.contentType === 'video/mp4' ? (
          <div className="flex flex-col items-center gap-4 p-8">
            <svg className="w-16 h-16 text-surface-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
            </svg>
            <p className="text-surface-300 text-lg font-medium">Video is being processed</p>
            <p className="text-surface-400 text-sm text-center max-w-md">
              HLS transcoding is in progress. Once complete, you'll be able to stream this video with quality selection. Check back shortly.
            </p>
          </div>
        ) : (
          <video
            ref={videoRef}
            className="w-full h-full max-h-[80vh] object-contain bg-black"
            controls
            autoPlay
            playsInline
          />
        )}
      </div>

      <div className="flex-1 bg-surface-900 px-4 sm:px-8 md:px-16 py-6">
        <button
          onClick={() => navigate('/')}
          className="flex items-center gap-2 text-surface-200 hover:text-white transition-colors mb-4 text-sm"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          Back to Browse
        </button>

        <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
          <div className="flex-1 min-w-0">
            <h1 className="text-2xl sm:text-3xl font-display font-bold text-white mb-2">
              {video?.title || 'Untitled'}
            </h1>
            {video?.videoDescription && (
              <p className="text-surface-200 text-sm sm:text-base leading-relaxed max-w-2xl">
                {video.videoDescription}
              </p>
            )}
            {fileName && (
              <p className="text-surface-300 text-xs mt-2 truncate">
                {fileName}
              </p>
            )}
          </div>

          <div className="flex items-center gap-3 shrink-0">
            <label className="text-surface-300 text-sm">Quality:</label>
            <select
              value={quality}
              onChange={handleQualityChange}
              className="bg-surface-700 text-white text-sm rounded-lg px-3 py-2 border border-surface-400/30 focus:outline-none focus:border-accent/50"
            >
              {QUALITIES.map((q) => (
                <option key={q.value} value={q.value}>{q.label}</option>
              ))}
            </select>
          </div>
        </div>
      </div>
    </div>
  )
}
