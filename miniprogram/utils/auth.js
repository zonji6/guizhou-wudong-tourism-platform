const { request } = require('./api')

const STORAGE = {
  clientInstanceId: 'wudong:v3:client-instance-id',
  authGeneration: 'wudong:v3:auth-generation',
  refreshToken: 'wudong:v3:refresh-token',
  anonymousGeneration: 'wudong:v3:anonymous-generation',
  anonymousThreadId: 'wudong:v3:anonymous-thread-id',
  anonymousCredential: 'wudong:v3:anonymous-credential',
  anonymousExpiresAt: 'wudong:v3:anonymous-expires-at'
}

let clientInstancePromise = null

function randomUuid() {
  if (typeof wx.getRandomValues !== 'function') return Promise.reject(new Error('当前环境不能生成安全的随机请求标识。'))
  return new Promise((resolve, reject) => wx.getRandomValues({
    length: 16,
    success(result) {
      const values = new Uint8Array(result.randomValues)
      values[6] = (values[6] & 0x0f) | 0x40
      values[8] = (values[8] & 0x3f) | 0x80
      const hex = Array.from(values, value => value.toString(16).padStart(2, '0')).join('')
      resolve(`${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`)
    },
    fail() { reject(new Error('当前环境不能生成安全的随机请求标识。')) }
  }))
}

async function ensureClientInstanceId(state = authState()) {
  let id = state.clientInstanceId || wx.getStorageSync(STORAGE.clientInstanceId)
  if (!id) {
    if (!clientInstancePromise) {
      const pending = randomUuid().then(value => {
        wx.setStorageSync(STORAGE.clientInstanceId, value)
        return value
      }).finally(() => {
        if (clientInstancePromise === pending) clientInstancePromise = null
      })
      clientInstancePromise = pending
    }
    id = await clientInstancePromise
  }
  state.clientInstanceId = id
  return id
}

function restoreAnonymousState(globalData) {
  globalData.anonymous = {
    anonymousGeneration: Number(wx.getStorageSync(STORAGE.anonymousGeneration)) || 0,
    threadId: wx.getStorageSync(STORAGE.anonymousThreadId) || '',
    anonymousCredential: wx.getStorageSync(STORAGE.anonymousCredential) || '',
    sessionExpiresAt: wx.getStorageSync(STORAGE.anonymousExpiresAt) || ''
  }
  return globalData.anonymous
}

function restoreAuthState(globalData) {
  globalData.auth = {
    clientInstanceId: wx.getStorageSync(STORAGE.clientInstanceId) || '',
    authGeneration: Number(wx.getStorageSync(STORAGE.authGeneration)) || 0,
    refreshToken: wx.getStorageSync(STORAGE.refreshToken) || '',
    accessToken: '',
    account: null,
    pendingRequestId: ''
  }
  restoreAnonymousState(globalData)
  if (!globalData.auth.clientInstanceId) ensureClientInstanceId(globalData.auth).catch(() => {})
  return globalData.auth
}

function authState() {
  const globalData = getApp().globalData
  return globalData.auth || restoreAuthState(globalData)
}

function anonymousState() {
  const globalData = getApp().globalData
  return globalData.anonymous || restoreAnonymousState(globalData)
}

