import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts'

const COLORS = ['#ef4444', '#3b82f6', '#22d3ee', '#f59e0b', '#8b5cf6', '#10b981']

export default function QualityDistributionChart({ data = [] }) {
  return (
    <div className="rounded-xl border border-surface-600/30 bg-surface-800 p-5">
      <h3 className="text-white font-semibold mb-4">Quality Distribution</h3>
      <ResponsiveContainer width="100%" height={250}>
        <PieChart>
          <Pie
            data={data}
            cx="50%"
            cy="50%"
            innerRadius={50}
            outerRadius={85}
            paddingAngle={3}
            dataKey="count"
            nameKey="quality"
          >
            {data.map((_, i) => (
              <Cell key={i} fill={COLORS[i % COLORS.length]} />
            ))}
          </Pie>
          <Tooltip
            contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, color: '#f1f5f9' }}
          />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </div>
  )
}
