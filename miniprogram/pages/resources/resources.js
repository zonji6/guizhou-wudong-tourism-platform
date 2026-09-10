const { request } = require('../../utils/api')
const { contentMediaNotice, contentMediaUrl, placeMediaUrl } = require('../../utils/contentMedia')

const tabs = [
  { id: 'product', name: '商品' },
  { id: 'food', name: '食' },
  { id: 'stay', name: '住' },
  { id: 'travel', name: '行' },
  { id: 'community', name: '社区' }
]

const tabIntros = {
  product: { title: '把山里的手艺，慢慢带回家', description: '从茶、苗绣、银饰与蜡染里，读一读乌东山地生活留下的手感。' },
  food: { title: '坐下来，尝一口寨里的烟火', description: '先选一家店，再把想吃的菜慢慢放进同一张到店订单。' },
  stay: { title: '在山谷里，安稳睡一晚', description: '从住处与房型开始，慢慢安排停留；容量、价格与房态均以现场核对为准。' },
  travel: { title: '沿着溪流，走进寨中日常', description: '把想去的地方连成水彩示意顺序，不把它当作真实导航。' },
  community: { title: '把山里的片刻，写进手账', description: '文化资料与寨里分享并列呈现，让阅读成为旅行开始前的一次相遇。' }
}

const foodCategories = [
  { id: 'ALL', name: '全部' },
  { id: 'DISH', name: '菜品' },
  { id: 'DRINK', name: '饮品' },
  { id: 'SET', name: '套餐' }
]

const foodTypeLabels = { DISH: '菜品', DRINK: '饮品', SET: '套餐' }
const productMetadataTags = new Set(['商品', '演示数据', '资料待核验', '资料参考', '待核验'])
const resourceMetadataTags = new Set(['商品', '餐食', '餐食主体', '住宿', '乌东', '演示数据', '资料待核验', '资料参考', '待核验', '资料菜单项', '资料候选', '共享示意图'])

function displayTags(item) {
  return (item?.tags || []).filter(tag => !resourceMetadataTags.has(tag))
}

