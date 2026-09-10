<script setup>
import { computed, reactive, ref, watch } from 'vue'
import SafeImage from '../components/common/SafeImage.vue'
import { authState } from '../services/authSession'
import { createPost, listFoodMerchants, listFoods, listPlaces, listPosts, listProducts, listStays } from '../services/tourismApi'

const props = defineProps({ section: { type: String, default: 'product' } })
const emit = defineEmits(['navigate', 'checkout'])
const tabs = [
  { id: 'product', label: '商品' },
  { id: 'food', label: '食' },
  { id: 'stay', label: '住' },
  { id: 'travel', label: '行' },
  { id: 'community', label: '社区' }
]
const state = reactive({ product: [], merchants: [], stay: [], posts: [], map: null })
const loading = ref(false)
const error = ref('')
const selectedMerchant = ref(null)
const foods = ref([])
const basket = reactive({})
const postType = ref('MOMENT')
const postForm = reactive({ title: '', content: '', tags: '', routeSummary: '', placeId: '' })
const posting = ref(false)
const postMessage = ref('')
let sequence = 0

const activeTab = computed(() => tabs.find(tab => tab.id === props.section) || tabs[0])
const basketItems = computed(() => foods.value
  .filter(item => Number(basket[item.id]) > 0)
  .map(item => ({ foodItemId: item.id, quantity: Number(basket[item.id]), resource: item })))
const drawablePlaces = computed(() => (state.map?.places || []).filter(place => place.schematicPosition))

function price(item) {
  if (item?.demoPrice) return { amount: item.demoPrice.amount, label: item.demoPrice.simulationNote }
  if (item?.referencePrice) return { amount: item.referencePrice.amount, label: `参考来源：${item.referencePrice.sourceTitle}` }
  return null
}

function mapStyle(place) {
  const position = place?.schematicPosition
  return position ? { left: `${Number(position.x) * 100}%`, top: `${Number(position.y) * 100}%` } : {}
}

function routePlaces(post) {
  const places = new Map((state.map?.places || []).map(place => [place.id, place]))
  return (post?.routeNodes || [])
    .slice()
    .sort((left, right) => left.sequence - right.sequence)
    .map(node => ({ node, place: places.get(node.placeId) }))
    .filter(entry => entry.node.drawable && entry.place?.schematicPosition)
}

function routePolyline(post) {
  return routePlaces(post).map(({ place }) => `${Number(place.schematicPosition.x) * 100},${Number(place.schematicPosition.y) * 100}`).join(' ')
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
  selectedMerchant.value = merchant
  foods.value = []
  Object.keys(basket).forEach(key => delete basket[key])
  loading.value = true
  error.value = ''
  try {
    foods.value = await listFoods(merchant.id)
  } catch (reason) {
    error.value = reason?.message || '菜单暂时无法读取。'
  } finally {
    loading.value = false
  }
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
  const body = postType.value === 'MOMENT'
    ? { postType: 'MOMENT', content: postForm.content.trim(), tags: tags() }
    : {
        postType: 'ROUTE_GUIDE',
        title: postForm.title.trim(),
        content: postForm.content.trim(),
        tags: tags(),
        routeSummary: postForm.routeSummary.trim(),
        routeNodes: [{ sequence: 1, placeId: postForm.placeId, note: null }]
      }
  posting.value = true
  try {
    await createPost(body)
    postMessage.value = '已发布到寨里。'
    postForm.title = ''
    postForm.content = ''
    postForm.tags = ''
    postForm.routeSummary = ''
    postForm.placeId = ''
    state.posts = await listPosts()
  } catch (reason) {
    postMessage.value = reason?.message || '发布失败。'
  } finally {
    posting.value = false
  }
}

watch(() => props.section, loadSection, { immediate: true })
</script>

