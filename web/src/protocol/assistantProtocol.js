const CARD_TYPES = new Set([
  'itinerary',
  'service_recommendation',
  'knowledge_answer',
  'clarifying_question',
  'pending_booking',
  'error'
])

const TARGET_TYPES = new Set(['PRODUCT', 'FOOD', 'STAY', 'PLACE', 'ROUTE_GUIDE'])
const SOURCE_TYPES = new Set(['KNOWLEDGE', 'CATALOG', 'ROUTE_GUIDE'])
const ACTION_TYPES = new Set([
  'OPEN_DETAIL',
  'ADD_TO_ITINERARY',
  'OPEN_ORDER_CONFIRMATION',
  'OPEN_MAP',
  'OPEN_ROUTE_GUIDE',
  'RETRY'
])
const RETRIEVAL_MODES = new Set(['VECTOR', 'KEYWORD_DEMO', 'NONE'])
const KNOWLEDGE_MODES = new Set(['VECTOR', 'KEYWORD_DEMO'])
const CONDITION_FIELDS = new Set([
  'travelDate',
  'visitAt',
  'checkInDate',
  'peopleCount',
  'preferences',
  'target'
])
const PROGRESS_STAGES = new Set(['UNDERSTANDING', 'RETRIEVING', 'PLANNING', 'PREPARING_RESULT'])
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const VIEW_MODEL_VERSION = 'assistant-ui-v1'

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function owns(value, key) {
  return Object.prototype.hasOwnProperty.call(value, key)
}

function hasExactKeys(value, keys) {
  if (!isObject(value)) return false
  const actual = Object.keys(value)
  return actual.length === keys.length && keys.every(key => owns(value, key))
}

function hasAllowedKeys(value, allowed) {
  return isObject(value) && Object.keys(value).every(key => allowed.includes(key))
}

function isText(value, min = 0, max = Number.MAX_SAFE_INTEGER) {
  return typeof value === 'string' && value.trim().length >= min && value.length <= max
}

function isUuid(value) {
  return typeof value === 'string' && UUID_PATTERN.test(value)
}

function isPublicTargetId(value) {
  return isText(value, 1, 120)
}

function isFiniteNumber(value, minimum = -Infinity) {
  return typeof value === 'number' && Number.isFinite(value) && value >= minimum
}

function isPositiveInteger(value) {
  return Number.isInteger(value) && value > 0
}

function isNonNegativeInteger(value) {
  return Number.isInteger(value) && value >= 0
}

function isDate(value) {
  const match = typeof value === 'string' && /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return false
  const [year, month, day] = match.slice(1).map(Number)
  if (year < 1 || month < 1 || month > 12 || day < 1) return false
  return day <= new Date(Date.UTC(year, month, 0)).getUTCDate()
}

function isTimestamp(value) {
  const match = typeof value === 'string' && value.length <= 40 &&
    /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,6})?)?(Z|[+-]\d{2}:\d{2})?$/.exec(value)
  if (!match || !isDate(match[1])) return false
  const [, , hour, minute, second = '0', zone] = match
  if (Number(hour) > 23 || Number(minute) > 59 || Number(second) > 59) return false
  if (!zone || zone === 'Z') return true
  const [, , offsetHour, offsetMinute] = /^([+-])(\d{2}):(\d{2})$/.exec(zone) || []
  return Number(offsetHour) <= 23 && Number(offsetMinute) <= 59
}

function isNullable(value, predicate) {
  return value === null || predicate(value)
}

function isArrayOf(value, max, predicate, min = 0) {
  return Array.isArray(value) && value.length >= min && value.length <= max && value.every(predicate)
}

function cleanText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function publicTarget(value) {
  if (!hasExactKeys(value, ['targetType', 'targetId', 'targetName'])) return null
  if (!TARGET_TYPES.has(value.targetType) || !isPublicTargetId(value.targetId) || !isText(value.targetName, 1, 180)) return null
  return {
    targetType: value.targetType,
    targetId: value.targetId,
    targetName: cleanText(value.targetName)
  }
}

