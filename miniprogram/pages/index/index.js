Page({
  data: {
    heroImageFailed: false,
    categories: [
      { id: 'product', icon: '礼', name: '商品', note: '茶与手作' },
      { id: 'food', icon: '味', name: '食', note: '同店多菜' },
      { id: 'stay', icon: '宿', name: '住', note: '山居房型' },
      { id: 'travel', icon: '行', name: '行', note: '水彩示意' },
      { id: 'community', icon: '记', name: '社区', note: '寨里手账' }
    ]
  },
  onShow() { this.getTabBar()?.setData({ selected: 0 }) },
  toResources(event) {
    const category = event.currentTarget.dataset.category
    if (category === 'community') { wx.switchTab({ url: '/pages/community/community' }); return }
    getApp().globalData.resourceCategory = category
    wx.switchTab({ url: '/pages/resources/resources' })
  },
  toAssistant() { wx.switchTab({ url: '/pages/assistant/assistant' }) },
  imageError() { this.setData({ heroImageFailed: true }) }
})