<template>
  <main class="page v3-explore">
    <header class="page-intro">
      <p class="eyebrow">逛乌东 · {{ activeTab.label }}</p>
      <h1>沿着山路，慢慢遇见乌东</h1>
      <p>商品、餐食、住宿、地点与寨里分享均来自本机 v3 服务；没有数据时不会用假资源补位。</p>
    </header>

    <nav class="v3-module-tabs" aria-label="五个体验模块">
      <button v-for="tab in tabs" :key="tab.id" :class="{ active: section === tab.id }" @click="emit('navigate', tab.id === 'community' ? '/community' : `/explore/${tab.id}`)">{{ tab.label }}</button>
    </nav>

    <section v-if="loading" class="v3-state">正在读取{{ activeTab.label }}内容…</section>
    <section v-else-if="error" class="v3-state v3-state--error" role="alert"><b>暂时无法读取</b><p>{{ error }}</p><button class="ghost" @click="loadSection">重试</button></section>

    <section v-else-if="section === 'product'" class="v3-card-grid">
      <article v-for="item in state.product" :key="item.id" class="v3-resource-card">
        <SafeImage :src="item.imageUrl" :alt="`${item.name}公开图片`" :label="item.name" />
        <div><p class="eyebrow">{{ item.merchantName }}</p><h2>{{ item.name }}</h2><p>{{ item.description }}</p><p v-if="price(item)" class="v3-price">¥{{ price(item).amount }} <small>{{ price(item).label }}</small></p><p v-else class="v3-muted">暂无可展示价格</p><button class="primary" :disabled="!item.orderable" @click="checkoutProduct(item)">{{ item.orderable ? '选择数量并核价' : '暂不可提交' }}</button></div>
      </article>
      <p v-if="!state.product.length" class="v3-state">当前没有已发布商品。</p>
    </section>

    <section v-else-if="section === 'food'" class="v3-food-layout">
      <aside class="v3-merchant-list"><h2>先选一家店</h2><button v-for="merchant in state.merchants" :key="merchant.id" :class="{ active: selectedMerchant?.id === merchant.id }" @click="chooseMerchant(merchant)"><b>{{ merchant.name }}</b><span>{{ merchant.description }}</span></button><p v-if="!state.merchants.length">当前没有已发布餐食店铺。</p></aside>
      <div class="v3-menu"><header><div><p class="eyebrow">同店多菜</p><h2>{{ selectedMerchant?.name || '选择店铺后查看菜单' }}</h2></div><button v-if="basketItems.length" class="primary" @click="checkoutFood">{{ basketItems.length }} 种餐食 · 去填写到店信息</button></header><article v-for="item in foods" :key="item.id" class="v3-menu-row"><div><h3>{{ item.name }}</h3><p>{{ item.description }}</p><p v-if="price(item)" class="v3-price">¥{{ price(item).amount }} / 份 <small>{{ price(item).label }}</small></p></div><label><span>{{ item.orderable ? '份数' : '暂不可提交' }}</span><input v-model.number="basket[item.id]" type="number" min="0" max="99" step="1" :disabled="!item.orderable"></label></article></div>
    </section>

    <section v-else-if="section === 'stay'" class="v3-card-grid">
      <article v-for="property in state.stay" :key="property.id" class="v3-resource-card v3-resource-card--wide"><SafeImage :src="property.imageUrl" :alt="`${property.name}公开图片`" :label="property.name" /><div><p class="eyebrow">{{ property.locationText || property.merchantName }}</p><h2>{{ property.name }}</h2><p>{{ property.description }}</p><div class="v3-room-list"><button v-for="room in property.roomTypes" :key="room.id" :disabled="!room.orderable" @click="checkoutStay(property, room)"><span><b>{{ room.name }}</b><small>每间演示容量 {{ room.maxGuestsPerRoom }} 人</small></span><span v-if="price(room)">¥{{ price(room).amount }} / 间夜</span><span v-else>暂无演示价</span></button></div></div></article>
      <p v-if="!state.stay.length" class="v3-state">当前没有已发布住宿。</p>
    </section>

    <section v-else-if="section === 'travel'" class="v3-map-panel">
      <header><p class="eyebrow">{{ state.map?.mapMode }}</p><h2>乌东水彩示意图</h2><p>{{ state.map?.notice }}</p></header>
      <div class="v3-map-canvas" role="img" aria-label="乌东地点相对位置示意图"><span v-for="place in drawablePlaces" :key="place.id" class="v3-map-pin" :style="mapStyle(place)"><i></i>{{ place.name }}</span></div>
      <div class="v3-place-list"><article v-for="place in state.map?.places || []" :key="place.id"><b>{{ place.name }}</b><span>{{ place.category }}</span><p>{{ place.description }}</p><small v-if="!place.schematicPosition">仅文字节点，未绘制位置</small></article></div>
    </section>

    <template v-else>
      <section class="v3-community-layout">
        <div><article v-for="post in state.posts" :key="post.id" class="v3-post"><p class="eyebrow">{{ post.postType === 'ROUTE_GUIDE' ? '示意路线攻略' : '寨里动态' }} · {{ post.authorName }}</p><h2>{{ post.title || '山里片刻' }}</h2><p>{{ post.content }}</p><template v-if="post.postType === 'ROUTE_GUIDE'"><div v-if="routePlaces(post).length" class="v3-route-sketch" role="img" :aria-label="`${post.title}水彩示意节点顺序`"><svg viewBox="0 0 100 100" preserveAspectRatio="none"><polyline :points="routePolyline(post)" /></svg><span v-for="entry in routePlaces(post)" :key="entry.node.sequence" :style="mapStyle(entry.place)"><i>{{ entry.node.sequence }}</i>{{ entry.place.name }}</span></div><p class="v3-notice">{{ post.routeSummary }}。仅为水彩示意顺序，不提供真实导航。</p><ol><li v-for="node in post.routeNodes" :key="`${post.id}-${node.sequence}`">{{ node.placeName }}<small v-if="!node.drawable">（仅文字，不绘制）</small></li></ol></template><footer>{{ post.tags?.join(' · ') }}</footer></article><p v-if="!state.posts.length" class="v3-state">寨里暂时没有公开分享。</p></div>
        <form class="v3-post-form" @submit.prevent="submitPost"><p class="eyebrow">登录后分享</p><h2>写一页寨里手账</h2><p v-if="!authState.user.account">请先到“我的”登录平台账号。</p><template v-else><label>类型<select v-model="postType"><option value="MOMENT">日常记录</option><option value="ROUTE_GUIDE">示意路线攻略</option></select></label><label v-if="postType === 'ROUTE_GUIDE'">标题<input v-model="postForm.title" maxlength="80" required></label><label>正文<textarea v-model="postForm.content" maxlength="2000" required></textarea></label><label>标签（逗号分隔）<input v-model="postForm.tags" placeholder="村寨生活"></label><template v-if="postType === 'ROUTE_GUIDE'"><label>路线说明<input v-model="postForm.routeSummary" maxlength="300" required></label><label>公开地点<select v-model="postForm.placeId" required><option value="">请选择真实平台地点</option><option v-for="place in state.map?.places || []" :key="place.id" :value="place.id">{{ place.name }}</option></select></label><small>本期只提交一个真实地点节点；路线仍是水彩示意，不提供导航。</small></template><button class="primary" :disabled="posting">{{ posting ? '发布中…' : '发布' }}</button><p v-if="postMessage">{{ postMessage }}</p></template></form>
      </section>
    </template>
  </main>
</template>
