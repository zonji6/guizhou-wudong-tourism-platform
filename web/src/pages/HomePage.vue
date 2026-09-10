<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import FiveSceneHero from '../components/home/FiveSceneHero.vue'
import LeafGuide from '../components/home/LeafGuide.vue'
import SceneExplorer from '../components/home/SceneExplorer.vue'
import ScrollGate from '../components/home/ScrollGate.vue'
import WudongJourney from '../components/home/WudongJourney.vue'
import { journeySections, scenes } from '../data/scenes'

const emit = defineEmits(['gate-change', 'navigate'])
const selectedScene = ref(null)
const leafVisible = ref(false)
const nearAssistant = ref(false)
const assistantAnchor = ref(null)
const scrollGate = ref(null)
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
const gateOpen = ref(sessionStorage.getItem('wudong:scroll-opened') === '1')
const gateOpening = ref(false)
let assistantObserver

const serviceEntries = [
  { id: 'product', mark: '礼', title: '商品', note: '茶、手作与可带走的山中记忆', path: '/explore/product' },
  { id: 'food', mark: '味', title: '食', note: '同一家店里选好几份寨味', path: '/explore/food' },
  { id: 'stay', mark: '宿', title: '住', note: '把夜晚留给溪声与木楼', path: '/explore/stay' },
  { id: 'travel', mark: '行', title: '行', note: '只看水彩示意，不冒充真实导航', path: '/explore/travel' },
  { id: 'community', mark: '记', title: '社区', note: '读一页寨里人的日常与路线手账', path: '/community' }
]

function selectScene(scene) {
  selectedScene.value = scene
}

function closeScene() {
  selectedScene.value = null
}

function handleSceneAction(action) {
  closeScene()
  emit('navigate', action)
}

function handleJourneyEntered() {
  leafVisible.value = true
}

function handleGateOpened() {
  gateOpening.value = false
  gateOpen.value = true
  emit('gate-change', true)
  nextTick(observeAssistant)
}

function replayScroll() {
  selectedScene.value = null
  gateOpen.value = false
  gateOpening.value = false
  leafVisible.value = false
  nearAssistant.value = false
  assistantObserver?.disconnect()
  emit('gate-change', false)
  scrollGate.value?.replay()
  window.scrollTo({ top: 0, behavior: 'instant' })
}

function handleGateOpening() {
  // 五景先在画卷下就位，门帘离开时不会露出空白底色。
  gateOpening.value = true
}

function scrollToAssistant() {
  assistantAnchor.value?.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth' })
}

function observeAssistant() {
  assistantObserver?.disconnect()
  if (!assistantAnchor.value) return
  assistantObserver = new IntersectionObserver(entries => {
    nearAssistant.value = entries.some(entry => entry.isIntersecting)
  }, { rootMargin: '20% 0px 20%', threshold: 0 })
  assistantObserver.observe(assistantAnchor.value)
}

onMounted(() => {
  emit('gate-change', gateOpen.value)
  scenes.forEach(scene => {
    const preload = new Image()
    preload.src = scene.src
  })
  if (gateOpen.value) nextTick(observeAssistant)
})

onBeforeUnmount(() => assistantObserver?.disconnect())
</script>

<template>
  <main class="home-page">
    <ScrollGate ref="scrollGate" @opening="handleGateOpening" @opened="handleGateOpened" />
    <template v-if="gateOpen || gateOpening">
      <FiveSceneHero :scenes="scenes" @open-scene="selectScene" @replay="replayScroll" />
      <SceneExplorer
        v-if="selectedScene"
        :scene="selectedScene"
        @close="closeScene"
        @action="handleSceneAction"
      />
      <WudongJourney
        :sections="journeySections"
        :entries="serviceEntries"
        @journey-entered="handleJourneyEntered"
        @navigate="emit('navigate', $event)"
      />

      <section ref="assistantAnchor" class="home-assistant">
        <p>一叶同行 · 旅行手账</p>
        <h2>下一页，想去哪里？</h2>
        <slot name="assistant">
          <button class="primary" @click="emit('navigate', { path: '/assistant' })">为我安排乌东之行</button>
        </slot>
      </section>
      <LeafGuide :visible="leafVisible" :near-assistant="nearAssistant" @open="scrollToAssistant" />
    </template>
  </main>
</template>
