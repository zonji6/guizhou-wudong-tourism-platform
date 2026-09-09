<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const emit = defineEmits(['opened'])
const KEY = 'wudong:scroll-opened'
const gateState = ref(sessionStorage.getItem(KEY) === '1' ? 'open' : 'closed')
const openButton = ref(null)
const motionQuery = matchMedia('(prefers-reduced-motion: reduce)')
const reducedMotion = ref(motionQuery.matches)

function openScroll() {
  if (gateState.value === 'closed') gateState.value = 'opening'
}

function finishOpen() {
  if (gateState.value !== 'opening') return
  sessionStorage.setItem(KEY, '1')
  gateState.value = 'open'
  emit('opened')
}

function replay() {
  gateState.value = 'closed'
  nextTick(() => openButton.value?.focus({ preventScroll: true }))
}

function syncMotion(event) { reducedMotion.value = event.matches }

defineExpose({ replay })
onMounted(() => motionQuery.addEventListener('change', syncMotion))
onBeforeUnmount(() => motionQuery.removeEventListener('change', syncMotion))
</script>

<template>
  <section v-if="gateState !== 'open'" class="scroll-gate" :class="{ 'is-opening': gateState === 'opening', 'is-reduced': reducedMotion }" aria-label="乌东水彩画卷">
    <div class="scroll-gate__panel scroll-gate__panel--left" aria-hidden="true" @animationend.self="finishOpen">
      <img class="scroll-gate__art" src="/images/wudong-art/scroll-watercolor.png" alt="" fetchpriority="high" />
      <span class="scroll-gate__wood"></span>
    </div>
    <div class="scroll-gate__panel scroll-gate__panel--right" aria-hidden="true" @animationend.self="finishOpen">
      <img class="scroll-gate__art" src="/images/wudong-art/scroll-watercolor.png" alt="" fetchpriority="high" />
      <span class="scroll-gate__wood"></span>
    </div>
    <div class="scroll-gate__center">
      <span class="scroll-gate__eyebrow">贵州 · 雷公山下</span>
      <h1>山水乌东</h1>
      <p>一卷山色，一寨人间</p>
      <button ref="openButton" class="scroll-gate__clasp" type="button" :disabled="gateState === 'opening'" @click="openScroll">
        <span class="scroll-gate__clasp-ripple" aria-hidden="true"></span>
        <svg aria-hidden="true" viewBox="0 0 48 40">
          <path class="scroll-gate__clasp-leaf" d="M5 32C11 10 28 3 43 5 39 23 23 38 5 32Z" />
          <path class="scroll-gate__clasp-vein" d="M9 30 38 9M22 21l-5-9m13 2 1-7" />
        </svg>
        <span>{{ gateState === 'opening' ? '画卷正展开' : '点击这里 · 开启画卷' }}</span>
        <span aria-hidden="true">↗</span>
      </button>
      <small>从一片茶叶，走进苗寨日常</small>
    </div>
    <span class="scroll-gate__footnote">乌东旅行手账 · 水彩意象</span>
  </section>
</template>