function confirmedConditions(value) {
  if (!hasExactKeys(value, [
    'travelDate',
    'visitAt',
    'checkInDate',
    'peopleCount',
    'preferences',
    'selectedTargets'
  ])) return null
  if (!isNullable(value.travelDate, isDate) ||
    !isNullable(value.visitAt, isTimestamp) ||
    !isNullable(value.checkInDate, isDate) ||
    !isNullable(value.peopleCount, isPositiveInteger) ||
    !isArrayOf(value.preferences, 12, item => isText(item, 0)) ||
    !isArrayOf(value.selectedTargets, 12, item => publicTarget(item) !== null)) return null
  return {
    travelDate: value.travelDate,
    visitAt: value.visitAt,
    checkInDate: value.checkInDate,
    peopleCount: value.peopleCount,
    preferences: value.preferences.map(cleanText),
    selectedTargets: value.selectedTargets.map(publicTarget)
  }
}

function publicSource(value) {
  if (!hasExactKeys(value, ['title', 'documentId', 'sourceType', 'demoData'])) return null
  if (!isText(value.title, 1, 180) ||
    !isNullable(value.documentId, item => isText(item, 0, 120)) ||
    !SOURCE_TYPES.has(value.sourceType) ||
    typeof value.demoData !== 'boolean') return null
  return {
    title: cleanText(value.title),
    documentId: value.documentId === null ? null : cleanText(value.documentId),
    sourceType: value.sourceType,
    demoData: value.demoData
  }
}

function cardAction(value) {
  if (!hasExactKeys(value, ['action', 'label', 'targetType', 'targetId'])) return null
  if (!ACTION_TYPES.has(value.action) || !isText(value.label, 1, 40)) return null
  const targetless = value.targetType === null && value.targetId === null
  const targeted = TARGET_TYPES.has(value.targetType) && isPublicTargetId(value.targetId)
  if (value.action === 'RETRY' ? !targetless : !targeted) return null
  if (value.action === 'OPEN_ORDER_CONFIRMATION' && !['FOOD', 'STAY'].includes(value.targetType)) return null
  if (value.action === 'OPEN_MAP' && value.targetType !== 'PLACE') return null
  if (value.action === 'OPEN_ROUTE_GUIDE' && value.targetType !== 'ROUTE_GUIDE') return null
  return {
    action: value.action,
    label: cleanText(value.label),
    targetType: value.targetType,
    targetId: value.targetId
  }
}

function publicToolSummary(value) {
  if (!hasExactKeys(value, ['nodeName', 'toolCategory', 'sourceTitles', 'durationMs', 'finalStatus'])) return false
  return isText(value.nodeName, 1, 80) &&
    isNullable(value.toolCategory, item => isText(item, 0, 80)) &&
    isArrayOf(value.sourceTitles, 20, item => isText(item, 0)) &&
    isNonNegativeInteger(value.durationMs) &&
    isText(value.finalStatus, 1, 32)
}

function itineraryStop(value) {
  if (!hasExactKeys(value, ['timeText', 'title', 'summary', 'targetType', 'targetId', 'demoData'])) return null
  if (!isText(value.timeText, 0, 80) || !isText(value.title, 1, 180) || !isText(value.summary, 0, 500)) return null
  const targetless = value.targetType === null && value.targetId === null
  const targeted = TARGET_TYPES.has(value.targetType) && isPublicTargetId(value.targetId)
  if ((!targetless && !targeted) || typeof value.demoData !== 'boolean') return null
  return {
    time: cleanText(value.timeText),
    title: cleanText(value.title),
    summary: cleanText(value.summary),
    targetType: value.targetType,
    targetId: value.targetId,
    demoData: value.demoData
  }
}

function itineraryDay(value) {
  if (!hasExactKeys(value, ['day', 'theme', 'items'])) return null
  if (!isPositiveInteger(value.day) || !isText(value.theme, 1, 120) || !isArrayOf(value.items, 12, item => itineraryStop(item) !== null)) return null
  return {
    day: value.day,
    theme: cleanText(value.theme),
    items: value.items.map(itineraryStop)
  }
}

