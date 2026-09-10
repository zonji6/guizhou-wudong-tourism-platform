const { request } = require('../../utils/api')
const { authState, randomUuid, userOptions } = require('../../utils/auth')

function title(order) { return order.productName || order.merchantName || `${order.stayPropertyName || ''} ${order.roomTypeName || ''}`.trim() || '乌东订单' }
function summary(order) {
  if (order.orderType === 'PRODUCT') return `${order.quantity} 份 · ${order.pickupPoint}`
  if (order.orderType === 'FOOD') return `${(order.items || []).map(item => `${item.resourceName || item.foodItemName}×${item.quantity}`).join('、')} · ${order.peopleCount} 人`
  return `${order.checkInDate} 至 ${order.checkOutDate} · ${order.nights} 晚 · ${order.roomCount} 间 · ${order.peopleCount} 人`
}
function clone(value) { return JSON.parse(JSON.stringify(value)) }
function segment(type) { return type === 'FOOD' ? 'food-drafts' : type === 'STAY' ? 'stay-drafts' : '' }
function operationType(type) { return type === 'FOOD' ? 'SAVE_FOOD_DRAFT' : type === 'STAY' ? 'SAVE_STAY_DRAFT' : '' }
function nullableText(value) { const text = typeof value === 'string' ? value.trim() : ''; return text || null }
function nullableInteger(value) { return value === '' || value === null || value === undefined ? null : Number(value) }

