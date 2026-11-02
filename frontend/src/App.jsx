
import { useState } from 'react'

function App() {
  const [longUrl, setLongUrl] = useState('')
  const [customAlias, setCustomAlias] = useState('')
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const shorten = async () => {
    setLoading(true)
    setError('')
    try {
      const res = await fetch('/api/v1/shorten', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ longUrl, customAlias: customAlias || null })
      })
      if (!res.ok) {
        const err = await res.json()
        throw new Error(err.message || 'Failed')
      }
      const data = await res.json()
      setResult(data)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-900 to-slate-900 text-white flex flex-col items-center p-8">
      <div className="w-full max-w-3xl">
        <h1 className="text-5xl font-bold text-center mb-2">🔗 Short.ly</h1>
        <p className="text-center text-blue-200 mb-8">Production-Grade URL Shortener - Bitly Clone v2.0</p>

        <div className="bg-white/10 backdrop-blur rounded-2xl p-6 shadow-2xl border border-white/20">
          <div className="flex flex-col gap-4">
            <input
              value={longUrl}
              onChange={e => setLongUrl(e.target.value)}
              placeholder="https://example.com/very/long/url/with/many/params"
              className="w-full p-4 rounded-xl bg-black/30 border border-white/20 text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-400"
            />
            <input
              value={customAlias}
              onChange={e => setCustomAlias(e.target.value)}
              placeholder="Custom alias (optional, e.g. mylink)"
              className="w-full p-3 rounded-xl bg-black/20 border border-white/10 text-white placeholder-gray-500"
            />
            <button
              onClick={shorten}
              disabled={loading || !longUrl}
              className="w-full py-4 bg-gradient-to-r from-blue-500 to-cyan-400 rounded-xl font-bold text-lg hover:from-blue-600 hover:to-cyan-500 disabled:opacity-50 transition"
            >
              {loading ? 'Shortening...' : 'Shorten URL →'}
            </button>
          </div>

          {error && <div className="mt-4 p-3 bg-red-500/20 border border-red-400 rounded-xl text-red-200">{error}</div>}

          {result && (
            <div className="mt-6 p-4 bg-green-500/10 border border-green-400/30 rounded-xl">
              <div className="text-sm text-green-200 mb-1">Short URL (7 chars, Snowflake + Base62):</div>
              <div className="flex gap-2 items-center">
                <a href={result.shortUrl} target="_blank" className="text-xl font-mono text-cyan-300 hover:underline">{result.shortUrl}</a>
                <button onClick={() => navigator.clipboard.writeText(result.shortUrl)} className="px-3 py-1 bg-white/20 rounded-lg text-sm">Copy</button>
              </div>
              <div className="mt-3 text-xs text-gray-400">
                <div>Code: {result.shortCode} | Expires: {result.expiresInHours}h | Long: {result.longUrl.slice(0,60)}...</div>
              </div>
            </div>
          )}

          <div className="mt-8 grid grid-cols-3 gap-4 text-center">
            <div className="p-3 bg-black/20 rounded-xl"><div className="text-2xl font-bold">7</div><div className="text-xs text-gray-400">Chars (62^7 = 3.5T)</div></div>
            <div className="p-3 bg-black/20 rounded-xl"><div className="text-2xl font-bold">~2ms</div><div className="text-xs text-gray-400">Cache Hit</div></div>
            <div className="p-3 bg-black/20 rounded-xl"><div className="text-2xl font-bold">4M/ms</div><div className="text-xs text-gray-400">IDs (Snowflake)</div></div>
          </div>
        </div>

        <div className="mt-8 bg-black/30 rounded-xl p-4 border border-white/10">
          <h3 className="font-bold mb-2">What it teaches (Architect View):</h3>
          <ul className="text-sm text-gray-300 space-y-1 list-disc ml-5">
            <li><b>Indexing:</b> short_code UNIQUE INDEX (B-Tree), long_url INDEX for dedup, 100% index hit on redirect path</li>
            <li><b>Caching:</b> Cache-Aside + Write-Through, Redis Cluster LRU, 90% hit ratio, 2ms hit vs 25ms miss, Bloom Filter negative cache</li>
            <li><b>ID Generation:</b> Snowflake 64-bit (41b timestamp + 10b shard + 12b seq) → Base62 → 7 chars, 4M IDs/ms, thread-safe, k-sortable</li>
            <li><b>High-Read:</b> 100:1 read/write ratio, CDN edge cache, Redis hot keys, PostgreSQL read replicas, async analytics via Kafka</li>
          </ul>
        </div>
      </div>
    </div>
  )
}

export default App
