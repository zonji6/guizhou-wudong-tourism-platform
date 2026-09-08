<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AssistantWorkspace from './components/assistant/AssistantWorkspace.vue'
import { useCatalogBrowser } from './composables/useCatalogBrowser'
import { useVisitorOrders } from './composables/useVisitorOrders'
import { posts, services as demoServices } from './data/demo'
import AdminPage from './pages/AdminPage.vue'
import BookingPage from './pages/BookingPage.vue'
import CommunityPage from './pages/CommunityPage.vue'
import HomePage from './pages/HomePage.vue'
import MyOrdersPage from './pages/MyOrdersPage.vue'
import ResourceDetailPage from './pages/ResourceDetailPage.vue'
import ResourcesPage from './pages/ResourcesPage.vue'
import { catalogPath, confirmPath, detailPath, parseHashRoute } from './router/hashRoutes'
import { request } from './services/api'
import { createOrder, getCatalog, getRoomType } from './services/tourismApi'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const route = ref(parseHashRoute())
const homeOpened = ref(sessionStorage.getItem('wudong:scroll-opened') === '1')
const postList = ref(posts)
const joinedServiceIds = ref([])
const adminTab = ref('orders')
const adminActionError = ref('')
const adminUpdatingOrderId = ref('')

const {
  items: catalogItems,
  selected: catalogSelected,
  loading: catalogLoading,
  error: catalogError,
  loadList,
  loadDetail,
  cancel: cancelCatalog
} = useCatalogBrowser()

const {
  orders: visitorOrders,
  errors: visitorOrderErrors,
  loading: visitorOrderLoading,
  identityError,
  loadAll: loadAllOrders,
  retry: retryOrders
} = useVisitorOrders()

const bookingTarget = ref(null)
const bookingLoading = ref(false)
const bookingError = ref('')
const bookingActionError = ref('')
const bookingSubmitting = ref(false)
const createdOrder = ref(null)
let bookingSequence = 0

const isAdmin = computed(() => route.value.name === 'admin')
const pageKind = computed(() => route.value.kind || 'product')

function go(path) {
  if (typeof path !== 'string' || !path.startsWith('/')) return
  if ((location.hash.slice(1) || '/') === path) {
    syncCurrentRoute()
    return
  }
  location.hash = path
}

function navigateIntent(action) {
  if (!action || typeof action !== 'object') return
  if (action.path === '/resources' && action.category === 'food') return go(catalogPath('food'))
  if (action.path === '/resources' && action.category === 'stay') return go(catalogPath('stay'))
  if (action.path === '/resources' && action.category === 'travel') return go('/assistant')
  if (action.path === '/resources' && action.category === 'culture') return go(catalogPath('product'))
  go(typeof action.path === 'string' ? action.path : '/')
}

function routeLegacyAssistantItem(item) {
  const serviceId = typeof item === 'string' ? item : (item?.serviceId || item?.id)
  const service = demoServices.find(value => value.id === serviceId)
  if (service?.category === 'food') return go(catalogPath('food'))
  if (service?.category === 'stay') return go(catalogPath('stay'))
  go(catalogPath('product'))
}

function joinLegacyAssistantItem(serviceId) {
  if (typeof serviceId !== 'string' || !UUID_PATTERN.test(serviceId)) return
  if (!demoServices.some(item => item.id === serviceId)) return
  if (!joinedServiceIds.value.includes(serviceId)) joinedServiceIds.value.push(serviceId)
}

function validString(value) {
  return typeof value === 'string' && value.trim().length > 0
}

function normalizePost(item = {}) {
  const itemId = validString(item.id) ? item.id : ''
  const fallback = posts.find(post => post.id === itemId) || {}
  const hasApiCoverPair = validString(item.coverUrl) && validString(item.coverAlt)
  const hasDirectCoverPair = validString(item.cover) && validString(item.coverAlt)
  const cover = hasApiCoverPair
    ? item.coverUrl
    : (hasDirectCoverPair ? item.cover : (fallback.cover || ''))
  const coverAlt = hasApiCoverPair || hasDirectCoverPair
    ? item.coverAlt
    : (fallback.coverAlt || '寨里分享配图暂缺')
  const tags = Array.isArray(item.tags)
    ? item.tags
    : (typeof item.tags === 'string'
        ? item.tags.split(',').map(tag => tag.trim()).filter(Boolean)
        : (fallback.tags || []))

  return {
    id: itemId || fallback.id || '',
    title: validString(item.title) ? item.title : (fallback.title || '乌东寨里分享'),
    text: typeof item.content === 'string'
      ? item.content
      : (typeof item.text === 'string' ? item.text : (fallback.text || '')),
    author: validString(item.authorName)
      ? item.authorName
      : (validString(item.author) ? item.author : (fallback.author || '乌东村寨记录者')),
    cover,
    coverAlt,
    tags,
    likes: typeof item.likes === 'number' ? item.likes : fallback.likes,
    publishedAt: typeof item.publishedAt === 'string' ? item.publishedAt : (fallback.publishedAt || ''),
    demoData: typeof item.demoData === 'boolean' ? item.demoData : (fallback.demoData ?? true)
  }
}

