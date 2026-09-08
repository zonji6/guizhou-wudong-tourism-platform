const STORAGE_KEY = 'wudong:visitor-id:v1'
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

export class VisitorIdentityError extends Error {
  constructor(message) {
    super(message)
    this.name = 'VisitorIdentityError'
  }
}

export function ensureVisitorId() {
  let stored
  try {
    stored = localStorage.getItem(STORAGE_KEY)
  } catch (_) {
    throw new VisitorIdentityError('浏览器未允许保存本机游客身份，暂时无法读取或提交订单。')
  }

  if (stored !== null) {
    if (!UUID_PATTERN.test(stored)) {
      throw new VisitorIdentityError('本机游客身份已损坏，请清理本站存储后重新进入。')
    }
    return stored
  }

  if (typeof globalThis.crypto?.randomUUID !== 'function') {
    throw new VisitorIdentityError('当前浏览器无法安全生成本机游客身份。')
  }
  const created = globalThis.crypto.randomUUID()
  if (!UUID_PATTERN.test(created)) {
    throw new VisitorIdentityError('本机游客身份生成失败。')
  }
  try {
    localStorage.setItem(STORAGE_KEY, created)
    if (localStorage.getItem(STORAGE_KEY) !== created) {
      throw new Error('storage_mismatch')
    }
  } catch (_) {
    throw new VisitorIdentityError('浏览器未能保存本机游客身份，暂时无法读取或提交订单。')
  }
  return created
}
