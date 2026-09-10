<script setup>
import { ref, watch } from 'vue'
const props = defineProps({ src: String, alt: { type: String, required: true }, label: { type: String, default: '乌东实景' }, loading: { type: String, default: 'eager' }, objectPosition: { type: String, default: '50% 50%' } })
const failed = ref(!props.src)
watch(() => props.src, value => { failed.value = !value })
function markFailed() { failed.value = true }
</script>
<template>
  <img v-if="!failed" class="safe-image" :src="src" :alt="alt" :loading="loading" decoding="async" :style="{ objectPosition }" @error="markFailed">
  <div v-else class="safe-image safe-image--fallback" role="img" :aria-label="alt"><strong>{{ label }}</strong><span>{{ alt }}</span></div>
</template>
