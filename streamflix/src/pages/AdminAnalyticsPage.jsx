import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import useStore from '../store/useStore'
import { fetchAnalyticsOverview, fetchVideoAnalyticsList, fetchVideoAnalyticsDetail } from '../api/analytics'
import StatsCard from '../components/analytics/StatsCard'
import ViewsOverTimeChart from '../components/analytics/ViewsOverTimeChart'
import TopVideosChart from '../components/analytics/TopVideosChart'
import DropOffChart from '../components/analytics/DropOffChart'
import QualityDistributionChart from '../components/analytics/QualityDistributionChart'
import DeviceBreakdownChart from '../components/analytics/DeviceBreakdownChart'

export default function AdminAnalyticsPage() {
  const navigate = useNavigate()
  const isAdmin = useStore((s) => s.isAdmin)
  const isAuthenticated = useStore((s) => s.isAuthenticated)
  const authLoading = useStore((s) => s.authLoading)

  const [days, setDays] = useState(7)
  const [overview, setOverview] = useState(null)
  const [videos, setVideos] = useState([])
  const [selectedVideo, setSelectedVideo] = useState(null)
  const [videoDetail, setVideoDetail] = useState(null)
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)

  useEffect(() => {
    if (!authLoading && (!isAuthenticated || !isAdmin)) {
      navigate('/login?redirect=/admin/analytics')
    }
  }, [authLoading, isAuthenticated, isAdmin, navigate])

  useEffect(() => {
    if (!isAdmin) return
    let cancelled = false
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true)
    Promise.all([
      fetchAnalyticsOverview(days),
      fetchVideoAnalyticsList(),
    ])
      .then(([ov, vids]) => {
        if (cancelled) return
        setOverview(ov)
        setVideos(vids)
        setLoading(false)
      })
      .catch(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [days, isAdmin])

  useEffect(() => {
    if (!selectedVideo) return
    let cancelled = false
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setDetailLoading(true)
    fetchVideoAnalyticsDetail(selectedVideo, days)
      .then((d) => { if (!cancelled) { setVideoDetail(d); setDetailLoading(false) } })
      .catch(() => { if (!cancelled) setDetailLoading(false) })
    return () => { cancelled = true }
  }, [selectedVideo, days])

  if (authLoading || !isAuthenticated || !isAdmin) return null

  return (
    <div className="min-h-screen bg-surface-900 pt-20 pb-10 px-4 sm:px-8 md:px-16">
      <div className="max-w-[1400px] mx-auto">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
          <div>
            <h1 className="text-2xl sm:text-3xl font-display font-bold text-white">Video Analytics</h1>
            <p className="text-surface-300 text-sm mt-1">Track views, engagement, and viewer behavior</p>
          </div>
          <div className="flex items-center gap-2">
            <a href="/admin.html" className="px-3 py-1.5 text-xs font-semibold text-surface-200 border border-surface-600/50 rounded-lg hover:bg-surface-700 transition-colors">
              Admin Panel
            </a>
            {[7, 30, 90].map((d) => (
              <button
                key={d}
                onClick={() => setDays(d)}
                className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors ${
                  days === d
                    ? 'bg-accent text-white'
                    : 'text-surface-200 border border-surface-600/50 hover:bg-surface-700'
                }`}
              >
                {d}d
              </button>
            ))}
          </div>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="w-8 h-8 border-2 border-accent border-t-transparent rounded-full animate-spin" />
          </div>
        ) : (
          <>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
              <StatsCard label="Total Views" value={overview?.totalViews?.toLocaleString() ?? '0'} />
              <StatsCard label="Watch Time" value={`${overview?.totalWatchTimeHours ?? 0}h`} />
              <StatsCard label="Avg Completion" value={`${overview?.avgCompletionRate ?? 0}%`} />
              <StatsCard label="Videos Tracked" value={overview?.totalVideos ?? 0} />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
              <ViewsOverTimeChart data={overview?.viewsOverTime ?? []} />
              <TopVideosChart data={overview?.topVideos ?? []} />
            </div>

            <div className="mb-8">
              <h2 className="text-lg font-semibold text-white mb-4">Per-Video Analytics</h2>
              <div className="rounded-xl border border-surface-600/30 bg-surface-800 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-surface-600/30">
                        <th className="text-left px-4 py-3 text-surface-300 font-medium">Video</th>
                        <th className="text-right px-4 py-3 text-surface-300 font-medium">Views</th>
                        <th className="text-right px-4 py-3 text-surface-300 font-medium hidden sm:table-cell">Plays</th>
                        <th className="text-right px-4 py-3 text-surface-300 font-medium hidden md:table-cell">Complete</th>
                        <th className="text-right px-4 py-3 text-surface-300 font-medium hidden md:table-cell">Drop-offs</th>
                        <th className="text-right px-4 py-3 text-surface-300 font-medium">Avg Time</th>
                        <th className="text-right px-4 py-3 text-surface-300 font-medium hidden lg:table-cell">Completion</th>
                        <th className="px-4 py-3"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {videos.map((v) => (
                        <tr
                          key={v.videoId}
                          className={`border-b border-surface-600/20 transition-colors cursor-pointer ${
                            selectedVideo === v.videoId ? 'bg-accent/10' : 'hover:bg-surface-700/50'
                          }`}
                          onClick={() => {
                            setSelectedVideo(selectedVideo === v.videoId ? null : v.videoId)
                            setVideoDetail(null)
                          }}
                        >
                          <td className="px-4 py-3 text-white font-medium max-w-[200px] truncate">{v.title}</td>
                          <td className="px-4 py-3 text-right text-surface-200">{v.totalViews?.toLocaleString()}</td>
                          <td className="px-4 py-3 text-right text-surface-200 hidden sm:table-cell">{v.totalPlays?.toLocaleString()}</td>
                          <td className="px-4 py-3 text-right text-surface-200 hidden md:table-cell">{v.totalCompletions?.toLocaleString()}</td>
                          <td className="px-4 py-3 text-right text-surface-200 hidden md:table-cell">{v.totalDropOffs?.toLocaleString()}</td>
                          <td className="px-4 py-3 text-right text-surface-200">{Math.round(v.avgWatchTimeSeconds ?? 0)}s</td>
                          <td className="px-4 py-3 text-right text-surface-200 hidden lg:table-cell">{v.completionRate ?? 0}%</td>
                          <td className="px-4 py-3 text-right">
                            <svg className={`w-4 h-4 text-surface-400 transition-transform ${selectedVideo === v.videoId ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                              <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                            </svg>
                          </td>
                        </tr>
                      ))}
                      {videos.length === 0 && (
                        <tr>
                          <td colSpan={8} className="px-4 py-10 text-center text-surface-400">
                            No analytics data yet. Start playing videos to generate events.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            {selectedVideo && (
              <div className="mb-8">
                <h2 className="text-lg font-semibold text-white mb-4">
                  {videoDetail?.title ?? 'Loading...'}
                </h2>
                {detailLoading ? (
                  <div className="flex items-center justify-center py-10">
                    <div className="w-6 h-6 border-2 border-accent border-t-transparent rounded-full animate-spin" />
                  </div>
                ) : videoDetail ? (
                  <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    <ViewsOverTimeChart data={videoDetail.viewsOverTime ?? []} />
                    <DropOffChart data={videoDetail.dropOffCurve ?? []} />
                    <QualityDistributionChart data={videoDetail.qualityDistribution ?? []} />
                    <DeviceBreakdownChart data={videoDetail.deviceBreakdown ?? []} />
                  </div>
                ) : null}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
