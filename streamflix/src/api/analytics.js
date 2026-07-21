const ANALYTICS_BASE = '/api/v1/admin/analytics'

function authHeaders() {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function fetchAnalyticsOverview(days = 7) {
  const res = await fetch(`${ANALYTICS_BASE}/overview?days=${days}`, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to fetch analytics overview')
  return res.json()
}

export async function fetchVideoAnalyticsList() {
  const res = await fetch(`${ANALYTICS_BASE}/videos`, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to fetch video analytics')
  return res.json()
}

export async function fetchVideoAnalyticsDetail(videoId, days = 7) {
  const res = await fetch(`${ANALYTICS_BASE}/videos/${videoId}?days=${days}`, { headers: authHeaders() })
  if (!res.ok) throw new Error('Failed to fetch video detail')
  return res.json()
}
