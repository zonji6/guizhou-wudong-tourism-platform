<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import SafeImage from '../common/SafeImage.vue'

defineProps({
  sections: { type: Array, default: () => [] },
  entries: { type: Array, default: () => [] }
})
const emit = defineEmits(['journey-entered', 'navigate'])
const boundary = ref(null)
const fogState = ref('idle')
let observer
const motionQuery = matchMedia('(prefers-reduced-motion: reduce)')
const reducedMotion = ref(motionQuery.matches)

function enterJourney() {
  if (fogState.value !== 'idle') return
  fogState.value = 'passing'
}

function finishFogTransition() {
  if (fogState.value !== 'passing') return
  fogState.value = 'cleared'
  emit('journey-entered')
}

function syncMotion(event) {
  reducedMotion.value = event.matches
}

onMounted(() => {
  motionQuery.addEventListener('change', syncMotion)
  observer = new IntersectionObserver(entries => {
    entries.forEach(entry => entry.isIntersecting && enterJourney())
  }, { threshold: 0.2 })
  if (boundary.value) observer.observe(boundary.value)
})

onBeforeUnmount(() => {
  motionQuery.removeEventListener('change', syncMotion)
  observer?.disconnect()
})
</script>

<template>
  <section ref="boundary" class="journey" :class="{ 'is-reduced': reducedMotion }">
    <div v-if="fogState === 'passing'" class="journey__fog" aria-hidden="true" @animationend.self="finishFogTransition">
      <span class="journey__fog-layer journey__fog-layer--far"></span>
      <span class="journey__fog-layer journey__fog-layer--middle"></span>
      <span class="journey__fog-layer journey__fog-layer--near"></span>
    </div>
    <div v-if="fogState === 'idle'" class="journey__trigger" aria-hidden="true"></div>
    <div class="journey__nature" aria-hidden="true">
      <span class="journey__ridge"></span>
      <span class="journey__canopy journey__canopy--left"></span>
      <span class="journey__canopy journey__canopy--right"></span>
      <span class="journey__moss"></span>
    </div>
    <svg class="journey__stream" :class="{ 'is-drawn': fogState === 'cleared' }" aria-hidden="true" viewBox="0 0 1000 2100" preserveAspectRatio="none">
      <path class="journey__stream-water" d="M520 0C780 210 280 390 395 650c112 251 445 238 344 520-95 266-492 240-399 552 48 160 272 203 374 378" />
      <path class="journey__stream-light" pathLength="1" d="M520 0C780 210 280 390 395 650c112 251 445 238 344 520-95 266-492 240-399 552 48 160 272 203 374 378" />
      <path class="journey__stream-branch" pathLength="1" d="M525 1840c-29 109-154 135-258 197M612 1953c84 20 146 49 211 108" />
      <g class="journey__ripples">
        <ellipse cx="420" cy="644" rx="68" ry="18" />
        <ellipse cx="721" cy="1170" rx="62" ry="16" />
        <ellipse cx="357" cy="1715" rx="72" ry="19" />
      </g>
    </svg>
    <article
      v-for="(section, index) in sections"
      :key="section.id"
      class="journey__chapter"
      :data-chapter="section.id"
      :style="{ '--chapter-index': index, '--chapter-shift': `${(index - 1) * 1.6}rem` }"
    >
      <figure class="journey__photo">
        <SafeImage :src="section.image" :alt="section.alt" :label="section.eyebrow" loading="lazy" />
      </figure>
      <div>
        <p>{{ section.eyebrow }}</p>
        <h2>{{ section.title }}</h2>
        <span>{{ section.body }}</span>
      </div>
    </article>
    <ol class="journey__services" aria-label="乌东服务入口">
      <li v-for="entry in entries" :key="entry.id">
        <span class="journey__service-branch" aria-hidden="true"></span>
        <button type="button" @click="emit('navigate', { path: entry.path })">
          <b>{{ entry.mark }}</b><span>{{ entry.title }}</span><small>{{ entry.note }}</small>
        </button>
      </li>
    </ol>
  </section>
</template>
