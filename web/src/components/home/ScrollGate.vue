<script setup>
import { ref } from 'vue'
const emit = defineEmits(['opened'])
const KEY = 'wudong:scroll-opened'
const gateState = ref(sessionStorage.getItem(KEY) === '1' ? 'open' : 'closed')
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches
function openScroll() { if (gateState.value === 'closed') { gateState.value = 'opening'; if (reducedMotion) finishOpen() } }
function finishOpen() { if (gateState.value === 'opening') { sessionStorage.setItem(KEY, '1'); gateState.value = 'open'; emit('opened') } }
</script>
<template>
  <section v-if="gateState !== 'open'" class="scroll-gate" :class="{ 'is-opening': gateState === 'opening', 'is-reduced': reducedMotion }">
    <div class="scroll-gate__panel scroll-gate__panel--left" aria-hidden="true" @animationend="finishOpen"></div><div class="scroll-gate__panel scroll-gate__panel--right" aria-hidden="true" @animationend="finishOpen"></div>
    <div class="scroll-gate__center"><p>走进雷公山半山的乌东苗寨</p><button @click="openScroll">点击这里</button><small>开启画卷</small></div>
  </section>
</template>
