export default function StatsCard({ label, value, sub }) {
  return (
    <div className="rounded-xl border border-surface-600/30 bg-surface-800 p-5">
      <p className="text-surface-300 text-xs font-medium uppercase tracking-wider mb-1">{label}</p>
      <p className="text-2xl font-bold text-white">{value}</p>
      {sub && <p className="text-surface-400 text-xs mt-1">{sub}</p>}
    </div>
  )
}
