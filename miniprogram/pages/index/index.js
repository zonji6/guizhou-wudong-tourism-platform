const { services } = require('../../utils/demo')
Page({
  data: { services, categories: [{ id:'culture', icon:'☘', name:'苗韵茶旅' }, { id:'stay', icon:'⌂', name:'暖居民宿' }, { id:'food', icon:'◌', name:'寨味餐食' }, { id:'travel', icon:'⌁', name:'山野出行' }] },
  toResources(e) { const category = e.currentTarget.dataset.category || ''; wx.navigateTo({ url: '/pages/resources/resources?category=' + category }) },
  toDetail(e) { wx.navigateTo({ url: '/pages/detail/detail?id=' + e.currentTarget.dataset.id }) },
  toAssistant() { wx.navigateTo({ url: '/pages/assistant/assistant' }) },
  toCommunity() { wx.navigateTo({ url: '/pages/community/community' }) }
})
