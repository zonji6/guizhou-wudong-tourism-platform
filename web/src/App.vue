<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { allowedCardTypes, categories, posts, services } from './data/demo'

const route = ref(location.hash.slice(1) || '/')
const selected = ref(services[0])
const assistantInput = ref('我想体验贵州乌冬的苗族文化和茶旅')
const assistantStage = ref('')
const assistantCard = ref(null)
const booking = ref({ date: '2026-09-20', people: 2, contact: '答辩体验官' })
const bookingDone = ref(false)
const activeCategory = ref('all')
const orders = ref([{ id: 'WD20260908001', name: '苗寨古茶园 · 制茶品茗体验', date: '2026-09-20', people: 2, status: '待确认' }])
const adminTab = ref('orders')

const isAdmin = computed(() => route.value.startsWith('/admin'))
const filteredServices = computed(() => activeCategory.value === 'all' ? services : services.filter(item => item.category === activeCategory.value))
const pageTitle = computed(() => ({
  '/resources': '遇见乌冬', '/community': '寨里分享', '/assistant': '乌冬 AI 旅伴', '/booking': '确认预约'
}[route.value] || '贵州乌冬文旅'))

function go(path) { location.hash = path }
function chooseService(item) { selected.value = item; go('/resource/' + item.id) }
function openBooking(item = selected.value) { selected.value = item; bookingDone.value = false; go('/booking') }
function goAssistant() { go('/assistant') }
function onHashChange() { route.value = location.hash.slice(1) || '/' }
function categoryName(id) { return categories.find(item => item.id === id)?.label || '乌冬体验' }

function renderCard(card) {
  return allowedCardTypes.includes(card.type) ? card : { type: 'error', title: '内容暂不可展示', text: 'AI 返回了不支持的卡片类型。' }
}

function askAssistant() {
  assistantCard.value = null
  assistantStage.value = '正在理解你的旅行心愿…'
  window.setTimeout(() => { assistantStage.value = '正在检索贵州乌冬茶旅资料…' }, 600)
  window.setTimeout(() => { assistantStage.value = '正在匹配茶园、苗寨与民宿…' }, 1200)
  window.setTimeout(() => {
    const needsDetails = !/(\d+).{0,4}(人|位)|日期|明天|周末/.test(assistantInput.value)
    assistantCard.value = renderCard(needsDetails
      ? { type: 'clarifying_question', title: '为你把茶旅安排得刚刚好', text: '想先确认一下：计划哪天出发、几位同行，以及更偏好半日体验还是两天一夜慢游？', chips: ['周末，两位', '两天一夜', '带孩子同行'] }
      : { type: 'itinerary', title: '苗族文化茶旅 · 两天一夜', text: '从一盏山茶开始，走进乌冬的苗寨日常。', items: ['Day 1｜古茶园采茶与制茶品茗', 'Day 1｜苗寨长桌宴与火塘夜话', 'Day 2｜苗绣纹样手作与山谷慢行'], sources: ['《乌冬古茶园体验指南》', '《苗寨待客与长桌宴》'] })
    assistantStage.value = '方案已生成'
  }, 1800)
}

function submitBooking() {
  const order = { id: `WD${Date.now().toString().slice(-9)}`, name: selected.value.title, date: booking.value.date, people: booking.value.people, status: '待确认' }
  orders.value.unshift(order)
  bookingDone.value = true
}

function updateOrder(order) { order.status = order.status === '待确认' ? '已确认' : '已完成' }
onMounted(() => addEventListener('hashchange', onHashChange))
onUnmounted(() => removeEventListener('hashchange', onHashChange))
</script>

