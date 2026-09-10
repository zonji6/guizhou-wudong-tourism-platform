const { request } = require('../../utils/api')
const { contentMediaNotice, contentMediaUrl, placeMediaUrl } = require('../../utils/contentMedia')

const tabs = [
  { id: 'product', name: '商品' },
  { id: 'food', name: '食' },
  { id: 'stay', name: '住' },
  { id: 'travel', name: '行' },
  { id: 'community', name: '社区' }
]

const foodCategories = [
  { id: 'ALL', name: '全部' },
  { id: 'DISH', name: '菜品' },
  { id: 'DRINK', name: '饮品' },
  { id: 'SET', name: '套餐' }
]

const foodTypeLabels = { DISH: '菜品', DRINK: '饮品', SET: '套餐' }

function priceView(item) {
  if (item?.demoPrice) return { amount: item.demoPrice.amount, note: item.demoPrice.simulationNote, kind: '演示价' }
  if (item?.referencePrice) return { amount: item.referencePrice.amount, note: `参考来源：${item.referencePrice.sourceTitle}`, kind: '参考价' }
  return null
}

function withImage(item, kind) {
  return {
    ...item,
    displayImageUrl: contentMediaUrl(item?.imageUrl, kind),
    imageNote: contentMediaNotice(item?.imageUrl, kind)
  }
}

function productView(item) {
  return { ...withImage(item, 'product'), priceView: priceView(item) }
}

function foodView(item) {
  return { ...withImage(item, 'food'), quantity: 0, priceView: priceView(item), itemTypeLabel: foodTypeLabels[item.itemType] || '餐食' }
}

function stayView(property) {
  return {
    ...withImage(property, 'stay'),
    roomTypes: (property.roomTypes || []).map(room => ({ ...withImage(room, 'stay'), priceView: priceView(room) }))
  }
}

function placeView(place) {
  const x = Number(place.schematicPosition?.x)
  const y = Number(place.schematicPosition?.y)
  const drawable = Number.isFinite(x) && Number.isFinite(y) && x >= 0 && x <= 1 && y >= 0 && y <= 1
  const displayImageUrl = placeMediaUrl(place.id)
  return { ...place, displayImageUrl, imageNote: displayImageUrl ? '原始资料参考图，公开使用范围待确认' : '', left: drawable ? x * 100 : null, top: drawable ? y * 100 : null, drawable }
}

function routeProjection(map, route) {
  const placeById = new Map((map?.places || []).map(place => [place.id, place]))
  const steps = (route?.routeNodes || []).map((node, index) => {
    const place = node.placeId ? placeById.get(node.placeId) : null
    const drawable = node.drawable === true && Boolean(place?.drawable)
    return { ...node, placeName: node.placeName || place?.name || '未命名地点', viewKey: `${node.sequence}-${index}`, place, drawable, displayNote: node.note || (drawable ? '已绘入水彩示意图' : '仅作文字节点') }
  })
  const points = steps.filter(step => step.drawable).map(step => ({ x: step.place.left, y: step.place.top }))
  const segments = []
  const mapHeightToWidth = 0.81
  for (let index = 1; index < points.length; index += 1) {
    const start = points[index - 1]
    const end = points[index]
    const dx = end.x - start.x
    const dy = (end.y - start.y) * mapHeightToWidth
    const width = Math.sqrt(dx * dx + dy * dy)
    const angle = Math.atan2(dy, dx) * 180 / Math.PI
    segments.push({ id: `${index}-${start.x}-${start.y}`, style: `left:${start.x}%;top:${start.y}%;width:${width}%;transform:rotate(${angle}deg);` })
  }
  return { steps, segments }
}

function mapWithCustomSelection(map, customRouteIds) {
  if (!map) return map
  return { ...map, places: (map.places || []).map(place => ({ ...place, customOrder: customRouteIds.indexOf(place.id) + 1 })) }
}

function customRouteProjection(map, customRouteIds) {
  return routeProjection(map, { routeNodes: customRouteIds.map((placeId, index) => ({ sequence: index + 1, placeId, drawable: true })) })
}

