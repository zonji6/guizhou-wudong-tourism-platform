<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import SafeImage from '../components/common/SafeImage.vue'
import { authState } from '../services/authSession'
import { createPost, getFood, listFoodMerchants, listFoods, listPlaces, listPosts, listProducts, listStays } from '../services/tourismApi'
import { contentMediaUrl, placeMediaUrl, postMediaUrl } from '../utils/contentMedia'

const props = defineProps({ section: { type: String, default: 'product' }, focusFoodId: { type: String, default: '' }, focusProductId: { type: String, default: '' }, focusRoomId: { type: String, default: '' }, focusPlaceId: { type: String, default: '' } })
const emit = defineEmits(['navigate', 'checkout'])
const tabs = [
  { id: 'product', label: '商品' },
  { id: 'food', label: '食' },
  { id: 'stay', label: '住' },
  { id: 'travel', label: '行' },
  { id: 'community', label: '社区' }
]
const foodTypeLabels = { DISH: '菜品', DRINK: '饮品', SET: '套餐' }
const state = reactive({ product: [], merchants: [], stay: [], posts: [], map: null })
const loading = ref(false)
const error = ref('')
const productKeyword = ref('')
const productTag = ref('')
const merchantKeyword = ref('')
const stayKeyword = ref('')
const stayPeople = ref('')
const selectedProduct = ref(null)
const productDetailRef = ref(null)
const selectedMerchant = ref(null)
const selectedFood = ref(null)
const foodDetailRef = ref(null)
const foodMenuRef = ref(null)
const foodType = ref('')
const foods = ref([])
const basket = reactive({})
const selectedStayId = ref('')
const selectedRouteIds = ref([])
const routeGuidePlaceIds = ref([])
const routeMessage = ref('')
const selectedPostId = ref('')
const postFilter = ref('')
const postTag = ref('')
const postType = ref('MOMENT')
const postForm = reactive({ title: '', content: '', tags: '', routeSummary: '' })
const posting = ref(false)
const postMessage = ref('')
let sequence = 0
let foodRequestSequence = 0

const activeTab = computed(() => tabs.find(tab => tab.id === props.section) || tabs[0])
const productTags = computed(() => [...new Set(state.product.flatMap(item => Array.isArray(item.tags) ? item.tags : []))])
const visibleProducts = computed(() => {
  const keyword = productKeyword.value.trim().toLocaleLowerCase('zh-CN')
  return state.product.filter(item => {
    const tags = Array.isArray(item.tags) ? item.tags : []
    const matchesTag = !productTag.value || tags.includes(productTag.value)
    const searchable = [item.name, item.description, item.merchantName, item.pickupPoint, ...tags]
      .filter(value => typeof value === 'string')
      .join(' ')
      .toLocaleLowerCase('zh-CN')
    return matchesTag && (!keyword || searchable.includes(keyword))
  })
})
const visibleMerchants = computed(() => {
  const keyword = merchantKeyword.value.trim().toLocaleLowerCase('zh-CN')
  if (!keyword) return state.merchants
  return state.merchants.filter(merchant => [merchant.name, merchant.description, ...(merchant.tags || [])]
    .filter(value => typeof value === 'string')
    .join(' ')
    .toLocaleLowerCase('zh-CN')
    .includes(keyword))
})
const foodStartingMerchants = computed(() => visibleMerchants.value.slice(0, 3))
const foodTypes = computed(() => [...new Set(foods.value.map(item => item.itemType).filter(type => foodTypeLabels[type]))])
const visibleFoods = computed(() => foods.value.filter(item => !foodType.value || item.itemType === foodType.value))
const basketItems = computed(() => foods.value
  .filter(item => Number(basket[item.id]) > 0)
  .map(item => ({ foodItemId: item.id, quantity: Number(basket[item.id]), resource: item })))
const basketPortions = computed(() => basketItems.value.reduce((total, item) => total + item.quantity, 0))
const roomTotal = computed(() => state.stay.reduce((total, property) => total + (property.roomTypes?.length || 0), 0))
const visibleStays = computed(() => {
  const keyword = stayKeyword.value.trim().toLocaleLowerCase('zh-CN')
  const people = Number(stayPeople.value)
  return state.stay.filter(property => {
    const searchable = [property.name, property.description, property.locationText, property.merchantName, ...(property.tags || []), ...(property.roomTypes || []).flatMap(room => [room.name, room.description])]
      .filter(value => typeof value === 'string').join(' ').toLocaleLowerCase('zh-CN')
    const hasSuitableRoom = !people || (property.roomTypes || []).some(room => Number(room.maxGuestsPerRoom) >= people)
    return (!keyword || searchable.includes(keyword)) && hasSuitableRoom
  })
})
const drawablePlaces = computed(() => (state.map?.places || []).filter(place => place.schematicPosition))
const communityTags = computed(() => Object.entries(state.posts
  .flatMap(post => Array.isArray(post.tags) ? post.tags : [])
  .reduce((counts, tag) => ({ ...counts, [tag]: (counts[tag] || 0) + 1 }), {}))
  .sort(([leftTag, leftCount], [rightTag, rightCount]) => rightCount - leftCount || leftTag.localeCompare(rightTag, 'zh-CN'))
  .slice(0, 12)
  .map(([tag]) => tag))
