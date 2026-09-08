const {
  createPhases,
  restorePhases,
  prepareCard,
  cardFingerprint,
  applyEvent,
  settlePhases,
  ensureAssistantSession,
  validServiceId,
  validBookingId
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
      .filter(item => validServiceId(item?.serviceId || item?.actionServiceId))
      .map(item => ({
        time: typeof item.time === 'string' ? item.time : '',
        title: typeof item.title === 'string' ? item.title : '乌东体验',
        summary: typeof item.summary === 'string' ? item.summary : '',
        serviceId: validServiceId(item.serviceId || item.actionServiceId),
        demoData: item.demoData === true || item.isDemo === true
      }))
  })).filter(day => day.items.length)
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
    session.phases = phases
    this.setData({ requestSeq, isBusy: false, phases })
    closeSocketTask(socketTask)
  },

  isCurrentRequest(seq, socketTask) {
    const session = ensureAssistantSession(getApp().globalData)
    return session.requestSeq === seq &&
      this.data.requestSeq === seq &&
      session.activeSocket === socketTask
  },

  acceptCardForSeq(seq, rawCard, modeOverride) {
    const session = ensureAssistantSession(getApp().globalData)
    if (session.requestSeq !== seq || this.data.requestSeq !== seq) return false
    const fingerprint = cardFingerprint(rawCard)
    if (session.acceptedCardSeq === seq && session.acceptedCardFingerprint === fingerprint) return false

    const card = prepareCard(rawCard)
    const previousCard = session.currentCard || session.previousCard || null
    const messages = [
      ...session.messages,
      { role: 'assistant', content: card.summary || card.title }
    ]
    const mode = modeOverride || (card.type === 'error' ? 'error' : (card.data.demoMode === true ? 'demo' : 'live'))

    session.messages = messages
    session.previousCard = previousCard
    session.currentCard = card
    session.mode = mode
    session.acceptedCardSeq = seq
    session.acceptedCardFingerprint = fingerprint
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

  acceptLiveCard(seq, socketTask, rawCard) {
    if (!this.isCurrentRequest(seq, socketTask)) {
      closeSocketTask(socketTask)
      return
    }
    this.acceptCardForSeq(seq, rawCard)
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
    const hasBusinessError = session.acceptedCardSeq === seq && session.currentCard?.type === 'error'
    if (!hasBusinessError) {
      this.acceptCardForSeq(seq, {
        type: 'error',
        title: '乌东向导暂不可用',
        summary,
        data: { demoAvailable: true },
        sources: []
      }, 'error')
    }
    this.settleRequest(seq, socketTask, true)
  },

  handleAssistantEvent(event, seq, socketTask) {
    if (!this.isCurrentRequest(seq, socketTask)) {
      closeSocketTask(socketTask)
      return
    }
    if (!event || typeof event.type !== 'string') return

    if (event.type === 'node_started' || event.type === 'tool_finished') {
      const phases = applyEvent(this.data.phases, event)
      ensureAssistantSession(getApp().globalData).phases = phases
      this.setData({ phases })
      return
    }
    if (event.type === 'card_ready') {
      this.acceptLiveCard(seq, socketTask, event.card)
      return
    }
    if (event.type === 'completed') {
      const session = ensureAssistantSession(getApp().globalData)
      const hasSuccessfulCard = session.acceptedCardSeq === seq && session.currentCard?.type !== 'error'
      if (!hasSuccessfulCard) {
        if (session.acceptedCardSeq !== seq) {
          this.acceptCardForSeq(seq, {
            type: 'error',
            title: '本次规划未返回结果',
            summary: '向导本轮没有可展示的结果，请稍后重试。',
            data: {},
            sources: []
          }, 'error')
        }
        this.settleRequest(seq, socketTask, true)
        return
      }
      this.settleRequest(seq, socketTask, false)
      return
    }
    if (event.type === 'failed') {
      const session = ensureAssistantSession(getApp().globalData)
      if (session.acceptedCardSeq !== seq) {
        this.acceptCardForSeq(seq, {
          type: 'error',
          title: '本次规划未完成',
          summary: '向导本轮未返回可展示的结果，请稍后重试。',
          data: {},
          sources: []
        }, 'error')
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
      let event
      try {
        event = JSON.parse(data)
      } catch (_) {
        this.failFromTransport(seq, socketTask, '向导返回的消息无法读取。')
        return
      }
      this.handleAssistantEvent(event, seq, socketTask)
    })
    socketTask.onError(() => {
      this.failFromTransport(seq, socketTask, '本机 AI 服务暂未准备好。')
    })
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
    this.acceptCardForSeq(seq, {
      type: 'error',
      title: '乌东向导暂不可用',
      summary,
      data: { demoAvailable: true },
      sources: []
    }, 'error')
    const phases = settlePhases(session.phases, true)
    session.phases = phases
    session.isBusy = false
    session.activeSocket = null
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
    session.phases = phases
    this.setData({ requestSeq: seq, isBusy: false, phases })
    closeSocketTask(oldSocket)
    this.acceptCardForSeq(seq, demoAssistantCard, 'demo')
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
  },

  openService(event) {
    const serviceId = validServiceId(event.currentTarget.dataset.serviceId)
    if (!serviceId) return
    wx.navigateTo({ url: `/pages/detail/detail?id=${encodeURIComponent(serviceId)}` })
  },

  addItinerary() {
    const card = this.data.currentCard
    if (card?.type !== 'itinerary') return
    const days = cleanItineraryDays(card.data.days)
    if (!days.length) return
    getApp().globalData.currentItinerary = { title: card.title, days }
    wx.switchTab({ url: '/pages/profile/profile' })
  },

  joinService(event) {
    const serviceId = validServiceId(event.currentTarget.dataset.serviceId)
    const card = this.data.currentCard
    if (!serviceId || card?.type !== 'service_recommendation') return
    const service = (card.data.services || []).find(item => item.actionServiceId === serviceId)
    if (!service) return

    const app = getApp()
    const existing = app.globalData.currentItinerary || {}
    const days = cleanItineraryDays(existing.days)
    const alreadyJoined = days.some(day => day.items.some(item => item.serviceId === serviceId))
    if (!alreadyJoined) {
      const entry = {
        time: '',
        title: service.title || '乌东体验',
        summary: service.summary || '',
        serviceId,
        demoData: service.isDemo === true
      }
      if (days.length) days[0].items.push(entry)
      else days.push({ day: 1, theme: '已加入的乌东体验', items: [entry] })
    }
    app.globalData.currentItinerary = {
      title: existing.title || '我的乌东行程',
      days
    }
    wx.switchTab({ url: '/pages/profile/profile' })
  },

  bookService(event) {
    const card = this.data.currentCard
    const booking = card?.type === 'pending_booking' ? card.data.booking : null
    const serviceId = validServiceId(booking?.actionServiceId || event.currentTarget.dataset.serviceId)
    if (!serviceId) return

    const bookingId = validBookingId(booking?.actionBookingId)
    getApp().globalData.pendingBooking = bookingId
      ? {
          id: bookingId,
          serviceId,
          serviceName: typeof booking.serviceName === 'string' ? booking.serviceName : '',
          travelDate: typeof booking.travelDate === 'string' ? booking.travelDate : '',
          peopleCount: Number(booking.peopleCount || 0),
          status: typeof booking.status === 'string' ? booking.status : '',
          demoData: booking.demoData === true
        }
      : null
    wx.navigateTo({ url: `/pages/booking/booking?id=${encodeURIComponent(serviceId)}` })
  }
})
