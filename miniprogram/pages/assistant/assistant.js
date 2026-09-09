const {
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
} = require('../../utils/assistant')
const { demoAssistantCard } = require('../../utils/demo')

function closeSocketTask(socketTask) {
  if (!socketTask || typeof socketTask.close !== 'function') return
  try {
    socketTask.close({ code: 1000, reason: 'client_settled' })
  } catch (_) {}
}

function cleanItineraryDays(days) {
  return (Array.isArray(days) ? days : []).map((day, dayIndex) => ({
    day: day?.day || dayIndex + 1,
    theme: typeof day?.theme === 'string' ? day.theme : '',
    items: (Array.isArray(day?.items) ? day.items : [])
      .filter(item => validServiceId(item?.serviceId || item?.legacyServiceId))
      .map(item => ({
        time: typeof item.time === 'string' ? item.time : '',
        title: typeof item.title === 'string' ? item.title : '乌东体验',
        summary: typeof item.summary === 'string' ? item.summary : '',
        serviceId: validServiceId(item.serviceId || item.legacyServiceId),
        demoData: item.demoData === true
      }))
  })).filter(day => day.items.length)
}

function collectActions(card) {
  const result = {}
  const add = actions => {
    for (const action of Array.isArray(actions) ? actions : []) {
      if (typeof action?.key === 'string') result[action.key] = action
    }
  }
  add(card?.actions)
  for (const day of card?.data?.days || []) {
    for (const item of day.items || []) add(item.actions)
  }
  for (const item of card?.data?.items || []) add(item.actions)
  add(card?.data?.proposal?.actions)
  add(card?.data?.actions)
  return result
}

