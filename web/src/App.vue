<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AdminPage from './pages/AdminPage.vue'
import BookingPage from './pages/BookingPage.vue'
import ExplorePage from './pages/ExplorePage.vue'
import GuidePage from './pages/GuidePage.vue'
import HomePage from './pages/HomePage.vue'
import MyOrdersPage from './pages/MyOrdersPage.vue'
import { parseHashRoute } from './router/hashRoutes'

const route = ref(parseHashRoute())
const homeOpened = ref(sessionStorage.getItem('wudong:scroll-opened') === '1')
const checkoutSelection = ref(null)
const isAdmin = computed(() => route.value.name === 'admin')

function go(path) {
  if (typeof path !== 'string' || !path.startsWith('/')) return
  if ((location.hash.slice(1) || '/') === path) route.value = parseHashRoute(`#${path}`)
  else location.hash = path
}

function navigateIntent(action) {
  const path = typeof action === 'string' ? action : action?.path
  const legacyMap = {
    '/resources/products': '/explore/product',
    '/resources/foods': '/explore/food',
    '/resources/stays': '/explore/stay',
    '/resources/travel': '/explore/travel',
    '/resources/community': '/community',
    '/assistant': '/guide',
    '/orders': '/my'
  }
  go(legacyMap[path] || path || '/')
}

function beginCheckout(selection) {
  checkoutSelection.value = selection
  go('/checkout')
}

function syncRoute() {
  route.value = parseHashRoute()
}

onMounted(() => {
  addEventListener('hashchange', syncRoute)
  syncRoute()
})
onUnmounted(() => removeEventListener('hashchange', syncRoute))
</script>

<template>
  <AdminPage v-if="isAdmin" />
  <div v-else class="visitor-shell">
    <header v-if="route.name !== 'home' || homeOpened" class="topbar v3-topbar">
      <a class="brand" href="#/">贵州乌东</a>
      <nav aria-label="主导航"><a href="#/explore">逛乌东</a><a href="#/guide">乌东向导</a><a href="#/my">我的</a></nav>
    </header>

    <HomePage v-if="route.name === 'home'" @gate-change="homeOpened = $event" @navigate="navigateIntent">
      <template #assistant><div class="v3-home-guide"><p>可以免登录先体验向导；需要保存个人成果时，再登录并由你确认。</p><button class="primary" @click="go('/guide')">进入乌东向导</button></div></template>
    </HomePage>
    <ExplorePage v-else-if="route.name === 'explore'" :section="route.section" :focus-food-id="route.focusFoodId" :focus-product-id="route.focusProductId" :focus-room-id="route.focusRoomId" :focus-place-id="route.focusPlaceId" @navigate="go" @checkout="beginCheckout" />
    <GuidePage v-else-if="route.name === 'guide'" />
    <MyOrdersPage v-else-if="route.name === 'my'" />
    <BookingPage v-else-if="route.name === 'checkout'" :selection="checkoutSelection" @back="go('/explore')" @go-my="go('/my')" />
    <main v-else class="page route-empty-state"><p class="eyebrow">这条山路暂时走不通</p><h1>没有找到这个页面</h1><p>请从水彩首页或五模块入口重新进入。</p><a class="primary" href="#/">返回首页</a></main>
  </div>
</template>
