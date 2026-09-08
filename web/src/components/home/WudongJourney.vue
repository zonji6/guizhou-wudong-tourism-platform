<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import SafeImage from '../common/SafeImage.vue'
defineProps({ sections: Array, categories: Array })
const emit = defineEmits(['journey-entered', 'navigate'])
const boundary = ref(null); const fogState = ref('idle'); let observer
const motionQuery = matchMedia('(prefers-reduced-motion: reduce)')
const reducedMotion = ref(motionQuery.matches)
function enterJourney() { if (fogState.value === 'idle') { fogState.value = 'passing'; if (reducedMotion.value) finishFogTransition() } }
function finishFogTransition() { if (fogState.value === 'passing') { fogState.value = 'cleared'; emit('journey-entered') } }
function syncMotion(event) { reducedMotion.value = event.matches; if (event.matches) finishFogTransition() }
onMounted(() => { motionQuery.addEventListener('change', syncMotion); observer = new IntersectionObserver(entries => entries.forEach(entry => entry.isIntersecting && enterJourney()), { threshold: .2 }); observer.observe(boundary.value) })
onBeforeUnmount(() => { motionQuery.removeEventListener('change', syncMotion); observer?.disconnect() })
</script>
<template><section ref="boundary" class="journey"><div v-if="fogState === 'passing'" class="journey__fog" aria-hidden="true" @animationend="finishFogTransition"></div><div v-if="fogState === 'idle'" class="journey__trigger" aria-hidden="true"></div><article v-for="section in sections" :key="section.id" class="journey__chapter"><SafeImage :src="section.image" :alt="section.alt" :label="section.eyebrow" loading="lazy" /><div><p>{{ section.eyebrow }}</p><h2>{{ section.title }}</h2><span>{{ section.body }}</span></div></article><ol class="journey__services"><li v-for="category in categories" :key="category.id"><button @click="emit('navigate', { path: '/resources', category: category.id })"><b>{{ category.icon }}</b>{{ category.label }}</button></li></ol></section></template>
