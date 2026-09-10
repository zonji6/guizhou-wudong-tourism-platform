import { reactive } from 'vue'
import { request } from './api'

const PURPOSE = {
  user: { csrf: '/api/auth/web/csrf', auth: '/api/auth/web', role: 'USER' },
  admin: { csrf: '/api/admin/auth/csrf', auth: '/api/admin/auth', role: 'ADMIN' }
}

function emptyChannel() {
  return reactive({ csrfToken: '', authGeneration: 0, accessToken: '', account: null, loginExpiresAt: '', pendingRequestId: '', ready: false })
}

export const authState = { user: emptyChannel(), admin: emptyChannel() }
export const anonymousWebState = reactive({ csrfToken: '', anonymousGeneration: 0, threadId: '', sessionExpiresAt: '', ready: false })
const preparePromises = { user: null, admin: null, anonymous: null }

function uuid() {
  return crypto.randomUUID().toLowerCase()
}

function channelFor(purpose) {
  if (!PURPOSE[purpose]) throw new Error('未知登录用途。')
  return authState[purpose]
}

function clearAccess(channel) {
  channel.accessToken = ''
  channel.account = null
  channel.loginExpiresAt = ''
}

function acceptSession(purpose, requestId, session) {
  const channel = channelFor(purpose)
  if (channel.pendingRequestId !== requestId || session?.authRequestId !== requestId) return false
  if (session?.purpose !== PURPOSE[purpose].role || session?.account?.role !== PURPOSE[purpose].role) throw new Error('服务端返回了错误用途的账号会话。')
  channel.authGeneration = session.authGeneration
  channel.accessToken = session.accessToken
  channel.account = session.account
  channel.loginExpiresAt = session.loginExpiresAt
  channel.pendingRequestId = ''
  return true
}

export function prepareAuth(purpose) {
  const channel = channelFor(purpose)
  if (channel.ready) return Promise.resolve(channel)
  if (preparePromises[purpose]) return preparePromises[purpose]
  const pending = request(PURPOSE[purpose].csrf).then(data => {
    if (typeof data?.csrfToken !== 'string' || !Number.isInteger(data?.authGeneration) || data.authGeneration < 0) throw new Error('登录安全通道初始化结果无效。')
    channel.csrfToken = data.csrfToken
    channel.authGeneration = data.authGeneration
    channel.ready = true
    return channel
  }).finally(() => {
    if (preparePromises[purpose] === pending) preparePromises[purpose] = null
  })
  preparePromises[purpose] = pending
  return pending
}

export function prepareAnonymousWeb() {
  if (anonymousWebState.ready) return Promise.resolve(anonymousWebState)
  if (preparePromises.anonymous) return preparePromises.anonymous
  const pending = request('/api/anonymous/web/csrf').then(data => {
    if (typeof data?.csrfToken !== 'string' || !Number.isInteger(data?.anonymousGeneration) || data.anonymousGeneration < 0) throw new Error('匿名向导安全通道初始化结果无效。')
    anonymousWebState.csrfToken = data.csrfToken
    anonymousWebState.anonymousGeneration = data.anonymousGeneration
    anonymousWebState.ready = true
    return anonymousWebState
  }).finally(() => {
    if (preparePromises.anonymous === pending) preparePromises.anonymous = null
  })
  preparePromises.anonymous = pending
  return pending
}

function acceptAnonymousWeb(requestId, data) {
  if (data?.anonymousRequestId !== requestId || !Number.isInteger(data?.anonymousGeneration) || data.anonymousGeneration < 1) throw new Error('匿名会话结果与当前请求不匹配。')
  anonymousWebState.anonymousGeneration = data.anonymousGeneration
  anonymousWebState.threadId = typeof data.threadId === 'string' ? data.threadId : anonymousWebState.threadId
  anonymousWebState.sessionExpiresAt = typeof data.sessionExpiresAt === 'string' ? data.sessionExpiresAt : anonymousWebState.sessionExpiresAt
  return data
}

export async function ensureAnonymousWebSession() {
  await prepareAnonymousWeb()
  if (anonymousWebState.anonymousGeneration > 0) {
    const requestId = uuid()
    try {
      await request('/api/anonymous/web/cookie-sync', {
        method: 'POST', csrfToken: anonymousWebState.csrfToken,
        body: JSON.stringify({ anonymousRequestId: requestId, expectedAnonymousGeneration: anonymousWebState.anonymousGeneration })
      })
      return anonymousWebState
    } catch (reason) {
      if (!['AUTH_REQUIRED', 'ANONYMOUS_REQUIRED', 'ANONYMOUS_EXPIRED', 'ANONYMOUS_REVOKED', 'RESOURCE_NOT_FOUND'].includes(reason?.code)) throw reason
    }
  }
  const requestId = uuid()
  const data = await request('/api/anonymous/web/sessions', {
    method: 'POST', csrfToken: anonymousWebState.csrfToken,
    body: JSON.stringify({ anonymousRequestId: requestId, expectedAnonymousGeneration: anonymousWebState.anonymousGeneration })
  })
  return acceptAnonymousWeb(requestId, data)
}

export async function registerUser({ username, password, nickname }) {
  const channel = channelFor('user')
  if (!channel.ready) await prepareAuth('user')
  return request('/api/auth/web/register', {
    method: 'POST', csrfToken: channel.csrfToken,
    body: JSON.stringify({ username, password, nickname: nickname?.trim() || null })
  })
}

export async function login(purpose, { username, password }) {
  const channel = channelFor(purpose)
  if (!channel.ready) await prepareAuth(purpose)
  const requestId = uuid()
  channel.pendingRequestId = requestId
  const data = await request(`${PURPOSE[purpose].auth}/login`, {
    method: 'POST', csrfToken: channel.csrfToken,
    body: JSON.stringify({ username, password, authRequestId: requestId, expectedAuthGeneration: channel.authGeneration })
  })
  acceptSession(purpose, requestId, data)
  return data
}

export async function refresh(purpose) {
  const channel = channelFor(purpose)
  if (!channel.ready) await prepareAuth(purpose)
  const requestId = uuid()
  channel.pendingRequestId = requestId
  const data = await request(`${PURPOSE[purpose].auth}/refresh`, {
    method: 'POST', csrfToken: channel.csrfToken,
    body: JSON.stringify({ authRequestId: requestId, expectedAuthGeneration: channel.authGeneration })
  })
  acceptSession(purpose, requestId, data)
  return data
}

export async function logout(purpose) {
  const channel = channelFor(purpose)
  const requestId = uuid()
  channel.pendingRequestId = requestId
  const data = await request(`${PURPOSE[purpose].auth}/logout`, {
    method: 'POST', accessToken: channel.accessToken, csrfToken: channel.csrfToken,
    body: JSON.stringify({ authRequestId: requestId, expectedAuthGeneration: channel.authGeneration })
  })
  if (channel.pendingRequestId === requestId && data?.authRequestId === requestId) {
    channel.authGeneration = data.authGeneration
    channel.pendingRequestId = ''
    clearAccess(channel)
  }
  return data
}

export function userRequestOptions(extra = {}) {
  if (!authState.user.accessToken) throw new Error('请先登录平台账号。')
  return { ...extra, accessToken: authState.user.accessToken }
}

export function adminRequestOptions(extra = {}) {
  if (!authState.admin.accessToken) throw new Error('请先登录运营后台。')
  return { ...extra, accessToken: authState.admin.accessToken }
}

export function newRequestKey() {
  return uuid()
}