Page({
  data: {
    input: '我想体验贵州乌东的苗族文化和茶旅',
    messages: [],
    visibleMessages: [],
    phases: createPhases(),
    currentCard: null,
    previousCard: null,
    isBusy: false,
    mode: 'live',
    requestSeq: 0,
    sourcesOpen: false,
    previousOpen: false
  },

  onShow() {
    this.getTabBar()?.setData({ selected: 2 })
    const session = ensureAssistantSession(getApp().globalData)
    this.cardActions = collectActions(session.currentCard)
    this.setData({
      messages: session.messages,
      visibleMessages: session.messages.slice(-6),
      phases: restorePhases(session.phases),
      currentCard: session.currentCard,
      previousCard: session.previousCard,
      isBusy: session.isBusy,
      mode: session.mode,
      requestSeq: session.requestSeq,
      sourcesOpen: false,
      previousOpen: false
    })
  },

  onUnload() {
    const session = ensureAssistantSession(getApp().globalData)
    const socketTask = session.activeSocket
    const requestSeq = session.requestSeq + 1
    const phases = session.isBusy ? settlePhases(session.phases, true) : restorePhases(session.phases)
    session.requestSeq = requestSeq
    session.activeSocket = null
    session.isBusy = false
    session.streamTerminal = true
    session.phases = phases
    this.setData({ requestSeq, isBusy: false, phases })
    closeSocketTask(socketTask)
  },

  isCurrentRequest(seq, socketTask) {
    const session = ensureAssistantSession(getApp().globalData)
    return session.requestSeq === seq && this.data.requestSeq === seq && session.activeSocket === socketTask
  },

  acceptCardForSeq(seq, card, modeOverride) {
    const session = ensureAssistantSession(getApp().globalData)
    if (session.requestSeq !== seq || this.data.requestSeq !== seq || !card) return false
    const fingerprint = cardFingerprint(card)
    if (!fingerprint) return false
    if (session.acceptedCardSeq === seq && session.acceptedCardFingerprint === fingerprint) return false

    const previousCard = session.currentCard || session.previousCard || null
    const messages = [...session.messages, { role: 'assistant', content: card.summary || card.title }]
    const mode = modeOverride || (card.type === 'error' ? 'error' : (card.demoMode ? 'demo' : 'live'))
    session.messages = messages
    session.previousCard = previousCard
    session.currentCard = card
    session.mode = mode
    session.acceptedCardSeq = seq
    session.acceptedCardFingerprint = fingerprint
    this.cardActions = collectActions(card)
    this.setData({
      messages,
      visibleMessages: messages.slice(-6),
      previousCard,
      currentCard: card,
      mode,
      sourcesOpen: false,
      previousOpen: false
    })
    return true
  },

  settleRequest(seq, socketTask, failed) {
    if (!this.isCurrentRequest(seq, socketTask)) {
      closeSocketTask(socketTask)
      return
    }
    const session = ensureAssistantSession(getApp().globalData)
    const phases = settlePhases(session.phases, failed)
    session.phases = phases
    session.isBusy = false
    session.activeSocket = null
    session.streamTerminal = true
    if (failed) session.mode = 'error'
    this.setData({ phases, isBusy: false, mode: session.mode })
    closeSocketTask(socketTask)
  },

  failFromTransport(seq, socketTask, summary) {
    if (!this.isCurrentRequest(seq, socketTask)) {
      closeSocketTask(socketTask)
      return
    }
    const session = ensureAssistantSession(getApp().globalData)
    if (session.acceptedCardSeq !== seq || session.currentCard?.type !== 'error') {
      this.acceptCardForSeq(seq, createClientErrorCard('乌东向导暂不可用', summary), 'error')
    }
    this.settleRequest(seq, socketTask, true)
  },

  acceptStreamCard(seq, socketTask, card) {
    const session = ensureAssistantSession(getApp().globalData)
    const fingerprint = session.streamProtocol === 'v2' ? card?.streamFingerprint : cardFingerprint(card)
    if (!fingerprint) {
      this.failFromTransport(seq, socketTask, '本轮结果无法安全展示，请重新描述需求。')
      return
    }
    if (session.streamProtocol === 'v2' && session.receivedCardFingerprint) {
      if (session.receivedCardFingerprint !== fingerprint) {
        this.failFromTransport(seq, socketTask, '本轮收到了不一致的结果，已停止展示。')
      }
      return
    }
    if (session.streamProtocol === 'v2') session.receivedCardFingerprint = fingerprint
    this.acceptCardForSeq(seq, card)
  },

  handleAssistantEvent(rawEvent, seq, socketTask) {
    if (!this.isCurrentRequest(seq, socketTask)) {
      closeSocketTask(socketTask)
      return
    }
    const session = ensureAssistantSession(getApp().globalData)
    if (session.streamTerminal) {
      closeSocketTask(socketTask)
      return
    }
    const adapted = adaptAssistantEvent(rawEvent, session.streamProtocol)
    if (!adapted) {
      this.failFromTransport(seq, socketTask, '本轮消息无法安全读取，请重新尝试。')
      return
    }
    if (session.streamProtocol === null) {
      session.streamProtocol = adapted.protocol
      session.streamStarted = true
      if (adapted.event.threadId !== session.threadId) {
        this.failFromTransport(seq, socketTask, '本轮会话标识不一致，请重新尝试。')
      }
      return
    }
    if (!session.streamStarted) {
      this.failFromTransport(seq, socketTask, '本轮没有收到会话开始消息，请重新尝试。')
      return
    }

    const event = adapted.event
    if (event.type === 'phase_update') {
      const phases = applyPhaseUpdate(session.phases, event)
      session.phases = phases
      this.setData({ phases })
      return
    }
    if (event.type === 'card_ready') {
      this.acceptStreamCard(seq, socketTask, event.card)
      return
    }
    if (event.type === 'completed') {
      const successful = session.acceptedCardSeq === seq && session.currentCard?.type !== 'error' &&
        (session.streamProtocol === 'legacy' || Boolean(session.receivedCardFingerprint))
      if (!successful) {
        this.failFromTransport(seq, socketTask, '本轮没有收到完整结果，请重新尝试。')
        return
      }
      this.settleRequest(seq, socketTask, false)
      return
    }
    if (event.type === 'failed') {
      if (session.acceptedCardSeq !== seq || (session.streamProtocol === 'v2' && !session.receivedCardFingerprint) || session.currentCard?.type !== 'error') {
        this.failFromTransport(seq, socketTask, '本轮向导未能完成，请稍后重试。')
        return
      }
      this.settleRequest(seq, socketTask, true)
    }
  },

  ask() {
    const text = typeof this.data.input === 'string' ? this.data.input.trim() : ''
    if (!text) return
    const session = ensureAssistantSession(getApp().globalData)
    const oldSocket = session.activeSocket
    const seq = session.requestSeq + 1
    const messages = [...session.messages, { role: 'user', content: text }]
    const phases = createPhases()
    session.requestSeq = seq
    session.activeSocket = null
    session.messages = messages
    session.phases = phases
    session.isBusy = true
    session.lastUserText = text
    session.streamProtocol = null
    session.streamStarted = false
    session.streamTerminal = false
    session.receivedCardFingerprint = ''
    this.setData({
      requestSeq: seq,
      messages,
      visibleMessages: messages.slice(-6),
      phases,
      isBusy: true,
      mode: session.mode,
      sourcesOpen: false,
      previousOpen: false
    })
    closeSocketTask(oldSocket)

    let socketTask
    try {
      socketTask = wx.connectSocket({ url: getApp().globalData.aiWsUrl })
    } catch (_) {
      this.failWithoutSocket(seq, '无法建立向导连接。')
      return
    }
    session.activeSocket = socketTask
    socketTask.onOpen(() => {
      if (!this.isCurrentRequest(seq, socketTask)) {
        closeSocketTask(socketTask)
        return
      }
      socketTask.send({
        data: JSON.stringify({ thread_id: session.threadId, user_text: text }),
        fail: () => this.failFromTransport(seq, socketTask, '向导请求未能发出。')
      })
    })
    socketTask.onMessage(({ data }) => {
      if (!this.isCurrentRequest(seq, socketTask)) {
        closeSocketTask(socketTask)
        return
      }
      try {
        this.handleAssistantEvent(JSON.parse(data), seq, socketTask)
      } catch (_) {
        this.failFromTransport(seq, socketTask, '向导返回的消息无法读取。')
      }
    })
    socketTask.onError(() => this.failFromTransport(seq, socketTask, '本机 AI 服务暂未准备好。'))
    socketTask.onClose(() => {
      const current = ensureAssistantSession(getApp().globalData)
      if (this.isCurrentRequest(seq, socketTask) && current.isBusy) {
        this.failFromTransport(seq, socketTask, '向导连接已断开。')
      }
    })
  },

  failWithoutSocket(seq, summary) {
    const session = ensureAssistantSession(getApp().globalData)
    if (session.requestSeq !== seq || this.data.requestSeq !== seq) return
    this.acceptCardForSeq(seq, createClientErrorCard('乌东向导暂不可用', summary), 'error')
    const phases = settlePhases(session.phases, true)
    session.phases = phases
    session.isBusy = false
    session.activeSocket = null
    session.streamTerminal = true
    session.mode = 'error'
    this.setData({ phases, isBusy: false, mode: 'error' })
  },

  showDemo() {
    if (this.data.currentCard?.canShowDemo !== true) return
    const session = ensureAssistantSession(getApp().globalData)
    const oldSocket = session.activeSocket
    const seq = session.requestSeq + 1
    const phases = session.isBusy ? settlePhases(session.phases, true) : restorePhases(session.phases)
    session.requestSeq = seq
    session.activeSocket = null
    session.isBusy = false
    session.streamTerminal = true
    session.phases = phases
    this.setData({ requestSeq: seq, isBusy: false, phases })
    closeSocketTask(oldSocket)
    this.acceptCardForSeq(seq, adaptAssistantCard(demoAssistantCard, 'legacy'), 'demo')
  },

  retry() {
    const session = ensureAssistantSession(getApp().globalData)
    if (typeof session.lastUserText !== 'string' || !session.lastUserText) return
    this.setData({ input: session.lastUserText }, () => this.ask())
  },

  runCardAction(event) {
    const key = event.currentTarget.dataset.actionKey
    const action = this.cardActions?.[key]
    if (!action) return
    if (action.action === 'RETRY') {
      this.retry()
      return
    }
    if (action.action === 'LEGACY_OPEN_SERVICE') {
      this.openLegacyService(action.serviceId)
      return
    }
    if (action.action === 'LEGACY_JOIN_SERVICE') {
      this.joinLegacyService(action.serviceId)
      return
    }
    if (action.action === 'LEGACY_BOOK_SERVICE') this.bookLegacyService(action)
  },

  openLegacyService(serviceId) {
    const id = validServiceId(serviceId)
    if (id) wx.navigateTo({ url: `/pages/detail/detail?id=${encodeURIComponent(id)}` })
  },

  joinLegacyService(serviceId) {
    const id = validServiceId(serviceId)
    const card = this.data.currentCard
    if (!id || card?.type !== 'service_recommendation') return
    const service = (card.data.items || []).find(item => item.legacyServiceId === id)
    if (!service) return
    const app = getApp()
    const existing = app.globalData.currentItinerary || {}
    const days = cleanItineraryDays(existing.days)
    const joined = days.some(day => day.items.some(item => item.serviceId === id))
    if (!joined) {
      const entry = { time: '', title: service.title || '乌东体验', summary: service.summary || '', serviceId: id, demoData: service.demoData === true }
      if (days.length) days[0].items.push(entry)
      else days.push({ day: 1, theme: '已加入的乌东体验', items: [entry] })
    }
    app.globalData.currentItinerary = { title: existing.title || '我的乌东行程', days }
    wx.switchTab({ url: '/pages/profile/profile' })
  },

  bookLegacyService(action) {
    const serviceId = validServiceId(action.serviceId)
    if (!serviceId) return
    const booking = action.booking
    const bookingId = validBookingId(booking?.id)
    getApp().globalData.pendingBooking = bookingId ? { ...booking } : null
    wx.navigateTo({ url: `/pages/booking/booking?id=${encodeURIComponent(serviceId)}` })
  },

  addItinerary() {
    const card = this.data.currentCard
    if (card?.type !== 'itinerary') return
    const days = cleanItineraryDays(card.data.days)
    if (!days.length) return
    getApp().globalData.currentItinerary = { title: card.title, days }
    wx.switchTab({ url: '/pages/profile/profile' })
  },

  change(event) {
    this.setData({ input: event.detail.value })
  },

  quick(event) {
    const input = event.currentTarget.dataset.text || ''
    this.setData({ input }, () => this.ask())
  },

  toggleSources() {
    this.setData({ sourcesOpen: !this.data.sourcesOpen })
  },

  togglePrevious() {
    this.setData({ previousOpen: !this.data.previousOpen })
  }
})