function recommendationItem(value) {
  if (!hasExactKeys(value, ['targetType', 'targetId', 'targetName', 'summary', 'price', 'tags', 'demoData'])) return null
  if (!TARGET_TYPES.has(value.targetType) || !isPublicTargetId(value.targetId) || !isText(value.targetName, 1, 180) ||
    !isText(value.summary, 0, 500) || !isNullable(value.price, item => isFiniteNumber(item, 0)) ||
    !isArrayOf(value.tags, 20, item => isText(item, 0)) || typeof value.demoData !== 'boolean') return null
  return {
    title: cleanText(value.targetName),
    summary: cleanText(value.summary),
    price: value.price,
    tags: value.tags.map(cleanText),
    targetType: value.targetType,
    targetId: value.targetId,
    demoData: value.demoData
  }
}

function orderProposal(value) {
  if (!hasExactKeys(value, [
    'proposalType',
    'targetType',
    'targetId',
    'targetName',
    'visitAt',
    'checkInDate',
    'peopleCount',
    'note',
    'sourceThreadId',
    'requiresVisitorConfirmation',
    'demoData'
  ])) return null
  if (!['FOOD_ORDER', 'STAY_BOOKING'].includes(value.proposalType) ||
    !['FOOD', 'STAY'].includes(value.targetType) || !isPublicTargetId(value.targetId) ||
    !isText(value.targetName, 1, 180) || !isNullable(value.visitAt, isTimestamp) ||
    !isNullable(value.checkInDate, isDate) || !isPositiveInteger(value.peopleCount) ||
    !isNullable(value.note, item => isText(item, 0, 300)) || !isText(value.sourceThreadId, 1, 100) ||
    value.requiresVisitorConfirmation !== true || typeof value.demoData !== 'boolean') return null
  const isFood = value.proposalType === 'FOOD_ORDER'
  if (isFood && (value.targetType !== 'FOOD' || value.visitAt === null || value.checkInDate !== null)) return null
  if (!isFood && (value.targetType !== 'STAY' || value.checkInDate === null || value.visitAt !== null)) return null
  return {
    proposalType: value.proposalType,
    targetType: value.targetType,
    targetId: value.targetId,
    targetName: cleanText(value.targetName),
    visitAt: value.visitAt,
    checkInDate: value.checkInDate,
    peopleCount: value.peopleCount,
    note: value.note === null ? null : cleanText(value.note),
    sourceThreadId: cleanText(value.sourceThreadId),
    requiresVisitorConfirmation: true,
    demoData: value.demoData,
    status: ''
  }
}

function isSupportedV2Action(action) {
  if (action.action === 'RETRY') return true
  if (action.action === 'OPEN_DETAIL') return ['PRODUCT', 'FOOD'].includes(action.targetType)
  return action.action === 'OPEN_ORDER_CONFIRMATION' && ['FOOD', 'STAY'].includes(action.targetType)
}

function actionsForTarget(actions, targetType, targetId) {
  if (!targetType || !targetId) return []
  return actions.filter(action =>
    action.targetType === targetType &&
    action.targetId === targetId &&
    action.action !== 'RETRY' &&
    isSupportedV2Action(action)
  )
}

function actionKey(action) {
  return `${action.action}|${action.targetType || ''}|${action.targetId || ''}`
}

