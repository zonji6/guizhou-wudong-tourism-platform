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

function nonEmptyText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function bookingResponseToOrder(item, expected, fallbackDemoData) {
  if (!item || typeof item !== 'object' || Array.isArray(item)) return null

  const id = stringId(item.id)
  const serviceId = stringId(item.serviceId)
  const serviceName = nonEmptyText(item.serviceName)
  const date = nonEmptyText(item.travelDate)
  const contact = nonEmptyText(item.contactName)
  const phone = nonEmptyText(item.contactPhone)
  const people = item.peopleCount
  const status = stringId(item.status)
  const validStatus = Object.prototype.hasOwnProperty.call(STATUS_LABELS, status)
  const expectedBookingId = stringId(expected?.bookingId)
  const expectedServiceId = stringId(expected?.serviceId)
  const expectedStatus = stringId(expected?.status)

  if (!id || !serviceId || !serviceName || !date || !contact || !phone) return null
  if (!Number.isInteger(people) || people <= 0 || !validStatus) return null
  if (expectedBookingId && id !== expectedBookingId) return null
  if (expectedServiceId && serviceId !== expectedServiceId) return null
  if (expectedStatus && status !== expectedStatus) return null

  const demoData = typeof item?.demoData === 'boolean'
    ? item.demoData
    : (typeof fallbackDemoData === 'boolean' ? fallbackDemoData : true)
  return {
    id,
    serviceId,
    name: serviceName,
    date,
    people,
    contact,
    phone,
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
    this.lockedServiceId = serviceId
    this.setData({
      handoffMode: true,
      pending: null,
      pendingLoading: true,
      pendingLoadError: '',
      actionError: ''
    })
    if (!this.lockedServiceId) {
      this.setData({
        pendingLoading: false,
        pendingLoadError: '这笔待确认预约缺少服务信息，请返回后重试。'
      })
      return
    }
    request(`/api/bookings/${encodeURIComponent(lockedBookingId)}`)
      .then(fullBooking => {
        const pending = bookingResponseToOrder(fullBooking, {
          bookingId: this.lockedBookingId,
          serviceId: this.lockedServiceId,
          status: 'PENDING_CONFIRMATION'
        }, handoff.demoData)
        if (!pending) throw new Error('invalid_pending_booking')

        this.requestedServiceId = pending.serviceId
        this.setData({
          pending,
          date: pending.date,
          people: pending.people,
          contact: pending.contact,
          phone: pending.phone
        })
        this.loadService(pending.serviceId)
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
        const pending = bookingResponseToOrder(item, {
          serviceId,
          status: 'PENDING_CONFIRMATION'
        }, this.data.item.demoData)
        if (!pending) throw new Error('invalid_created_booking')

        this.lockedBookingId = pending.id
        this.lockedServiceId = pending.serviceId
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
    const pendingServiceId = stringId(this.data.pending?.serviceId)
    const lockedBookingId = stringId(this.lockedBookingId)
    const lockedServiceId = stringId(this.lockedServiceId)
    if (!pendingId || pendingId !== lockedBookingId || !pendingServiceId || pendingServiceId !== lockedServiceId || this.data.pending?.canConfirm !== true) {
      this.setData({ actionError: '预约信息不完整，请返回后重试。' })
      return
    }

    this.confirmingRequest = true
    this.setData({ actionError: '', confirming: true })
    request(`/api/bookings/${encodeURIComponent(lockedBookingId)}/confirm`, 'POST')
      .then(item => {
        const order = bookingResponseToOrder(item, {
          bookingId: lockedBookingId,
          serviceId: lockedServiceId,
          status: 'CONFIRMED'
        }, this.data.pending.demoData)
        if (!order) throw new Error('invalid_confirmed_booking')

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