const visiblePosts = computed(() => state.posts.filter(post => (!postFilter.value || post.postType === postFilter.value) && (!postTag.value || (post.tags || []).includes(postTag.value))))
const selectedRoutePlaces = computed(() => selectedRouteIds.value
  .map(id => (state.map?.places || []).find(place => place.id === id))
  .filter(Boolean))
const drawableRoutePlaces = computed(() => selectedRoutePlaces.value.filter(place => place.schematicPosition))
const routePolyline = computed(() => drawableRoutePlaces.value
  .map(place => `${Number(place.schematicPosition.x) * 100},${Number(place.schematicPosition.y) * 100}`)
  .join(' '))

function price(item) {
  if (item?.demoPrice) return { amount: item.demoPrice.amount, label: item.demoPrice.simulationNote, kind: '演示价' }
  if (item?.referencePrice) return { amount: item.referencePrice.amount, label: `资料参考：${item.referencePrice.sourceTitle}`, kind: '参考价' }
  return null
}

function catalogNote(item) {
  const notes = []
  if (item?.demoData) notes.push('演示目录')
  notes.push(item?.verificationStatus === 'VERIFIED' ? '资料已核验' : '资料参考 · 待核验')
  return notes.join(' · ')
}

function imageOf(item, kind) {
  return contentMediaUrl(item?.imageUrl, kind)
}

function postLabel(post) {
  if (post?.postType === 'ROUTE_GUIDE') return '示意路线攻略'
  return post?.legacyData ? '文化文章' : '寨里动态'
}

function isTeaIllustration(item) {
  return /\/image(?:36|38)\.jpg$/.test(imageOf(item, 'product'))
}

function postExcerpt(post) {
  const content = String(post?.content || '')
  return content.length > 120 ? `${content.slice(0, 120)}…` : content
}

function mapStyle(place) {
  const position = place?.schematicPosition
  return position ? { left: `${Number(position.x) * 100}%`, top: `${Number(position.y) * 100}%` } : {}
}

function routeOrder(placeId) {
  const index = selectedRouteIds.value.indexOf(placeId)
  return index < 0 ? '' : index + 1
}

function routeNotice(post) {
  const summary = String(post?.routeSummary || '').trim().replace(/[。.!！?？]+$/, '')
  if (/不提供真实导航/.test(summary)) return `${summary}。`
  return `${summary || '路线仅表达地点顺序'}；不提供真实导航。`
}

function routeGuideOrder(placeId) {
  const index = routeGuidePlaceIds.value.indexOf(placeId)
  return index < 0 ? '' : index + 1
}

function toggleRouteGuidePlace(place) {
  const ids = routeGuidePlaceIds.value.slice()
  const existing = ids.indexOf(place.id)
  if (existing >= 0) ids.splice(existing, 1)
  else if (ids.length < 12) ids.push(place.id)
  routeGuidePlaceIds.value = ids
}

function routePlaces(post) {
  const places = new Map((state.map?.places || []).map(place => [place.id, place]))
  return (post?.routeNodes || [])
    .slice()
    .sort((left, right) => left.sequence - right.sequence)
    .map(node => ({ node, place: places.get(node.placeId) }))
    .filter(entry => entry.node.drawable && entry.place?.schematicPosition)
}

function postRoutePolyline(post) {
  return routePlaces(post).map(({ place }) => `${Number(place.schematicPosition.x) * 100},${Number(place.schematicPosition.y) * 100}`).join(' ')
}

function resetSectionState() {
  foodRequestSequence += 1
  selectedProduct.value = null
  selectedFood.value = null
  selectedPostId.value = ''
  routeMessage.value = ''
}

async function showProductDetail(item) {
  selectedProduct.value = item
  await nextTick()
  productDetailRef.value?.scrollIntoView({ block: 'start' })
}

