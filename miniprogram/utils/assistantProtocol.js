const CARD_TYPES = ['itinerary', 'service_recommendation', 'knowledge_answer', 'clarifying_question', 'pending_booking', 'error']
const TARGET_TYPES = ['PRODUCT', 'FOOD', 'STAY', 'PLACE', 'ROUTE_GUIDE']
const SOURCE_TYPES = ['KNOWLEDGE', 'CATALOG', 'ROUTE_GUIDE']
const ACTION_TYPES = ['OPEN_DETAIL', 'ADD_TO_ITINERARY', 'OPEN_ORDER_CONFIRMATION', 'OPEN_MAP', 'OPEN_ROUTE_GUIDE', 'RETRY']
const CONDITION_FIELDS = ['travelDate', 'visitAt', 'checkInDate', 'peopleCount', 'preferences', 'target']
const RETRIEVAL_MODES = ['VECTOR', 'KEYWORD_DEMO', 'NONE']
const KNOWLEDGE_MODES = ['VECTOR', 'KEYWORD_DEMO']
const PROGRESS_STAGES = ['UNDERSTANDING', 'RETRIEVING', 'PLANNING', 'PREPARING_RESULT']
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const VIEW_MODEL_VERSION = 'assistant-ui-v1'

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function owns(value, key) {
  return Object.prototype.hasOwnProperty.call(value, key)
}

function exact(value, keys) {
  if (!isObject(value)) return false
  const actual = Object.keys(value)
  return actual.length === keys.length && keys.every(key => owns(value, key))
}

function allowed(value, keys) {
  return isObject(value) && Object.keys(value).every(key => keys.includes(key))
}

function text(value, min = 0, max = Number.MAX_SAFE_INTEGER) {
  return typeof value === 'string' && value.trim().length >= min && value.length <= max
}

function uuid(value) {
  return typeof value === 'string' && UUID_PATTERN.test(value)
}

function publicTargetId(value) {
  return text(value, 1, 120)
}

function positiveInteger(value) {
  return Number.isInteger(value) && value > 0
}

function nonNegativeInteger(value) {
  return Number.isInteger(value) && value >= 0
}

function finiteNumber(value, minimum = -Infinity) {
  return typeof value === 'number' && Number.isFinite(value) && value >= minimum
}

function dateValue(value) {
  const match = typeof value === 'string' && /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return false
  const [year, month, day] = match.slice(1).map(Number)
  if (year < 1 || month < 1 || month > 12 || day < 1) return false
  return day <= new Date(Date.UTC(year, month, 0)).getUTCDate()
}

function timestamp(value) {
  const match = typeof value === 'string' && value.length <= 40 &&
    /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,6})?)?(Z|[+-]\d{2}:\d{2})?$/.exec(value)
  if (!match || !dateValue(match[1])) return false
  const [, , hour, minute, second = '0', zone] = match
  if (Number(hour) > 23 || Number(minute) > 59 || Number(second) > 59) return false
  if (!zone || zone === 'Z') return true
  const [, , offsetHour, offsetMinute] = /^([+-])(\d{2}):(\d{2})$/.exec(zone) || []
  return Number(offsetHour) <= 23 && Number(offsetMinute) <= 59
}

function nullable(value, validator) {
  return value === null || validator(value)
}

function list(value, max, validator, min = 0) {
  return Array.isArray(value) && value.length >= min && value.length <= max && value.every(validator)
}

