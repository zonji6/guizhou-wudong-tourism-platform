<script setup>
import { computed, ref, watch } from 'vue'
import { useAssistantSession } from '../../composables/useAssistantSession'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const props = defineProps({
  embedded: Boolean,
  joinedServiceIds: { type: Array, default: () => [] }
})
const emit = defineEmits(['open-service', 'join-service', 'book-service'])

const input = ref('我想体验贵州乌东的苗族文化和茶旅')
const sourcesOpen = ref(false)
const session = useAssistantSession()
const card = computed(() => session.activeCard.value)
const previousCard = computed(() => session.previousCard.value)
const recentMessages = computed(() => session.messages.value.slice(-6))
const days = computed(() => arrayValue(card.value?.data?.days))
const services = computed(() => arrayValue(card.value?.data?.services))
const sourceCount = computed(() => card.value?.sources?.length || 0)
const previousCardKey = computed(() => `previous-card-${session.previousCardVersion.value}`)

watch(card, () => { sourcesOpen.value = false })

function arrayValue(value) {
  return Array.isArray(value) ? value : []
}

function validId(value) {
  return typeof value === 'string' && value.trim().length > 0 && UUID_PATTERN.test(value)
}

function itemServiceId(item) {
  return validId(item?.serviceId) ? item.serviceId : ''
}

function isJoined(item) {
  const id = itemServiceId(item)
  return id ? props.joinedServiceIds.includes(id) : false
}

function emitServiceAction(item, action) {
  const serviceId = itemServiceId(item)
  if (!serviceId) return
  emit(action, action === 'book-service' ? { serviceId } : serviceId)
}

function emitPendingBooking() {
  const booking = card.value?.data?.booking
  if (!validId(booking?.serviceId) || !validId(booking?.id)) return
  emit('book-service', { serviceId: booking.serviceId, pendingBooking: booking })
}

function askSuggestion(text) {
  input.value = text
  session.send(text)
}

function phaseStateLabel(state) {
  return {
    idle: '待开始',
    active: '进行中',
    done: '已完成',
    skipped: '未经过',
    failed: '未完成'
  }[state] || '待开始'
}

function bookingStatusLabel(status) {
  return {
    PENDING_CONFIRMATION: '待你确认',
    CONFIRMED: '已确认',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }[status] || '状态待核对'
}

function hasPrice(value) {
  return typeof value === 'number' && Number.isFinite(value)
}
</script>

