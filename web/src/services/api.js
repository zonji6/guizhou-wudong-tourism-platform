export const CONTRACT_VERSION = 'tourism-api-v3-draft-r3'
const ENVELOPE_KEYS = ['code', 'data', 'details', 'message', 'success']
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

export class ApiError extends Error {
  constructor(message, status = 0, code = 'REQUEST_FAILED', details = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

function hasStrictEnvelope(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const keys = Object.keys(value).sort()
  return keys.length === ENVELOPE_KEYS.length && keys.every((key, index) => key === ENVELOPE_KEYS[index])
}

export async function request(path, options = {}) {
  if (typeof path !== 'string' || !path.startsWith('/api/')) {
    throw new ApiError('客户端拒绝了非 v3 API 地址。', 0, 'INVALID_API_PATH')
  }
  const { accessToken, csrfToken, idempotencyKey, headers: extraHeaders, ...fetchOptions } = options
  const headers = {
    'X-Wudong-Contract': CONTRACT_VERSION,
    ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    ...(csrfToken ? { 'X-Wudong-CSRF': csrfToken } : {}),
    ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
    ...(extraHeaders || {})
  }
  let response
  try {
    response = await fetch(path, { ...fetchOptions, headers, credentials: 'include', redirect: 'error' })
  } catch (_) {
    throw new ApiError('服务暂时无法连接，请稍后重试。')
  }
  if (response.headers.get('X-Wudong-Contract') !== CONTRACT_VERSION) {
    throw new ApiError('服务端契约版本不一致，已停止继续处理。', response.status, 'CONTRACT_INCOMPATIBLE')
  }
  if (!UUID_PATTERN.test(response.headers.get('X-Request-Id') || '') || !/\bno-store\b/i.test(response.headers.get('Cache-Control') || '')) {
    throw new ApiError('服务端缺少 v3 响应追踪或禁缓存边界。', response.status, 'INVALID_RESPONSE')
  }
  let payload
  try {
    payload = await response.json()
  } catch (_) {
    throw new ApiError('服务返回了无法识别的结果。', response.status, 'INVALID_RESPONSE')
  }
  if (!hasStrictEnvelope(payload)) {
    throw new ApiError('服务返回了不完整的 v3 信封。', response.status, 'INVALID_RESPONSE')
  }
  if (!response.ok || payload.success !== true) {
    throw new ApiError(payload.message || '服务暂不可用', response.status, payload.code || 'REQUEST_FAILED', payload.details)
  }
  if (payload.message !== null || payload.code !== null || payload.details !== null) {
    throw new ApiError('服务成功信封含有意外字段。', response.status, 'INVALID_RESPONSE')
  }
  return payload.data
}

function webSocketUrl(path) {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${location.host}${path}`
}

export const userWebSocketUrl = () => webSocketUrl('/ai/ws/user')
export const anonymousWebSocketUrl = () => webSocketUrl('/ai/ws/anonymous')
