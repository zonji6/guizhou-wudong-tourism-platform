const { services } = require('../../utils/demo')
const { request } = require('../../utils/api')
Page({
  data: { services, categories: [{ id:'culture', icon:'☘', name:'苗韵茶旅' }, { id:'stay', icon:'⌂', name:'暖居民宿' }, { id:'food', icon:'◌', name:'寨味餐食' }, { id:'travel', icon:'⌁', name:'山野出行' }] },
  onLoad() { request('/api/services').then(items => this.setData({ services: items.map(normalize) })).catch(() => {}) },
  toResources(e) { const category = e.currentTarget.dataset.category || ''; wx.navigateTo({ url: '/pages/resources/resources?category=' + category }) },
  toDetail(e) { wx.navigateTo({ url: '/pages/detail/detail?id=' + e.currentTarget.dataset.id }) },
  toAssistant() { wx.navigateTo({ url: '/pages/assistant/assistant' }) },
  toCommunity() { wx.navigateTo({ url: '/pages/community/community' }) }
})
function normalize(item) { return { ...item, title:item.name || item.title, image:item.imageUrl || item.image, intro:item.description || item.intro, tags:item.tags || [] } }