function clean(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function target(value) {
  if (!exact(value, ['targetType', 'targetId', 'targetName']) || !TARGET_TYPES.includes(value.targetType) ||
    !publicTargetId(value.targetId) || !text(value.targetName, 1, 180)) return null
  return { targetType: value.targetType, targetId: value.targetId, targetName: clean(value.targetName) }
}

function conditions(value) {
  if (!exact(value, ['travelDate', 'visitAt', 'checkInDate', 'peopleCount', 'preferences', 'selectedTargets'])) return null
  if (!nullable(value.travelDate, dateValue) || !nullable(value.visitAt, timestamp) ||
    !nullable(value.checkInDate, dateValue) || !nullable(value.peopleCount, positiveInteger) ||
    !list(value.preferences, 12, item => text(item, 0)) ||
    !list(value.selectedTargets, 12, item => target(item) !== null)) return null
  return {
    travelDate: value.travelDate,
    visitAt: value.visitAt,
    checkInDate: value.checkInDate,
    peopleCount: value.peopleCount,
    preferences: value.preferences.map(clean),
    selectedTargets: value.selectedTargets.map(target)
  }
}

function source(value) {
  if (!exact(value, ['title', 'documentId', 'sourceType', 'demoData']) || !text(value.title, 1, 180) ||
    !nullable(value.documentId, item => text(item, 0, 120)) || !SOURCE_TYPES.includes(value.sourceType) ||
    typeof value.demoData !== 'boolean') return null
  return {
    title: clean(value.title),
    documentId: value.documentId === null ? null : clean(value.documentId),
    sourceType: value.sourceType,
    demoData: value.demoData
  }
}

function action(value) {
  if (!exact(value, ['action', 'label', 'targetType', 'targetId']) || !ACTION_TYPES.includes(value.action) || !text(value.label, 1, 40)) return null
  const targetless = value.targetType === null && value.targetId === null
  const targeted = TARGET_TYPES.includes(value.targetType) && publicTargetId(value.targetId)
  if (value.action === 'RETRY' ? !targetless : !targeted) return null
  if (value.action === 'OPEN_ORDER_CONFIRMATION' && !['FOOD', 'STAY'].includes(value.targetType)) return null
  if (value.action === 'OPEN_MAP' && value.targetType !== 'PLACE') return null
  if (value.action === 'OPEN_ROUTE_GUIDE' && value.targetType !== 'ROUTE_GUIDE') return null
  return { action: value.action, label: clean(value.label), targetType: value.targetType, targetId: value.targetId }
}

function toolSummary(value) {
  return exact(value, ['nodeName', 'toolCategory', 'sourceTitles', 'durationMs', 'finalStatus']) &&
    text(value.nodeName, 1, 80) && nullable(value.toolCategory, item => text(item, 0, 80)) &&
    list(value.sourceTitles, 20, item => text(item, 0)) && nonNegativeInteger(value.durationMs) &&
    text(value.finalStatus, 1, 32)
}

function stop(value) {
  if (!exact(value, ['timeText', 'title', 'summary', 'targetType', 'targetId', 'demoData']) ||
    !text(value.timeText, 0, 80) || !text(value.title, 1, 180) || !text(value.summary, 0, 500)) return null
  const targetless = value.targetType === null && value.targetId === null
  const targeted = TARGET_TYPES.includes(value.targetType) && publicTargetId(value.targetId)
  if ((!targetless && !targeted) || typeof value.demoData !== 'boolean') return null
  return {
    time: clean(value.timeText),
    title: clean(value.title),
    summary: clean(value.summary),
    targetType: value.targetType,
    targetId: value.targetId,
    demoData: value.demoData,
    actions: []
  }
}

function day(value) {
  if (!exact(value, ['day', 'theme', 'items']) || !positiveInteger(value.day) || !text(value.theme, 1, 120) ||
    !list(value.items, 12, item => stop(item) !== null)) return null
  return { day: value.day, dayLabel: `第${value.day}天`, theme: clean(value.theme), items: value.items.map(stop) }
}

function recommendation(value) {
  if (!exact(value, ['targetType', 'targetId', 'targetName', 'summary', 'price', 'tags', 'demoData']) ||
    !TARGET_TYPES.includes(value.targetType) || !publicTargetId(value.targetId) || !text(value.targetName, 1, 180) ||
    !text(value.summary, 0, 500) || !nullable(value.price, item => finiteNumber(item, 0)) ||
    !list(value.tags, 20, item => text(item, 0)) || typeof value.demoData !== 'boolean') return null
  return {
    title: clean(value.targetName),
    summary: clean(value.summary),
    price: value.price,
    hasPrice: value.price !== null,
    tags: value.tags.map(clean),
    targetType: value.targetType,
    targetId: value.targetId,
    demoData: value.demoData,
    actions: []
  }
}

function proposal(value) {
  if (!exact(value, ['proposalType', 'targetType', 'targetId', 'targetName', 'visitAt', 'checkInDate', 'peopleCount', 'note', 'sourceThreadId', 'requiresVisitorConfirmation', 'demoData'])) return null
  if (!['FOOD_ORDER', 'STAY_BOOKING'].includes(value.proposalType) || !['FOOD', 'STAY'].includes(value.targetType) ||
    !publicTargetId(value.targetId) || !text(value.targetName, 1, 180) || !nullable(value.visitAt, timestamp) ||
    !nullable(value.checkInDate, dateValue) || !positiveInteger(value.peopleCount) ||
    !nullable(value.note, item => text(item, 0, 300)) || !text(value.sourceThreadId, 1, 100) ||
    value.requiresVisitorConfirmation !== true || typeof value.demoData !== 'boolean') return null
  const food = value.proposalType === 'FOOD_ORDER'
  if (food && (value.targetType !== 'FOOD' || value.visitAt === null || value.checkInDate !== null)) return null
  if (!food && (value.targetType !== 'STAY' || value.checkInDate === null || value.visitAt !== null)) return null
  return {
    proposalType: value.proposalType,
    targetType: value.targetType,
    targetId: value.targetId,
    targetName: clean(value.targetName),
    visitAt: value.visitAt,
    checkInDate: value.checkInDate,
    peopleCount: value.peopleCount,
    note: value.note === null ? null : clean(value.note),
    sourceThreadId: clean(value.sourceThreadId),
    requiresVisitorConfirmation: true,
    demoData: value.demoData,
    status: '',
    actions: []
  }
}

function view(raw, protocol, sources, demoMode) {
  return {
    viewModelVersion: VIEW_MODEL_VERSION,
    protocol,
    type: raw.type,
    title: clean(raw.title),
    summary: raw.summary,
    sources,
    sourceCount: sources.length,
    actions: [],
    data: {},
    demoMode,
    keywordDemo: false,
    canShowDemo: false,
    canAddLegacyItinerary: false
  }
}

function v2Card(raw) {
  if (!exact(raw, ['cardVersion', 'type', 'title', 'summary', 'sources', 'actions', 'data']) || raw.cardVersion !== '2.0' ||
    !CARD_TYPES.includes(raw.type) || !text(raw.title, 1, 180) || !text(raw.summary, 0, 1200) || !isObject(raw.data) ||
    !list(raw.sources, 20, item => source(item) !== null) || !list(raw.actions, 10, item => action(item) !== null)) return null
  const sources = raw.sources.map(source)
  const actions = raw.actions.map(action)
  const card = view(raw, 'v2', sources, sources.some(item => item.demoData))
  card.actions = actions.filter(item => item.action === 'RETRY')

  if (raw.type === 'itinerary') {
    if (!exact(raw.data, ['days', 'notice', 'retrievalMode']) || !list(raw.data.days, 7, item => day(item) !== null, 1) ||
      !text(raw.data.notice, 1, 300) || !RETRIEVAL_MODES.includes(raw.data.retrievalMode)) return null
    const days = raw.data.days.map(day)
    card.data = { days, notice: clean(raw.data.notice), retrievalMode: raw.data.retrievalMode }
    card.keywordDemo = raw.data.retrievalMode === 'KEYWORD_DEMO'
    card.demoMode = card.demoMode || days.some(item => item.items.some(entry => entry.demoData))
  } else if (raw.type === 'service_recommendation') {
    if (!exact(raw.data, ['items', 'notice']) || !list(raw.data.items, 12, item => recommendation(item) !== null) || !text(raw.data.notice, 1, 300)) return null
    const items = raw.data.items.map(recommendation)
    card.data = { items, notice: clean(raw.data.notice) }
    card.demoMode = card.demoMode || items.some(item => item.demoData)
  } else if (raw.type === 'knowledge_answer') {
    if (!exact(raw.data, ['answer', 'retrievalMode']) || !text(raw.data.answer, 1, 2400) ||
      !KNOWLEDGE_MODES.includes(raw.data.retrievalMode) || sources.length < 1) return null
    card.data = { answer: clean(raw.data.answer), retrievalMode: raw.data.retrievalMode }
    card.keywordDemo = raw.data.retrievalMode === 'KEYWORD_DEMO'
  } else if (raw.type === 'clarifying_question') {
    if (!exact(raw.data, ['requiredFields', 'confirmedConditions']) || !list(raw.data.requiredFields, 6, item => CONDITION_FIELDS.includes(item), 1)) return null
    const confirmed = conditions(raw.data.confirmedConditions)
    if (!confirmed) return null
    card.data = {
      requiredFields: [...raw.data.requiredFields],
      requiredFieldLabels: raw.data.requiredFields.map(conditionLabel),
      requiredFieldsText: raw.data.requiredFields.map(conditionLabel).join('、'),
      confirmedConditions: confirmed,
      question: raw.summary
    }
  } else if (raw.type === 'pending_booking') {
    if (!exact(raw.data, ['proposal'])) return null
    const value = proposal(raw.data.proposal)
    if (!value) return null
    card.data = { proposal: value }
    card.demoMode = card.demoMode || value.demoData
  } else if (raw.type === 'error') {
    if (!exact(raw.data, ['code', 'retryable', 'demoAvailable']) || !text(raw.data.code, 1, 64) ||
      typeof raw.data.retryable !== 'boolean' || typeof raw.data.demoAvailable !== 'boolean') return null
    card.data = { code: clean(raw.data.code), retryable: raw.data.retryable, demoAvailable: raw.data.demoAvailable }
    card.canShowDemo = raw.data.demoAvailable
  }
  card.streamFingerprint = JSON.stringify({
    type: card.type,
    title: card.title,
    summary: card.summary,
    sources: card.sources,
    actions,
    data: card.data
  })
  return card
}

function legacySource(value) {
  return allowed(value, ['title', 'document_id']) && text(value.title, 1, 180) ? { title: clean(value.title) } : null
}

function legacyActions(serviceId, itinerary) {
  if (!uuid(serviceId)) return []
  const actions = [
    { action: 'LEGACY_OPEN_SERVICE', label: '查看详情', serviceId },
    { action: 'LEGACY_BOOK_SERVICE', label: '预约', serviceId }
  ]
  if (!itinerary) actions.splice(1, 0, { action: 'LEGACY_JOIN_SERVICE', label: '加入行程', serviceId })
  return actions
}

function legacyItem(value, itinerary = false) {
  if (!isObject(value)) return null
  const serviceId = uuid(value.serviceId) ? value.serviceId : ''
  const price = value.price === null || value.price === undefined ? null : (finiteNumber(value.price, 0) ? value.price : null)
  return {
    title: clean(value.title) || '乌东体验',
    summary: typeof value.summary === 'string' ? value.summary : '',
    time: itinerary && typeof value.time === 'string' ? value.time : '',
    price,
    hasPrice: price !== null,
    tags: Array.isArray(value.tags) ? value.tags.filter(item => typeof item === 'string').slice(0, 20) : [],
    demoData: value.demoData === true,
    legacyServiceId: serviceId,
    targetType: TARGET_TYPES.includes(value.targetType) && uuid(value.targetId) ? value.targetType : null,
    targetId: TARGET_TYPES.includes(value.targetType) && uuid(value.targetId) ? value.targetId : null,
    actions: legacyActions(serviceId, itinerary)
  }
}

function legacyCard(raw) {
  if (owns(raw, 'cardVersion') || !exact(raw, ['type', 'title', 'summary', 'data', 'sources']) ||
    !CARD_TYPES.includes(raw.type) || !text(raw.title, 1, 180) || typeof raw.summary !== 'string' || !isObject(raw.data) ||
    !Array.isArray(raw.sources) || raw.sources.length > 20 || !raw.sources.every(item => legacySource(item) !== null)) return null
  const sources = raw.sources.map(legacySource)
  const card = view(raw, 'legacy', sources, raw.data.demoMode === true)
  card.keywordDemo = raw.data.retrievalMode === 'keyword_demo' || raw.data.retrievalMode === 'KEYWORD_DEMO'

  if (raw.type === 'itinerary') {
    if (!Array.isArray(raw.data.days)) return null
    const days = []
    for (let index = 0; index < raw.data.days.length; index += 1) {
      const value = raw.data.days[index]
      if (!isObject(value) || !Array.isArray(value.items)) return null
      const items = value.items.map(item => legacyItem(item, true))
      if (items.some(item => item === null)) return null
      days.push({ day: positiveInteger(value.day) ? value.day : index + 1, dayLabel: `第${positiveInteger(value.day) ? value.day : index + 1}天`, theme: typeof value.theme === 'string' ? value.theme : '', items })
    }
    card.data = { days, notice: typeof raw.data.notice === 'string' ? raw.data.notice : '', retrievalMode: typeof raw.data.retrievalMode === 'string' ? raw.data.retrievalMode : '' }
    card.demoMode = card.demoMode || days.some(item => item.items.some(entry => entry.demoData))
    card.canAddLegacyItinerary = days.some(item => item.items.some(entry => entry.legacyServiceId))
  } else if (raw.type === 'service_recommendation') {
    if (!Array.isArray(raw.data.services)) return null
    const items = raw.data.services.map(item => legacyItem(item))
    if (items.some(item => item === null)) return null
    card.data = { items, notice: typeof raw.data.notice === 'string' ? raw.data.notice : '' }
    card.demoMode = card.demoMode || items.some(item => item.demoData)
  } else if (raw.type === 'pending_booking') {
    if (!isObject(raw.data.booking)) return null
    const booking = raw.data.booking
    const serviceId = uuid(booking.serviceId) ? booking.serviceId : ''
    const bookingId = uuid(booking.id) ? booking.id : ''
    card.data = {
      proposal: {
        targetName: clean(booking.serviceName) || '乌东体验',
        visitAt: typeof booking.travelDate === 'string' ? booking.travelDate : null,
        checkInDate: null,
        peopleCount: positiveInteger(booking.peopleCount) ? booking.peopleCount : null,
        note: null,
        status: typeof booking.status === 'string' ? booking.status : '',
        demoData: booking.demoData === true,
        actions: serviceId && bookingId
          ? [{ action: 'LEGACY_BOOK_SERVICE', label: '继续确认预约', serviceId, bookingId, booking: {
              id: bookingId,
              serviceId,
              serviceName: clean(booking.serviceName),
              travelDate: typeof booking.travelDate === 'string' ? booking.travelDate : '',
              peopleCount: positiveInteger(booking.peopleCount) ? booking.peopleCount : 0,
              status: typeof booking.status === 'string' ? booking.status : '',
              demoData: booking.demoData === true
            } }]
          : []
      }
    }
    card.demoMode = card.demoMode || booking.demoData === true
  } else if (raw.type === 'knowledge_answer') {
    card.data = { answer: typeof raw.data.answer === 'string' ? raw.data.answer : raw.summary, retrievalMode: typeof raw.data.retrievalMode === 'string' ? raw.data.retrievalMode : '' }
  } else if (raw.type === 'clarifying_question') {
    const serviceId = uuid(raw.data.serviceId) ? raw.data.serviceId : ''
    card.data = { question: typeof raw.data.question === 'string' ? raw.data.question : raw.summary, requiredFieldLabels: [], requiredFieldsText: '', actions: serviceId ? [{ action: 'LEGACY_BOOK_SERVICE', label: '前往预约页补全信息', serviceId }] : [] }
  } else if (raw.type === 'error') {
    card.data = { demoAvailable: raw.data.demoAvailable === true }
    card.canShowDemo = raw.data.demoAvailable === true
  }
  return withActionKeys(card)
}

function conditionLabel(value) {
  return { travelDate: '出行日期', visitAt: '到店时间', checkInDate: '入住日期', peopleCount: '人数', preferences: '旅行偏好', target: '想体验的项目' }[value] || value
}

function withActionKeys(card) {
  let sequence = 0
  const mark = actions => (Array.isArray(actions) ? actions : []).map(item => ({ ...item, key: `action-${sequence++}` }))
  const copy = { ...card, actions: mark(card.actions), data: { ...card.data } }
  if (copy.type === 'itinerary') {
    copy.data.days = copy.data.days.map(value => ({ ...value, items: value.items.map(item => ({ ...item, actions: mark(item.actions) })) }))
  } else if (copy.type === 'service_recommendation') {
    copy.data.items = copy.data.items.map(item => ({ ...item, actions: mark(item.actions) }))
  } else if (copy.type === 'pending_booking' && copy.data.proposal) {
    copy.data.proposal = { ...copy.data.proposal, actions: mark(copy.data.proposal.actions) }
  } else if (copy.type === 'clarifying_question') {
    copy.data.actions = mark(copy.data.actions)
  }
  return copy
}

function adaptAssistantCard(raw, protocol) {
  if (!isObject(raw)) return null
  if (protocol === 'v2') {
    const card = v2Card(raw)
    return card ? withActionKeys(card) : null
  }
  return protocol === 'legacy' ? legacyCard(raw) : null
}

function phaseUpdate(activate = '', complete = []) {
  return { type: 'phase_update', activate, complete }
}

function progress(stage) {
  if (stage === 'UNDERSTANDING') return phaseUpdate('understanding')
  if (stage === 'RETRIEVING') return phaseUpdate('knowledge', ['understanding'])
  if (stage === 'PLANNING') return phaseUpdate('service', ['understanding', 'knowledge'])
  return phaseUpdate('itinerary', ['understanding', 'knowledge', 'service'])
}

function legacyEvent(raw) {
  if (raw.type === 'node_started') {
    if (!exact(raw, ['type', 'node', 'message']) || !text(raw.node, 1, 80) || typeof raw.message !== 'string') return null
    if (['route', 'intent'].includes(raw.node)) return phaseUpdate('understanding')
    if (raw.node === 'retrieve') return phaseUpdate('knowledge', ['understanding'])
    if (raw.node === 'service_search') return phaseUpdate('service', ['understanding'])
    if (raw.node === 'itinerary') return phaseUpdate('itinerary', ['understanding'])
    return phaseUpdate()
  }
  if (raw.type === 'tool_finished') {
    if (!allowed(raw, ['type', 'tool', 'service_count', 'source_count']) || !owns(raw, 'type') || !owns(raw, 'tool') || !text(raw.tool, 1, 80)) return null
    if (owns(raw, 'service_count') && !nonNegativeInteger(raw.service_count)) return null
    if (owns(raw, 'source_count') && !nonNegativeInteger(raw.source_count)) return null
    if (raw.tool === 'knowledge_retrieval') return phaseUpdate('', ['knowledge'])
    if (raw.tool === 'service_search') return phaseUpdate('', ['service'])
    return phaseUpdate()
  }
  if (raw.type === 'card_ready') {
    if (!exact(raw, ['type', 'card'])) return null
    const card = adaptAssistantCard(raw.card, 'legacy')
    return card ? { type: 'card_ready', card } : null
  }
  if (raw.type === 'completed' || raw.type === 'failed') return exact(raw, ['type']) ? { type: raw.type } : null
  return null
}

function v2Event(raw) {
  if (raw.type === 'progress') {
    return exact(raw, ['type', 'stage', 'message']) && PROGRESS_STAGES.includes(raw.stage) && text(raw.message, 1, 160) ? progress(raw.stage) : null
  }
  if (raw.type === 'tool_summary') return exact(raw, ['type', 'summary']) && toolSummary(raw.summary) ? phaseUpdate() : null
  if (raw.type === 'card_ready') {
    if (!exact(raw, ['type', 'card'])) return null
    const card = adaptAssistantCard(raw.card, 'v2')
    return card ? { type: 'card_ready', card } : null
  }
  if (raw.type === 'completed') return exact(raw, ['type']) ? { type: 'completed' } : null
  if (raw.type === 'failed') return exact(raw, ['type', 'code']) && text(raw.code, 1, 64) ? { type: 'failed' } : null
  return null
}

function adaptAssistantEvent(raw, lockedProtocol) {
  if (!isObject(raw) || typeof raw.type !== 'string') return null
  if (lockedProtocol === null || lockedProtocol === undefined) {
    if (raw.type !== 'task_started') return null
    const legacy = exact(raw, ['type', 'thread_id']) && text(raw.thread_id, 1, 100)
    const v2 = exact(raw, ['type', 'threadId']) && text(raw.threadId, 1, 100)
    if (legacy === v2) return null
    return { protocol: legacy ? 'legacy' : 'v2', event: { type: 'task_started', threadId: clean(legacy ? raw.thread_id : raw.threadId) } }
  }
  if (!['legacy', 'v2'].includes(lockedProtocol) || raw.type === 'task_started') return null
  const event = lockedProtocol === 'legacy' ? legacyEvent(raw) : v2Event(raw)
  return event ? { protocol: lockedProtocol, event } : null
}

function createClientErrorCard(title, summary) {
  return withActionKeys({
    viewModelVersion: VIEW_MODEL_VERSION,
    protocol: 'client',
    type: 'error',
    title,
    summary,
    sources: [],
    sourceCount: 0,
    actions: [],
    data: { demoAvailable: true },
    demoMode: false,
    keywordDemo: false,
    canShowDemo: true,
    canAddLegacyItinerary: false
  })
}

function isAssistantViewCard(value) {
  return isObject(value) && value.viewModelVersion === VIEW_MODEL_VERSION && CARD_TYPES.includes(value.type)
}

function cardFingerprint(card) {
  return isAssistantViewCard(card) ? JSON.stringify(card) : ''
}

module.exports = {
  CARD_TYPES,
  adaptAssistantCard,
  adaptAssistantEvent,
  createClientErrorCard,
  isAssistantViewCard,
  cardFingerprint
}