function uniqueActions(actions) {
  const seen = new Set()
  return actions.filter(action => {
    const key = actionKey(action)
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function boundActionKeys(card) {
  const keys = new Set()
  const add = actions => actions.forEach(action => keys.add(actionKey(action)))
  if (card.type === 'itinerary') card.data.days.forEach(day => day.items.forEach(item => add(item.actions)))
  if (card.type === 'service_recommendation') card.data.items.forEach(item => add(item.actions))
  if (card.type === 'pending_booking') add(card.data.proposal.actions)
  return keys
}

function baseView(raw, protocol, sources, demoMode) {
  return {
    viewModelVersion: VIEW_MODEL_VERSION,
    protocol,
    type: raw.type,
    title: cleanText(raw.title),
    summary: raw.summary,
    sources,
    sourceCount: sources.length,
    actions: [],
    data: {},
    demoMode,
    keywordDemo: false,
    canShowDemo: false
  }
}

function v2Card(raw) {
  if (!hasExactKeys(raw, ['cardVersion', 'type', 'title', 'summary', 'sources', 'actions', 'data'])) return null
  if (raw.cardVersion !== '2.0' || !CARD_TYPES.has(raw.type) || !isText(raw.title, 1, 180) ||
    !isText(raw.summary, 0, 1200) || !isObject(raw.data) ||
    !isArrayOf(raw.sources, 20, item => publicSource(item) !== null) ||
    !isArrayOf(raw.actions, 10, item => cardAction(item) !== null)) return null

  const sources = raw.sources.map(publicSource)
  const strictActions = raw.actions.map(cardAction)
  const supportedActions = uniqueActions(strictActions.filter(isSupportedV2Action))
  const card = baseView(raw, 'v2', sources, sources.some(source => source.demoData))

  if (raw.type === 'itinerary') {
    if (!hasExactKeys(raw.data, ['days', 'notice', 'retrievalMode']) ||
      !isArrayOf(raw.data.days, 7, item => itineraryDay(item) !== null, 1) ||
      !isText(raw.data.notice, 1, 300) || !RETRIEVAL_MODES.has(raw.data.retrievalMode)) return null
    const days = raw.data.days.map(itineraryDay).map(day => ({
      ...day,
      items: day.items.map(item => ({
        ...item,
        actions: actionsForTarget(supportedActions, item.targetType, item.targetId)
      }))
    }))
    card.data = { days, notice: cleanText(raw.data.notice), retrievalMode: raw.data.retrievalMode }
    card.keywordDemo = raw.data.retrievalMode === 'KEYWORD_DEMO'
    card.demoMode = card.demoMode || days.some(day => day.items.some(item => item.demoData))
  } else if (raw.type === 'service_recommendation') {
    if (!hasExactKeys(raw.data, ['items', 'notice']) ||
      !isArrayOf(raw.data.items, 12, item => recommendationItem(item) !== null) ||
      !isText(raw.data.notice, 1, 300)) return null
    const items = raw.data.items.map(recommendationItem).map(item => ({
      ...item,
      actions: actionsForTarget(supportedActions, item.targetType, item.targetId)
    }))
    card.data = { items, notice: cleanText(raw.data.notice) }
    card.demoMode = card.demoMode || items.some(item => item.demoData)
  } else if (raw.type === 'knowledge_answer') {
    if (!hasExactKeys(raw.data, ['answer', 'retrievalMode']) || !isText(raw.data.answer, 1, 2400) ||
      !KNOWLEDGE_MODES.has(raw.data.retrievalMode) || sources.length < 1) return null
    card.data = { answer: cleanText(raw.data.answer), retrievalMode: raw.data.retrievalMode }
    card.keywordDemo = raw.data.retrievalMode === 'KEYWORD_DEMO'
  } else if (raw.type === 'clarifying_question') {
    if (!hasExactKeys(raw.data, ['requiredFields', 'confirmedConditions']) ||
      !isArrayOf(raw.data.requiredFields, 6, item => CONDITION_FIELDS.has(item), 1)) return null
    const conditions = confirmedConditions(raw.data.confirmedConditions)
    if (!conditions) return null
    card.data = {
      requiredFields: [...raw.data.requiredFields],
      requiredFieldLabels: raw.data.requiredFields.map(conditionLabel),
      confirmedConditions: conditions,
      question: raw.summary
    }
  } else if (raw.type === 'pending_booking') {
    if (!hasExactKeys(raw.data, ['proposal'])) return null
    const proposal = orderProposal(raw.data.proposal)
    if (!proposal) return null
    proposal.actions = actionsForTarget(supportedActions, proposal.targetType, proposal.targetId)
    card.data = { proposal }
    card.demoMode = card.demoMode || proposal.demoData
  } else if (raw.type === 'error') {
    if (!hasExactKeys(raw.data, ['code', 'retryable', 'demoAvailable']) ||
      !isText(raw.data.code, 1, 64) || typeof raw.data.retryable !== 'boolean' ||
      typeof raw.data.demoAvailable !== 'boolean') return null
    card.data = {
      code: cleanText(raw.data.code),
      retryable: raw.data.retryable,
      demoAvailable: raw.data.demoAvailable
    }
    card.canShowDemo = raw.data.demoAvailable
  }
  const bound = boundActionKeys(card)
  card.actions = supportedActions.filter(action => action.action === 'RETRY' || !bound.has(actionKey(action)))
  card.streamFingerprint = JSON.stringify({
    type: card.type,
    title: card.title,
    summary: card.summary,
    sources: card.sources,
    actions: strictActions,
    data: card.data
  })
  return card
}

function legacySource(value) {
  if (!hasAllowedKeys(value, ['title', 'document_id']) || !isText(value.title, 1, 180)) return null
  return { title: cleanText(value.title) }
}

function legacyTarget(value) {
  if (!TARGET_TYPES.has(value?.targetType) || !isUuid(value?.targetId)) return null
  return { targetType: value.targetType, targetId: value.targetId }
}

function legacyServiceActions(serviceId) {
  if (!isUuid(serviceId)) return []
  return [
    { action: 'LEGACY_OPEN_SERVICE', label: '查看详情', serviceId },
    { action: 'LEGACY_JOIN_SERVICE', label: '加入行程', serviceId },
    { action: 'LEGACY_BOOK_SERVICE', label: '预约', serviceId }
  ]
}

function legacyItem(value, itinerary = false) {
  if (!isObject(value)) return null
  const serviceId = isUuid(value.serviceId) ? value.serviceId : ''
  const target = legacyTarget(value)
  const title = cleanText(value.title) || '乌东体验'
  const summary = typeof value.summary === 'string' ? value.summary : ''
  const price = value.price === null || value.price === undefined
    ? null
    : (isFiniteNumber(value.price, 0) ? value.price : null)
  return {
    title,
    summary,
    time: itinerary && typeof value.time === 'string' ? value.time : '',
    price,
    tags: Array.isArray(value.tags) ? value.tags.filter(item => typeof item === 'string').slice(0, 20) : [],
    demoData: value.demoData === true,
    legacyServiceId: serviceId,
    targetType: target?.targetType || null,
    targetId: target?.targetId || null,
    actions: legacyServiceActions(serviceId)
  }
}

function legacyCard(raw) {
  if (owns(raw, 'cardVersion') || !hasExactKeys(raw, ['type', 'title', 'summary', 'data', 'sources'])) return null
  if (!CARD_TYPES.has(raw.type) || !isText(raw.title, 1, 180) || typeof raw.summary !== 'string' ||
    !isObject(raw.data) || !Array.isArray(raw.sources) || raw.sources.length > 20 ||
    !raw.sources.every(source => legacySource(source) !== null)) return null
  const sources = raw.sources.map(legacySource)
  const card = baseView(raw, 'legacy', sources, raw.data.demoMode === true)
  card.keywordDemo = raw.data.retrievalMode === 'keyword_demo' || raw.data.retrievalMode === 'KEYWORD_DEMO'

  if (raw.type === 'itinerary') {
    if (!Array.isArray(raw.data.days)) return null
    const days = []
    for (let index = 0; index < raw.data.days.length; index += 1) {
      const day = raw.data.days[index]
      if (!isObject(day) || !Array.isArray(day.items)) return null
      const items = day.items.map(item => legacyItem(item, true))
      if (items.some(item => item === null)) return null
      days.push({
        day: isPositiveInteger(day.day) ? day.day : index + 1,
        theme: typeof day.theme === 'string' ? day.theme : '',
        items
      })
    }
    card.data = {
      days,
      notice: typeof raw.data.notice === 'string' ? raw.data.notice : '',
      retrievalMode: typeof raw.data.retrievalMode === 'string' ? raw.data.retrievalMode : ''
    }
    card.demoMode = card.demoMode || days.some(day => day.items.some(item => item.demoData))
  } else if (raw.type === 'service_recommendation') {
    if (!Array.isArray(raw.data.services)) return null
    const items = raw.data.services.map(item => legacyItem(item))
    if (items.some(item => item === null)) return null
    card.data = { items, notice: typeof raw.data.notice === 'string' ? raw.data.notice : '' }
    card.demoMode = card.demoMode || items.some(item => item.demoData)
  } else if (raw.type === 'pending_booking') {
    if (!isObject(raw.data.booking)) return null
    const booking = raw.data.booking
    const serviceId = isUuid(booking.serviceId) ? booking.serviceId : ''
    const bookingId = isUuid(booking.id) ? booking.id : ''
    card.data = {
      proposal: {
        targetName: cleanText(booking.serviceName) || '乌东体验',
        visitAt: typeof booking.travelDate === 'string' ? booking.travelDate : null,
        checkInDate: null,
        peopleCount: isPositiveInteger(booking.peopleCount) ? booking.peopleCount : null,
        note: null,
        status: typeof booking.status === 'string' ? booking.status : '',
        demoData: booking.demoData === true,
        actions: serviceId && bookingId
          ? [{ action: 'LEGACY_BOOK_SERVICE', label: '继续确认预约', serviceId, bookingId, booking: {
              id: bookingId,
              serviceId,
              serviceName: cleanText(booking.serviceName),
              travelDate: typeof booking.travelDate === 'string' ? booking.travelDate : '',
              peopleCount: isPositiveInteger(booking.peopleCount) ? booking.peopleCount : 0,
              status: typeof booking.status === 'string' ? booking.status : '',
              demoData: booking.demoData === true
            } }]
          : []
      }
    }
    card.demoMode = card.demoMode || booking.demoData === true
  } else if (raw.type === 'knowledge_answer') {
    card.data = {
      answer: typeof raw.data.answer === 'string' ? raw.data.answer : raw.summary,
      retrievalMode: typeof raw.data.retrievalMode === 'string' ? raw.data.retrievalMode : ''
    }
  } else if (raw.type === 'clarifying_question') {
    const serviceId = isUuid(raw.data.serviceId) ? raw.data.serviceId : ''
    card.data = {
      question: typeof raw.data.question === 'string' ? raw.data.question : raw.summary,
      requiredFieldLabels: [],
      actions: serviceId ? [{ action: 'LEGACY_BOOK_SERVICE', label: '前往预约页补全信息', serviceId }] : []
    }
  } else if (raw.type === 'error') {
    card.data = { demoAvailable: raw.data.demoAvailable === true }
    card.canShowDemo = raw.data.demoAvailable === true
  }
  return card
}

function conditionLabel(value) {
  return {
    travelDate: '出行日期',
    visitAt: '到店时间',
    checkInDate: '入住日期',
    peopleCount: '人数',
    preferences: '旅行偏好',
    target: '想体验的项目'
  }[value] || value
}

export function adaptAssistantCard(raw, protocol) {
  if (!isObject(raw)) return null
  if (protocol === 'v2') return v2Card(raw)
  if (protocol === 'legacy') return legacyCard(raw)
  return null
}

function phaseUpdate(activate = '', complete = []) {
  return { type: 'phase_update', activate, complete }
}

function v2Progress(stage) {
  if (stage === 'UNDERSTANDING') return phaseUpdate('understanding')
  if (stage === 'RETRIEVING') return phaseUpdate('knowledge', ['understanding'])
  if (stage === 'PLANNING') return phaseUpdate('service', ['understanding', 'knowledge'])
  return phaseUpdate('itinerary', ['understanding', 'knowledge', 'service'])
}

function legacyEvent(raw) {
  if (raw.type === 'node_started') {
    if (!hasExactKeys(raw, ['type', 'node', 'message']) || !isText(raw.node, 1, 80) || typeof raw.message !== 'string') return null
    if (['route', 'intent'].includes(raw.node)) return phaseUpdate('understanding')
    if (raw.node === 'retrieve') return phaseUpdate('knowledge', ['understanding'])
    if (raw.node === 'service_search') return phaseUpdate('service', ['understanding'])
    if (raw.node === 'itinerary') return phaseUpdate('itinerary', ['understanding'])
    return phaseUpdate()
  }
  if (raw.type === 'tool_finished') {
    if (!hasAllowedKeys(raw, ['type', 'tool', 'service_count', 'source_count']) ||
      !owns(raw, 'type') || !owns(raw, 'tool') || !isText(raw.tool, 1, 80)) return null
    if (owns(raw, 'service_count') && !isNonNegativeInteger(raw.service_count)) return null
    if (owns(raw, 'source_count') && !isNonNegativeInteger(raw.source_count)) return null
    if (raw.tool === 'knowledge_retrieval') return phaseUpdate('', ['knowledge'])
    if (raw.tool === 'service_search') return phaseUpdate('', ['service'])
    return phaseUpdate()
  }
  if (raw.type === 'card_ready') {
    if (!hasExactKeys(raw, ['type', 'card'])) return null
    const card = adaptAssistantCard(raw.card, 'legacy')
    return card ? { type: 'card_ready', card } : null
  }
  if (raw.type === 'completed' || raw.type === 'failed') {
    return hasExactKeys(raw, ['type']) ? { type: raw.type } : null
  }
  return null
}

function v2Event(raw) {
  if (raw.type === 'progress') {
    if (!hasExactKeys(raw, ['type', 'stage', 'message']) || !PROGRESS_STAGES.has(raw.stage) || !isText(raw.message, 1, 160)) return null
    return v2Progress(raw.stage)
  }
  if (raw.type === 'tool_summary') {
    if (!hasExactKeys(raw, ['type', 'summary']) || !publicToolSummary(raw.summary)) return null
    return phaseUpdate()
  }
  if (raw.type === 'card_ready') {
    if (!hasExactKeys(raw, ['type', 'card'])) return null
    const card = adaptAssistantCard(raw.card, 'v2')
    return card ? { type: 'card_ready', card } : null
  }
  if (raw.type === 'completed') return hasExactKeys(raw, ['type']) ? { type: 'completed' } : null
  if (raw.type === 'failed') {
    return hasExactKeys(raw, ['type', 'code']) && isText(raw.code, 1, 64) ? { type: 'failed' } : null
  }
  return null
}

export function adaptAssistantEvent(raw, lockedProtocol = null) {
  if (!isObject(raw) || typeof raw.type !== 'string') return null
  if (lockedProtocol === null) {
    if (raw.type !== 'task_started') return null
    const legacy = hasExactKeys(raw, ['type', 'thread_id']) && isText(raw.thread_id, 1, 100)
    const v2 = hasExactKeys(raw, ['type', 'threadId']) && isText(raw.threadId, 1, 100)
    if (legacy === v2) return null
    return {
      protocol: legacy ? 'legacy' : 'v2',
      event: { type: 'task_started', threadId: cleanText(legacy ? raw.thread_id : raw.threadId) }
    }
  }
  if (!['legacy', 'v2'].includes(lockedProtocol) || raw.type === 'task_started') return null
  const event = lockedProtocol === 'legacy' ? legacyEvent(raw) : v2Event(raw)
  return event ? { protocol: lockedProtocol, event } : null
}

export function createClientErrorCard(title, summary) {
  return {
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
    canShowDemo: true
  }
}

export function isAssistantViewCard(value) {
  return isObject(value) && value.viewModelVersion === VIEW_MODEL_VERSION && CARD_TYPES.has(value.type)
}

export function cardFingerprint(card) {
  return isAssistantViewCard(card) ? JSON.stringify(card) : ''
}
