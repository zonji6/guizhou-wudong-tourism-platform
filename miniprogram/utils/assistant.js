const CARD_TYPES = [
  'itinerary',
  'service_recommendation',
  'knowledge_answer',
  'clarifying_question',
  'pending_booking',
  'error'
]

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

function normalizeCard(card) {
  if (!card || !CARD_TYPES.includes(card.type)) {
    return {
      type: 'error',
      title: '内容暂不可展示',
      summary: '向导返回了不支持的内容。',
      data: {},
      sources: []
    }
  }
  const data = card.data && typeof card.data === 'object' && !Array.isArray(card.data)
    ? card.data
    : {}
  return {
    type: card.type,
    title: typeof card.title === 'string' ? card.title : '',
    summary: typeof card.summary === 'string' ? card.summary : '',
    data,
    sources: Array.isArray(card.sources) ? card.sources : []
  }
}

function validServiceId(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function validBookingId(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function prepareCard(rawCard) {
  const card = normalizeCard(rawCard)
  const sources = card.sources
    .map(source => ({ ...source, title: typeof source?.title === 'string' ? source.title.trim() : '' }))
    .filter(source => source.title)
  const data = { ...card.data }
  let joinableCount = 0

  if (card.type === 'itinerary') {
    data.days = (Array.isArray(card.data.days) ? card.data.days : []).map((day, dayIndex) => {
      const items = (Array.isArray(day?.items) ? day.items : []).map(item => {
        const actionServiceId = validServiceId(item?.serviceId)
        if (actionServiceId) joinableCount += 1
        return {
          ...item,
          title: typeof item?.title === 'string' ? item.title : '',
          summary: typeof item?.summary === 'string' ? item.summary : '',
          time: typeof item?.time === 'string' ? item.time : '',
          actionServiceId,
          isDemo: item?.demoData === true
        }
      })
      return {
        ...day,
        dayLabel: day?.day ? `第${day.day}天` : `第${dayIndex + 1}天`,
        theme: typeof day?.theme === 'string' ? day.theme : '',
        items
      }
    })
  }

  if (card.type === 'service_recommendation') {
    data.services = (Array.isArray(card.data.services) ? card.data.services : []).map(service => ({
      ...service,
      title: typeof service?.title === 'string' ? service.title : '',
      summary: typeof service?.summary === 'string' ? service.summary : '',
      actionServiceId: validServiceId(service?.serviceId),
      hasPrice: service?.price !== undefined && service?.price !== null && service?.price !== '',
      isDemo: service?.demoData === true
    }))
  }

  if (card.type === 'pending_booking') {
    const booking = card.data.booking && typeof card.data.booking === 'object'
      ? card.data.booking
      : null
    data.booking = booking
      ? {
          ...booking,
          actionServiceId: validServiceId(booking.serviceId),
          actionBookingId: validBookingId(booking.id),
          isDemo: booking.demoData === true
        }
      : null
  }

  if (card.type === 'clarifying_question') {
    data.actionServiceId = validServiceId(card.data.serviceId)
  }

  return {
    ...card,
    data,
    sources,
    sourceCount: sources.length,
    joinableCount,
    canShowDemo: card.type === 'error' && card.data.demoAvailable === true
  }
}

function cardFingerprint(rawCard) {
  return JSON.stringify(normalizeCard(rawCard))
}

function applyEvent(phases, event) {
  const next = restorePhases(phases)
  const setState = (key, state) => {
    const index = next.findIndex(item => item.key === key)
    if (index >= 0) next[index] = withStateLabel({ ...next[index], state })
  }
  const finishUnderstanding = () => {
    const phase = next.find(item => item.key === 'understanding')
    if (phase?.state === 'active') setState('understanding', 'done')
  }

  if (event?.type === 'node_started') {
    if (event.node === 'route' || event.node === 'intent') setState('understanding', 'active')
    if (event.node === 'retrieve' || event.node === 'service_search' || event.node === 'itinerary') {
      finishUnderstanding()
    }
    if (event.node === 'retrieve') setState('knowledge', 'active')
    if (event.node === 'service_search') setState('service', 'active')
    if (event.node === 'itinerary') setState('itinerary', 'active')
  }
  if (event?.type === 'tool_finished' && event.tool === 'knowledge_retrieval') {
    setState('knowledge', 'done')
  }
  if (event?.type === 'tool_finished' && event.tool === 'service_search') {
    setState('service', 'done')
  }
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

function ensureAssistantSession(globalData) {
  const existing = globalData.assistantSession && typeof globalData.assistantSession === 'object'
    ? globalData.assistantSession
    : {}
  if (typeof existing.threadId !== 'string' || !existing.threadId.trim()) {
    existing.threadId = `mini-${Date.now()}`
  }
  existing.messages = Array.isArray(existing.messages) ? existing.messages : []
  existing.currentCard = existing.currentCard ? prepareCard(existing.currentCard) : null
  existing.previousCard = existing.previousCard ? prepareCard(existing.previousCard) : null
  existing.mode = ['live', 'demo', 'error'].includes(existing.mode) ? existing.mode : 'live'
  existing.phases = restorePhases(existing.phases)
  existing.requestSeq = Number.isFinite(existing.requestSeq) ? existing.requestSeq : 0
  existing.isBusy = existing.isBusy === true
  existing.activeSocket = existing.activeSocket || null
  existing.acceptedCardSeq = Number.isFinite(existing.acceptedCardSeq) ? existing.acceptedCardSeq : -1
  existing.acceptedCardFingerprint = typeof existing.acceptedCardFingerprint === 'string'
    ? existing.acceptedCardFingerprint
    : ''
  globalData.assistantSession = existing
  return existing
}

module.exports = {
  CARD_TYPES,
  createPhases,
  restorePhases,
  normalizeCard,
  prepareCard,
  cardFingerprint,
  applyEvent,
  settlePhases,
  ensureAssistantSession,
  validServiceId,
  validBookingId
}