<template>
  <section class="assistant-workspace" :class="{ 'assistant-workspace--embedded': embedded }">
    <header class="assistant-workspace__head">
      <div>
        <p class="eyebrow">一叶同行 · 乌东向导</p>
        <h2>把想去的地方，慢慢说给我听</h2>
      </div>
      <span v-if="session.mode.value === 'demo' || card?.data?.demoMode === true" class="demo-badge">演示模式</span>
    </header>

    <div v-if="recentMessages.length" class="assistant-dialogue" aria-label="本次简短对话">
      <p
        v-for="(message, index) in recentMessages"
        :key="`${message.role}-${index}-${message.content}`"
        :class="['assistant-message', `assistant-message--${message.role}`]"
      >
        <b>{{ message.role === 'user' ? '你' : '乌东向导' }}</b>
        <span>{{ message.content }}</span>
      </p>
    </div>

    <div class="suggestions" aria-label="示例问题">
      <button type="button" @click="askSuggestion('周末两位，安排两天一夜的苗族文化茶旅')">两天一夜茶旅</button>
      <button type="button" @click="askSuggestion('推荐适合亲子的苗绣体验')">亲子苗绣</button>
      <button type="button" @click="askSuggestion('介绍一下乌东的茶旅体验')">认识乌东茶旅</button>
    </div>

    <label class="assistant-input">
      <span>你的旅行需求</span>
      <textarea
        v-model="input"
        placeholder="例如：周末两位，想体验苗族文化和茶旅"
        @keydown.ctrl.enter.prevent="session.send(input)"
      />
    </label>
    <div class="assistant-submit-row">
      <button type="button" class="primary" @click="session.send(input)">
        {{ session.isBusy.value ? '按新需求重新规划' : '为我安排乌东之行' }}
      </button>
      <p aria-live="polite">{{ session.activityText.value }}</p>
    </div>

    <ol class="assistant-phases" aria-label="向导协作进度">
      <li
        v-for="(phase, index) in session.stages.value"
        :key="phase.id"
        :class="phase.state"
      >
        <span>{{ index + 1 }}</span>
        <b>{{ phase.label }}</b>
        <small>{{ phaseStateLabel(phase.state) }}</small>
      </li>
    </ol>

    <article v-if="card" class="agent-card" aria-live="polite">
      <header class="agent-card__head">
        <div>
          <p v-if="sourceCount > 0 && card.data?.retrievalMode === 'keyword_demo'" class="card-note">关键词资料（演示）</p>
          <h2>{{ card.title }}</h2>
          <p>{{ card.summary }}</p>
        </div>
      </header>

      <div v-if="card.type === 'itinerary'" class="itinerary-days">
        <section v-for="day in days" :key="day.day" class="itinerary-day">
          <header>
            <span>第 {{ day.day }} 天</span>
            <h3>{{ day.theme || '慢游乌东' }}</h3>
          </header>
          <article v-for="(item, index) in arrayValue(day.items)" :key="`${day.day}-${index}-${item.title}`" class="itinerary-item">
            <p class="itinerary-time">{{ item.time || '时间待商议' }}</p>
            <div>
              <h4>{{ item.title || '乌东行程节点' }}</h4>
              <p>{{ item.summary }}</p>
              <span v-if="item.demoData === true" class="demo-data-label">演示数据</span>
              <div v-if="itemServiceId(item)" class="card-actions">
                <button type="button" @click="emitServiceAction(item, 'open-service')">查看详情</button>
                <button v-if="!isJoined(item)" type="button" @click="emitServiceAction(item, 'join-service')">加入行程</button>
                <span v-else class="joined-label">已加入行程</span>
                <button type="button" class="card-action--warm" @click="emitServiceAction(item, 'book-service')">预约</button>
              </div>
            </div>
          </article>
        </section>
        <p class="service-notice">{{ card.data.notice || '服务时间、价格与可预约情况请以服务方确认结果为准。' }}</p>
      </div>

      <div v-else-if="card.type === 'service_recommendation'" class="assistant-service-list">
        <article v-for="(item, index) in services" :key="`${index}-${item.title}`" class="assistant-service-item">
          <div>
            <h3>{{ item.title || '乌东体验' }}</h3>
            <p>{{ item.summary }}</p>
            <p v-if="hasPrice(item.price)" class="assistant-service-price">¥{{ item.price }} <small>起</small></p>
            <span v-if="item.demoData === true" class="demo-data-label">演示数据</span>
          </div>
          <div v-if="itemServiceId(item)" class="card-actions">
            <button type="button" @click="emitServiceAction(item, 'open-service')">查看详情</button>
            <button v-if="!isJoined(item)" type="button" @click="emitServiceAction(item, 'join-service')">加入行程</button>
            <span v-else class="joined-label">已加入行程</span>
            <button type="button" class="card-action--warm" @click="emitServiceAction(item, 'book-service')">预约</button>
          </div>
        </article>
        <p class="service-notice">请以服务方确认的时间、价格与可预约情况为准。</p>
      </div>

      <div v-else-if="card.type === 'knowledge_answer'" class="knowledge-card-copy">
        <p>这段说明仅依据当前接入的乌东资料整理。</p>
      </div>

      <div v-else-if="card.type === 'clarifying_question'" class="clarifying-card-copy">
        <p v-if="card.data?.question && card.data.question !== card.summary">{{ card.data.question }}</p>
        <p>你可以补充日期、人数，以及茶旅、苗寨文化、美食或住宿偏好。</p>
      </div>

      <div v-else-if="card.type === 'pending_booking' && card.data?.booking" class="pending-booking-card">
        <span v-if="card.data.booking.demoData === true" class="demo-data-label">演示数据</span>
        <dl>
          <dt>服务</dt><dd>{{ card.data.booking.serviceName || '乌东体验' }}</dd>
          <dt>日期</dt><dd>{{ card.data.booking.travelDate || '待核对' }}</dd>
          <dt>人数</dt><dd>{{ card.data.booking.peopleCount || '待核对' }}</dd>
          <dt>状态</dt><dd>{{ bookingStatusLabel(card.data.booking.status) }}</dd>
        </dl>
        <button
          v-if="validId(card.data.booking.serviceId) && validId(card.data.booking.id)"
          type="button"
          class="primary"
          @click="emitPendingBooking"
        >
          继续确认预约
        </button>
      </div>

      <div v-else-if="card.type === 'error'" class="assistant-error-card">
        <button
          v-if="card.data?.demoAvailable === true"
          type="button"
          class="ghost"
          @click="session.useDemo"
        >
          查看演示结果
        </button>
      </div>

      <div v-if="sourceCount" class="card-sources">
        <button type="button" :aria-expanded="sourcesOpen" @click="sourcesOpen = !sourcesOpen">
          参考了 {{ sourceCount }} 条乌东资料
        </button>
        <ul v-if="sourcesOpen">
          <li v-for="(source, index) in card.sources" :key="`${index}-${source.title}`">{{ source.title }}</li>
        </ul>
      </div>
    </article>

    <details v-if="previousCard" :key="previousCardKey" class="previous-card">
      <summary>上一版 · {{ previousCard.title }}</summary>
      <div class="previous-card__body">
        <p>{{ previousCard.summary }}</p>
        <p v-if="previousCard.sources?.length && previousCard.data?.retrievalMode === 'keyword_demo'" class="card-note">关键词资料（演示）</p>

        <template v-if="previousCard.type === 'itinerary'">
          <section v-for="day in arrayValue(previousCard.data?.days)" :key="day.day">
            <h3>第 {{ day.day }} 天 · {{ day.theme || '慢游乌东' }}</h3>
            <article v-for="(item, index) in arrayValue(day.items)" :key="`${day.day}-${index}-${item.title}`">
              <b>{{ item.time || '时间待商议' }} · {{ item.title || '乌东行程节点' }}</b>
              <p>{{ item.summary }}</p>
              <span v-if="item.demoData === true" class="demo-data-label">演示数据</span>
            </article>
          </section>
          <p v-if="previousCard.data?.notice" class="service-notice">{{ previousCard.data.notice }}</p>
        </template>

        <template v-else-if="previousCard.type === 'service_recommendation'">
          <article v-for="(item, index) in arrayValue(previousCard.data?.services)" :key="`${index}-${item.title}`">
            <h3>{{ item.title || '乌东体验' }}</h3>
            <p>{{ item.summary }}</p>
            <p v-if="hasPrice(item.price)" class="assistant-service-price">¥{{ item.price }} <small>起</small></p>
            <span v-if="item.demoData === true" class="demo-data-label">演示数据</span>
          </article>
        </template>

        <template v-else-if="previousCard.type === 'pending_booking' && previousCard.data?.booking">
          <span v-if="previousCard.data.booking.demoData === true" class="demo-data-label">演示数据</span>
          <dl>
            <dt>服务</dt><dd>{{ previousCard.data.booking.serviceName || '乌东体验' }}</dd>
            <dt>日期</dt><dd>{{ previousCard.data.booking.travelDate || '待核对' }}</dd>
            <dt>人数</dt><dd>{{ previousCard.data.booking.peopleCount || '待核对' }}</dd>
            <dt>状态</dt><dd>{{ bookingStatusLabel(previousCard.data.booking.status) }}</dd>
          </dl>
        </template>

        <p v-else-if="previousCard.type === 'clarifying_question' && previousCard.data?.question">
          {{ previousCard.data.question }}
        </p>

        <ul v-if="previousCard.sources?.length" class="previous-card__sources">
          <li v-for="(source, index) in previousCard.sources" :key="`${index}-${source.title}`">{{ source.title }}</li>
        </ul>
      </div>
    </details>
  </section>
</template>
