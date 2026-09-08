<script setup>
import { computed, ref, watch } from 'vue'
import { useAssistantSession } from '../../composables/useAssistantSession'

const props = defineProps({ embedded: Boolean, joinedServiceIds: Array })
const emit = defineEmits(['open-service', 'join-service', 'book-service'])
const input = ref('我想体验贵州乌东的苗族文化和茶旅')
const sourcesOpen = ref(false)
const previousOpen = ref(false)
const session = useAssistantSession()
const card = computed(() => session.activeCard.value)
const days = computed(() => Array.isArray(card.value?.data?.days) ? card.value.data.days : [])
const services = computed(() => Array.isArray(card.value?.data?.services) ? card.value.data.services : [])

watch(card, () => { sourcesOpen.value = false; previousOpen.value = false })
function serviceId(item) { return typeof item?.serviceId === 'string' && item.serviceId.trim() ? item.serviceId : '' }
function serviceAction(item, action) {
  const id = serviceId(item)
  if (!id) return
  emit(action, action === 'book-service' ? { serviceId: id } : id)
}
function pendingAction() {
  const booking = card.value?.data?.booking
  if (typeof booking?.serviceId !== 'string' || !booking.serviceId.trim() || typeof booking?.id !== 'string' || !booking.id.trim()) return
  emit('book-service', { serviceId: booking.serviceId, pendingBooking: booking })
}
</script>
<template>
  <section class="assistant-workspace" :class="{ embedded }">
    <p v-if="session.mode.value === 'demo'" class="demo-badge">演示模式</p>
    <div class="suggestions"><button @click="input='周末两位，安排两天一夜的苗族文化茶旅';session.send(input)">两天一夜茶旅</button><button @click="input='推荐适合亲子的苗绣体验';session.send(input)">亲子苗绣</button></div>
    <textarea v-model="input" aria-label="输入旅行需求" placeholder="例如：我想体验贵州乌东的苗族文化和茶旅" />
    <button class="primary" @click="session.send(input)">请向导规划</button>
    <ol class="assistant-phases"><li v-for="phase in session.phases.value" :key="phase.label" :class="phase.state">{{ phase.label }}：{{ phase.state }}</li></ol>
    <article v-if="card" class="agent-card"><h2>{{ card.title }}</h2><p>{{ card.summary }}</p>
      <template v-if="card.type === 'itinerary'"><section v-for="day in days" :key="day.day"><h3>第 {{ day.day }} 天 · {{ day.theme }}</h3><div v-for="item in day.items || []" :key="item.title"><b>{{ item.time }} {{ item.title }}</b><p>{{ item.summary }}</p><button v-if="serviceId(item)" @click="serviceAction(item,'open-service')">查看详情</button><button v-if="serviceId(item) && !joinedServiceIds?.includes(serviceId(item))" @click="serviceAction(item,'join-service')">加入行程</button><button v-if="serviceId(item)" @click="serviceAction(item,'book-service')">预约</button></div></section></template>
      <template v-else-if="card.type === 'service_recommendation'"><div v-for="item in services" :key="item.title"><h3>{{ item.title }}</h3><p>{{ item.summary }}</p><button v-if="serviceId(item)" @click="serviceAction(item,'open-service')">查看详情</button><button v-if="serviceId(item)" @click="serviceAction(item,'join-service')">加入行程</button><button v-if="serviceId(item)" @click="serviceAction(item,'book-service')">预约</button></div></template>
      <template v-else-if="card.type === 'clarifying_question'"><p>{{ card.data.question }}</p></template>
      <template v-else-if="card.type === 'pending_booking'"><p>{{ card.data.booking?.serviceName }}</p><button v-if="typeof card.data.booking?.serviceId === 'string' && typeof card.data.booking?.id === 'string'" @click="pendingAction">继续确认预约</button></template>
      <button v-if="card.type === 'error' && card.data.demoAvailable === true" @click="session.useDemo">查看演示结果</button>
      <button v-if="card.sources.length" :aria-expanded="sourcesOpen" @click="sourcesOpen=!sourcesOpen">参考了 {{ card.sources.length }} 条乌东资料</button><ul v-if="sourcesOpen"><li v-for="source in card.sources" :key="source.title">{{ source.title }}</li></ul>
    </article>
    <details v-if="session.previousCard.value" v-model:open="previousOpen"><summary>上一版</summary><h3>{{ session.previousCard.value.title }}</h3><p>{{ session.previousCard.value.summary }}</p></details>
  </section>
</template>
