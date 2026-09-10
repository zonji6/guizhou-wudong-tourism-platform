const { CONTRACT_VERSION } = require('../../utils/api')
const { request } = require('../../utils/api')
const { authState, ensureAnonymousMiniSession, randomUuid, userOptions } = require('../../utils/auth')

const ASSISTANT_CONTRACT = 'assistant-card-v3-draft-r1'
const EVENT_CONTRACT = 'assistant-event-v3-draft-r1'
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const stageLabels = { UNDERSTANDING: '理解需求', RETRIEVING: '检索乌东资料', VERIFYING_KNOWLEDGE: '核对资料版本', PLANNING: '匹配体验', PREPARING_RESULT: '整理建议', PERSISTING_RESULT: '保存本轮结果' }
const stagePhases = Object.entries(stageLabels).map(([id, label]) => ({ id, label }))

function emptyConditions() {
  return { travelDate: null, visitAt: null, checkInDate: null, checkOutDate: null, roomCount: null, peopleCount: null, preferences: [], selectedTargets: [], merchantItems: null }
}

function validSession(frame) {
  return frame && frame.type === 'session_state' && frame.assistantContractVersion === ASSISTANT_CONTRACT && frame.tourismContractVersion === CONTRACT_VERSION && UUID.test(frame.threadId || '')
}

