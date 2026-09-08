const { services, normalizeService } = require('../../utils/demo')
const { request } = require('../../utils/api')

const categories = [{ id: 'all', name: '全部' }, { id: 'culture', name: '苗韵茶旅' }, { id: 'stay', name: '暖居民宿' }, { id: 'food', name: '寨味餐食' }, { id: 'travel', name: '山野出行' }]
let allServices = services

Page({
  data: { active: 'all', services, categories, imageErrors: {} },
  onLoad() {
    request('/api/services').then(items => {
      allServices = Array.isArray(items) && items.length ? items.map(normalizeService) : services
      this.applyFilter(this.data.active)
    }).catch(() => { allServices = services; this.applyFilter(this.data.active) })
  },
  onShow() {
    this.getTabBar()?.setData({ selected: 1 })
    const app = getApp()
    const requested = app.globalData.resourceCategory
    app.globalData.resourceCategory = null
    const category = requested || this.data.active
    if (requested) this.setData({ active: requested })
    this.applyFilter(category)
  },
  applyFilter(category) { this.setData({ active: category, services: category === 'all' ? allServices : allServices.filter(item => item.category === category) }) },
  filter(event) { this.applyFilter(event.currentTarget.dataset.id) },
  toDetail(event) { wx.navigateTo({ url: `/pages/detail/detail?id=${encodeURIComponent(event.currentTarget.dataset.id)}` }) },
  imageError(event) { const id = event.currentTarget.dataset.id; this.setData({ imageErrors: { ...this.data.imageErrors, [id]: true } }) }
})
