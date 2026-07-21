import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

export default function TopVideosChart({ data = [] }) {
  const formatted = data.map((d) => ({
    ...d,
    shortTitle: d.title?.length > 20 ? d.title.slice(0, 20) + '...' : d.title,
  }))

  return (
    <div className="rounded-xl border border-surface-600/30 bg-surface-800 p-5">
      <h3 className="text-white font-semibold mb-4">Top Videos</h3>
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={formatted} layout="vertical" margin={{ left: 20 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#334155" horizontal={false} />
          <XAxis type="number" tick={{ fontSize: 11, fill: '#94a3b8' }} />
          <YAxis type="category" dataKey="shortTitle" tick={{ fontSize: 11, fill: '#94a3b8' }} width={140} />
          <Tooltip
            contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, color: '#f1f5f9' }}
          />
          <Bar dataKey="views" fill="#ef4444" radius={[0, 4, 4, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