Page({
  data: {
    loggedIn: false, tab: 'orders', orders: [], drafts: [], itineraries: [], legacy: [], loading: false, error: '',
    draftEditor: null, saveStatus: '', savePaused: false, saveUnknown: false, saving: false,
    quote: null, oldQuote: null, submitStatus: '', submitting: false, submitLocked: false
  },
  onShow() {
    const loggedIn = Boolean(authState().account)
    this.setData({ loggedIn })
    if (loggedIn && !this.data.draftEditor) this.load()
  },
  onUnload() { clearTimeout(this._saveTimer) },
  changeTab(event) { this.setData({ tab: event.currentTarget.dataset.tab }) },
  async load() {
    if (this.data.draftEditor) {
      this.setData({ error: '当前草稿编辑内容不会被刷新覆盖；请先确认保存状态并关闭编辑器。' })
      return
    }
    const paths = ['/api/me/product-orders', '/api/me/food-orders', '/api/me/stay-bookings', '/api/me/food-drafts', '/api/me/stay-drafts', '/api/me/itineraries', '/api/me/legacy-records']
    this.setData({ loading: true, error: '' })
    try {
      const [products, foods, stays, foodDrafts, stayDrafts, itineraries, legacy] = await Promise.all(paths.map(path => request(path, userOptions())))
      this.setData({
        orders: [...products, ...foods, ...stays].map(order => ({ ...order, title: title(order), summary: summary(order) })),
        drafts: [...foodDrafts, ...stayDrafts], itineraries, legacy
      })
    } catch (reason) {
      this.setData({ error: reason?.message || '同账号数据暂时无法读取。' })
    } finally { this.setData({ loading: false }) }
  },
  openDraft(event) {
    if (this.data.draftEditor) {
      wx.showToast({ title: '请先关闭当前草稿', icon: 'none' })
      return
    }
    const draft = this.data.drafts.find(item => item.id === event.currentTarget.dataset.id && item.state === 'DRAFT')
    if (!draft) return
    clearTimeout(this._saveTimer)
    this._editRevision = 0
    this._savedRevision = 0
    this._saving = false
    this._saveQueued = false
    this._pendingSave = null
    this._saveErrorCode = ''
    this._submitAttempt = null
    this.setData({
      draftEditor: { id: draft.id, draftType: draft.draftType, version: draft.version, content: clone(draft.content) },
      saveStatus: '尚未修改', savePaused: false, saveUnknown: false, saving: false,
      quote: null, oldQuote: null, submitStatus: '', submitting: false, submitLocked: false, error: ''
    })
  },
  closeDraft() {
    if (this._saving) {
      wx.showToast({ title: '保存仍在处理中', icon: 'none' })
      return
    }
    if (this._submitAttempt || this._submitting) {
      wx.showToast({ title: '请先核对提交结果', icon: 'none' })
      return
    }
    const dirty = this._editRevision !== this._savedRevision || this.data.saving || this.data.savePaused
    if (!dirty) { this.finishCloseDraft(); return }
    wx.showModal({
      title: '仍有未确认保存的内容',
      content: '关闭不会撤销已发请求，页面内容也不会保存到本地。建议先留在本页核对。',
      confirmText: '仍要关闭', cancelText: '留在本页',
      success: result => { if (result.confirm) this.finishCloseDraft() }
    })
  },
  finishCloseDraft() {
    clearTimeout(this._saveTimer)
    this._saving = false
    this._pendingSave = null
    this._submitAttempt = null
    this.setData({ draftEditor: null, quote: null, oldQuote: null, submitStatus: '', submitLocked: false, saveStatus: '' })
  },
  editField(event) {
    if (this._submitting || this._submitAttempt) return
    const field = event.currentTarget.dataset.field
    this.setData({ [`draftEditor.content.${field}`]: event.detail.value })
    this.scheduleSave()
  },
  editItemQuantity(event) {
    if (this._submitting || this._submitAttempt) return
    const index = Number(event.currentTarget.dataset.index)
    this.setData({ [`draftEditor.content.items[${index}].quantity`]: event.detail.value })
    this.scheduleSave()
  },
  normalizedContent() {
    const editor = this.data.draftEditor
    const content = editor.content
    if (editor.draftType === 'FOOD') {
      return {
        merchantId: content.merchantId,
        items: content.items.map(item => ({ foodItemId: item.foodItemId, quantity: Number(item.quantity) })),
        visitAt: nullableText(content.visitAt)?.replace(/^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})$/, '$1:00') || null,
        peopleCount: nullableInteger(content.peopleCount), contactName: nullableText(content.contactName),
        contactPhone: nullableText(content.contactPhone), note: nullableText(content.note)
      }
    }
    return {
      roomTypeId: content.roomTypeId, checkInDate: nullableText(content.checkInDate), checkOutDate: nullableText(content.checkOutDate),
      roomCount: nullableInteger(content.roomCount), peopleCount: nullableInteger(content.peopleCount),
      contactName: nullableText(content.contactName), contactPhone: nullableText(content.contactPhone), note: nullableText(content.note)
    }
  },
  scheduleSave() {
    if (!this.data.draftEditor) return
    this._editRevision += 1
    this._submitAttempt = null
    clearTimeout(this._saveTimer)
    this._saveQueued = false
    this.setData({
      saveStatus: this.data.savePaused ? '保存已暂停，页面内容仍保留。' : '等待停止编辑…',
      quote: null, oldQuote: null, submitStatus: '内容已修改，请等待最新版本保存后重新核价。'
    })
    if (!this.data.savePaused) this._saveTimer = setTimeout(() => { this._saveTimer = null; this.saveNow() }, 800)
  },
  async saveNow() {
    if (!this.data.draftEditor || this.data.savePaused) return
    if (this._savedRevision === this._editRevision) return
    if (this._saving) { this._saveQueued = true; return }
    this._saving = true
    this.setData({ saving: true, saveStatus: '正在保存…' })
    const editor = this.data.draftEditor
    const content = this.normalizedContent()
    const revision = this._editRevision
    const expectedVersion = editor.version
    let requestKey
    try { requestKey = await randomUuid() } catch (reason) {
      this._saving = false
      this.setData({ saving: false, savePaused: true, saveStatus: reason.message })
      return
    }
    this._pendingSave = { requestKey, revision, original: { targetId: editor.id, expectedVersion, content } }
    try {
      const receipt = await request(`/api/me/${segment(editor.draftType)}/${encodeURIComponent(editor.id)}`, userOptions({
        method: 'PUT', idempotencyKey: requestKey, data: { expectedVersion, content }
      }))
      const version = receipt.committedVersion
      this._savedRevision = revision
      this._pendingSave = null
      if (!Number.isInteger(version) || receipt.resource?.version !== version) {
        clearTimeout(this._saveTimer)
        this._saveTimer = null
        this._saveQueued = false
        this._saveErrorCode = 'VERSION_CONFLICT'
        this.setData({ 'draftEditor.version': Number.isInteger(version) ? version : editor.version, savePaused: true, saveUnknown: false, saveStatus: '本次保存已提交，但服务端已有更高版本；已暂停，不能用旧页面内容覆盖跨端修改。' })
        return
      }
      this.setData({ 'draftEditor.version': version, saveStatus: revision === this._editRevision ? `已保存版本 ${version}` : '还有新修改等待保存…' })
    } catch (reason) {
      this._saveErrorCode = reason?.code || ''
      const unknown = reason?.statusCode === 0 || ['WRITE_RESULT_UNAVAILABLE', 'INVALID_RESPONSE', 'CONTRACT_INCOMPATIBLE'].includes(reason?.code)
      this.setData({ savePaused: true, saveUnknown: unknown, saveStatus: unknown ? '保存结果未知，已暂停；请主动核对后再继续。' : `${reason?.message || '保存失败。'} 已暂停，未覆盖页面内容。` })
    } finally {
      this._saving = false
      this.setData({ saving: false })
      if (this._saveQueued && !this.data.savePaused) {
        this._saveQueued = false
        this._saveTimer = setTimeout(() => this.saveNow(), 0)
      }
    }
  },
  async continueSave() {
    if (!this.data.draftEditor || this._saving) return
    this._saveQueued = false
    if (!this.data.saveUnknown) {
      if (['VERSION_CONFLICT', 'DRAFT_ALREADY_SUBMITTED'].includes(this._saveErrorCode)) {
        this.setData({ saveStatus: '服务端版本或状态已变化，不能盲目重试；请保留内容并关闭后手动读取最新版。' })
        return
      }
      this.setData({ savePaused: false, saveStatus: '正在用新请求键重试当前内容…' })
      this._saveTimer = setTimeout(() => this.saveNow(), 0)
      return
    }
    if (!this._pendingSave) return
    const pending = this._pendingSave
    this._saving = true
    this.setData({ saving: true, saveStatus: '正在核对原保存结果…' })
    try {
      const result = await request(`/api/me/write-results/${operationType(this.data.draftEditor.draftType)}/${encodeURIComponent(pending.requestKey)}/reconcile`, userOptions({ method: 'POST', data: pending.original }))
      if (result.outcome === 'SUCCEEDED' && result.receipt?.resource?.version === result.receipt?.committedVersion) {
        const version = result.receipt.committedVersion
        this._savedRevision = pending.revision
        this._pendingSave = null
        this.setData({ 'draftEditor.version': version, savePaused: false, saveUnknown: false, saveStatus: '原保存已确认成功。' })
        if (this._editRevision !== pending.revision) this._saveTimer = setTimeout(() => this.saveNow(), 0)
      } else if (result.outcome === 'NOT_APPLIED' && result.current?.version === pending.original.expectedVersion && result.current?.editable === true) {
        this._pendingSave = null
        this.setData({ savePaused: false, saveUnknown: false, saveStatus: '原保存已确认未执行，正在用新请求键继续。' })
        this._saveTimer = setTimeout(() => this.saveNow(), 0)
      } else {
        this.setData({ saveStatus: result.outcome === 'SUCCEEDED' ? '原保存已成功，但服务端已有更高版本；请保留内容并手动核对。' : '仍不能安全继续，请保留页面内容并手动刷新核对。' })
      }
    } catch (reason) {
      this.setData({ saveStatus: reason?.message || '核对结果仍未知，继续保持暂停。' })
    } finally { this._saving = false; this.setData({ saving: false }) }
  },
  quoteSelection() {
    const content = this.normalizedContent()
    if (this.data.draftEditor.draftType === 'FOOD') return { merchantId: content.merchantId, items: content.items, visitAt: content.visitAt, peopleCount: content.peopleCount }
    return { roomTypeId: content.roomTypeId, checkInDate: content.checkInDate, checkOutDate: content.checkOutDate, roomCount: content.roomCount, peopleCount: content.peopleCount }
  },
  async quoteDraft() {
    if (!this.data.draftEditor) return
    if (this._submitAttempt) {
      this.setData({ submitStatus: '提交结果尚待核对；请先用同一请求键重试，不能重新核价。' })
      return
    }
    if (this.data.saving || this._saveQueued || this.data.savePaused || this._savedRevision !== this._editRevision) {
      this.setData({ submitStatus: '只有当前页面最新改动已由服务端确认保存后，才能核价并提交。' })
      return
    }
    if (this._submitting) return
    this._submitting = true
    this.setData({ submitting: true, submitStatus: '正在按已保存草稿核价…' })
    try {
      const kind = this.data.draftEditor.draftType.toLowerCase()
      const quote = await request(`/api/order-quotes/${kind}`, userOptions({ method: 'POST', data: this.quoteSelection() }))
      this._submitAttempt = null
      this.setData({ quote, oldQuote: null, submitLocked: false, submitStatus: '核价完成；请核对模拟金额后再次确认提交。' })
    } catch (reason) {
      this.setData({ submitStatus: reason?.message || '草稿核价失败。' })
    } finally { this._submitting = false; this.setData({ submitting: false }) }
  },
  async submitDraft() {
    if (!this.data.draftEditor || !this.data.quote || this._submitting) return
    if (this.data.saving || this._saveQueued || this.data.savePaused || this._savedRevision !== this._editRevision) {
      this.setData({ submitStatus: '最新改动尚未确认保存，不能提交。' })
      return
    }
    this._submitting = true
    this.setData({ submitting: true, submitLocked: true, submitStatus: '正在准备草稿提交…' })
    try {
      if (!this._submitAttempt) this._submitAttempt = { requestKey: await randomUuid(), expectedVersion: this.data.draftEditor.version, expectedQuoteFingerprint: this.data.quote.quoteFingerprint }
    } catch (reason) {
      this._submitting = false
      this.setData({ submitting: false, submitLocked: false, submitStatus: reason.message })
      return
    }
    const attempt = { ...this._submitAttempt }
    const editor = this.data.draftEditor
    this.setData({ submitting: true, submitStatus: '正在提交已保存草稿…' })
    try {
      const receipt = await request(`/api/me/${segment(editor.draftType)}/${encodeURIComponent(editor.id)}/submit`, userOptions({
        method: 'POST', idempotencyKey: attempt.requestKey,
        data: { expectedVersion: attempt.expectedVersion, expectedQuoteFingerprint: attempt.expectedQuoteFingerprint }
      }))
      const orderId = receipt.resource?.id || receipt.resourceId
      this.finishCloseDraft()
      await this.load()
      wx.showModal({ title: '草稿提交成功', content: orderId ? `正式订单 ${orderId}` : '服务端已返回成功回执。', showCancel: false })
    } catch (reason) {
      if (reason?.code === 'QUOTE_CHANGED' && reason.details?.currentQuote) {
        this._submitAttempt = null
        this.setData({ oldQuote: this.data.quote, quote: reason.details.currentQuote, submitLocked: false, submitStatus: '报价已变化：旧请求键已终结。请比较新旧金额，再明确确认用新请求键提交。' })
      } else {
        this.setData({ submitStatus: `${reason?.message || '提交结果暂未确认。'} 重试将沿用同一请求键与原请求内容。` })
      }
    } finally { this._submitting = false; this.setData({ submitting: false }) }
  },
  toProfile() { wx.switchTab({ url: '/pages/profile/profile' }) }
})