async function showFoodDetail(item) {
  selectedFood.value = item
  await nextTick()
  foodDetailRef.value?.scrollIntoView({ block: 'start' })
}

async function loadSection() {
  const current = ++sequence
  loading.value = true
  error.value = ''
  try {
    if (props.section === 'product' && !state.product.length) state.product = await listProducts()
    if (props.section === 'food' && !state.merchants.length) state.merchants = await listFoodMerchants()
    if (props.section === 'stay' && !state.stay.length) state.stay = await listStays()
    if (props.section === 'travel' && !state.map) state.map = await listPlaces()
    if (props.section === 'community') {
      if (!state.map) state.map = await listPlaces()
      state.posts = await listPosts()
    }
  } catch (reason) {
    if (current === sequence) error.value = reason?.message || '内容暂时无法读取。'
  } finally {
    if (current === sequence) loading.value = false
  }
}

async function chooseMerchant(merchant) {
  const merchantChanged = selectedMerchant.value?.id !== merchant.id
  if (!merchantChanged && (foods.value.length || loading.value)) return
  const current = ++foodRequestSequence
  selectedMerchant.value = merchant
  selectedFood.value = null
  foodType.value = ''
  if (merchantChanged) {
    foods.value = []
    Object.keys(basket).forEach(key => delete basket[key])
  }
  loading.value = true
  error.value = ''
  let menuReady = false
  try {
    const items = await listFoods(merchant.id)
    if (current === foodRequestSequence && selectedMerchant.value?.id === merchant.id) {
      foods.value = items
      menuReady = true
    }
  } catch (reason) {
    if (current === foodRequestSequence) error.value = reason?.message || '菜单暂时无法读取。'
  } finally {
    if (current === foodRequestSequence) {
      loading.value = false
      await nextTick()
      if (menuReady && window.matchMedia('(max-width: 760px)').matches) foodMenuRef.value?.scrollIntoView({ block: 'start' })
    }
  }
}

async function focusRecommendedFood(foodId) {
  if (props.section !== 'food' || !foodId) return
  try {
    const food = await getFood(foodId)
    if (!state.merchants.length) state.merchants = await listFoodMerchants()
    const merchant = state.merchants.find(item => item.id === food.merchantId)
    if (!merchant) throw new Error('该菜品所属店铺暂未在公开目录中展示。')
    await chooseMerchant(merchant)
    if (selectedMerchant.value?.id !== merchant.id) return
    selectedFood.value = foods.value.find(item => item.id === food.id) || food
    await nextTick()
    foodDetailRef.value?.scrollIntoView({ block: 'center' })
  } catch (reason) {
    error.value = reason?.message || '暂时无法定位这道餐食。'
  }
}

async function focusRecommendedResource() {
  if (props.section === 'product' && props.focusProductId) {
    const product = state.product.find(item => item.id === props.focusProductId)
    if (product) await showProductDetail(product)
    return
  }
  if (props.section === 'stay' && props.focusRoomId) {
    const property = state.stay.find(item => (item.roomTypes || []).some(room => room.id === props.focusRoomId))
    if (!property) return
    selectedStayId.value = property.id
    await nextTick()
    document.getElementById(`stay-${property.id}`)?.scrollIntoView({ block: 'center' })
    return
  }
  if (props.section === 'travel' && props.focusPlaceId) {
    const place = (state.map?.places || []).find(item => item.id === props.focusPlaceId)
    if (!place) return
    selectedRouteIds.value = [place.id]
    routeMessage.value = `已在水彩示意图中选中“${place.name}”。`
    await nextTick()
    document.getElementById(`place-${place.id}`)?.scrollIntoView({ block: 'center' })
  }
}

function normalizeQuantity(itemId) {
  const quantity = Number(basket[itemId])
  basket[itemId] = Number.isInteger(quantity) && quantity > 0 ? Math.min(quantity, 99) : 0
}

function toggleRoute(place) {
  const ids = selectedRouteIds.value.slice()
  const existing = ids.indexOf(place.id)
  routeMessage.value = ''
  if (existing >= 0) ids.splice(existing, 1)
  else if (ids.length < 12) ids.push(place.id)
  else routeMessage.value = '一条示意路线最多保留 12 个公开地点。'
  selectedRouteIds.value = ids
}

function usePostRoute(post) {
  const publishedIds = new Set((state.map?.places || []).map(place => place.id))
  selectedRouteIds.value = [...new Set((post?.routeNodes || [])
    .slice()
    .sort((left, right) => left.sequence - right.sequence)
    .map(node => node.placeId)
    .filter(id => id && publishedIds.has(id)))]
  routeMessage.value = selectedRouteIds.value.length ? '已把攻略中的公开地点放到示意图。' : '这篇攻略没有可放到当前示意图的公开地点。'
  emit('navigate', '/explore/travel')
}

