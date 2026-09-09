<script setup>
import { onBeforeUnmount, ref } from 'vue'

defineProps({
  visible: Boolean,
  nearAssistant: Boolean
})
const emit = defineEmits(['open'])
const clicked = ref(false)
let clickTimer

function handleOpen() {
  clicked.value = true
  clearTimeout(clickTimer)
  clickTimer = setTimeout(() => { clicked.value = false }, 950)
  emit('open')
}

onBeforeUnmount(() => clearTimeout(clickTimer))
</script>

<template>
  <button
    v-if="visible"
    class="leaf-guide"
    :class="{ 'is-near-assistant': nearAssistant, 'is-clicked': clicked }"
    aria-label="打开乌东向导"
    @click="handleOpen"
  >
    <span class="leaf-guide__ripple leaf-guide__ripple--one" aria-hidden="true"></span>
    <span class="leaf-guide__ripple leaf-guide__ripple--two" aria-hidden="true"></span>
    <span class="leaf-guide__leaf" aria-hidden="true">
      <svg viewBox="0 0 92 72">
        <path class="leaf-guide__outline" d="M7 56C19 18 50 5 84 10 76 47 49 69 7 56Z" />
        <path class="leaf-guide__vein" d="M13 54C34 44 52 31 75 16M37 42 27 28m26 3-2-15" />
      </svg>
    </span>
    <small>一叶同行</small>
  </button>
</template>
