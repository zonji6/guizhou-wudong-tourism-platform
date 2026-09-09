import { ref } from 'vue'
import { demoAssistantCard } from '../data/demo'
import {
  adaptAssistantCard,
  adaptAssistantEvent,
  cardFingerprint,
  createClientErrorCard
} from '../protocol/assistantProtocol'
import { aiWs } from '../services/api'

function createThreadId() {
  const randomId = globalThis.crypto?.randomUUID?.()
  return `web-${randomId || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`}`
}

function createPhases() {
  return [
    { id: 'understanding', label: '需求理解', state: 'idle' },
    { id: 'knowledge', label: '知识检索', state: 'idle' },
    { id: 'service', label: '服务协同', state: 'idle' },
    { id: 'itinerary', label: '行程生成', state: 'idle' }
  ]
}

const threadId = createThreadId()
const messages = ref([])
const stages = ref(createPhases())
const activeCard = ref(null)
const previousCard = ref(null)
const cardVersion = ref(0)
const previousCardVersion = ref(0)
const mode = ref('idle')
const isBusy = ref(false)
const activityText = ref('说说你想怎样游乌东')
const requestSeq = ref(0)
let activeSocket = null
let acceptedCardSeq = -1
let acceptedCardFingerprint = ''
let streamProtocol = null
let streamStarted = false
let streamTerminal = false
let receivedCardFingerprint = ''

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0
}

function resetPhases() {
  stages.value = createPhases()
}

function setPhase(id, state) {
  const phase = stages.value.find(item => item.id === id)
  if (phase) phase.state = state
}

function applyPhaseUpdate(event) {
  for (const id of event.complete || []) {
    const phase = stages.value.find(item => item.id === id)
    if (phase && ['idle', 'active'].includes(phase.state)) phase.state = 'done'
  }
  if (event.activate) setPhase(event.activate, 'active')
  activityText.value = {
    understanding: '正在理解你的出行需求',
    knowledge: '正在查找乌东资料',
    service: '正在匹配平台服务',
    itinerary: '正在整理行程建议'
  }[event.activate] || activityText.value
}

function closeAssistantSocket(socket) {
  if (!socket) return
  try {
    const connecting = globalThis.WebSocket?.CONNECTING ?? 0
    const open = globalThis.WebSocket?.OPEN ?? 1
    if (socket.readyState === open) {
      socket.close()
      return
    }
    if (socket.readyState === connecting && typeof socket.addEventListener === 'function') {
      socket.addEventListener('open', () => {
        try { socket.close() } catch (_) {}
      }, { once: true })
    }
  } catch (_) {}
}

function isCurrentRequest(seq, socket) {
  return seq === requestSeq.value && socket === activeSocket
}

function resetStreamBoundary() {
  streamProtocol = null
  streamStarted = false
  streamTerminal = false
  receivedCardFingerprint = ''
}

function acceptViewCard(card, seq) {
  if (seq !== requestSeq.value || !card) return null
  const fingerprint = cardFingerprint(card)
  if (!fingerprint) return null
  if (acceptedCardSeq === seq && acceptedCardFingerprint === fingerprint) return activeCard.value

  if (activeCard.value) {
    previousCard.value = activeCard.value
    previousCardVersion.value = cardVersion.value
  }
  activeCard.value = card
  cardVersion.value += 1
  acceptedCardSeq = seq
  acceptedCardFingerprint = fingerprint
  messages.value.push({ role: 'assistant', content: card.summary || card.title || '向导已返回一条结果。' })
  return card
}

function settleRequest(seq, socket, outcome) {
  if (!isCurrentRequest(seq, socket)) {
    closeAssistantSocket(socket)
    return
  }
  streamTerminal = true
  stages.value = stages.value.map(phase => {
    if (phase.state === 'active') return { ...phase, state: outcome === 'completed' ? 'done' : 'failed' }
    if (phase.state === 'idle') return { ...phase, state: 'skipped' }
    return phase
  })
  isBusy.value = false
  if (outcome === 'failed') mode.value = 'error'
  activityText.value = outcome === 'completed' ? '本轮建议已生成' : '本轮未能完成'
  if (activeSocket === socket) activeSocket = null
  closeAssistantSocket(socket)
}

function failWithCard(seq, socket, title, summary) {
  if (!isCurrentRequest(seq, socket) || !isBusy.value) return
  if (activeCard.value?.type !== 'error' || acceptedCardSeq !== seq) {
    acceptViewCard(createClientErrorCard(title, summary), seq)
  }
  mode.value = 'error'
  activityText.value = summary
  settleRequest(seq, socket, 'failed')
}

function failTransport(seq, socket, summary) {
  if (!isCurrentRequest(seq, socket) || !isBusy.value) {
    closeAssistantSocket(socket)
    return
  }
  failWithCard(seq, socket, '向导暂不可用', summary)
}

function acceptStreamCard(card, seq, socket) {
  const fingerprint = streamProtocol === 'v2' ? card?.streamFingerprint : cardFingerprint(card)
  if (!fingerprint) {
    failWithCard(seq, socket, '向导结果不完整', '本轮结果无法安全展示，请重新描述需求或查看演示结果。')
    return
  }
  if (streamProtocol === 'v2' && receivedCardFingerprint) {
    if (receivedCardFingerprint !== fingerprint) {
      failWithCard(seq, socket, '向导结果发生冲突', '本轮收到了不一致的结果，已停止展示后续内容。')
    }
    return
  }
  if (streamProtocol === 'v2') receivedCardFingerprint = fingerprint
  const accepted = acceptViewCard(card, seq)
  mode.value = accepted?.type === 'error' ? 'error' : accepted?.demoMode ? 'demo' : 'live'
  activityText.value = accepted?.type === 'error' ? '向导返回了一条说明' : '建议已整理，正在结束本轮'
}

function handleAssistantEvent(rawEvent, seq, socket) {
  if (!isCurrentRequest(seq, socket)) {
    closeAssistantSocket(socket)
    return
  }
  if (streamTerminal) {
    closeAssistantSocket(socket)
    return
  }
  const adapted = adaptAssistantEvent(rawEvent, streamProtocol)
  if (!adapted) {
    failWithCard(seq, socket, '向导消息格式不一致', '本轮消息无法安全读取，请重新尝试或查看演示结果。')
    return
  }
  if (streamProtocol === null) {
    streamProtocol = adapted.protocol
    streamStarted = true
    if (adapted.event.threadId !== threadId) {
      failWithCard(seq, socket, '向导会话无法确认', '本轮会话标识不一致，请重新尝试。')
      return
    }
    activityText.value = '乌东向导已收到需求'
    return
  }
  if (!streamStarted) {
    failWithCard(seq, socket, '向导消息顺序不完整', '本轮没有收到会话开始消息，请重新尝试。')
    return
  }
  const event = adapted.event
  if (event.type === 'phase_update') {
    applyPhaseUpdate(event)
    return
  }
  if (event.type === 'card_ready') {
    acceptStreamCard(event.card, seq, socket)
    return
  }
  if (event.type === 'completed') {
    const hasSuccessfulCard = acceptedCardSeq === seq && activeCard.value?.type !== 'error' &&
      (streamProtocol === 'legacy' || Boolean(receivedCardFingerprint))
    if (!hasSuccessfulCard) {
      failWithCard(seq, socket, '向导结果未送达', '本轮没有收到完整结果，请重新尝试或查看演示结果。')
      return
    }
    settleRequest(seq, socket, 'completed')
    return
  }
  if (event.type === 'failed') {
    if (acceptedCardSeq !== seq || (streamProtocol === 'v2' && !receivedCardFingerprint) || activeCard.value?.type !== 'error') {
      failWithCard(seq, socket, '本轮向导未能完成', '本轮处理没有完成，请稍后重试或查看演示结果。')
      return
    }
    settleRequest(seq, socket, 'failed')
  }
}

function send(text) {
  if (typeof text !== 'string') return
  const userText = text.trim()
  if (!userText) return
  const seq = ++requestSeq.value
  const previousSocket = activeSocket
  activeSocket = null
  closeAssistantSocket(previousSocket)
  resetStreamBoundary()
  resetPhases()
  isBusy.value = true
  activityText.value = '正在连接乌东向导'
  messages.value.push({ role: 'user', content: userText })

  let socket = null
  try {
    if (typeof globalThis.WebSocket !== 'function') throw new Error('websocket_unavailable')
    socket = new globalThis.WebSocket(aiWs)
    activeSocket = socket
  } catch (_) {
    activeSocket = null
    failTransport(seq, null, '当前浏览器无法连接向导，可选择查看演示结果。')
    return
  }
  socket.onopen = () => {
    if (!isCurrentRequest(seq, socket)) {
      closeAssistantSocket(socket)
      return
    }
    try {
      socket.send(JSON.stringify({ thread_id: threadId, user_text: userText }))
    } catch (_) {
      failTransport(seq, socket, '发送需求时连接中断，可选择查看演示结果。')
    }
  }
  socket.onmessage = ({ data }) => {
    if (!isCurrentRequest(seq, socket)) {
      closeAssistantSocket(socket)
      return
    }
    try {
      handleAssistantEvent(JSON.parse(data), seq, socket)
    } catch (_) {
      failTransport(seq, socket, '向导返回了无法读取的内容，可选择查看演示结果。')
    }
  }
  socket.onerror = () => failTransport(seq, socket, '连接暂时中断，可选择查看演示结果。')
  socket.onclose = () => {
    if (isCurrentRequest(seq, socket) && isBusy.value) {
      failTransport(seq, socket, '连接在结果完成前关闭，可选择查看演示结果。')
    }
  }
}

function retry() {
  const lastUserMessage = [...messages.value].reverse().find(message => message.role === 'user')
  if (lastUserMessage) send(lastUserMessage.content)
}

function useDemo() {
  if (activeCard.value?.type !== 'error' || activeCard.value.canShowDemo !== true) return
  if (isBusy.value && activeSocket) settleRequest(requestSeq.value, activeSocket, 'failed')
  const seq = ++requestSeq.value
  const socket = activeSocket
  activeSocket = null
  isBusy.value = false
  closeAssistantSocket(socket)
  resetStreamBoundary()
  acceptViewCard(adaptAssistantCard(demoAssistantCard, 'legacy'), seq)
  mode.value = 'demo'
  activityText.value = '正在查看静态演示结果'
}

export function useAssistantSession() {
  return {
    threadId,
    messages,
    stages,
    activeCard,
    previousCard,
    cardVersion,
    previousCardVersion,
    mode,
    isBusy,
    activityText,
    requestSeq,
    send,
    retry,
    useDemo,
    isNonEmptyString
  }
}
