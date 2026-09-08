<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AssistantWorkspace from './components/assistant/AssistantWorkspace.vue'
import { categories, normalizeService, posts, services } from './data/demo'
import AdminPage from './pages/AdminPage.vue'
import BookingPage from './pages/BookingPage.vue'
import CommunityPage from './pages/CommunityPage.vue'
import HomePage from './pages/HomePage.vue'
import ResourceDetailPage from './pages/ResourceDetailPage.vue'
import ResourcesPage from './pages/ResourcesPage.vue'
import { request } from './services/api'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const STATIC_ROUTES = new Set(['/', '/resources', '/community', '/assistant', '/booking', '/admin'])
const ADMIN_TABS = new Set(['orders', 'content', 'ai-summary'])
const CATEGORY_IDS = new Set(['all', ...categories.map(item => item.id)])

function currentHashRoute() {
  return location.hash.slice(1) || '/'
}

function validString(value) {
  return typeof value === 'string' && value.trim().length > 0
}

function decodeResourceId(path) {
  const match = typeof path === 'string' ? path.match(/^\/resource\/([^/]+)$/) : null
  if (!match) return ''
  try {
    const decoded = decodeURIComponent(match[1])
    return UUID_PATTERN.test(decoded) ? decoded : ''
  } catch (_) {
    return ''
  }
}

const initialRoute = currentHashRoute()
const initialResourceId = decodeResourceId(initialRoute)
const route = ref(initialRoute)
const routeServiceId = ref(initialResourceId)
const serviceList = ref(services)
const postList = ref(posts)
const selected = ref(services.find(item => item.id === initialResourceId) || null)
const resourceLoading = ref(Boolean(initialResourceId) && !selected.value)
const activeCategory = ref('all')
const homeOpened = ref(sessionStorage.getItem('wudong:scroll-opened') === '1')
const joinedServiceIds = ref([])
const orders = ref([])
const adminTab = ref('orders')

const booking = ref({ date: '', people: 1, contact: '', phone: '' })
const pendingBooking = ref(null)
const bookingDone = ref(false)
const bookingLoading = ref(false)
const bookingLoadError = ref('')
const bookingActionError = ref('')
const bookingSubmitting = ref(false)
const bookingConfirming = ref(false)
const adminActionError = ref('')
const adminUpdatingOrderId = ref('')

let resourceRequestSeq = 0
let bookingLoadSeq = 0
let bookingActionSeq = 0

const filteredServices = computed(() => activeCategory.value === 'all'
  ? serviceList.value
  : serviceList.value.filter(item => item.category === activeCategory.value))
const isAdmin = computed(() => route.value === '/admin')
const isResourceRoute = computed(() => Boolean(routeServiceId.value) && decodeResourceId(route.value) === routeServiceId.value)
const isKnownRoute = computed(() => STATIC_ROUTES.has(route.value) || isResourceRoute.value)

function go(path) {
  if (!validString(path)) return
  location.hash = path
}

function categoryLabel(id) {
  return categories.find(item => item.id === id)?.label || '乌东体验'
}

async function loadResource(id) {
  const seq = ++resourceRequestSeq
  const fallback = serviceList.value.find(item => item.id === id) || null
  selected.value = fallback
  resourceLoading.value = !fallback
  try {
    const result = await request(`/api/services/${encodeURIComponent(id)}`)
    if (seq !== resourceRequestSeq || routeServiceId.value !== id) return
    if (result?.id !== id) throw new Error('service_id_mismatch')
    selected.value = normalizeService(result)
  } catch (_) {
    if (seq === resourceRequestSeq && routeServiceId.value === id) selected.value = fallback
  } finally {
    if (seq === resourceRequestSeq && routeServiceId.value === id) resourceLoading.value = false
  }
}

function onHashChange() {
  const nextRoute = currentHashRoute()
  if (route.value === '/booking' && nextRoute !== '/booking') {
    ++bookingLoadSeq
    ++bookingActionSeq
    bookingLoading.value = false
    bookingSubmitting.value = false
    bookingConfirming.value = false
  }
  route.value = nextRoute
  const id = decodeResourceId(nextRoute)
  routeServiceId.value = id

  if (nextRoute.startsWith('/resource/')) {
    selected.value = null
    resourceLoading.value = Boolean(id)
    if (id) loadResource(id)
  }
}