async function loadCommunity() {
  try {
    const result = await request('/api/posts')
    if (Array.isArray(result)) postList.value = result.map(normalizePost).filter(item => item.id)
  } catch (_) {
    // 社区页沿用既有演示内容；目录与正式订单不使用这条回退链。
  }
}

async function loadBookingTarget(kind, id) {
  const sequence = ++bookingSequence
  bookingTarget.value = null
  createdOrder.value = null
  bookingLoading.value = true
  bookingError.value = ''
  bookingActionError.value = ''
  bookingSubmitting.value = false
  try {
    const result = kind === 'stay' ? await getRoomType(id) : await getCatalog(kind, id)
    if (sequence !== bookingSequence) return
    if (result.id !== id) throw new Error('服务详情与当前订单地址不一致。')
    bookingTarget.value = result
  } catch (reason) {
    if (sequence === bookingSequence) bookingError.value = reason?.message || '服务信息暂时无法读取。'
  } finally {
    if (sequence === bookingSequence) bookingLoading.value = false
  }
}

function cancelBooking() {
  bookingSequence += 1
  bookingLoading.value = false
  bookingSubmitting.value = false
}

function syncCurrentRoute() {
  const next = parseHashRoute()
  route.value = next

  if (next.name === 'catalog') {
    cancelBooking()
    loadList(next.kind)
    return
  }
  if (next.name === 'detail') {
    cancelBooking()
    loadDetail(next.kind, next.id)
    return
  }
  cancelCatalog()
  if (next.name === 'confirm') {
    loadBookingTarget(next.kind, next.id)
    return
  }
  cancelBooking()
  if (next.name === 'orders') loadAllOrders()
}

function openCatalogItem(item) {
  if (!item || typeof item.id !== 'string' || !UUID_PATTERN.test(item.id)) return
  go(detailPath(pageKind.value, item.id))
}

function openConfirm(kind, target) {
  if (!target || typeof target.id !== 'string' || !UUID_PATTERN.test(target.id)) return
  go(confirmPath(kind, target.id))
}

function retryCurrentResource() {
  if (route.value.name === 'catalog') return loadList(route.value.kind)
  if (route.value.name === 'detail') return loadDetail(route.value.kind, route.value.id)
  if (route.value.name === 'confirm') return loadBookingTarget(route.value.kind, route.value.id)
}

function validPositiveInteger(value) {
  return Number.isInteger(Number(value)) && Number(value) > 0
}

function validateOrderForm(kind, form, target) {
  if (!form.contactName?.trim() || !form.contactPhone?.trim()) return '请填写联系人和联系电话。'
  if (form.contactName.trim().length > 80 || form.contactPhone.trim().length > 32) return '联系人或电话超过长度限制。'
  if (String(form.note || '').trim().length > 500) return '备注不能超过 500 字。'
  if (kind === 'product' && !validPositiveInteger(form.quantity)) return '购买数量必须是正整数。'
  if (kind === 'food' && (!form.visitAt || !validPositiveInteger(form.peopleCount))) return '请填写到店时间和人数。'
  if (kind === 'stay') {
    if (!form.checkInDate || !validPositiveInteger(form.peopleCount)) return '请填写入住日期和人数。'
    if (Number(target.maxGuests) > 0 && Number(form.peopleCount) > Number(target.maxGuests)) return `该房型最多入住 ${target.maxGuests} 人。`
  }
  return ''
}

function matchesCreatedOrder(kind, order, targetId) {
  if (!order || typeof order.id !== 'string' || !UUID_PATTERN.test(order.id)) return false
  if (typeof order.demoData !== 'boolean' || !validString(order.createdAt) || !validString(order.updatedAt)) return false
  if (kind === 'product') {
    return order.productId === targetId
      && order.status === 'PENDING_PICKUP'
      && validString(order.productName)
      && validPositiveInteger(order.quantity)
      && validString(order.pickupPoint)
  }
  if (kind === 'food') {
    return order.foodItemId === targetId
      && order.status === 'PENDING_VISIT'
      && validString(order.foodItemName)
      && validString(order.visitAt)
      && validPositiveInteger(order.peopleCount)
  }
  return order.roomTypeId === targetId
    && order.status === 'PENDING_CONFIRMATION'
    && validString(order.roomTypeName)
    && validString(order.checkInDate)
    && validPositiveInteger(order.peopleCount)
}

