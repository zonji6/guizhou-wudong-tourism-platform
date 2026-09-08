<script setup>
import { onMounted, ref } from 'vue'
import FiveSceneHero from '../components/home/FiveSceneHero.vue'
import LeafGuide from '../components/home/LeafGuide.vue'
import SceneExplorer from '../components/home/SceneExplorer.vue'
import ScrollGate from '../components/home/ScrollGate.vue'
import WudongJourney from '../components/home/WudongJourney.vue'
import { journeySections, scenes } from '../data/scenes'

const emit = defineEmits(['gate-change', 'navigate'])
const selectedScene = ref(null)
const leafVisible = ref(false)
const assistantAnchor = ref(null)
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
const gateOpen = ref(sessionStorage.getItem('wudong:scroll-opened') === '1')

const serviceEntries = [
  { id: 'product', mark: '礼', title: '乌东好物', note: '茶、手作与可带走的山中记忆', path: '/resources/products' },
  { id: 'food', mark: '味', title: '在地风味', note: '坐下来，尝寨里的一顿热饭', path: '/resources/foods' },
  { id: 'stay', mark: '宿', title: '山居住宿', note: '把夜晚留给溪声与木楼', path: '/resources/stays' },
  { id: 'orders', mark: '记', title: '我的订单', note: '找回这台浏览器提交的三类订单', path: '/orders' }
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
  gateOpen.value = true
  emit('gate-change', true)
}

function scrollToAssistant() {
  assistantAnchor.value?.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth' })
}

onMounted(() => scenes.forEach(scene => {
  const preload = new Image()
  preload.src = scene.src
}))
</script>

<template>
  <main class="home-page">
    <ScrollGate @opened="handleGateOpened" />
    <template v-if="gateOpen">
      <FiveSceneHero :scenes="scenes" @open-scene="selectScene" />
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

      <section class="home-service-v2">
        <header>
          <p class="eyebrow">衣食住行，从一条溪边小路分开</p>
          <h2>把时间留给乌东的山与人</h2>
          <span>每个入口连接真实公开目录；没有内容时如实留白，不用演示卡片替代。</span>
        </header>
        <div>
          <button v-for="entry in serviceEntries" :key="entry.id" type="button" @click="emit('navigate', { path: entry.path })">
            <i>{{ entry.mark }}</i>
            <span><strong>{{ entry.title }}</strong><small>{{ entry.note }}</small></span>
            <b aria-hidden="true">↗</b>
          </button>
        </div>
      </section>

      <section ref="assistantAnchor" class="home-assistant">
        <p>乌东向导</p>
        <h2>想好下一步，再出发</h2>
        <slot name="assistant">
          <button class="primary" @click="emit('navigate', { path: '/assistant' })">为我安排乌东之行</button>
        </slot>
      </section>
      <LeafGuide :visible="leafVisible" @open="scrollToAssistant" />
    </template>
  </main>
</template>
