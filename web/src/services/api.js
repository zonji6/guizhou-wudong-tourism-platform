const base = import.meta.env.VITE_JAVA_API_BASE || 'http://127.0.0.1:8080'
const aiWs = import.meta.env.VITE_AI_WS_URL || 'ws://127.0.0.1:8000/ws/assistant'

export { base, aiWs }

export async function request(path, options = {}) {
  const response = await fetch(`${base}${path}`, { headers: { 'Content-Type': 'application/json' }, ...options })
  const payload = await response.json()
  if (!response.ok || !payload.success) throw new Error(payload.message || '服务暂不可用')
  return payload.data
}