Page({
  data: {
    account: null, status: 'idle', message: '', prompt: '周末两位，想在乌东体验苗族文化和茶旅，请安排两天一夜的慢游建议。',
    busy: false, threadId: '', checkpointRevision: null, activeStage: '', card: null, dialogue: [], stageLabels, stagePhases, savingItinerary: false
  },
  onShow() { this._closing = false; this.getTabBar()?.setData({ selected: 2 }); this.setData({ account: authState().account, status: 'idle', message: '', busy: false, card: null, dialogue: [] }) },
  onHide() { this.closeSocket('page_hidden') },
  onUnload() { this.closeSocket('page_leave') },
  closeSocket(reason) { this._closing = true; this.socket?.close({ code: 1000, reason }); this.socket = null },
  async connect() {
    if (this.data.status === 'connecting' || this.data.status === 'ready') return
    const account = authState(); const userMode = Boolean(account.account)
    if (userMode && !account.accessToken) { this.setData({ status: 'error', message: '当前登录已失效，请先到“我的”重新登录。' }); return }
    this._closing = false; this._terminal = false; this.setData({ status: 'connecting', message: userMode ? '正在连接账号向导…' : '正在准备匿名预览…' })
    let anonymous
    try { if (!userMode) anonymous = await ensureAnonymousMiniSession() } catch (reason) { this.setData({ status: 'error', message: reason?.message || '匿名预览会话暂时无法准备。' }); return }
    const socket = wx.connectSocket({ url: `${getApp().globalData.aiWsBase}${userMode ? '/ws/mini/user' : '/ws/mini/anonymous'}` })
    this.socket = socket
    socket.onOpen(() => {
      const frame = { type: 'auth', authVersion: 'wudong-ws-auth-v1', contractVersion: CONTRACT_VERSION, mode: userMode ? 'USER' : 'ANONYMOUS_MINI' }
      if (userMode) frame.accessToken = account.accessToken
      else frame.anonymousCredential = anonymous.anonymousCredential
      socket.send({ data: JSON.stringify(frame) })
    })
    socket.onMessage(({ data }) => this.receive(data, socket, userMode))
    socket.onError(() => { if (this.socket === socket && !this._closing && !this._terminal) this.setData({ status: 'error', busy: false, message: '乌东向导暂未准备好。' }) })
    socket.onClose(() => { if (this.socket === socket) { this.socket = null; if (!this._closing && !this._terminal) this.setData({ status: 'error', busy: false, message: '向导连接已关闭；可手动重新连接。' }) } })
  },
  receive(raw, socket, userMode) {
    let frame; try { frame = JSON.parse(raw) } catch (_) { frame = null }
    if (frame?.type === 'auth_ok' && frame.authVersion === 'wudong-ws-auth-v1' && frame.contractVersion === CONTRACT_VERSION) {
      socket.send({ data: JSON.stringify({ type: 'session_state_request', assistantContractVersion: ASSISTANT_CONTRACT, tourismContractVersion: CONTRACT_VERSION, threadId: null, runId: null, lastEventSequence: 0 }) }); return
    }
    if (validSession(frame)) { this.setData({ threadId: frame.threadId, checkpointRevision: frame.checkpointRevision, card: frame.lastCard || null, status: 'ready', message: userMode ? '账号向导已就绪。' : '匿名向导已就绪。' }); return }
    if (frame?.type === 'auth_failed') { this._terminal = true; this.setData({ status: frame.retryable ? 'error' : 'blocked', busy: false, message: frame.message || '身份状态暂不可用。' }); return }
    if (!frame || frame.assistantContractVersion !== ASSISTANT_CONTRACT || frame.tourismContractVersion !== CONTRACT_VERSION || frame.eventVersion !== EVENT_CONTRACT || frame.threadId !== this.data.threadId) { this._terminal = true; this.setData({ status: 'blocked', busy: false, message: '收到无法确认的向导消息，已停止展示。' }); return }
    if (frame.type === 'run_started') this.setData({ busy: true, activeStage: 'UNDERSTANDING' })
    if (frame.type === 'progress') this.setData({ activeStage: frame.data?.status === 'COMPLETED' ? '' : frame.data?.stage || this.data.activeStage })
    if (frame.type === 'card_ready' && frame.data?.card?.cardVersion === '3.0') {
      const summary = frame.data.card.summary || frame.data.card.title
      this.setData({ card: frame.data.card, checkpointRevision: frame.data.checkpointRevision, dialogue: [...this.data.dialogue.slice(-3), { role: 'guide', content: summary }] })
    }
    if (['completed', 'failed', 'stopped'].includes(frame.type)) this.setData({ busy: false, activeStage: '', message: frame.type === 'completed' ? '本轮建议已整理完毕。' : '本轮生成中断，可调整需求后重试。' })
  },
  input(event) { this.setData({ prompt: event.detail.value }) },
  async sendPrompt(event) {
    const text = String(event?.currentTarget?.dataset?.text || this.data.prompt || '').trim()
    if (!text || this.data.busy || this.data.status !== 'ready' || !this.socket) return
    const clientRequestId = await randomUuid()
    this.setData({ prompt: text, card: null, busy: true, activeStage: 'UNDERSTANDING', message: '乌东向导正在读取你的需求。', dialogue: [...this.data.dialogue.slice(-3), { role: 'user', content: text }] })
    this.socket.send({ data: JSON.stringify({ type: 'generate', assistantContractVersion: ASSISTANT_CONTRACT, tourismContractVersion: CONTRACT_VERSION, clientRequestId, threadId: this.data.threadId, expectedCheckpointRevision: this.data.checkpointRevision, userText: text, pageAction: null, selectedTarget: null, baseResource: null, conditions: emptyConditions(), retrievalMode: 'KEYWORD_DEMO' }) })
  },
  async saveItinerary() {
    if (this.data.savingItinerary) return
    if (!authState().account) { this.setData({ message: '匿名预览不能保存个人行程；请先到“我的”登录后重新生成。' }); return }
    const candidate = this.data.card?.data?.candidate
    if (!candidate?.candidateRef) { this.setData({ message: '当前行程候选无法确认，请重新生成后再保存。' }); return }
    const base = candidate.baseResource
    const updating = candidate.adoptionAction === 'UPDATE' && base?.resourceId && Number.isInteger(base.resourceVersion)
    if (candidate.adoptionAction !== 'CREATE' && !updating) { this.setData({ message: '此行程候选缺少可确认的保存版本，请重新生成。' }); return }
    this.setData({ savingItinerary: true, message: '正在保存到“我的”…' })
    try {
      const requestKey = await randomUuid()
      const path = updating ? `/api/me/itineraries/${encodeURIComponent(base.resourceId)}/adoptions` : '/api/me/itineraries/adoptions'
      const data = updating ? { candidateRef: candidate.candidateRef, expectedVersion: base.resourceVersion } : { candidateRef: candidate.candidateRef }
      await request(path, userOptions({ method: 'POST', idempotencyKey: requestKey, data }))
      this.setData({ message: '行程已保存到“我的”，可在订单页继续调整。' })
    } catch (reason) {
      this.setData({ message: reason?.message || '保存行程失败。' })
    } finally { this.setData({ savingItinerary: false }) }
  },
  async saveDraftCandidate() {
    if (this.data.savingItinerary) return
    if (!authState().account) { this.setData({ message: '匿名预览不能保存食宿草稿；请先到“我的”登录后重新生成。' }); return }
    const candidate = this.data.card?.data?.candidate
    const candidateType = candidate?.candidateType
    const segment = candidateType === 'FOOD_DRAFT' ? 'food-drafts' : candidateType === 'STAY_DRAFT' ? 'stay-drafts' : ''
    if (!segment || !candidate?.candidateRef) { this.setData({ message: '当前食宿候选无法确认，请重新生成后再保存。' }); return }
    const base = candidate.baseResource
    const updating = candidate.adoptionAction === 'UPDATE' && base?.resourceId && Number.isInteger(base.resourceVersion)
    if (candidate.adoptionAction !== 'CREATE' && !updating) { this.setData({ message: '此草稿候选缺少可确认的保存版本，请重新生成。' }); return }
    this.setData({ savingItinerary: true, message: '正在保存食宿草稿…' })
    try {
      const requestKey = await randomUuid()
      const path = updating ? `/api/me/${segment}/${encodeURIComponent(base.resourceId)}/adoptions` : `/api/me/${segment}/adoptions`
      const data = updating ? { candidateRef: candidate.candidateRef, expectedVersion: base.resourceVersion } : { candidateRef: candidate.candidateRef }
      await request(path, userOptions({ method: 'POST', idempotencyKey: requestKey, data }))
      this.setData({ message: '方案已保存为草稿，请到“我的”补齐联系人、核价后再正式提交。' })
    } catch (reason) {
      this.setData({ message: reason?.message || '保存草稿失败。' })
    } finally { this.setData({ savingItinerary: false }) }
  },
  retry() { this.sendPrompt() },
  toProfile() { wx.switchTab({ url: '/pages/profile/profile' }) },
  toResources(event) { getApp().globalData.resourceCategory = event.currentTarget.dataset.kind; wx.switchTab({ url: '/pages/resources/resources' }) },
  openRecommendedResource(type, id) {
    const category = { PRODUCT: 'product', FOOD: 'food', STAY: 'stay', PLACE: 'travel', ROUTE_GUIDE: 'travel' }[type]
    if (!category) { this.setData({ message: '当前建议请登录后到“我的”确认，或重新描述你的需求。' }); return }
    if (type === 'PRODUCT' || type === 'PLACE') {
      const kind = type === 'PRODUCT' ? 'product' : 'travel'
      wx.navigateTo({ url: `/pages/detail/detail?kind=${kind}&id=${encodeURIComponent(id)}` })
      return
    }
    getApp().globalData.resourceCategory = category
    getApp().globalData.resourceFocusFoodId = type === 'FOOD' && id ? id : null
    getApp().globalData.resourceFocusRoomId = type === 'STAY' && id ? id : null
    wx.switchTab({ url: '/pages/resources/resources' })
  },
  followRecommendation(event) {
    this.openRecommendedResource(event.currentTarget.dataset.type, event.currentTarget.dataset.id)
  },
  followCardAction(event) {
    this.openRecommendedResource(event.currentTarget.dataset.type, event.currentTarget.dataset.id)
  }
})
