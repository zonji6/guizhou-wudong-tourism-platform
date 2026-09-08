<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import SafeImage from '../common/SafeImage.vue'
const props = defineProps({ scene: Object })
const emit = defineEmits(['close', 'action'])
const dialogRef = ref(null)
const compact = ref(false)
let media
function syncViewport() { compact.value = media.matches }
function closeExplorer() { if (dialogRef.value?.open) dialogRef.value.close(); emit('close') }
function handleDialogClick(event) { if (event.target === dialogRef.value) closeExplorer() }
function runSceneAction() { emit('action', props.scene.action) }
watch(() => props.scene, scene => { if (scene) nextTick(() => { if (!dialogRef.value.open) dialogRef.value.showModal() }) }, { immediate: true })
onBeforeUnmount(() => { if (dialogRef.value?.open) dialogRef.value.close() })
onMounted(() => { media = matchMedia('(max-width: 760px)'); syncViewport(); media.addEventListener('change', syncViewport) })
onBeforeUnmount(() => media?.removeEventListener('change', syncViewport))
</script>
<template><dialog ref="dialogRef" class="scene-explorer" @click="handleDialogClick" @cancel.prevent="closeExplorer"><article v-if="scene"><button class="scene-explorer__close" aria-label="关闭探景" @click="closeExplorer">×</button><SafeImage :src="scene.expandedAsset" :alt="scene.expandedAlt" :label="scene.label" loading="lazy" :object-position="compact ? scene.mobileFocus : scene.desktopFocus" /><div><p>{{ scene.label }} · 探景</p><h2>{{ scene.story }}</h2><button class="primary" @click="runSceneAction">{{ scene.action.label }}</button></div></article></dialog></template>