function publicName(name) {
  const value = String(name || '').trim()
  return value.replace(/\s*演示\s*SKU\s*$/i, '') || value
}

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
  return { ...withImage(item, 'product'), displayName: publicName(item.name), priceView: priceView(item), displayTags: displayTags(item) }
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
  const displayX = Math.min(.86, Math.max(.15, x))
  const displayY = Math.min(.86, Math.max(.14, y))
  return { ...place, displayImageUrl, imageNote: displayImageUrl ? '原始资料参考图，公开使用范围待确认' : '', left: drawable ? displayX * 100 : null, top: drawable ? displayY * 100 : null, drawable }
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
    active: 'product', tabs, intro: tabIntros.product,
    products: [], visibleProducts: [], productQuery: '', productTag: '', productTags: [], productSummary: '',
    merchants: [], visibleMerchants: [], foodStartingMerchants: [], merchantQuery: '', merchantSummary: '', foods: [], visibleFoods: [], selectedMerchant: null, highlightedFoodId: '', foodHint: '', foodCategory: 'ALL', foodCategories,
    stays: [], visibleStays: [], stayQuery: '', stayPeople: '', stayCount: 0, roomTypeCount: 0, staySummary: '', highlightedStayId: '', stayHint: '',
    map: null, routes: [], selectedRouteId: '', customRouteIds: [], routeSteps: [], routeSegments: [], routeMessage: '',
    loading: false, error: ''
  },
  onShow() {
    this.getTabBar()?.setData({ selected: 1 })
    const requested = getApp().globalData.resourceCategory
    this._pendingFoodFocusId = getApp().globalData.resourceFocusFoodId
    this._pendingRoomFocusId = getApp().globalData.resourceFocusRoomId
    getApp().globalData.resourceCategory = null
    getApp().globalData.resourceFocusFoodId = null
    getApp().globalData.resourceFocusRoomId = null
    if (tabs.some(tab => tab.id === requested)) this.setData({ active: requested, intro: tabIntros[requested] })
    this.loadActive()
  },
  changeTab(event) {
    const active = event.currentTarget.dataset.id
    if (active === 'community') { wx.switchTab({ url: '/pages/community/community' }); return }
    this.setData({ active, intro: tabIntros[active], error: '' }, () => this.loadActive())
  },
  loadActive() {
    const active = this.data.active
    const cached = active === 'product' ? this.data.products.length : active === 'food' ? this.data.merchants.length : active === 'stay' ? this.data.stays.length : this.data.map
    if (cached) { this.applyPendingFoodFocus(); this.applyPendingRoomFocus(); return }
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
        const tags = [...new Set(products.reduce((all, item) => all.concat(item.tags || []), []))]
          .filter(tag => !productMetadataTags.has(tag))
          .sort((left, right) => left.localeCompare(right, 'zh-CN'))
        this.setData({ products, visibleProducts: products, productTags: tags, productSummary: `当前 ${products.length} 项公开商品` })
      }
      if (active === 'food') {
        const merchants = (result || []).map(item => withImage(item, 'food'))
        this.setData({ merchants, visibleMerchants: merchants, foodStartingMerchants: merchants.slice(0, 3).map(item => ({ ...item, guideTags: displayTags(item).slice(0, 2).join(' · ') || '乌东餐食资料' })), merchantSummary: `当前 ${merchants.length} 家公开店铺` }, () => this.applyPendingFoodFocus())
      }
      if (active === 'stay') {
        const stays = (result || []).map(stayView)
        const roomTypeCount = stays.reduce((total, item) => total + item.roomTypes.length, 0)
        this.setData({ stays, visibleStays: stays, stayCount: stays.length, roomTypeCount, staySummary: `当前公开 ${stays.length} 家住宿，共 ${roomTypeCount} 个房型` }, () => this.applyPendingRoomFocus())
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
    const conditions = [query ? '关键词' : '', tag || ''].filter(Boolean)
    this.setData({
      visibleProducts,
      productSummary: conditions.length ? `匹配 ${visibleProducts.length} / ${this.data.products.length} 项（${conditions.join('，')}）` : `当前 ${this.data.products.length} 项公开商品`
    })
  },
  merchantSearch(event) {
    const merchantQuery = event.detail.value
    const query = merchantQuery.trim().toLocaleLowerCase()
    const visibleMerchants = this.data.merchants.filter(item => {
      const text = [item.name, item.description, ...(item.tags || [])].filter(Boolean).join(' ').toLocaleLowerCase()
      return !query || text.includes(query)
    })
    this.setData({ merchantQuery, visibleMerchants, foodStartingMerchants: visibleMerchants.slice(0, 3).map(item => ({ ...item, guideTags: displayTags(item).slice(0, 2).join(' · ') || '乌东餐食资料' })), merchantSummary: query ? `匹配 ${visibleMerchants.length} / ${this.data.merchants.length} 家` : `当前 ${this.data.merchants.length} 家公开店铺` })
  },
  staySearch(event) {
    this.setData({ stayQuery: event.detail.value }, () => this.applyStayFilters())
  },
  stayPeopleChange(event) {
    this.setData({ stayPeople: event.detail.value }, () => this.applyStayFilters())
  },
  applyStayFilters() {
    const query = this.data.stayQuery.trim().toLocaleLowerCase()
    const people = Number(this.data.stayPeople)
    const requestedPeople = Number.isInteger(people) && people > 0 ? people : 0
    const visibleStays = this.data.stays.filter(stay => {
      const text = [stay.name, stay.description, stay.locationText, stay.merchantName, ...(stay.tags || []), ...(stay.roomTypes || []).flatMap(room => [room.name, room.description])].filter(Boolean).join(' ').toLocaleLowerCase()
      const keywordMatches = !query || text.includes(query)
      const capacityMatches = !requestedPeople || (stay.roomTypes || []).some(room => Number(room.maxGuestsPerRoom) >= requestedPeople)
      return keywordMatches && capacityMatches
    })
    const conditions = [query ? '关键词' : '', requestedPeople ? `${requestedPeople} 人/间` : ''].filter(Boolean)
    this.setData({ visibleStays, highlightedStayId: '', stayHint: '', staySummary: conditions.length ? `匹配 ${visibleStays.length} / ${this.data.stayCount} 家（${conditions.join('，')}）` : `当前公开 ${this.data.stayCount} 家住宿，共 ${this.data.roomTypeCount} 个房型` })
  },
  chooseMerchant(event) { this.selectMerchantById(event.currentTarget.dataset.id) },
  selectMerchantById(merchantId, highlightedFoodId = '') {
    const merchant = this.data.merchants.find(item => item.id === merchantId)
    if (!merchant) return
    const sameMerchant = this.data.selectedMerchant?.id === merchant.id
    if (sameMerchant && (this.data.foods.length || this._foodRequestMerchantId === merchant.id)) return
    const requestSequence = (this._foodRequestSequence || 0) + 1
    this._foodRequestSequence = requestSequence
    this._foodRequestMerchantId = merchant.id
    this.setData({
      selectedMerchant: merchant,
      ...(sameMerchant ? {} : { foods: [], visibleFoods: [], foodCategory: 'ALL', highlightedFoodId: '', foodHint: '' }),
      loading: true,
      error: ''
    })
    request(`/api/foods?merchantId=${encodeURIComponent(merchant.id)}`).then(items => {
      if (this._foodRequestSequence !== requestSequence || this.data.selectedMerchant?.id !== merchant.id) return
      const foods = (items || []).map(foodView)
      const highlighted = highlightedFoodId && foods.some(item => item.id === highlightedFoodId) ? highlightedFoodId : ''
      this.setData({ foods, visibleFoods: foods, highlightedFoodId: highlighted, foodHint: highlighted ? `已为你打开“${foods.find(item => item.id === highlighted).name}”所在店铺，可继续同店选餐。` : '' })
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
  applyPendingFoodFocus() {
    const foodId = this._pendingFoodFocusId
    if (this.data.active !== 'food' || !foodId || !this.data.merchants.length) return
    this._pendingFoodFocusId = ''
    request(`/api/foods/${encodeURIComponent(foodId)}`).then(food => {
      if (!food?.merchantId) throw new Error('推荐餐食暂时无法定位到公开店铺。')
      this.selectMerchantById(food.merchantId, food.id)
    }).catch(reason => this.setData({ error: reason?.message || '暂时无法定位推荐餐食。' }))
  },
  applyPendingRoomFocus() {
    const roomId = this._pendingRoomFocusId
    if (this.data.active !== 'stay' || !roomId || !this.data.stays.length) return
    this._pendingRoomFocusId = ''
    request(`/api/room-types/${encodeURIComponent(roomId)}`).then(room => {
      const property = this.data.stays.find(item => item.id === room?.stayPropertyId)
      if (!property) throw new Error('推荐房型所属住宿暂未在公开目录中展示。')
      this.setData({ visibleStays: [property], highlightedStayId: property.id, stayHint: `已为你定位“${room.name}”所在住宿，可继续查看房型并主动核价。`, staySummary: '已收束到向导推荐的公开住宿' })
    }).catch(reason => this.setData({ error: reason?.message || '暂时无法定位推荐房型。' }))
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
    if (resource?.orderable) this.openCheckout({ kind: 'product', title: resource.displayName || resource.name, resource })
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
