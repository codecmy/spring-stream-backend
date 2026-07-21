const SESSION_ID = crypto.randomUUID()

function getDeviceType() {
  const ua = navigator.userAgent
  if (/Mobi|Android/i.test(ua)) return 'mobile'
  if (/Tablet|iPad/i.test(ua)) return 'tablet'
  return 'desktop'
}

export function trackEvent({ eventType, videoId, position, duration, seekFrom, seekTo, quality }) {
  const token = localStorage.getItem('token')
  if (!token || !videoId) return

  const event = {
    eventType,
    videoId,
    sessionId: SESSION_ID,
    deviceType: getDeviceType(),
    quality: quality || '',
    timestamp: new Date().toISOString(),
  }
  if (position != null) event.position = Math.floor(position)
  if (duration != null) event.duration = Math.floor(duration)
  if (seekFrom != null) event.seekFrom = Math.floor(seekFrom)
  if (seekTo != null) event.seekTo = Math.floor(seekTo)

  fetch('/api/v1/events', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + token,
    },
    body: JSON.stringify(event),
  }).catch(() => {})
}