function openServiceById(value) {
  const id = typeof value === 'string'
    ? value
    : (typeof value?.id === 'string' ? value.id : '')
  if (!validString(id) || !UUID_PATTERN.test(id)) return

  const fallback = serviceList.value.find(item => item.id === id) || null
  selected.value = fallback
  const target = `/resource/${encodeURIComponent(id)}`
  if (route.value === target) {
    routeServiceId.value = id
    loadResource(id)
    return
  }
  go(target)
}

function navigateIntent(action) {
  if (CATEGORY_IDS.has(action?.category)) activeCategory.value = action.category
  const path = validString(action?.path) && STATIC_ROUTES.has(action.path) ? action.path : '/'
  go(path)
}

function resetBookingState(item, loading = false) {
  const seq = ++bookingLoadSeq
  ++bookingActionSeq
  selected.value = item
  pendingBooking.value = null
  bookingDone.value = false
  bookingLoading.value = loading
  bookingLoadError.value = ''
  bookingActionError.value = ''
  bookingSubmitting.value = false
  bookingConfirming.value = false
  booking.value = { date: '', people: 1, contact: '', phone: '' }
  go('/booking')
  return seq
}

function openBooking(item) {
  if (!validString(item?.id) || !UUID_PATTERN.test(item.id)) return
  const current = serviceList.value.find(service => service.id === item.id) || item
  resetBookingState(current)
}

function normalizeBooking(item, fallbackDemoData) {
  const demoData = typeof item?.demoData === 'boolean'
    ? item.demoData
    : (typeof fallbackDemoData === 'boolean' ? fallbackDemoData : true)
  const people = Number(item?.peopleCount ?? item?.people ?? 0)
  return {
    id: validString(item?.id) ? item.id : '',
    serviceId: validString(item?.serviceId) ? item.serviceId : '',
    name: validString(item?.serviceName)
      ? item.serviceName
      : (validString(item?.name) ? item.name : '乌东体验'),
    date: validString(item?.travelDate)
      ? item.travelDate
      : (validString(item?.date) ? item.date : ''),
    people: Number.isFinite(people) ? people : 0,
    contact: typeof item?.contactName === 'string'
      ? item.contactName
      : (typeof item?.contact === 'string' ? item.contact : ''),
    phone: typeof item?.contactPhone === 'string'
      ? item.contactPhone
      : (typeof item?.phone === 'string' ? item.phone : ''),
    status: typeof item?.status === 'string' ? item.status : '',
    demoData
  }
}

function hasCompleteBookingSummary(item) {
  return validString(item?.name)
    && validString(item?.date)
    && Number.isInteger(item?.people)
    && item.people > 0
    && validString(item?.contact)
    && validString(item?.phone)
}

async function bookServiceFromAssistant(payload) {
  const serviceId = typeof payload?.serviceId === 'string' ? payload.serviceId : ''
  if (!validString(serviceId) || !UUID_PATTERN.test(serviceId)) return
  const item = serviceList.value.find(value => value.id === serviceId)
  if (!item) return

  const hasHandoff = payload?.pendingBooking && typeof payload.pendingBooking === 'object'
  if (!hasHandoff) {
    resetBookingState(item)
    return
  }

  const handoffId = typeof payload.pendingBooking.id === 'string' ? payload.pendingBooking.id : ''
  if (!validString(handoffId) || !UUID_PATTERN.test(handoffId)) return
  const seq = resetBookingState(item, true)

  try {
    const fullBooking = await request(`/api/bookings/${encodeURIComponent(handoffId)}`)
    if (seq !== bookingLoadSeq) return
    if (fullBooking?.id !== handoffId || fullBooking?.serviceId !== serviceId || fullBooking?.status !== 'PENDING_CONFIRMATION') {
      throw new Error('pending_booking_mismatch')
    }
    const normalized = normalizeBooking(fullBooking, payload.pendingBooking.demoData ?? item.demoData)
    if (!hasCompleteBookingSummary(normalized)) throw new Error('pending_booking_incomplete')
    pendingBooking.value = normalized
    booking.value = {
      date: normalized.date,
      people: normalized.people,
      contact: normalized.contact,
      phone: normalized.phone
    }
  } catch (_) {
    if (seq === bookingLoadSeq) {
      pendingBooking.value = null
      bookingLoadError.value = '无法读取这笔待确认预约，请返回后重试。'
    }
  } finally {
    if (seq === bookingLoadSeq) bookingLoading.value = false
  }
}

function returnToAssistant() {
  ++bookingLoadSeq
  ++bookingActionSeq
  bookingLoading.value = false
  bookingSubmitting.value = false
  bookingConfirming.value = false
  go('/assistant')
}

