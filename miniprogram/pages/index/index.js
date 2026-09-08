const { services } = require('../../utils/demo')

Page({
  data: { services: services.slice(0, 2), currentItinerary: null, pendingBooking: null, heroImageFailed: false, serviceImageErrors: {}, categories: [{ id: 'culture', icon: '☘', name: '茶旅' }, { id: 'stay', icon: '⌂', name: '食宿' }, { id: 'travel', icon: '⌁', name: '路线' }, { id: 'all', icon: '◒', name: '导览' }] },
  onShow() { this.getTabBar()?.setData({ selected: 0 }); const app = getApp(); this.setData({ currentItinerary: app.globalData.currentItinerary, pendingBooking: app.globalData.pendingBooking }) },
  toResources(event) { getApp().globalData.resourceCategory = event.currentTarget.dataset.category || 'all'; wx.switchTab({ url: '/pages/resources/resources' }) },
  toAssistant() { wx.switchTab({ url: '/pages/assistant/assistant' }) },
  toDetail(event) { wx.navigateTo({ url: `/pages/detail/detail?id=${encodeURIComponent(event.currentTarget.dataset.id)}` }) },
  imageError() { this.setData({ heroImageFailed: true }) },
  serviceImageError(event) { const id = event.currentTarget.dataset.id; this.setData({ serviceImageErrors: { ...this.data.serviceImageErrors, [id]: true } }) }
})
