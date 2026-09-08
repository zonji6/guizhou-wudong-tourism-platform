<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
const emit = defineEmits(['opened'])
const KEY = 'wudong:scroll-opened'
const gateState = ref(sessionStorage.getItem(KEY) === '1' ? 'open' : 'closed')
const motionQuery = matchMedia('(prefers-reduced-motion: reduce)')
const reducedMotion = ref(motionQuery.matches)
function openScroll() { if (gateState.value === 'closed') { gateState.value = 'opening'; if (reducedMotion.value) finishOpen() } }
function finishOpen() { if (gateState.value === 'opening') { sessionStorage.setItem(KEY, '1'); gateState.value = 'open'; emit('opened') } }
function syncMotion(event) { reducedMotion.value = event.matches; if (event.matches) finishOpen() }
onMounted(() => motionQuery.addEventListener('change', syncMotion))
onBeforeUnmount(() => motionQuery.removeEventListener('change', syncMotion))
</script>
<template>
  <section v-if="gateState !== 'open'" class="scroll-gate" :class="{ 'is-opening': gateState === 'opening', 'is-reduced': reducedMotion }">
    <div class="scroll-gate__panel scroll-gate__panel--left" aria-hidden="true" @animationend="finishOpen"></div><div class="scroll-gate__panel scroll-gate__panel--right" aria-hidden="true" @animationend="finishOpen"></div>
    <div class="scroll-gate__center"><p>走进雷公山半山的乌东苗寨</p><button @click="openScroll">点击这里</button><small>开启画卷</small></div>
  </section>
</template>
