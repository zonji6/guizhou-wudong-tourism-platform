const base = import.meta.env.VITE_JAVA_API_BASE || 'http://127.0.0.1:8080'
const aiWs = import.meta.env.VITE_AI_WS_URL || 'ws://127.0.0.1:8000/ws/assistant'

export { base, aiWs }

export class ApiError extends Error {
  constructor(message, status = 0, code = 'REQUEST_FAILED') {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export async function request(path, options = {}) {
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) }
  let response
  try {
    response = await fetch(`${base}${path}`, { ...options, headers })
  } catch (_) {
    throw new ApiError('服务暂时无法连接，请稍后重试。')
  }
  let payload
  try {
    payload = await response.json()
  } catch (_) {
    throw new ApiError('服务返回了无法识别的结果。', response.status, 'INVALID_RESPONSE')
  }
  if (!response.ok || payload?.success !== true) {
    throw new ApiError(payload?.message || '服务暂不可用', response.status, payload?.code || 'REQUEST_FAILED')
  }
  return payload.data
}
