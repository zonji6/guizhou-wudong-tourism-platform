const { services, normalizeService } = require('../../utils/demo')
const { request } = require('../../utils/api')

const STATUS_LABELS = {
  PENDING_CONFIRMATION: '待你确认',
  CONFIRMED: '已确认',
  PROCESSING: '服务方处理中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

function stringId(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function toOrder(item, fallbackDemoData) {
  const people = Number(item?.peopleCount ?? item?.people ?? 0)
  const status = typeof item?.status === 'string' ? item.status : ''
  const demoData = typeof item?.demoData === 'boolean'
    ? item.demoData
    : (typeof fallbackDemoData === 'boolean' ? fallbackDemoData : true)
  return {
    id: stringId(item?.id),
    serviceId: stringId(item?.serviceId),
    name: item?.serviceName || item?.name || '乌东体验',
    date: item?.travelDate || item?.date || '',
    people: Number.isFinite(people) ? people : 0,
    contact: item?.contactName || item?.contact || '',
    phone: item?.contactPhone || item?.phone || '',
    status,
    statusLabel: STATUS_LABELS[status] || '等待服务方处理',
    canConfirm: status === 'PENDING_CONFIRMATION',
    demoData
  }
}

Page({
  data: {
    item: null,
    date: '',
    people: 2,
    contact: '',
    phone: '',
    pending: null,
    pendingLoading: false,
    pendingLoadError: '',
    actionError: '',
    done: false,
    imageFailed: false,
    handoffMode: false,
    submitting: false,
    confirming: false
  },

  onLoad(options) {
    const app = getApp()
    const incoming = app.globalData.pendingBooking
    const handoff = incoming && typeof incoming === 'object' ? { ...incoming } : null
    app.globalData.pendingBooking = null

    const requestedServiceId = stringId(options.id)
    const handoffServiceId = stringId(handoff?.serviceId)
    const serviceId = handoffServiceId || requestedServiceId
    this.requestedServiceId = serviceId
    this.loadService(serviceId)

    const lockedBookingId = stringId(handoff?.id)
    if (!lockedBookingId) return

    this.lockedBookingId = lockedBookingId
    this.setData({
      handoffMode: true,
      pending: null,
      pendingLoading: true,
      pendingLoadError: '',
      actionError: ''
    })
    request(`/api/bookings/${encodeURIComponent(lockedBookingId)}`)
      .then(fullBooking => {
        const responseId = stringId(fullBooking?.id)
        const responseServiceId = stringId(fullBooking?.serviceId)
        const expectedServiceId = handoffServiceId || requestedServiceId
        if (responseId !== lockedBookingId) throw new Error('booking_id_mismatch')
        if (!responseServiceId || (expectedServiceId && responseServiceId !== expectedServiceId)) {
          throw new Error('booking_service_mismatch')
        }
        const pending = toOrder(fullBooking, handoff.demoData)
        this.requestedServiceId = responseServiceId
        this.setData({
          pending,
          date: pending.date,
          people: pending.people,
          contact: pending.contact,
          phone: pending.phone
        })
        this.loadService(responseServiceId)
      })
      .catch(() => {
        this.setData({ pendingLoadError: '无法读取这笔待确认预约，请返回后重试。' })
      })
      .finally(() => {
        this.setData({ pendingLoading: false })
      })
  },

  loadService(serviceId) {
    if (!serviceId) {
      this.setService(null)
      return
    }
    const fallback = services.find(item => String(item.id) === serviceId) || null
    this.setService(fallback)
    request(`/api/services/${encodeURIComponent(serviceId)}`)
      .then(item => {
        if (this.requestedServiceId !== serviceId) return
        const normalized = normalizeService(item)
        if (String(normalized.id) !== serviceId) return
        this.setService(normalized)
      })
      .catch(() => {})
  },

  setService(item) {
    const previous = this.data.item
    const resourceChanged = String(previous?.id || '') !== String(item?.id || '') ||
      String(previous?.image || '') !== String(item?.image || '')
    this.setData({
      item,
      imageFailed: resourceChanged ? false : this.data.imageFailed
    })
  },

  dateChange(event) {
    this.setData({ date: event.detail.value })
  },

  peopleChange(event) {
    this.setData({ people: event.detail.value })
  },

  contactChange(event) {
    this.setData({ contact: event.detail.value })
  },

  phoneChange(event) {
    this.setData({ phone: event.detail.value })
  },

  imageError() {
    this.setData({ imageFailed: true })
  },

  submit() {
    if (this.submittingRequest || this.data.submitting || this.confirmingRequest || this.data.confirming || this.data.pending || this.data.handoffMode) return
    const serviceId = stringId(this.data.item?.id)
    const contact = typeof this.data.contact === 'string' ? this.data.contact.trim() : ''
    const phone = typeof this.data.phone === 'string' ? this.data.phone.trim() : ''
    const people = Number(this.data.people)
    const complete = serviceId && this.data.date && Number.isInteger(people) && people > 0 && contact && phone
    if (!complete) {
      this.setData({ actionError: '请完整填写日期、人数、联系人和电话。' })
      return
    }

    this.submittingRequest = true
    this.setData({ actionError: '', submitting: true })
    request('/api/bookings', 'POST', {
      serviceId,
      travelDate: this.data.date,
      peopleCount: people,
      contactName: contact,
      contactPhone: phone,
      note: '小程序预约'
    })
      .then(item => {
        const pending = toOrder(item, this.data.item.demoData)
        if (!pending.id || pending.serviceId !== serviceId || !pending.canConfirm) {
          throw new Error('created_booking_mismatch')
        }
        this.lockedBookingId = pending.id
        this.setData({
          pending,
          date: pending.date,
          people: pending.people,
          contact: pending.contact,
          phone: pending.phone
        })
      })
      .catch(() => {
        this.setData({ actionError: '暂时无法创建预约，请稍后重试。' })
      })
      .finally(() => {
        this.submittingRequest = false
        this.setData({ submitting: false })
      })
  },

  confirm() {
    if (this.confirmingRequest || this.data.confirming || this.submittingRequest || this.data.submitting || this.data.done) return
    const pendingId = stringId(this.data.pending?.id)
    const lockedBookingId = stringId(this.lockedBookingId)
    if (!pendingId || pendingId !== lockedBookingId || this.data.pending?.canConfirm !== true) return

    this.confirmingRequest = true
    this.setData({ actionError: '', confirming: true })
    request(`/api/bookings/${encodeURIComponent(lockedBookingId)}/confirm`, 'POST')
      .then(item => {
        const order = toOrder(item, this.data.pending.demoData)
        if (order.id !== lockedBookingId || order.status !== 'CONFIRMED') {
          throw new Error('confirmed_booking_mismatch')
        }
        const app = getApp()
        const orders = Array.isArray(app.globalData.orders) ? app.globalData.orders : []
        app.globalData.orders = [order, ...orders.filter(existing => String(existing.id) !== lockedBookingId)]
        this.setData({ pending: order, done: true })
      })
      .catch(() => {
        this.setData({ actionError: '暂时无法确认预约，请稍后重试。' })
      })
      .finally(() => {
        this.confirmingRequest = false
        this.setData({ confirming: false })
      })
  },

  orders() {
    wx.navigateTo({ url: '/pages/orders/orders' })
  }
})
