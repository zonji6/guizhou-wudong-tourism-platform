const {
  adaptAssistantCard,
  adaptAssistantEvent,
  createClientErrorCard,
  isAssistantViewCard,
  cardFingerprint
} = require('./assistantProtocol')

const PHASE_LABELS = {
  idle: '待开始',
  active: '进行中',
  done: '已完成',
  skipped: '已跳过',
  failed: '未完成'
}

const PHASE_DEFINITIONS = [
  { key: 'understanding', label: '需求理解' },
  { key: 'knowledge', label: '知识检索' },
  { key: 'service', label: '服务协同' },
  { key: 'itinerary', label: '行程生成' }
]

function withStateLabel(phase) {
  const state = PHASE_LABELS[phase?.state] ? phase.state : 'idle'
  return { ...phase, state, stateLabel: PHASE_LABELS[state] }
}

function createPhases() {
  return PHASE_DEFINITIONS.map(item => withStateLabel({ ...item, state: 'idle' }))
}

function restorePhases(phases) {
  if (!Array.isArray(phases)) return createPhases()
  return PHASE_DEFINITIONS.map(definition => {
    const stored = phases.find(item => item?.key === definition.key)
    return withStateLabel({ ...definition, state: stored?.state || 'idle' })
  })
}

function applyPhaseUpdate(phases, event) {
  const next = restorePhases(phases)
  const setState = (key, state) => {
    const index = next.findIndex(item => item.key === key)
    if (index >= 0) next[index] = withStateLabel({ ...next[index], state })
  }
  for (const key of event?.complete || []) setState(key, 'done')
  if (event?.activate) setState(event.activate, 'active')
  return next
}

function settlePhases(phases, failed) {
  return restorePhases(phases).map(item => withStateLabel({
    ...item,
    state: item.state === 'active'
      ? (failed ? 'failed' : 'done')
      : (item.state === 'idle' ? 'skipped' : item.state)
  }))
}

function restoreCard(value) {
  if (!value) return null
  if (isAssistantViewCard(value)) return value
  return adaptAssistantCard(value, 'legacy')
}

function ensureAssistantSession(globalData) {
  const existing = globalData.assistantSession && typeof globalData.assistantSession === 'object'
    ? globalData.assistantSession
    : {}
  if (typeof existing.threadId !== 'string' || !existing.threadId.trim()) {
    existing.threadId = `mini-${Date.now()}`
  }
  existing.messages = Array.isArray(existing.messages) ? existing.messages : []
  existing.currentCard = restoreCard(existing.currentCard)
  existing.previousCard = restoreCard(existing.previousCard)
  existing.mode = ['live', 'demo', 'error'].includes(existing.mode) ? existing.mode : 'live'
  existing.phases = restorePhases(existing.phases)
  existing.requestSeq = Number.isFinite(existing.requestSeq) ? existing.requestSeq : 0
  existing.isBusy = existing.isBusy === true
  existing.activeSocket = existing.activeSocket || null
  existing.acceptedCardSeq = Number.isFinite(existing.acceptedCardSeq) ? existing.acceptedCardSeq : -1
  existing.acceptedCardFingerprint = typeof existing.acceptedCardFingerprint === 'string'
    ? existing.acceptedCardFingerprint
    : ''
  existing.streamProtocol = ['legacy', 'v2'].includes(existing.streamProtocol) ? existing.streamProtocol : null
  existing.streamStarted = existing.streamStarted === true
  existing.streamTerminal = existing.streamTerminal === true
  existing.receivedCardFingerprint = typeof existing.receivedCardFingerprint === 'string'
    ? existing.receivedCardFingerprint
    : ''
  globalData.assistantSession = existing
  return existing
}

function validServiceId(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function validBookingId(value) {
  return typeof value === 'string' ? value.trim() : ''
}

module.exports = {
  createPhases,
  restorePhases,
  applyPhaseUpdate,
  settlePhases,
  ensureAssistantSession,
  validServiceId,
  validBookingId,
  adaptAssistantCard,
  adaptAssistantEvent,
  createClientErrorCard,
  cardFingerprint
}
