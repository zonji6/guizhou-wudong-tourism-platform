const { request } = require('../../utils/api')

const tabs = [
  { id: 'product', name: '商品' },
  { id: 'food', name: '食' },
  { id: 'stay', name: '住' },
  { id: 'travel', name: '行' },
  { id: 'community', name: '社区' }
]

function priceView(item) {
  if (item?.demoPrice) return { amount: item.demoPrice.amount, note: item.demoPrice.simulationNote, kind: '演示价' }
  if (item?.referencePrice) return { amount: item.referencePrice.amount, note: `参考来源：${item.referencePrice.sourceTitle}`, kind: '参考价' }
  return null
}

Page({
  data: { active: 'product', tabs, products: [], merchants: [], foods: [], stays: [], map: null, selectedMerchant: null, loading: false, error: '' },
  onShow() {
    this.getTabBar()?.setData({ selected: 1 })
    const requested = getApp().globalData.resourceCategory
    getApp().globalData.resourceCategory = null
    if (tabs.some(tab => tab.id === requested)) this.setData({ active: requested })
    this.loadActive()
  },
  changeTab(event) {
    const active = event.currentTarget.dataset.id
    if (active === 'community') { wx.switchTab({ url: '/pages/community/community' }); return }
    this.setData({ active, error: '' }, () => this.loadActive())
  },
  loadActive() {
    const active = this.data.active
    const cached = active === 'product' ? this.data.products.length : active === 'food' ? this.data.merchants.length : active === 'stay' ? this.data.stays.length : this.data.map
    if (cached) return
    const paths = { product: '/api/products', food: '/api/food-merchants', stay: '/api/stays', travel: '/api/places' }
    this.setData({ loading: true, error: '' })
    request(paths[active]).then(result => {
      if (active === 'product') this.setData({ products: (result || []).map(item => ({ ...item, priceView: priceView(item) })) })
      if (active === 'food') this.setData({ merchants: result || [] })
      if (active === 'stay') this.setData({ stays: (result || []).map(property => ({ ...property, roomTypes: (property.roomTypes || []).map(room => ({ ...room, priceView: priceView(room) })) })) })
      if (active === 'travel') this.setData({ map: { ...result, places: (result.places || []).map(place => ({ ...place, left: Number(place.schematicPosition?.x) * 100, top: Number(place.schematicPosition?.y) * 100 })) } })
    }).catch(reason => this.setData({ error: reason?.message || '内容暂时无法读取。' })).finally(() => this.setData({ loading: false }))
  },
  chooseMerchant(event) {
    const merchant = this.data.merchants.find(item => item.id === event.currentTarget.dataset.id)
    if (!merchant) return
    this.setData({ selectedMerchant: merchant, foods: [], loading: true, error: '' })
    request(`/api/foods?merchantId=${encodeURIComponent(merchant.id)}`).then(items => {
      this.setData({ foods: (items || []).map(item => ({ ...item, quantity: 0, priceView: priceView(item) })) })
    }).catch(reason => this.setData({ error: reason?.message || '菜单暂时无法读取。' })).finally(() => this.setData({ loading: false }))
  },
  quantityChange(event) {
    const index = Number(event.currentTarget.dataset.index)
    this.setData({ [`foods[${index}].quantity`]: Math.max(0, Number(event.detail.value) || 0) })
  },
  checkoutProduct(event) {
    const resource = this.data.products.find(item => item.id === event.currentTarget.dataset.id)
    if (resource?.orderable) this.openCheckout({ kind: 'product', title: resource.name, resource })
  },
  checkoutFood() {
    const items = this.data.foods.filter(item => item.quantity > 0).map(item => ({ foodItemId: item.id, quantity: item.quantity, resource: item }))
    if (!this.data.selectedMerchant || !items.length) { this.setData({ error: '请在同一家店至少选择一种餐食。' }); return }
    this.openCheckout({ kind: 'food', title: this.data.selectedMerchant.name, merchant: this.data.selectedMerchant, items })
  },
  checkoutStay(event) {
    const property = this.data.stays.find(item => item.id === event.currentTarget.dataset.propertyId)
    const resource = property?.roomTypes?.find(item => item.id === event.currentTarget.dataset.roomId)
    if (resource?.orderable) this.openCheckout({ kind: 'stay', title: `${property.name} · ${resource.name}`, property, resource })
  },
  openCheckout(selection) {
    getApp().globalData.checkoutSelection = selection
    wx.navigateTo({ url: '/pages/booking/booking' })
  }
})