function checkoutProduct(item) {
  emit('checkout', { kind: 'product', title: item.name, resource: item })
}

function checkoutFood() {
  if (!selectedMerchant.value || !basketItems.value.length) return
  emit('checkout', { kind: 'food', title: selectedMerchant.value.name, merchant: selectedMerchant.value, items: basketItems.value })
}

function checkoutStay(property, roomType) {
  emit('checkout', { kind: 'stay', title: `${property.name} · ${roomType.name}`, property, resource: roomType })
}

function tags() {
  return postForm.tags.split(/[,，]/).map(tag => tag.trim()).filter(Boolean)
}

async function submitPost() {
  postMessage.value = ''
  if (postType.value === 'ROUTE_GUIDE' && !routeGuidePlaceIds.value.length) {
    postMessage.value = '请至少选择一个公开地点作为示意路线节点。'
    return
  }
  const body = postType.value === 'MOMENT'
    ? { postType: 'MOMENT', content: postForm.content.trim(), tags: tags() }
    : {
        postType: 'ROUTE_GUIDE',
        title: postForm.title.trim(),
        content: postForm.content.trim(),
        tags: tags(),
        routeSummary: postForm.routeSummary.trim(),
        routeNodes: routeGuidePlaceIds.value.map((placeId, index) => ({ sequence: index + 1, placeId, note: null }))
      }
  posting.value = true
  try {
    await createPost(body)
    postMessage.value = '已发布到寨里。'
    postForm.title = ''
    postForm.content = ''
    postForm.tags = ''
    postForm.routeSummary = ''
    routeGuidePlaceIds.value = []
    state.posts = await listPosts()
  } catch (reason) {
    postMessage.value = reason?.message || '发布失败。'
  } finally {
    posting.value = false
  }
}

watch(() => props.section, async () => {
  resetSectionState()
  await loadSection()
  await focusRecommendedFood(props.focusFoodId)
  await focusRecommendedResource()
}, { immediate: true })

watch(() => props.focusFoodId, foodId => focusRecommendedFood(foodId))
watch(() => [props.focusProductId, props.focusRoomId, props.focusPlaceId], () => focusRecommendedResource())
</script>

