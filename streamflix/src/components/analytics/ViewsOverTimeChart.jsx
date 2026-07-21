import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'

const COLORS = { views: '#ef4444', unique: '#22d3ee' }

export default function ViewsOverTimeChart({ data = [] }) {
  return (
    <div className="rounded-xl border border-surface-600/30 bg-surface-800 p-5">
      <h3 className="text-white font-semibold mb-4">Views Over Time</h3>
      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
          <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#94a3b8' }} />
          <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} />
          <Tooltip
            contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, color: '#f1f5f9' }}
          />
          <Legend />
          <Line type="monotone" dataKey="views" stroke={COLORS.views} strokeWidth={2} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
