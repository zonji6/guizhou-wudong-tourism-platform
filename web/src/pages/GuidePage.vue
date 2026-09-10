<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { anonymousWebSocketUrl, CONTRACT_VERSION, userWebSocketUrl } from '../services/api'
import { authState, ensureAnonymousWebSession } from '../services/authSession'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const AUTH_FAILED_CODES = new Set(['AUTH_REQUIRED', 'AUTH_EXPIRED', 'SESSION_REVOKED', 'ANONYMOUS_REQUIRED', 'ANONYMOUS_EXPIRED', 'ANONYMOUS_REVOKED', 'ORIGIN_REJECTED', 'FORBIDDEN', 'AUTH_MODE_MISMATCH', 'CONTRACT_INCOMPATIBLE', 'RATE_LIMITED', 'AUTH_STATE_UNAVAILABLE'])
const status = ref('idle')
const message = ref('')
let socket = null
let leaving = false
let terminalFrame = false

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
  return exactKeys(frame, ['type', 'authVersion', 'contractVersion', 'connectionId', 'mode', 'authExpiresAt']) &&
    frame.type === 'auth_ok' && frame.authVersion === 'wudong-ws-auth-v1' && frame.contractVersion === CONTRACT_VERSION &&
    frame.mode === expectedMode && UUID_PATTERN.test(frame.connectionId) && validTimestamp(frame.authExpiresAt)
}

function validAuthFailed(frame) {
  const rateLimited = frame?.code === 'RATE_LIMITED'
  const keys = ['type', 'authVersion', 'contractVersion', 'code', 'message', 'retryable', ...(rateLimited ? ['retryAfterSeconds'] : [])]
  return exactKeys(frame, keys) && frame.type === 'auth_failed' && frame.authVersion === 'wudong-ws-auth-v1' &&
    frame.contractVersion === CONTRACT_VERSION && AUTH_FAILED_CODES.has(frame.code) && typeof frame.message === 'string' &&
    frame.retryable === ['AUTH_STATE_UNAVAILABLE', 'RATE_LIMITED'].includes(frame.code) &&
    (!rateLimited || Number.isInteger(frame.retryAfterSeconds) && frame.retryAfterSeconds > 0)
}

async function connect() {
  if (status.value === 'connecting' || status.value === 'ready') return
  const userMode = Boolean(authState.user.account)
  if (userMode && !authState.user.accessToken) {
    status.value = 'error'
    message.value = '账号访问令牌已失效，请先到“我的”恢复登录；不会降级为匿名会话。'
    return
  }
  const previousSocket = socket
  socket = null
  previousSocket?.close(1000, 'new_connection')
  terminalFrame = false
  status.value = 'connecting'
  message.value = userMode ? '正在建立 USER 向导安全通道…' : '正在恢复或创建匿名预览会话…'
  try {
    if (!userMode) await ensureAnonymousWebSession()
  } catch (reason) {
    status.value = 'error'
    message.value = reason?.message || '匿名预览会话暂时无法准备。'
    return
  }

  const connection = new WebSocket(userMode ? userWebSocketUrl() : anonymousWebSocketUrl())
  socket = connection
  const requestedMode = userMode ? 'USER' : 'ANONYMOUS_WEB'
  const expectedMode = userMode ? 'USER' : 'ANONYMOUS'
  connection.addEventListener('open', () => {
    const frame = { type: 'auth', authVersion: 'wudong-ws-auth-v1', contractVersion: CONTRACT_VERSION, mode: requestedMode }
    if (userMode) frame.accessToken = authState.user.accessToken
    connection.send(JSON.stringify(frame))
  })
  connection.addEventListener('message', event => {
    let frame
    try { frame = JSON.parse(event.data) } catch (_) { frame = null }
    if (validAuthOk(frame, expectedMode)) {
      status.value = 'ready'
      message.value = userMode ? 'USER 向导身份通道已就绪。' : '匿名向导预览通道已就绪。'
      return
    }
    if (validAuthFailed(frame)) {
      terminalFrame = true
      status.value = frame.retryable ? 'error' : 'blocked'
      message.value = `${frame.message}${frame.code === 'RATE_LIMITED' ? `（${frame.retryAfterSeconds} 秒后可重试）` : ''}`
      return
    }
    terminalFrame = true
    status.value = 'blocked'
    message.value = '收到未冻结的向导消息，已停止展示。'
    connection.close(4403, 'CONTRACT_INCOMPATIBLE')
  })
  connection.addEventListener('error', () => {
    if (connection !== socket || leaving || terminalFrame) return
    status.value = 'error'
    message.value = '本机 AI 服务暂未准备好。'
  })
  connection.addEventListener('close', event => {
    if (connection !== socket) return
    socket = null
    if (leaving || terminalFrame) return
    status.value = 'error'
    message.value = `向导连接已关闭（${event.code}）；如已重新登录或服务恢复，可手动重新连接。`
  })
}

onBeforeUnmount(() => {
  leaving = true
  socket?.close(1000, 'page_leave')
  socket = null
})
</script>

<template>
  <main class="page v3-guide-page">
    <header class="page-intro"><p class="eyebrow">乌东向导 · AI 入口</p><h1>先确认身份，再把心愿交给山茶</h1><p>本页只启用已经冻结的 WebSocket 身份首帧；在独立的 assistant-card-v3 契约交付前，不猜测卡片、候选或保存事件。</p></header>
    <section class="v3-guide-card"><span class="v3-guide-leaf">叶</span><div><h2>{{ authState.user.account ? `${authState.user.account.nickname}，准备连接账号向导` : '免登录体验匿名向导' }}</h2><p>{{ message || (authState.user.account ? '点击后连接同源 /ai/ws/user；USER 身份失效时不会降级匿名。' : '匿名预览不能读取账号资料；需要保存成果时再登录并再次确认。') }}</p><button class="primary" :disabled="status === 'connecting' || status === 'ready'" @click="connect">{{ status === 'ready' ? '身份通道已就绪' : status === 'connecting' ? '连接中…' : authState.user.account ? '连接账号向导' : '开始匿名预览' }}</button><a v-if="!authState.user.account" class="v3-text-link" href="#/my">已有账号？去“我的”登录</a></div></section>
    <section class="v3-guide-links"><a href="#/explore/product"><span>01</span><b>先看看商品</b><small>公开目录无需登录</small></a><a href="#/explore/food"><span>02</span><b>同店选餐</b><small>核价前需要 USER</small></a><a href="#/explore/travel"><span>03</span><b>查看示意路线</b><small>不冒充真实导航</small></a></section>
  </main>
</template>