<template>
  <main class="page v3-explore">
    <header class="page-intro v3-content-intro">
      <p class="eyebrow">逛乌东 · {{ activeTab.label }}</p>
      <h1>沿着山路，慢慢遇见乌东</h1>
      <p>这里呈现平台已经发布的乌东内容；资料待核验、演示价格与示意信息都会如实标明。</p>
    </header>

    <nav class="v3-module-tabs" aria-label="五个体验模块">
      <button v-for="tab in tabs" :key="tab.id" type="button" :class="{ active: section === tab.id }" @click="emit('navigate', tab.id === 'community' ? '/community' : `/explore/${tab.id}`)">{{ tab.label }}</button>
    </nav>

    <section v-if="loading" class="v3-state">正在读取{{ activeTab.label }}内容…</section>
    <section v-else-if="error" class="v3-state v3-state--error" role="alert"><b>暂时无法读取</b><p>{{ error }}</p><button class="ghost" type="button" @click="loadSection">重试</button></section>

    <template v-else-if="section === 'product'">
      <section v-if="state.product.length" class="v3-catalog-tools" aria-label="筛选商品">
        <label><span>寻找乌东好物</span><input v-model="productKeyword" type="search" placeholder="输入名称、商家或标签"></label>
        <div class="v3-filter-chips"><button type="button" :class="{ active: !productTag }" @click="productTag = ''">全部</button><button v-for="tag in productTags" :key="tag" type="button" :class="{ active: productTag === tag }" @click="productTag = productTag === tag ? '' : tag">{{ tag }}</button></div>
        <p>当前公开目录 {{ state.product.length }} 项 · 筛选后 {{ visibleProducts.length }} 项</p>
      </section>

      <article v-if="selectedProduct" ref="productDetailRef" class="v3-detail-sheet">
        <SafeImage :src="imageOf(selectedProduct, 'product')" :alt="`${selectedProduct.name}资料图片`" :label="selectedProduct.name" />
        <div><button class="v3-detail-close" type="button" aria-label="关闭商品详情" @click="selectedProduct = null">×</button><p class="eyebrow">商品详情 · {{ selectedProduct.merchantName }}</p><h2>{{ selectedProduct.name }}</h2><p>{{ selectedProduct.description }}</p><div class="v3-tag-row"><span v-for="tag in selectedProduct.tags || []" :key="tag">{{ tag }}</span></div><p v-if="isTeaIllustration(selectedProduct)" class="v3-notice">茶品资料示意，不代表当前商品实物。</p><dl><dt>取货地点</dt><dd>{{ selectedProduct.pickupPoint }}</dd><dt>资料状态</dt><dd>{{ catalogNote(selectedProduct) }}</dd></dl><p v-if="price(selectedProduct)" class="v3-price">¥{{ price(selectedProduct).amount }} <small>{{ price(selectedProduct).kind }} · {{ price(selectedProduct).label }}</small></p><p v-else class="v3-muted">暂无可展示价格</p><button class="primary" type="button" :disabled="!selectedProduct.orderable" @click="checkoutProduct(selectedProduct)">{{ selectedProduct.orderable ? '选择数量并核价' : '当前仅供查看' }}</button></div>
      </article>

      <section class="v3-card-grid">
        <article v-for="item in visibleProducts" :key="item.id" class="v3-resource-card">
          <SafeImage :src="imageOf(item, 'product')" :alt="`${item.name}资料图片`" :label="item.name" loading="lazy" />
          <div><p class="eyebrow">{{ item.merchantName }}</p><h2>{{ item.name }}</h2><p>{{ item.description }}</p><div class="v3-tag-row"><span v-for="tag in item.tags || []" :key="tag">{{ tag }}</span></div><p v-if="isTeaIllustration(item)" class="v3-source-note">茶品资料示意，不代表当前商品实物</p><p class="v3-source-note">{{ catalogNote(item) }}</p><p v-if="price(item)" class="v3-price">¥{{ price(item).amount }} <small>{{ price(item).kind }} · {{ price(item).label }}</small></p><p v-else class="v3-muted">暂无可展示价格</p><button class="ghost" type="button" @click="showProductDetail(item)">查看详情</button></div>
        </article>
        <p v-if="state.product.length && !visibleProducts.length" class="v3-state">没有匹配的商品，试试清空关键词或标签。</p>
        <p v-if="!state.product.length" class="v3-state">当前没有已发布商品。</p>
      </section>
    </template>

    <section v-else-if="section === 'food'" class="v3-food-layout">
      <aside class="v3-merchant-list"><p class="eyebrow">按店选餐</p><h2>先选一家店</h2><label class="v3-merchant-search"><span>找一家店</span><input v-model="merchantKeyword" type="search" placeholder="输入店名、菜品或标签"></label><p v-if="state.merchants.length" class="v3-merchant-count">{{ merchantKeyword ? `匹配 ${visibleMerchants.length} / ${state.merchants.length} 家` : `当前 ${state.merchants.length} 家公开店铺` }}</p><button v-for="merchant in visibleMerchants" :key="merchant.id" type="button" :class="{ active: selectedMerchant?.id === merchant.id }" @click="chooseMerchant(merchant)"><SafeImage :src="imageOf(merchant, 'food')" :alt="`${merchant.name}资料图片`" :label="merchant.name" loading="lazy" /><span><b>{{ merchant.name }}</b><small>{{ merchant.description }}</small></span></button><p v-if="state.merchants.length && !visibleMerchants.length" class="v3-muted">没有匹配的店铺，试试清空关键词。</p><p v-if="!state.merchants.length">当前没有已发布餐食店铺。</p></aside>
      <div ref="foodMenuRef" class="v3-menu">
        <header><div><p class="eyebrow">同店多菜</p><h2>{{ selectedMerchant?.name || '选择店铺后查看菜单' }}</h2><p v-if="selectedMerchant" class="v3-source-note">{{ catalogNote(selectedMerchant) }}</p></div><button v-if="basketItems.length" class="primary" type="button" @click="checkoutFood">{{ basketItems.length }} 种 · {{ basketPortions }} 份 · 去填写到店信息</button></header>
        <section v-if="!selectedMerchant && foodStartingMerchants.length" class="v3-food-start">
          <div><p class="eyebrow">从一口风味开始</p><h3>先挑一家想坐下来的店</h3><p>乌东的餐食以具体店铺为单位整理。选店后可查看菜单、调整份数，再由你决定是否核价与提交。</p><small>不跨店合单 · 演示价格以现场核对为准</small></div>
          <div class="v3-food-start-options"><button v-for="merchant in foodStartingMerchants" :key="merchant.id" type="button" @click="chooseMerchant(merchant)"><SafeImage :src="imageOf(merchant, 'food')" :alt="`${merchant.name}资料图片`" :label="merchant.name" loading="lazy" /><span><b>{{ merchant.name }}</b><small>{{ (merchant.tags || []).slice(0, 2).join(' · ') || '乌东餐食资料' }}</small></span><i>查看菜单 →</i></button></div>
        </section>
        <div v-if="foodTypes.length" class="v3-filter-chips"><button type="button" :class="{ active: !foodType }" @click="foodType = ''">全部</button><button v-for="type in foodTypes" :key="type" type="button" :class="{ active: foodType === type }" @click="foodType = type">{{ foodTypeLabels[type] }}</button></div>
        <article v-if="selectedFood" ref="foodDetailRef" class="v3-inline-detail"><SafeImage :src="imageOf(selectedFood, 'food')" :alt="`${selectedFood.name}资料图片`" :label="selectedFood.name" /><div><button class="v3-detail-close" type="button" aria-label="关闭餐食详情" @click="selectedFood = null">×</button><p class="eyebrow">{{ foodTypeLabels[selectedFood.itemType] || '餐食' }}详情</p><h3>{{ selectedFood.name }}</h3><p>{{ selectedFood.description }}</p><div class="v3-tag-row"><span v-for="tag in selectedFood.tags || []" :key="tag">{{ tag }}</span></div><p v-if="selectedFood.visitTimeText">接待说明：{{ selectedFood.visitTimeText }}</p><p v-if="price(selectedFood)" class="v3-price">¥{{ price(selectedFood).amount }} / 份 <small>{{ price(selectedFood).kind }} · {{ price(selectedFood).label }}</small></p><p class="v3-source-note">{{ catalogNote(selectedFood) }}</p></div></article>
        <article v-for="item in visibleFoods" :key="item.id" class="v3-menu-row"><SafeImage :src="imageOf(item, 'food')" :alt="`${item.name}资料图片`" :label="item.name" loading="lazy" /><div><p class="eyebrow">{{ foodTypeLabels[item.itemType] || '餐食' }}</p><h3>{{ item.name }}</h3><p>{{ item.description }}</p><div class="v3-tag-row"><span v-for="tag in item.tags || []" :key="tag">{{ tag }}</span></div><p v-if="price(item)" class="v3-price">¥{{ price(item).amount }} / 份 <small>{{ price(item).kind }} · {{ price(item).label }}</small></p><button class="v3-text-button" type="button" @click="showFoodDetail(item)">查看详情</button></div><label><span>{{ item.orderable ? '份数' : '仅供查看' }}</span><input v-model.number="basket[item.id]" type="number" min="0" max="99" step="1" :disabled="!item.orderable" @change="normalizeQuantity(item.id)"></label></article>
        <p v-if="selectedMerchant && !visibleFoods.length" class="v3-state">这个分类暂时没有餐食。</p>
      </div>
    </section>

    <section v-else-if="section === 'stay'" class="v3-stay-list">
      <header class="v3-count-strip"><p class="eyebrow">山居目录</p><strong>{{ state.stay.length }} 家住宿 · {{ roomTotal }} 种房型</strong><span>数量来自当前已发布目录；房价与容量均按演示信息展示，不代表实时房态。</span></header>
      <section class="v3-stay-tools"><label><span>找山居</span><input v-model="stayKeyword" type="search" placeholder="民宿名、位置、特色或房型"></label><label><span>入住人数</span><select v-model="stayPeople"><option value="">不限</option><option v-for="count in [1, 2, 3, 4, 5, 6]" :key="count" :value="count">{{ count }} 人</option></select></label><p>{{ stayKeyword || stayPeople ? `匹配 ${visibleStays.length} / ${state.stay.length} 家住宿` : '可按关键词或单间容纳人数筛选' }}</p></section>
      <article v-for="property in visibleStays" :id="`stay-${property.id}`" :key="property.id" class="v3-resource-card v3-stay-card"><SafeImage :src="imageOf(property, 'stay')" :alt="`${property.name}资料图片`" :label="property.name" loading="lazy" /><div><p class="eyebrow">{{ property.locationText || property.merchantName }}</p><h2>{{ property.name }}</h2><p>{{ property.description }}</p><div class="v3-tag-row"><span v-for="tag in property.tags || []" :key="tag">{{ tag }}</span></div><p class="v3-source-note">{{ catalogNote(property) }}</p><button class="ghost" type="button" @click="selectedStayId = selectedStayId === property.id ? '' : property.id">{{ selectedStayId === property.id ? '收起房型' : `查看 ${property.roomTypes?.length || 0} 种房型` }}</button></div><section v-if="selectedStayId === property.id" class="v3-room-grid"><article v-for="room in property.roomTypes || []" :key="room.id"><SafeImage v-if="imageOf(room, 'stay')" :src="imageOf(room, 'stay')" :alt="`${room.name}房型资料图片`" :label="room.name" loading="lazy" /><div><p v-if="!imageOf(room, 'stay')" class="v3-room-image-note">住宿配图见上方，房型以文字资料为准。</p><h3>{{ room.name }}</h3><p>{{ room.description }}</p><p><b>每间演示容量 {{ room.maxGuestsPerRoom }} 人</b></p><p class="v3-source-note">{{ catalogNote(room) }}</p><p v-if="price(room)" class="v3-price">¥{{ price(room).amount }} / 间夜<small>{{ price(room).kind }} · {{ price(room).label }}</small></p><p v-else class="v3-muted">暂无演示价</p><button class="primary" type="button" :disabled="!room.orderable" @click="checkoutStay(property, room)">{{ room.orderable ? '选择此房型' : '当前仅供查看' }}</button></div></article></section></article>
      <p v-if="state.stay.length && !visibleStays.length" class="v3-state">没有符合当前关键词或单间人数条件的住宿，试试放宽筛选。</p>
      <p v-if="!state.stay.length" class="v3-state">当前没有已发布住宿。</p>
    </section>

    <section v-else-if="section === 'travel'" class="v3-map-panel">
      <header><p class="eyebrow">水彩示意地图</p><h2>按自己的顺序，串起乌东地点</h2><p>{{ state.map?.notice }}</p><p class="v3-notice">非等比例、非实时导航；不据此计算道路、距离、时长或安全承诺。</p></header>
      <div class="v3-route-toolbar"><div><b>我的示意路线</b><span>{{ selectedRoutePlaces.length ? `${selectedRoutePlaces.length} 个公开地点` : '点击地图或地点卡片开始选择' }}</span></div><button v-if="selectedRoutePlaces.length" class="ghost" type="button" @click="selectedRouteIds = []; routeMessage = ''">清空</button></div>
      <div class="v3-map-canvas" role="group" aria-label="乌东地点相对位置与自选示意路线"><svg aria-hidden="true" viewBox="0 0 100 100" preserveAspectRatio="none"><polyline v-if="drawableRoutePlaces.length > 1" :points="routePolyline" /></svg><button v-for="place in drawablePlaces" :key="place.id" type="button" class="v3-map-pin" :class="{ selected: routeOrder(place.id) }" :style="mapStyle(place)" @click="toggleRoute(place)"><i>{{ routeOrder(place.id) }}</i>{{ place.name }}</button></div>
      <ol v-if="selectedRoutePlaces.length" class="v3-route-sequence"><li v-for="(place, index) in selectedRoutePlaces" :key="place.id"><b>{{ index + 1 }}</b><span>{{ place.name }}<small v-if="!place.schematicPosition">仅文字节点，未绘制位置</small></span><button type="button" @click="toggleRoute(place)">移除</button></li></ol><p v-if="routeMessage" class="v3-notice">{{ routeMessage }}</p>
      <div class="v3-place-list"><article v-for="place in state.map?.places || []" :id="`place-${place.id}`" :key="place.id" :class="{ selected: routeOrder(place.id) }"><SafeImage :src="placeMediaUrl(place.id)" :alt="`${place.name}资料图片`" :label="place.name" loading="lazy" /><div><p class="eyebrow">{{ place.category }}</p><h3>{{ place.name }}</h3><p>{{ place.description }}</p><div class="v3-tag-row"><span v-for="tag in place.tags || []" :key="tag">{{ tag }}</span></div><p class="v3-source-note">{{ catalogNote(place) }}</p><small v-if="!place.schematicPosition">仅文字节点，未绘制位置</small><button class="ghost" type="button" @click="toggleRoute(place)">{{ routeOrder(place.id) ? `路线第 ${routeOrder(place.id)} 站` : '加入示意路线' }}</button></div></article></div>
    </section>

    <template v-else>
      <section class="v3-community-layout">
        <div><header class="v3-reading-head"><p class="eyebrow">文化阅读与寨里分享</p><h2>从一篇文章，走近一段山里日常</h2><p>文化资料与个人分享并列呈现；路线内容始终只作示意。</p></header><section v-if="state.posts.length" class="v3-community-filters"><div><button type="button" :class="{ active: !postFilter }" @click="postFilter = ''">全部</button><button type="button" :class="{ active: postFilter === 'MOMENT' }" @click="postFilter = 'MOMENT'">寨里动态</button><button type="button" :class="{ active: postFilter === 'ROUTE_GUIDE' }" @click="postFilter = 'ROUTE_GUIDE'">示意路线</button></div><div v-if="communityTags.length"><button type="button" :class="{ active: !postTag }" @click="postTag = ''">全部标签</button><button v-for="tag in communityTags" :key="tag" type="button" :class="{ active: postTag === tag }" @click="postTag = postTag === tag ? '' : tag">{{ tag }}</button></div></section><article v-for="post in visiblePosts" :key="post.id" class="v3-post" :class="{ 'is-reading': selectedPostId === post.id }"><SafeImage v-if="postMediaUrl(post.id)" :src="postMediaUrl(post.id)" :alt="`${post.title}资料参考图`" :label="post.title || '乌东文化文章'" loading="lazy" /><div><p class="eyebrow">{{ postLabel(post) }} · {{ post.authorName }}</p><h2>{{ post.title || '山里片刻' }}</h2><p v-if="postMediaUrl(post.id)" class="v3-source-note">原始资料参考图，公开使用范围待确认</p><p class="v3-source-note">{{ post.legacyData ? '资料参考内容' : post.demoData ? '演示分享' : '公开分享' }}</p><p class="v3-post-copy">{{ selectedPostId === post.id ? post.content : postExcerpt(post) }}</p><button v-if="String(post.content || '').length > 120" class="v3-text-button" type="button" @click="selectedPostId = selectedPostId === post.id ? '' : post.id">{{ selectedPostId === post.id ? '收起全文' : '阅读全文' }}</button><template v-if="post.postType === 'ROUTE_GUIDE'"><div v-if="routePlaces(post).length" class="v3-route-sketch" role="img" :aria-label="`${post.title}水彩示意节点顺序`"><svg viewBox="0 0 100 100" preserveAspectRatio="none"><polyline :points="postRoutePolyline(post)" /></svg><span v-for="(entry, index) in routePlaces(post)" :key="`${post.id}-${entry.node.sequence}-${index}`" :style="mapStyle(entry.place)"><i>{{ entry.node.sequence }}</i>{{ entry.place.name }}</span></div><p class="v3-notice">{{ routeNotice(post) }}</p><ol><li v-for="(node, index) in post.routeNodes" :key="`${post.id}-${node.sequence}-${index}`">{{ node.placeName }}<small v-if="!node.drawable">（仅文字，不绘制）</small></li></ol><button class="ghost" type="button" @click="usePostRoute(post)">在示意图查看这条路线</button></template><footer>{{ post.tags?.join(' · ') }}</footer></div></article><p v-if="state.posts.length && !visiblePosts.length" class="v3-state">没有符合当前筛选的寨里内容，试试清空标签或类型。</p><p v-if="!state.posts.length" class="v3-state">寨里暂时没有公开分享。</p></div>
        <form class="v3-post-form" @submit.prevent="submitPost"><p class="eyebrow">登录后分享</p><h2>写一页寨里手账</h2><p v-if="!authState.user.account">请先到“我的”登录平台账号。</p><template v-else><label>类型<select v-model="postType"><option value="MOMENT">日常记录</option><option value="ROUTE_GUIDE">示意路线攻略</option></select></label><label v-if="postType === 'ROUTE_GUIDE'">标题<input v-model="postForm.title" maxlength="80" required></label><label>正文<textarea v-model="postForm.content" maxlength="2000" required></textarea></label><label>标签（逗号分隔）<input v-model="postForm.tags" placeholder="村寨生活"></label><template v-if="postType === 'ROUTE_GUIDE'"><label>路线说明<input v-model="postForm.routeSummary" maxlength="300" required></label><fieldset class="v3-route-picker"><legend>公开地点（最多 12 个）</legend><p>{{ routeGuidePlaceIds.length ? `已选 ${routeGuidePlaceIds.length} 个，按编号即发布顺序` : '点击地点加入路线，再次点击可移除。' }}</p><div><button v-for="place in state.map?.places || []" :key="place.id" type="button" :class="{ active: routeGuideOrder(place.id) }" @click="toggleRouteGuidePlace(place)"><i>{{ routeGuideOrder(place.id) || '+' }}</i>{{ place.name }}</button></div></fieldset><small>路线仅为水彩示意顺序，不提供真实导航。</small></template><button class="primary" :disabled="posting">{{ posting ? '发布中…' : '发布' }}</button><p v-if="postMessage">{{ postMessage }}</p></template></form>
      </section>
    </template>
  </main>
</template>
