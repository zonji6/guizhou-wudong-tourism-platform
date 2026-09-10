const { request } = require('../../utils/api')
const { authState, randomUuid, userOptions } = require('../../utils/auth')

const ORDER_PATHS = { product: '/api/product-orders', food: '/api/food-orders', stay: '/api/stay-bookings' }

Page({
  data: { selection: null, loggedIn: false, quantity: 1, visitDate: '', visitTime: '', checkInDate: '', checkOutDate: '', roomCount: 1, peopleCount: 1, contactName: '', contactPhone: '', note: '', quote: null, oldTotal: '', needsReconfirm: false, loading: false, submitting: false, attemptLocked: false, error: '', receipt: null },
  onLoad() {
    const selection = getApp().globalData.checkoutSelection
    getApp().globalData.checkoutSelection = null
    this.setData({ selection: selection || null, loggedIn: Boolean(authState().account) })
  },
  change(event) {
    if (this._attempt || this._submitting) return
    const field = event.currentTarget.dataset.field
    this.setData({ [field]: event.detail.value, quote: null, oldTotal: '', needsReconfirm: false, receipt: null, error: '' })
  },
  quoteBody() {
    const selection = this.data.selection
    if (selection.kind === 'product') return { productId: selection.resource.id, quantity: Number(this.data.quantity), pickupPoint: selection.resource.pickupPoint }
    if (selection.kind === 'food') return { merchantId: selection.merchant.id, items: selection.items.map(item => ({ foodItemId: item.foodItemId, quantity: item.quantity })), visitAt: `${this.data.visitDate}T${this.data.visitTime}:00`, peopleCount: Number(this.data.peopleCount) }
    return { roomTypeId: selection.resource.id, checkInDate: this.data.checkInDate, checkOutDate: this.data.checkOutDate, roomCount: Number(this.data.roomCount), peopleCount: Number(this.data.peopleCount) }
  },
  validate() {
    const selection = this.data.selection
    if (!authState().account) return '请先到“我的”登录平台账号。'
    if (selection.kind === 'product' && (!Number.isInteger(Number(this.data.quantity)) || Number(this.data.quantity) < 1)) return '商品数量必须是正整数。'
    if (selection.kind === 'food') {
      if (!selection.items?.length || selection.items.some(item => !Number.isInteger(Number(item.quantity)) || Number(item.quantity) < 1)) return '每种餐食份数必须是正整数。'
      if (!this.data.visitDate || !this.data.visitTime || !Number.isInteger(Number(this.data.peopleCount)) || Number(this.data.peopleCount) < 1) return '请填写到店日期、时间和人数。'
    }
    if (selection.kind === 'stay') {
      if (!this.data.checkInDate || !this.data.checkOutDate || this.data.checkOutDate <= this.data.checkInDate) return '离店日期必须晚于入住日期。'
      if (![this.data.roomCount, this.data.peopleCount].every(value => Number.isInteger(Number(value)) && Number(value) > 0)) return '房间数和入住人数必须是正整数。'
      const capacity = selection.resource.maxGuestsPerRoom * Number(this.data.roomCount)
      if (Number(this.data.peopleCount) > capacity) return `当前选择最多容纳 ${capacity} 人。`
    }
    return ''
  },
  getQuote() {
    if (this._attempt || this._submitting || this.data.loading) return
    const error = this.validate()
    if (error) { this.setData({ error }); return }
    const kind = this.data.selection.kind
    this.setData({ loading: true, error: '' })
    request(`/api/order-quotes/${kind}`, userOptions({ method: 'POST', data: this.quoteBody() }))
      .then(quote => this.setData({ quote, oldTotal: quote.totalAmount, needsReconfirm: false }))
      .catch(reason => this.setData({ error: reason?.message || '暂时无法核价。' }))
      .finally(() => this.setData({ loading: false }))
  },
  async submit() {
    if (!this.data.quote || this.data.needsReconfirm || this._submitting) return
    const contactName = this.data.contactName.trim()
    const contactPhone = this.data.contactPhone.trim()
    if (!contactName || !contactPhone) { this.setData({ error: '请填写联系人和联系电话。' }); return }
    if (!/^(?=.*\d)[0-9 +()\-]+$/.test(contactPhone)) { this.setData({ error: '联系电话只能包含数字、空格、+、- 和括号。' }); return }
    const kind = this.data.selection.kind
    const data = { ...this.quoteBody(), contactName, contactPhone, note: this.data.note.trim() || null, sourceThreadId: null, expectedQuoteFingerprint: this.data.quote.quoteFingerprint }
    this._submitting = true
    this.setData({ submitting: true, attemptLocked: true, error: '' })
    try {
      if (!this._attempt) this._attempt = { requestKey: await randomUuid(), kind, data }
    } catch (reason) {
      this._submitting = false
      this.setData({ submitting: false, attemptLocked: false, error: reason.message })
      return
    }
    const attempt = this._attempt
    request(ORDER_PATHS[attempt.kind], userOptions({ method: 'POST', idempotencyKey: attempt.requestKey, data: attempt.data }))
      .then(receipt => this.setData({ receipt }))
      .catch(reason => {
        if (reason?.code === 'QUOTE_CHANGED' && reason.details?.currentQuote) {
          this._attempt = null
          this.setData({ oldTotal: this.data.quote?.totalAmount || '', quote: reason.details.currentQuote, needsReconfirm: true, attemptLocked: false, error: '报价已变化，请核对新金额后再次确认。' })
        } else this.setData({ error: reason?.message || '提交失败；再次点击会沿用同一请求键安全重试。' })
      }).finally(() => { this._submitting = false; this.setData({ submitting: false }) })
  },
  acceptChangedQuote() { this.setData({ needsReconfirm: false, error: '' }) },
  input(event) { if (!this._attempt && !this._submitting) this.setData({ [event.currentTarget.dataset.field]: event.detail.value }) },
  toProfile() { wx.switchTab({ url: '/pages/profile/profile' }) },
  toOrders() { wx.navigateTo({ url: '/pages/orders/orders' }) }
})