function joinServiceById(serviceId) {
  if (!validString(serviceId) || !UUID_PATTERN.test(serviceId)) return
  if (!serviceList.value.some(item => item.id === serviceId)) return
  if (!joinedServiceIds.value.includes(serviceId)) joinedServiceIds.value.push(serviceId)
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

async function submitBooking() {
  if (bookingSubmitting.value || pendingBooking.value) return
  const serviceId = typeof selected.value?.id === 'string' ? selected.value.id : ''
  const travelDate = typeof booking.value.date === 'string' ? booking.value.date : ''
  const peopleCount = Number(booking.value.people)
  const contactName = typeof booking.value.contact === 'string' ? booking.value.contact.trim() : ''
  const contactPhone = typeof booking.value.phone === 'string' ? booking.value.phone.trim() : ''

  if (!validString(serviceId) || !UUID_PATTERN.test(serviceId)) return
  if (!validString(travelDate) || !Number.isInteger(peopleCount) || peopleCount < 1 || !contactName || !contactPhone) {
    bookingActionError.value = '请完整填写日期、人数、联系人和电话。'
    return
  }

  bookingActionError.value = ''
  bookingSubmitting.value = true
  const actionSeq = ++bookingActionSeq
  try {
    const result = await request('/api/bookings', {
      method: 'POST',
      body: JSON.stringify({ serviceId, travelDate, peopleCount, contactName, contactPhone })
    })
    if (actionSeq !== bookingActionSeq) return
    const normalized = normalizeBooking(result, selected.value.demoData)
    if (!UUID_PATTERN.test(normalized.id)
      || normalized.serviceId !== serviceId
      || normalized.status !== 'PENDING_CONFIRMATION'
      || !hasCompleteBookingSummary(normalized)) {
      throw new Error('created_booking_mismatch')
    }
    pendingBooking.value = normalized
    booking.value = {
      date: normalized.date,
      people: normalized.people,
      contact: normalized.contact,
      phone: normalized.phone
    }
  } catch (_) {
    if (actionSeq === bookingActionSeq) {
      pendingBooking.value = null
      bookingActionError.value = '暂时无法创建预约，请稍后重试。'
    }
  } finally {
    if (actionSeq === bookingActionSeq) bookingSubmitting.value = false
  }
}

async function confirmBooking() {
  if (bookingConfirming.value) return
  const pending = pendingBooking.value
  const bookingId = typeof pending?.id === 'string' ? pending.id : ''
  if (!validString(bookingId) || !UUID_PATTERN.test(bookingId) || pending.status !== 'PENDING_CONFIRMATION') return

  bookingActionError.value = ''
  bookingConfirming.value = true
  const actionSeq = ++bookingActionSeq
  try {
    const result = await request(`/api/bookings/${encodeURIComponent(bookingId)}/confirm`, { method: 'POST' })
    if (actionSeq !== bookingActionSeq) return
    const normalized = normalizeBooking(result, pending.demoData)
    if (normalized.id !== bookingId
      || normalized.serviceId !== pending.serviceId
      || normalized.status !== 'CONFIRMED'
      || !hasCompleteBookingSummary(normalized)) {
      throw new Error('confirmed_booking_mismatch')
    }
    orders.value = [normalized, ...orders.value.filter(item => item.id !== bookingId)]
    pendingBooking.value = normalized
    bookingDone.value = true
  } catch (_) {
    if (actionSeq === bookingActionSeq) bookingActionError.value = '确认预约失败，请稍后重试。'
  } finally {
    if (actionSeq === bookingActionSeq) bookingConfirming.value = false
  }
}

async function updateOrder(order) {
  if (adminUpdatingOrderId.value) return
  const orderId = typeof order?.id === 'string' ? order.id : ''
  const nextStatus = order?.status === 'CONFIRMED'
    ? 'PROCESSING'
    : (order?.status === 'PROCESSING' ? 'COMPLETED' : '')
  if (!validString(orderId) || !UUID_PATTERN.test(orderId) || !nextStatus) return

  adminActionError.value = ''
  adminUpdatingOrderId.value = orderId
  try {
    const result = await request(`/api/admin/bookings/${encodeURIComponent(orderId)}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status: nextStatus })
    })
    if (result?.id !== orderId || result?.serviceId !== order.serviceId || result?.status !== nextStatus) {
      throw new Error('order_status_mismatch')
    }
    const normalized = normalizeBooking(result, order.demoData)
    orders.value = orders.value.map(item => item.id === orderId ? normalized : item)
  } catch (_) {
    adminActionError.value = '订单状态更新失败，请稍后重试。'
  } finally {
    adminUpdatingOrderId.value = ''
  }
}

function changeAdminTab(tab) {
  if (!ADMIN_TABS.has(tab)) return
  adminActionError.value = ''
  adminTab.value = tab
}

async function loadInitialData() {
  try {
    const result = await request('/api/services')
    if (Array.isArray(result)) {
      const normalized = result.map(normalizeService).filter(item => validString(item.id))
      if (normalized.length) serviceList.value = normalized
    }
  } catch (_) {}

  if (routeServiceId.value) loadResource(routeServiceId.value)

  try {
    const result = await request('/api/posts')
    if (Array.isArray(result)) postList.value = result.map(normalizePost).filter(item => validString(item.id))
  } catch (_) {}
}

onMounted(() => {
  addEventListener('hashchange', onHashChange)
  onHashChange()
  loadInitialData()
})

onUnmounted(() => {
  removeEventListener('hashchange', onHashChange)
})
</script>

<template>
  <div :class="{ 'admin-shell': isAdmin }">
    <template v-if="!isAdmin">
      <header v-if="route !== '/' || homeOpened" class="topbar">
        <a class="brand" href="#/">贵州乌东</a>
        <nav aria-label="主导航">
          <a href="#/resources">游乌东</a>
          <a href="#/community">寨里</a>
          <a href="#/assistant">乌东向导</a>
        </nav>
        <button type="button" class="admin-link" @click="go('/admin')">运营后台</button>
      </header>

      <HomePage
        v-if="route === '/'"
        :services="serviceList"
        :categories="categories"
        @gate-change="homeOpened = $event"
        @navigate="navigateIntent"
        @open-service="openServiceById"
      >
        <template #assistant>
          <AssistantWorkspace
            embedded
            :joined-service-ids="joinedServiceIds"
            @open-service="openServiceById"
            @join-service="joinServiceById"
            @book-service="bookServiceFromAssistant"
          />
        </template>
      </HomePage>

      <ResourcesPage
        v-else-if="route === '/resources'"
        :services="filteredServices"
        :categories="categories"
        :active-category="activeCategory"
        @filter="activeCategory = $event"
        @open-service="openServiceById"
      />

      <ResourceDetailPage
        v-else-if="isResourceRoute && !resourceLoading"
        :service="selected"
        :category-label="categoryLabel(selected?.category)"
        @book="openBooking"
      />

      <main v-else-if="isResourceRoute" class="page route-empty-state" aria-live="polite">
        <p class="eyebrow">沿溪寻找这项体验</p>
        <h1>正在读取服务详情</h1>
      </main>

      <CommunityPage v-else-if="route === '/community'" :posts="postList" />

      <main v-else-if="route === '/assistant'" class="page assistant-page">
        <AssistantWorkspace
          :joined-service-ids="joinedServiceIds"
          @open-service="openServiceById"
          @join-service="joinServiceById"
          @book-service="bookServiceFromAssistant"
        />
      </main>

      <BookingPage
        v-else-if="route === '/booking'"
        :service="selected"
        :booking="booking"
        :pending="pendingBooking"
        :done="bookingDone"
        :latest-order="orders[0]"
        :loading="bookingLoading"
        :load-error="bookingLoadError"
        :action-error="bookingActionError"
        :submitting="bookingSubmitting"
        :confirming="bookingConfirming"
        @update-booking="booking = $event"
        @submit="submitBooking"
        @confirm="confirmBooking"
        @open-admin="go('/admin')"
        @return-assistant="returnToAssistant"
      />

      <main v-else-if="!isKnownRoute" class="page route-empty-state">
        <p class="eyebrow">这条山路暂时走不通</p>
        <h1>没有找到这个页面</h1>
        <p>链接可能不完整，请从首页或“游乌东”重新进入。</p>
        <div class="route-empty-state__actions">
          <a class="primary" href="#/">返回首页</a>
          <a class="ghost" href="#/resources">游乌东</a>
        </div>
      </main>
    </template>

    <AdminPage
      v-else
      :tab="adminTab"
      :orders="orders"
      :services="serviceList"
      :action-error="adminActionError"
      :updating-order-id="adminUpdatingOrderId"
      @change-tab="changeAdminTab"
      @update-order="updateOrder"
      @return-home="go('/')"
    />
  </div>
</template>