Page({
  data: {
    active: 'product', tabs,
    products: [], visibleProducts: [], productQuery: '', productTag: '', productTags: [],
    merchants: [], visibleMerchants: [], merchantQuery: '', merchantSummary: '', foods: [], visibleFoods: [], selectedMerchant: null, foodCategory: 'ALL', foodCategories,
    stays: [], stayCount: 0, roomTypeCount: 0,
    map: null, routes: [], selectedRouteId: '', customRouteIds: [], routeSteps: [], routeSegments: [], routeMessage: '',
    loading: false, error: ''
  },
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
    this.setData({ loading: true, error: '' })
    if (active === 'travel') {
      Promise.all([request('/api/places'), request('/api/posts?type=ROUTE_GUIDE')]).then(([result, posts]) => {
        const map = mapWithCustomSelection({ ...result, places: (result?.places || []).map(placeView) }, [])
        const routes = (posts || []).filter(post => post.postType === 'ROUTE_GUIDE')
        const selectedRouteId = routes[0]?.id || ''
        const projection = routeProjection(map, routes[0])
        this.setData({ map, routes, selectedRouteId, customRouteIds: [], routeSteps: projection.steps, routeSegments: projection.segments, routeMessage: '' })
      }).catch(reason => this.setData({ error: reason?.message || '地点与路线暂时无法读取。' })).finally(() => this.setData({ loading: false }))
      return
    }
    const paths = { product: '/api/products', food: '/api/food-merchants', stay: '/api/stays' }
    request(paths[active]).then(result => {
      if (active === 'product') {
        const products = (result || []).map(productView)
        const tags = [...new Set(products.reduce((all, item) => all.concat(item.tags || []), []))].sort((left, right) => left.localeCompare(right, 'zh-CN'))
        this.setData({ products, visibleProducts: products, productTags: tags })
      }
      if (active === 'food') {
        const merchants = (result || []).map(item => withImage(item, 'food'))
        this.setData({ merchants, visibleMerchants: merchants, merchantSummary: `当前 ${merchants.length} 家公开店铺` })
      }
      if (active === 'stay') {
        const stays = (result || []).map(stayView)
        this.setData({ stays, stayCount: stays.length, roomTypeCount: stays.reduce((total, item) => total + item.roomTypes.length, 0) })
      }
    }).catch(reason => this.setData({ error: reason?.message || '内容暂时无法读取。' })).finally(() => this.setData({ loading: false }))
  },
  productSearch(event) {
    this.setData({ productQuery: event.detail.value }, () => this.applyProductFilters())
  },
  selectProductTag(event) {
    this.setData({ productTag: event.currentTarget.dataset.tag || '' }, () => this.applyProductFilters())
  },
  applyProductFilters() {
    const query = this.data.productQuery.trim().toLocaleLowerCase()
    const tag = this.data.productTag
    const visibleProducts = this.data.products.filter(item => {
      const matchesTag = !tag || (item.tags || []).includes(tag)
      const text = [item.name, item.description, item.merchantName, ...(item.tags || [])].filter(Boolean).join(' ').toLocaleLowerCase()
      return matchesTag && (!query || text.includes(query))
    })
    this.setData({ visibleProducts })
  },
  merchantSearch(event) {
    const merchantQuery = event.detail.value
    const query = merchantQuery.trim().toLocaleLowerCase()
    const visibleMerchants = this.data.merchants.filter(item => {
      const text = [item.name, item.description, ...(item.tags || [])].filter(Boolean).join(' ').toLocaleLowerCase()
      return !query || text.includes(query)
    })
    this.setData({ merchantQuery, visibleMerchants, merchantSummary: query ? `匹配 ${visibleMerchants.length} / ${this.data.merchants.length} 家` : `当前 ${this.data.merchants.length} 家公开店铺` })
  },
  chooseMerchant(event) {
    const merchant = this.data.merchants.find(item => item.id === event.currentTarget.dataset.id)
    if (!merchant) return
    const sameMerchant = this.data.selectedMerchant?.id === merchant.id
    if (sameMerchant && (this.data.foods.length || this._foodRequestMerchantId === merchant.id)) return
    const requestSequence = (this._foodRequestSequence || 0) + 1
    this._foodRequestSequence = requestSequence
    this._foodRequestMerchantId = merchant.id
    this.setData({
      selectedMerchant: merchant,
      ...(sameMerchant ? {} : { foods: [], visibleFoods: [], foodCategory: 'ALL' }),
      loading: true,
      error: ''
    })
    request(`/api/foods?merchantId=${encodeURIComponent(merchant.id)}`).then(items => {
      if (this._foodRequestSequence !== requestSequence || this.data.selectedMerchant?.id !== merchant.id) return
      const foods = (items || []).map(foodView)
      this.setData({ foods, visibleFoods: foods })
      this._foodMenuReadySequence = requestSequence
    }).catch(reason => {
      if (this._foodRequestSequence !== requestSequence || this.data.selectedMerchant?.id !== merchant.id) return
      this.setData({ error: reason?.message || '菜单暂时无法读取。' })
    }).finally(() => {
      if (this._foodRequestSequence !== requestSequence || this.data.selectedMerchant?.id !== merchant.id) return
      this._foodRequestMerchantId = ''
      const shouldScroll = this._foodMenuReadySequence === requestSequence
      this.setData({ loading: false }, () => {
        if (shouldScroll && this._foodRequestSequence === requestSequence && this.data.selectedMerchant?.id === merchant.id && this.data.active === 'food') {
          wx.pageScrollTo({ selector: '#food-menu', duration: 280 })
        }
      })
    })
  },
  selectFoodCategory(event) {
    const foodCategory = event.currentTarget.dataset.id
    this.setData({ foodCategory, visibleFoods: foodCategory === 'ALL' ? this.data.foods : this.data.foods.filter(item => item.itemType === foodCategory) })
  },
  quantityChange(event) {
    const id = event.currentTarget.dataset.id
    const index = this.data.foods.findIndex(item => item.id === id)
    if (index < 0) return
    const entered = Number(event.detail.value)
    const quantity = Number.isInteger(entered) && entered >= 1 && entered <= 99 ? entered : 0
    this.setData({ [`foods[${index}].quantity`]: quantity }, () => {
      const foodCategory = this.data.foodCategory
      this.setData({ visibleFoods: foodCategory === 'ALL' ? this.data.foods : this.data.foods.filter(item => item.itemType === foodCategory) })
    })
  },
  selectRoute(event) {
    const selectedRouteId = event.currentTarget.dataset.id
    const route = this.data.routes.find(item => item.id === selectedRouteId)
    if (!route) return
    const map = mapWithCustomSelection(this.data.map, [])
    const projection = routeProjection(map, route)
    this.setData({ map, selectedRouteId, customRouteIds: [], routeSteps: projection.steps, routeSegments: projection.segments, routeMessage: '' })
  },
  startCustomRoute() {
    const customRouteIds = []
    const map = mapWithCustomSelection(this.data.map, customRouteIds)
    const projection = customRouteProjection(map, customRouteIds)
    this.setData({ map, selectedRouteId: 'CUSTOM', customRouteIds, routeSteps: projection.steps, routeSegments: projection.segments, routeMessage: '已切换到自选路线，点击地图节点或地点卡片开始编排。' })
  },
  toggleCustomRoute(event) {
    const placeId = event.currentTarget.dataset.id
    if (!placeId || !this.data.map) return
    const customRouteIds = this.data.customRouteIds.slice()
    const index = customRouteIds.indexOf(placeId)
    if (index >= 0) customRouteIds.splice(index, 1)
    else if (customRouteIds.length < 12) customRouteIds.push(placeId)
    else { this.setData({ routeMessage: '一条自选示意路线最多保留 12 个公开地点。' }); return }
    const map = mapWithCustomSelection(this.data.map, customRouteIds)
    const projection = customRouteProjection(map, customRouteIds)
    this.setData({ map, selectedRouteId: 'CUSTOM', customRouteIds, routeSteps: projection.steps, routeSegments: projection.segments, routeMessage: customRouteIds.length ? `已编排 ${customRouteIds.length} 个公开地点，编号即游览顺序。` : '已清空自选路线。' })
  },
  clearCustomRoute() {
    this.startCustomRoute()
  },
  openDetail(event) {
    const kind = event.currentTarget.dataset.kind
    const id = event.currentTarget.dataset.id
    if (!['product', 'stay', 'travel', 'community'].includes(kind) || !id) return
    wx.navigateTo({ url: `/pages/detail/detail?kind=${kind}&id=${encodeURIComponent(id)}` })
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
  openCheckout(selection) {
    getApp().globalData.checkoutSelection = selection
    wx.navigateTo({ url: '/pages/booking/booking' })
  }
})
