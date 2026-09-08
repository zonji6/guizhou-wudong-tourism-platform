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
  if (reducedMotion.value) finishFogTransition()
}

function finishFogTransition() {
  if (fogState.value !== 'passing') return
  fogState.value = 'cleared'
  emit('journey-entered')
}

function syncMotion(event) {
  reducedMotion.value = event.matches
  if (event.matches) finishFogTransition()
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
  <section ref="boundary" class="journey">
    <div v-if="fogState === 'passing'" class="journey__fog" aria-hidden="true" @animationend="finishFogTransition"></div>
    <div v-if="fogState === 'idle'" class="journey__trigger" aria-hidden="true"></div>
    <article v-for="section in sections" :key="section.id" class="journey__chapter">
      <SafeImage :src="section.image" :alt="section.alt" :label="section.eyebrow" loading="lazy" />
      <div>
        <p>{{ section.eyebrow }}</p>
        <h2>{{ section.title }}</h2>
        <span>{{ section.body }}</span>
      </div>
    </article>
    <ol class="journey__services" aria-label="乌东服务入口">
      <li v-for="entry in entries" :key="entry.id">
        <button type="button" @click="emit('navigate', { path: entry.path })"><b>{{ entry.mark }}</b>{{ entry.title }}</button>
      </li>
    </ol>
  </section>
</template>
