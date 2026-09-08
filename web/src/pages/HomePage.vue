<script setup>
import { onMounted, ref } from 'vue'
import { scenes, journeySections } from '../data/scenes'
import ScrollGate from '../components/home/ScrollGate.vue'
import FiveSceneHero from '../components/home/FiveSceneHero.vue'
import SceneExplorer from '../components/home/SceneExplorer.vue'
import WudongJourney from '../components/home/WudongJourney.vue'
import LeafGuide from '../components/home/LeafGuide.vue'
defineProps({ services: Array, categories: Array })
const emit = defineEmits(['gate-change', 'navigate', 'open-service'])
const selectedScene = ref(null); const leafVisible = ref(false); const assistantAnchor = ref(null)
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
function selectScene(scene) { selectedScene.value = scene }
function closeScene() { selectedScene.value = null }
function handleSceneAction(action) { closeScene(); emit('navigate', action) }
function handleJourneyEntered() { leafVisible.value = true }
function handleGateOpened() { emit('gate-change', true) }
function scrollToAssistant() { assistantAnchor.value?.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth' }) }
onMounted(() => scenes.forEach(scene => { const preload = new Image(); preload.src = scene.src }))
</script>
<template>
  <main class="home-page"><ScrollGate @opened="handleGateOpened" /><FiveSceneHero :scenes="scenes" @open-scene="selectScene" /><SceneExplorer v-if="selectedScene" :scene="selectedScene" @close="closeScene" @action="handleSceneAction" /><WudongJourney :sections="journeySections" :categories="categories" @journey-entered="handleJourneyEntered" @navigate="emit('navigate', $event)" /><section class="home-services"><p>衣食住行</p><h2>把时间留给乌东的山与人</h2><div><button v-for="service in services.slice(0, 3)" :key="service.id" @click="emit('open-service', service)">{{ service.title }}<small>¥{{ service.price }} 起 · 演示数据</small></button></div></section><section ref="assistantAnchor" class="home-assistant"><p>乌东向导</p><h2>想好下一步，再出发</h2><button class="primary" @click="emit('navigate', { path: '/assistant' })">为我安排乌东之行</button></section><LeafGuide :visible="leafVisible" @open="scrollToAssistant" /></main>
</template>
