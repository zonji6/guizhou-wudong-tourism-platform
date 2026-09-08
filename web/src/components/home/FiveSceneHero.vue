<script setup>
import { ref } from 'vue'
import SafeImage from '../common/SafeImage.vue'
defineProps({ scenes: { type: Array, required: true } })
const emit = defineEmits(['open-scene'])
const activeId = ref('')
</script>
<template>
  <section class="five-scenes" aria-label="乌东山水寨茶人实景">
    <button v-for="(scene, index) in scenes" :key="scene.id" class="scene-panel" :style="{ '--scene-index': index }" @focus="activeId = scene.id" @blur="activeId = ''" @mouseenter="activeId = scene.id" @mouseleave="activeId = ''" @click="emit('open-scene', scene)">
      <span class="scene-panel__visual" :class="{ 'is-active': activeId === scene.id }"><SafeImage :src="scene.src" :alt="scene.alt" :label="scene.label" :object-position="scene.desktopFocus" /></span><span class="scene-panel__label">{{ scene.label }}</span>
    </button>
  </section>
</template>