<template>
  <div class="app-shell" :class="{ 'admin-shell': isAdmin }">
    <template v-if="!isAdmin">
      <header class="topbar">
        <a class="brand" href="#/">贵州乌冬 <span>· 文旅</span></a>
        <nav><a href="#/resources">遇见乌冬</a><a href="#/community">寨里分享</a><a href="#/assistant">AI 旅伴</a></nav>
        <button class="admin-link" @click="go('/admin')">运营后台</button>
      </header>

      <main v-if="route === '/'" class="home-page">
        <section class="hero">
          <div class="hero-copy"><p class="eyebrow">GUIZHOU WUDONG · DEMO</p><h1>在山雾与茶香里<br><em>遇见苗寨的温度</em></h1><p>一段以茶为引、以苗寨为家的贵州乌冬慢旅行。</p><div class="actions"><button class="primary" @click="goAssistant">让 AI 帮我规划</button><button class="ghost" @click="go('/resources')">浏览乌冬体验</button></div></div>
          <div class="hero-note">苗族文化茶旅体验<br><small>本页资源、价格均为答辩演示数据</small></div>
        </section>
        <section class="section"><div class="section-head"><p class="eyebrow">从一杯茶开始</p><h2>衣食住行，都有乌冬的山野味</h2></div><div class="category-grid"><button v-for="item in categories" :key="item.id" class="category-card" @click="activeCategory = item.id; go('/resources')"><b>{{ item.icon }}</b><span>{{ item.label }}</span><small>查看体验</small></button></div></section>
        <section class="section featured"><div class="section-head inline"><div><p class="eyebrow">精选体验</p><h2>把时间留给山与人</h2></div><button class="text-button" @click="go('/resources')">查看全部 →</button></div><div class="service-grid"><article v-for="item in services.slice(0, 3)" :key="item.id" class="service-card" @click="chooseService(item)"><img :src="item.image" :alt="item.title"><div><p>{{ categoryName(item.category) }}</p><h3>{{ item.title }}</h3><span>¥{{ item.price }} 起 <i>演示数据</i></span></div></article></div></section>
      </main>

      <main v-else-if="route === '/resources'" class="page"><div class="page-intro"><p class="eyebrow">乌冬体验清单</p><h1>{{ pageTitle }}</h1><p>从茶园、火塘到山谷慢行，每一项均为演示资源。</p></div><div class="filter"><button :class="{active: activeCategory === 'all'}" @click="activeCategory = 'all'">全部</button><button v-for="item in categories" :key="item.id" :class="{active: activeCategory === item.id}" @click="activeCategory = item.id">{{ item.label }}</button></div><div class="service-grid all"><article v-for="item in filteredServices" :key="item.id" class="service-card" @click="chooseService(item)"><img :src="item.image" :alt="item.title"><div><p>{{ categoryName(item.category) }} · {{ item.tags.join(' · ') }}</p><h3>{{ item.title }}</h3><span>¥{{ item.price }} 起 <i>演示数据</i></span></div></article></div></main>

      <main v-else-if="route.startsWith('/resource/')" class="page detail"><img class="detail-image" :src="selected.image" :alt="selected.title"><div class="detail-copy"><p class="eyebrow">{{ categoryName(selected.category) }} · 演示资源</p><h1>{{ selected.title }}</h1><div class="tag-row"><span v-for="tag in selected.tags" :key="tag">{{ tag }}</span></div><p class="intro">{{ selected.intro }}</p><div class="price">¥{{ selected.price }} <small>起 / 演示价格</small></div><button class="primary" @click="openBooking(selected)">预约这项体验</button></div></main>

      <main v-else-if="route === '/community'" class="page"><div class="page-intro"><p class="eyebrow">寨里分享</p><h1>听当地人讲山的故事</h1><p>攻略与动态均为答辩演示内容。</p></div><div class="post-grid"><article v-for="post in posts" :key="post.id" class="post-card"><img :src="post.cover" :alt="post.title"><div><small>{{ post.author }}</small><h2>{{ post.title }}</h2><p>{{ post.text }}</p><span>♡ {{ post.likes }}</span></div></article></div></main>

      <main v-else-if="route === '/assistant'" class="page assistant-page"><div class="page-intro"><p class="eyebrow">乌冬 AI 旅伴</p><h1>把你的心愿交给山茶</h1><p>仅展示可执行进度和结构化结果，不展示模型思维链。</p></div><section class="assistant-panel"><div class="suggestions"><button @click="assistantInput = '周末两位，安排两天一夜的苗族文化茶旅'; askAssistant()">两天一夜茶旅</button><button @click="assistantInput = '推荐适合亲子的苗绣体验'; askAssistant()">亲子苗绣</button><button @click="assistantInput = '我想体验贵州乌冬的苗族文化和茶旅'; askAssistant()">苗寨茶旅</button></div><textarea v-model="assistantInput" aria-label="输入旅行需求" placeholder="例如：我想体验贵州乌冬的苗族文化和茶旅"></textarea><button class="primary" @click="askAssistant">请 AI 规划</button><p v-if="assistantStage" class="progress">● {{ assistantStage }}</p><article v-if="assistantCard" class="agent-card"><p class="card-type">{{ assistantCard.type }}</p><h2>{{ assistantCard.title }}</h2><p>{{ assistantCard.text }}</p><ul v-if="assistantCard.items"><li v-for="item in assistantCard.items" :key="item">{{ item }}</li></ul><div v-if="assistantCard.chips" class="chip-row"><button v-for="chip in assistantCard.chips" :key="chip" @click="assistantInput = chip; askAssistant()">{{ chip }}</button></div><footer v-if="assistantCard.sources">资料来源：<span v-for="source in assistantCard.sources" :key="source">{{ source }}</span></footer></article></section></main>

      <main v-else-if="route === '/booking'" class="page booking-page"><div class="page-intro"><p class="eyebrow">预约确认</p><h1>留一席山里的慢时光</h1></div><section v-if="!bookingDone" class="booking-card"><div><img :src="selected.image" :alt="selected.title"><h2>{{ selected.title }}</h2><p>¥{{ selected.price }} 起 · 演示资源</p></div><label>日期<input v-model="booking.date" type="date"></label><label>人数<input v-model.number="booking.people" type="number" min="1"></label><label>联系人<input v-model="booking.contact"></label><button class="primary" @click="submitBooking">确认提交预约</button><small>提交后会生成演示订单；真实订单需由 Java 服务确认。</small></section><section v-else class="success-card"><b>✓</b><h2>预约申请已提交</h2><p>演示订单已进入“待确认”，可前往运营后台处理。</p><button class="primary" @click="go('/admin')">去运营后台查看</button></section></main>

      <main v-else class="page"><h1>页面正在赶路</h1><button class="primary" @click="go('/')">返回首页</button></main>
    </template>

    <template v-else>
      <aside class="admin-aside"><a class="brand" href="#/">贵州乌冬 <span>运营</span></a><button :class="{active: adminTab === 'orders'}" @click="adminTab = 'orders'">预约订单</button><button :class="{active: adminTab === 'content'}" @click="adminTab = 'content'">资源与社区</button><button :class="{active: adminTab === 'traces'}" @click="adminTab = 'traces'">AI 运行摘要</button><a href="#/">← 返回游客端</a></aside>
      <main class="admin-main"><div class="admin-head"><div><p class="eyebrow">本机答辩演示后台</p><h1>{{ adminTab === 'orders' ? '预约订单' : adminTab === 'content' ? '资源与社区' : 'AI 脱敏运行摘要' }}</h1></div><span class="demo-badge">演示数据</span></div><section v-if="adminTab === 'orders'" class="admin-table"><div class="table-row header"><span>订单号</span><span>体验项目</span><span>日期 / 人数</span><span>状态</span><span>操作</span></div><div v-for="order in orders" :key="order.id" class="table-row"><span>{{ order.id }}</span><span>{{ order.name }}</span><span>{{ order.date }} · {{ order.people }} 人</span><span><i class="status">{{ order.status }}</i></span><button @click="updateOrder(order)">{{ order.status === '待确认' ? '确认订单' : '完成订单' }}</button></div></section><section v-else-if="adminTab === 'content'" class="admin-content"><div v-for="item in services.slice(0, 4)" :key="item.id" class="admin-resource"><img :src="item.image"><div><h3>{{ item.title }}</h3><p>{{ item.intro }}</p></div><button>编辑演示内容</button></div></section><section v-else class="trace-list"><article><span>会话 wd-demo-001</span><b>route → intent</b><p>识别为“苗族文化茶旅行程规划”</p><small>耗时 82ms · 已脱敏</small></article><article><span>会话 wd-demo-001</span><b>retrieve → itinerary</b><p>检索到《乌冬古茶园体验指南》《苗寨待客与长桌宴》</p><small>耗时 315ms · 来源摘要</small></article><article><span>会话 wd-demo-001</span><b>compose_card → completed</b><p>已生成 itinerary 卡片</p><small>耗时 41ms · 不含提示词与思维链</small></article></section></main>
    </template>
  </div>
</template>
