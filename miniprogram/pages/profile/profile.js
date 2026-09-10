const { request } = require('../../utils/api')
const { authState, login, logout, refresh, register, userOptions } = require('../../utils/auth')

Page({
  data: { account: null, mode: 'login', username: '', password: '', nickname: '', busy: false, message: '', summary: null },
  onShow() {
    this.getTabBar()?.setData({ selected: 4 })
    this.syncAuth()
    if (authState().account) this.loadWorkspace()
  },
  syncAuth() { this.setData({ account: authState().account }) },
  setMode(event) { this.setData({ mode: event.currentTarget.dataset.mode, message: '' }) },
  input(event) { this.setData({ [event.currentTarget.dataset.field]: event.detail.value }) },
  authenticate() {
    if (this.data.busy) return
    this.setData({ busy: true, message: '' })
    const action = this.data.mode === 'register'
      ? register({ username: this.data.username, password: this.data.password, nickname: this.data.nickname })
      : login({ username: this.data.username, password: this.data.password })
    action.then(() => {
      if (this.data.mode === 'register') this.setData({ mode: 'login', password: '', message: '账号已创建，请用同一账号登录 Web 或小程序。' })
      else { this.setData({ password: '' }); this.syncAuth(); this.loadWorkspace() }
    }).catch(reason => this.setData({ message: reason?.message || '账号操作失败。' })).finally(() => this.setData({ busy: false }))
  },
  restore() {
    this.setData({ busy: true, message: '' })
    refresh().then(() => { this.syncAuth(); this.loadWorkspace() }).catch(reason => this.setData({ message: reason?.message || '没有可恢复的登录。' })).finally(() => this.setData({ busy: false }))
  },
  signOut() {
    this.setData({ busy: true, message: '' })
    logout().then(() => { this.syncAuth(); this.setData({ summary: null, message: '服务端已确认退出当前安装实例。' }) }).catch(reason => this.setData({ message: `${reason?.message || '退出结果未知。'} 未冒充服务端已撤销。` })).finally(() => this.setData({ busy: false }))
  },
  loadWorkspace() {
    if (!authState().account) return
    const paths = ['/api/me/product-orders', '/api/me/food-orders', '/api/me/stay-bookings', '/api/me/itineraries', '/api/me/food-drafts', '/api/me/stay-drafts', '/api/me/legacy-records']
    this.setData({ busy: true, message: '' })
    Promise.all(paths.map(path => request(path, userOptions()))).then(([products, foods, stays, itineraries, foodDrafts, stayDrafts, legacy]) => {
      this.setData({ summary: { orderCount: products.length + foods.length + stays.length, itineraryCount: itineraries.length, draftCount: foodDrafts.length + stayDrafts.length, legacyCount: legacy.length } })
    }).catch(reason => this.setData({ message: reason?.message || '“我的”暂时无法读取。' })).finally(() => this.setData({ busy: false }))
  },
  toOrders() { wx.navigateTo({ url: '/pages/orders/orders' }) }
})