async function submitOrder(form) {
  if (bookingSubmitting.value || !bookingTarget.value || route.value.name !== 'confirm') return
  const validationError = validateOrderForm(route.value.kind, form, bookingTarget.value)
  if (validationError) {
    bookingActionError.value = validationError
    return
  }

  bookingActionError.value = ''
  bookingSubmitting.value = true
  const sequence = bookingSequence
  try {
    const result = await createOrder(route.value.kind, route.value.id, form, bookingTarget.value)
    if (sequence !== bookingSequence) return
    if (!matchesCreatedOrder(route.value.kind, result, route.value.id)) {
      throw new Error('服务端返回的订单与本次提交不一致，请前往“我的订单”核对。')
    }
    createdOrder.value = result
  } catch (reason) {
    if (sequence === bookingSequence) bookingActionError.value = reason?.message || '订单提交失败，请稍后重试。'
  } finally {
    if (sequence === bookingSequence) bookingSubmitting.value = false
  }
}

function changeAdminTab(tab) {
  if (!['orders', 'content', 'ai-summary'].includes(tab)) return
  adminActionError.value = ''
  adminTab.value = tab
}

function updateLegacyOrder() {
  adminActionError.value = '正式三类订单的运营处理将在后续后台任务中接入。'
}

onMounted(() => {
  addEventListener('hashchange', syncCurrentRoute)
  syncCurrentRoute()
  loadCommunity()
})

onUnmounted(() => {
  removeEventListener('hashchange', syncCurrentRoute)
  cancelCatalog()
  cancelBooking()
})
</script>

<template>
  <div :class="{ 'admin-shell': isAdmin }">
    <template v-if="!isAdmin">
      <header v-if="route.name !== 'home' || homeOpened" class="topbar">
        <a class="brand" href="#/">贵州乌东</a>
        <nav aria-label="主导航">
          <a href="#/resources/products">乌东好物</a>
          <a href="#/resources/foods">在地风味</a>
          <a href="#/resources/stays">山居住宿</a>
          <a href="#/community">寨里</a>
          <a href="#/assistant">乌东向导</a>
        </nav>
        <div class="topbar__actions">
          <a class="topbar__mobile-link" href="#/community">寨里</a>
          <a class="topbar__mobile-link" href="#/assistant">向导</a>
          <a href="#/orders">我的订单</a>
          <button type="button" class="admin-link" @click="go('/admin')">运营后台</button>
        </div>
      </header>

      <HomePage
        v-if="route.name === 'home'"
        @gate-change="homeOpened = $event"
        @navigate="navigateIntent"
      >
        <template #assistant>
          <AssistantWorkspace
            embedded
            :joined-service-ids="joinedServiceIds"
            @open-service="routeLegacyAssistantItem"
            @join-service="joinLegacyAssistantItem"
            @book-service="routeLegacyAssistantItem"
          />
        </template>
      </HomePage>

      <ResourcesPage
        v-else-if="route.name === 'catalog'"
        :kind="route.kind"
        :items="catalogItems"
        :loading="catalogLoading"
        :error="catalogError"
        @switch-kind="go(catalogPath($event))"
        @open-item="openCatalogItem"
        @retry="retryCurrentResource"
      />

      <ResourceDetailPage
        v-else-if="route.name === 'detail'"
        :kind="route.kind"
        :item="catalogSelected"
        :loading="catalogLoading"
        :error="catalogError"
        @confirm="openConfirm"
        @retry="retryCurrentResource"
      />

      <CommunityPage v-else-if="route.name === 'community'" :posts="postList" />

      <main v-else-if="route.name === 'assistant'" class="page assistant-page">
        <AssistantWorkspace
          :joined-service-ids="joinedServiceIds"
          @open-service="routeLegacyAssistantItem"
          @join-service="joinLegacyAssistantItem"
          @book-service="routeLegacyAssistantItem"
        />
      </main>

      <BookingPage
        v-else-if="route.name === 'confirm'"
        :kind="route.kind"
        :target="bookingTarget"
        :loading="bookingLoading"
        :error="bookingError"
        :submitting="bookingSubmitting"
        :action-error="bookingActionError"
        :order="createdOrder"
        @submit="submitOrder"
        @retry="retryCurrentResource"
        @go-orders="go('/orders')"
      />

      <MyOrdersPage
        v-else-if="route.name === 'orders'"
        :orders="visitorOrders"
        :errors="visitorOrderErrors"
        :loading="visitorOrderLoading"
        :identity-error="identityError"
        @retry="retryOrders"
        @reload="loadAllOrders"
      />

      <main v-else class="page route-empty-state">
        <p class="eyebrow">这条山路暂时走不通</p>
        <h1>没有找到这个页面</h1>
        <p>链接可能不完整，请从首页或公开目录重新进入。</p>
        <div class="route-empty-state__actions">
          <a class="primary" href="#/">返回首页</a>
          <a class="ghost" href="#/resources/products">看看乌东好物</a>
        </div>
      </main>
    </template>

    <AdminPage
      v-else
      :tab="adminTab"
      :orders="[]"
      :services="demoServices"
      :action-error="adminActionError"
      :updating-order-id="adminUpdatingOrderId"
      @change-tab="changeAdminTab"
      @update-order="updateLegacyOrder"
      @return-home="go('/')"
    />
  </div>
</template>
