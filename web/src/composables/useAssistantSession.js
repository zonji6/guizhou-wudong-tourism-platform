import { ref } from 'vue'
import { demoAssistantCard } from '../data/demo'
import { aiWs } from '../services/api'

const CARD_TYPES = new Set([
  'itinerary',
  'service_recommendation',
  'knowledge_answer',
  'clarifying_question',
  'pending_booking',
  'error'
])

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

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function isStructurallyValidCard(raw) {
  if (!isPlainObject(raw) || !CARD_TYPES.has(raw.type)) return false
  if (!isNonEmptyString(raw.title) || typeof raw.summary !== 'string') return false
  if (!isPlainObject(raw.data) || !Array.isArray(raw.sources)) return false
  if (!raw.sources.every(source => isPlainObject(source) && isNonEmptyString(source.title))) return false

  if (raw.type === 'itinerary') {
    return Array.isArray(raw.data.days) && raw.data.days.every(day =>
      isPlainObject(day) &&
      Array.isArray(day.items) &&
      day.items.every(isPlainObject)
    )
  }
  if (raw.type === 'service_recommendation') {
    return Array.isArray(raw.data.services) && raw.data.services.every(isPlainObject)
  }
  if (raw.type === 'pending_booking') return isPlainObject(raw.data.booking)
  return true
}

function normalizeCard(raw) {
  const sources = raw.sources.map(source => ({ title: source.title.trim() }))

  return {
    type: raw.type,
    title: typeof raw.title === 'string' ? raw.title : '',
    summary: typeof raw.summary === 'string' ? raw.summary : '',
    data: raw.data && typeof raw.data === 'object' && !Array.isArray(raw.data) ? raw.data : {},
    sources
  }
}

function errorCard(title, summary) {
  return {
    type: 'error',
    title,
    summary,
    data: { demoAvailable: true },
    sources: []
  }
}

function cardFingerprint(card) {
  return JSON.stringify(card)
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

function finishPhaseIfActive(id) {
  const phase = stages.value.find(item => item.id === id)
  if (phase?.state === 'active') phase.state = 'done'
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

function acceptCard(raw, seq) {
  if (seq !== requestSeq.value) return null
  if (!isStructurallyValidCard(raw)) return null

  const card = normalizeCard(raw)
  const fingerprint = cardFingerprint(card)
  if (acceptedCardSeq === seq && acceptedCardFingerprint === fingerprint) return activeCard.value

  if (activeCard.value) {
    previousCard.value = activeCard.value
    previousCardVersion.value = cardVersion.value
  }
  activeCard.value = card
  cardVersion.value += 1
  acceptedCardSeq = seq
  acceptedCardFingerprint = fingerprint
  messages.value.push({
    role: 'assistant',
    content: card.summary || card.title || '向导已返回一条结果。'
  })
  return card
}

function settleRequest(seq, socket, outcome) {
  if (!isCurrentRequest(seq, socket)) {
    closeAssistantSocket(socket)
    return
  }

  stages.value = stages.value.map(phase => {
    if (phase.state === 'active') {
      return { ...phase, state: outcome === 'completed' ? 'done' : 'failed' }
    }
    if (phase.state === 'idle') return { ...phase, state: 'skipped' }
    return phase
  })
  isBusy.value = false
  if (outcome === 'failed') mode.value = 'error'
  activityText.value = outcome === 'completed' ? '本轮建议已生成' : '本轮未能完成'

  if (activeSocket === socket) activeSocket = null
  closeAssistantSocket(socket)
}

function failTransport(seq, socket, summary) {
  if (!isCurrentRequest(seq, socket) || !isBusy.value) {
    closeAssistantSocket(socket)
    return
  }

  if (acceptedCardSeq === seq && activeCard.value?.type === 'error') {
    settleRequest(seq, socket, 'failed')
    return
  }

  acceptCard(errorCard('向导暂不可用', summary), seq)
  settleRequest(seq, socket, 'failed')
}

function failWithCard(seq, socket, title, summary) {
  if (!isCurrentRequest(seq, socket) || !isBusy.value) return

  if (acceptedCardSeq !== seq || activeCard.value?.type !== 'error') {
    acceptCard(errorCard(title, summary), seq)
  }
  mode.value = 'error'
  activityText.value = summary
  settleRequest(seq, socket, 'failed')
}

function handleAssistantEvent(event, seq, socket) {
  if (!isCurrentRequest(seq, socket)) {
    closeAssistantSocket(socket)
    return
  }
  if (!event || typeof event !== 'object') return

  if (event.type === 'task_started') {
    activityText.value = '乌东向导已收到需求'
    return
  }

  if (event.type === 'node_started') {
    if (event.node === 'route' || event.node === 'intent') {
      setPhase('understanding', 'active')
      activityText.value = '正在理解你的出行需求'
    }
    if (event.node === 'retrieve' || event.node === 'service_search' || event.node === 'itinerary') {
      finishPhaseIfActive('understanding')
    }
    if (event.node === 'retrieve') {
      setPhase('knowledge', 'active')
      activityText.value = '正在查找乌东资料'
    }
    if (event.node === 'service_search') {
      setPhase('service', 'active')
      activityText.value = '正在匹配平台服务'
    }
    if (event.node === 'itinerary') {
      setPhase('itinerary', 'active')
      activityText.value = '正在整理行程建议'
    }
    return
  }

  if (event.type === 'tool_finished') {
    if (event.tool === 'knowledge_retrieval') setPhase('knowledge', 'done')
    if (event.tool === 'service_search') setPhase('service', 'done')
    return
  }

  if (event.type === 'card_ready') {
    if (!isStructurallyValidCard(event.card)) {
      failWithCard(
        seq,
        socket,
        '向导结果不完整',
        '本轮结果无法安全展示，请重新描述需求或查看演示结果。'
      )
      return
    }
    const acceptedCard = acceptCard(event.card, seq)
    mode.value = acceptedCard?.type === 'error'
      ? 'error'
      : acceptedCard?.data?.demoMode === true ? 'demo' : 'live'
    activityText.value = acceptedCard?.type === 'error' ? '向导返回了一条说明' : '建议已整理，正在结束本轮'
    return
  }

  if (event.type === 'completed') {
    const hasSuccessfulCard = acceptedCardSeq === seq &&
      activeCard.value?.type !== 'error' &&
      isStructurallyValidCard(activeCard.value)
    if (!hasSuccessfulCard) {
      failWithCard(
        seq,
        socket,
        '向导结果未送达',
        '本轮没有收到完整结果，请重新尝试或查看演示结果。'
      )
      return
    }
    settleRequest(seq, socket, 'completed')
  }
  if (event.type === 'failed') {
    failWithCard(
      seq,
      socket,
      '本轮向导未能完成',
      '本轮处理没有完成，请稍后重试或查看演示结果。'
    )
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
  socket.onerror = () => {
    failTransport(seq, socket, '连接暂时中断，可选择查看演示结果。')
  }
  socket.onclose = () => {
    if (isCurrentRequest(seq, socket) && isBusy.value) {
      failTransport(seq, socket, '连接在结果完成前关闭，可选择查看演示结果。')
    }
  }
}

function useDemo() {
  if (activeCard.value?.type !== 'error' || activeCard.value.data?.demoAvailable !== true) return

  if (isBusy.value && activeSocket) {
    settleRequest(requestSeq.value, activeSocket, 'failed')
  }
  const seq = ++requestSeq.value
  const socket = activeSocket
  activeSocket = null
  isBusy.value = false
  closeAssistantSocket(socket)
  acceptCard(demoAssistantCard, seq)
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
    useDemo,
    isNonEmptyString
  }
}