async function ensureAnonymousMiniSession({ renew = false } = {}) {
  const state = anonymousState()
  if (state.anonymousCredential && !renew) return state
  const clientInstanceId = await ensureClientInstanceId()
  const anonymousRequestId = await randomUuid()
  const data = await request('/api/anonymous/mini/sessions', {
    method: 'POST',
    data: { clientInstanceId, anonymousRequestId, expectedAnonymousGeneration: state.anonymousGeneration }
  })
  if (data?.anonymousRequestId !== anonymousRequestId || !Number.isInteger(data?.anonymousGeneration) || data.anonymousGeneration < 1 || typeof data?.threadId !== 'string' || typeof data?.anonymousCredential !== 'string') {
    throw new Error('匿名会话结果与当前请求不匹配。')
  }
  Object.assign(state, {
    anonymousGeneration: data.anonymousGeneration,
    threadId: data.threadId,
    anonymousCredential: data.anonymousCredential,
    sessionExpiresAt: data.sessionExpiresAt
  })
  wx.setStorageSync(STORAGE.anonymousGeneration, state.anonymousGeneration)
  wx.setStorageSync(STORAGE.anonymousThreadId, state.threadId)
  wx.setStorageSync(STORAGE.anonymousCredential, state.anonymousCredential)
  wx.setStorageSync(STORAGE.anonymousExpiresAt, state.sessionExpiresAt)
  return state
}

function persistSession(data) {
  const state = authState()
  state.authGeneration = data.authGeneration
  state.refreshToken = data.refreshToken || state.refreshToken
  state.accessToken = data.accessToken
  state.account = data.account
  wx.setStorageSync(STORAGE.authGeneration, state.authGeneration)
  if (data.refreshToken) wx.setStorageSync(STORAGE.refreshToken, data.refreshToken)
}

function acceptSession(requestId, data) {
  const state = authState()
  if (state.pendingRequestId !== requestId || data?.authRequestId !== requestId) return false
  if (data?.purpose !== 'USER' || data?.account?.role !== 'USER') throw new Error('服务端返回了错误用途的账号会话。')
  state.pendingRequestId = ''
  persistSession(data)
  return true
}

async function register({ username, password, nickname }) {
  return request('/api/auth/mini/register', { method: 'POST', data: { username, password, nickname: nickname?.trim() || null } })
}

async function login({ username, password }) {
  const state = authState()
  const clientInstanceId = await ensureClientInstanceId(state)
  const requestId = await randomUuid()
  state.pendingRequestId = requestId
  const data = await request('/api/auth/mini/login', {
    method: 'POST',
    data: { username, password, clientInstanceId, authRequestId: requestId, expectedAuthGeneration: state.authGeneration }
  })
  acceptSession(requestId, data)
  return data
}

async function refresh() {
  const state = authState()
  if (!state.refreshToken) throw new Error('当前安装实例没有可恢复的登录。')
  const clientInstanceId = await ensureClientInstanceId(state)
  const requestId = await randomUuid()
  state.pendingRequestId = requestId
  const data = await request('/api/auth/mini/refresh', {
    method: 'POST',
    data: { clientInstanceId, authRequestId: requestId, expectedAuthGeneration: state.authGeneration, refreshToken: state.refreshToken }
  })
  acceptSession(requestId, data)
  return data
}

async function logout() {
  const state = authState()
  if (!state.accessToken) throw new Error('当前页面没有可退出的访问会话。')
  const clientInstanceId = await ensureClientInstanceId(state)
  const requestId = await randomUuid()
  state.pendingRequestId = requestId
  const data = await request('/api/auth/mini/logout', {
    method: 'POST',
    accessToken: state.accessToken,
    data: { clientInstanceId, authRequestId: requestId, expectedAuthGeneration: state.authGeneration }
  })
  if (state.pendingRequestId === requestId && data?.authRequestId === requestId) {
    state.pendingRequestId = ''
    state.authGeneration = data.authGeneration
    state.accessToken = ''
    state.refreshToken = ''
    state.account = null
    wx.setStorageSync(STORAGE.authGeneration, state.authGeneration)
    wx.removeStorageSync(STORAGE.refreshToken)
  }
  return data
}

function userOptions(extra = {}) {
  const state = authState()
  if (!state.accessToken) throw new Error('请先登录平台账号。')
  return { ...extra, accessToken: state.accessToken }
}

module.exports = { anonymousState, authState, ensureAnonymousMiniSession, login, logout, randomUuid, refresh, register, restoreAuthState, userOptions }
