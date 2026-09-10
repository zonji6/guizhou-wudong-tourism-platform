const CONTRACT_VERSION = 'tourism-api-v3-draft-r3'
const ENVELOPE_KEYS = ['code', 'data', 'details', 'message', 'success']
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

class ApiError extends Error {
  constructor(message, statusCode = 0, code = 'REQUEST_FAILED', details = null) {
    super(message)
    this.name = 'ApiError'
    this.statusCode = statusCode
    this.code = code
    this.details = details
  }
}

function strictEnvelope(payload) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return false
  const keys = Object.keys(payload).sort()
  return keys.length === ENVELOPE_KEYS.length && keys.every((key, index) => key === ENVELOPE_KEYS[index])
}

function responseHeader(headers, name) {
  const key = Object.keys(headers || {}).find(value => value.toLowerCase() === name.toLowerCase())
  return key ? headers[key] : undefined
}

function request(path, options = {}) {
  if (typeof path !== 'string' || !path.startsWith('/api/')) return Promise.reject(new ApiError('客户端拒绝了非 v3 API 地址。', 0, 'INVALID_API_PATH'))
  const config = getApp().globalData
  const header = {
    'X-Wudong-Contract': CONTRACT_VERSION,
    ...(options.data !== undefined ? { 'content-type': 'application/json' } : {}),
    ...(options.accessToken ? { Authorization: `Bearer ${options.accessToken}` } : {}),
    ...(options.idempotencyKey ? { 'Idempotency-Key': options.idempotencyKey } : {}),
    ...(options.headers || {})
  }
  return new Promise((resolve, reject) => wx.request({
    url: config.apiBase + path,
    method: options.method || 'GET',
    data: options.data,
    header,
    success(response) {
      if (responseHeader(response.header, 'X-Wudong-Contract') !== CONTRACT_VERSION) {
        reject(new ApiError('服务端契约版本不一致，已停止继续处理。', response.statusCode, 'CONTRACT_INCOMPATIBLE'))
        return
      }
      if (!UUID_PATTERN.test(responseHeader(response.header, 'X-Request-Id') || '') || !/\bno-store\b/i.test(responseHeader(response.header, 'Cache-Control') || '')) {
        reject(new ApiError('服务端缺少 v3 响应追踪或禁缓存边界。', response.statusCode, 'INVALID_RESPONSE'))
        return
      }
      const payload = response.data
      if (!strictEnvelope(payload)) {
        reject(new ApiError('服务返回了不完整的 v3 信封。', response.statusCode, 'INVALID_RESPONSE'))
        return
      }
      if (response.statusCode < 200 || response.statusCode >= 300 || payload.success !== true) {
        reject(new ApiError(payload.message || '本机服务暂不可用', response.statusCode, payload.code || 'REQUEST_FAILED', payload.details))
        return
      }
      if (payload.message !== null || payload.code !== null || payload.details !== null) {
        reject(new ApiError('服务成功信封含有意外字段。', response.statusCode, 'INVALID_RESPONSE'))
        return
      }
      resolve(payload.data)
    },
    fail() { reject(new ApiError('本机服务暂时无法连接。')) }
  }))
}

module.exports = { ApiError, CONTRACT_VERSION, request }
