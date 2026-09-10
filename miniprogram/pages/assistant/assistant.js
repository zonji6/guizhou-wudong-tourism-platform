const { CONTRACT_VERSION } = require('../../utils/api')
const { anonymousState, authState, ensureAnonymousMiniSession } = require('../../utils/auth')

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const AUTH_FAILED_CODES = new Set(['AUTH_REQUIRED', 'AUTH_EXPIRED', 'SESSION_REVOKED', 'ANONYMOUS_REQUIRED', 'ANONYMOUS_EXPIRED', 'ANONYMOUS_REVOKED', 'ORIGIN_REJECTED', 'FORBIDDEN', 'AUTH_MODE_MISMATCH', 'CONTRACT_INCOMPATIBLE', 'RATE_LIMITED', 'AUTH_STATE_UNAVAILABLE'])

function exactKeys(value, keys) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const actual = Object.keys(value).sort()
  const expected = [...keys].sort()
  return actual.length === expected.length && actual.every((key, index) => key === expected[index])
}

function validTimestamp(value) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(value) && Number.isFinite(Date.parse(value))
}

function validAuthOk(frame, expectedMode) {
  return exactKeys(frame, ['type', 'authVersion', 'contractVersion', 'connectionId', 'mode', 'authExpiresAt']) && frame.type === 'auth_ok' &&
    frame.authVersion === 'wudong-ws-auth-v1' && frame.contractVersion === CONTRACT_VERSION && frame.mode === expectedMode &&
    UUID_PATTERN.test(frame.connectionId) && validTimestamp(frame.authExpiresAt)
}

function validAuthFailed(frame) {
  const rateLimited = frame?.code === 'RATE_LIMITED'
  const keys = ['type', 'authVersion', 'contractVersion', 'code', 'message', 'retryable', ...(rateLimited ? ['retryAfterSeconds'] : [])]
  return exactKeys(frame, keys) && frame.type === 'auth_failed' && frame.authVersion === 'wudong-ws-auth-v1' &&
    frame.contractVersion === CONTRACT_VERSION && AUTH_FAILED_CODES.has(frame.code) && typeof frame.message === 'string' &&
    frame.retryable === ['AUTH_STATE_UNAVAILABLE', 'RATE_LIMITED'].includes(frame.code) &&
    (!rateLimited || Number.isInteger(frame.retryAfterSeconds) && frame.retryAfterSeconds > 0)
}

Page({
  data: { account: null, status: 'idle', message: '', anonymousNeedsRenew: false },
  onShow() {
    this._closing = false
    this.getTabBar()?.setData({ selected: 2 })
    this.setData({ account: authState().account, status: 'idle', message: '', anonymousNeedsRenew: false })
  },
  onHide() { this.closeSocket('page_hidden') },
  onUnload() { this.closeSocket('page_leave') },
  closeSocket(reason) {
    this._closing = true
    this.socket?.close({ code: 1000, reason })
    this.socket = null
  },
  async connect() {
    if (this.data.status === 'connecting' || this.data.status === 'ready') return
    const state = authState()
    const userMode = Boolean(state.account)
    if (userMode && !state.accessToken) {
      this.setData({ status: 'error', message: '账号访问令牌已失效，请先到“我的”恢复登录；不会降级为匿名会话。' })
      return
    }
    this._closing = false
    this._terminalFrame = false
    this.setData({ status: 'connecting', message: userMode ? '正在建立 USER 向导安全通道…' : '正在恢复或创建匿名预览会话…' })
    let anonymous
    try {
      if (!userMode) {
        anonymous = await ensureAnonymousMiniSession({ renew: this.data.anonymousNeedsRenew })
        this.setData({ anonymousNeedsRenew: false })
      }
    } catch (reason) {
      this.setData({ status: 'error', message: reason?.message || '匿名预览会话暂时无法准备。' })
      return
    }
    const socket = wx.connectSocket({ url: `${getApp().globalData.aiWsBase}${userMode ? '/ws/mini/user' : '/ws/mini/anonymous'}` })
    this.socket = socket
    const requestedMode = userMode ? 'USER' : 'ANONYMOUS_MINI'
    const expectedMode = userMode ? 'USER' : 'ANONYMOUS'
    socket.onOpen(() => {
      const frame = { type: 'auth', authVersion: 'wudong-ws-auth-v1', contractVersion: CONTRACT_VERSION, mode: requestedMode }
      if (userMode) frame.accessToken = state.accessToken
      else frame.anonymousCredential = anonymous.anonymousCredential
      socket.send({ data: JSON.stringify(frame) })
    })
    socket.onMessage(({ data }) => {
      let frame
      try { frame = JSON.parse(data) } catch (_) { frame = null }
      if (validAuthOk(frame, expectedMode)) {
        this.setData({ status: 'ready', message: userMode ? 'USER 向导身份通道已就绪。' : '匿名向导预览通道已就绪。', anonymousNeedsRenew: false })
        return
      }
      if (validAuthFailed(frame)) {
        this._terminalFrame = true
        const needsRenew = !userMode && ['AUTH_REQUIRED', 'ANONYMOUS_REQUIRED', 'ANONYMOUS_EXPIRED', 'ANONYMOUS_REVOKED'].includes(frame.code)
        this.setData({
          status: frame.retryable ? 'error' : 'blocked',
          anonymousNeedsRenew: needsRenew,
          message: `${frame.message}${frame.code === 'RATE_LIMITED' ? `（${frame.retryAfterSeconds} 秒后可重试）` : ''}`
        })
        return
      }
      this._terminalFrame = true
      this.setData({ status: 'blocked', message: '收到尚未冻结的向导业务消息，已停止展示。' })
      socket.close({ code: 4403, reason: 'CONTRACT_INCOMPATIBLE' })
    })
    socket.onError(() => {
      if (this.socket === socket && !this._closing && !this._terminalFrame) this.setData({ status: 'error', message: '本机 AI 服务暂未准备好。' })
    })
    socket.onClose(event => {
      if (this.socket !== socket) return
      this.socket = null
      if (this._closing || this._terminalFrame) return
      const anonymousNeedsRenew = !userMode && event.code === 4401
      this.setData({ status: 'error', anonymousNeedsRenew, message: anonymousNeedsRenew ? '匿名会话已失效；请明确重新开始匿名预览。' : `向导连接已关闭（${event.code}）；如已重新登录或服务恢复，可手动重新连接。` })
    })
  },
  toProfile() { wx.switchTab({ url: '/pages/profile/profile' }) },
  toResources(event) { getApp().globalData.resourceCategory = event.currentTarget.dataset.kind; wx.switchTab({ url: '/pages/resources/resources' }) }
})
