const API_BASE = '/api/v1/videos'

export async function fetchVideos() {
  const res = await fetch(API_BASE)
  if (!res.ok) throw new Error('Failed to fetch videos')
  return res.json()
}

export function getMasterPlaylistUrl(videoId) {
  return `${API_BASE}/${videoId}/master.m3u8`
}

export function getQualityPlaylistUrl(videoId, quality) {
  return `${API_BASE}/${videoId}/${quality}/index.m3u8`
}

export function searchVideos(videos, query) {
  if (!query?.trim()) return videos
  const q = query.toLowerCase()
  return videos.filter(
    (v) =>
      v.title?.toLowerCase().includes(q) ||
      v.videoDescription?.toLowerCase().includes(q)
  )
}
