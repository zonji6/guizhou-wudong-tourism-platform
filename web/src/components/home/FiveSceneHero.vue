<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import SafeImage from '../common/SafeImage.vue'
defineProps({ scenes: { type: Array, required: true } })
const emit = defineEmits(['open-scene', 'replay'])
const sceneSubtitles = {
  mountain: '云起雷公山',
  water: '风雨桥畔',
  village: '乌东苗寨',
  tea: '采一叶春意',
  people: '门前笑声'
}
const activeId = ref('')
const compact = ref(false)
let media
function syncViewport() { compact.value = media.matches }
onMounted(() => { media = matchMedia('(max-width: 760px)'); syncViewport(); media.addEventListener('change', syncViewport) })
onBeforeUnmount(() => media?.removeEventListener('change', syncViewport))
</script>
<template>
  <section class="five-scenes" aria-label="乌东山水寨茶人实景">
    <header class="five-scenes__heading">
      <div>
        <span class="five-scenes__eyebrow">在雷公山下，翻开这一页</span>
        <h1>乌东 · 山水寨茶人</h1>
      </div>
      <div class="five-scenes__intro">
        <p>点开一幅风景，看看寨里的日常</p>
        <button class="five-scenes__replay" type="button" @click="emit('replay')">重看画卷 <span aria-hidden="true">↗</span></button>
      </div>
    </header>
    <div class="five-scenes__frame">
      <button v-for="(scene, index) in scenes" :key="scene.id" class="scene-panel" :data-scene="scene.id" :style="{ '--scene-index': index }" @focus="activeId = scene.id" @blur="activeId = ''" @mouseenter="activeId = scene.id" @mouseleave="activeId = ''" @click="emit('open-scene', scene)">
        <span class="scene-panel__visual" :class="{ 'is-active': activeId === scene.id }"><SafeImage :src="scene.src" :alt="scene.alt" :label="scene.label" :object-position="compact ? scene.mobileFocus : scene.desktopFocus" /></span>
        <span class="scene-panel__label"><b>{{ scene.label }}</b><small>{{ sceneSubtitles[scene.id] }}</small></span>
      </button>
    </div>
    <footer class="five-scenes__footer"><span>山中五景，慢慢看</span><span>向下，沿着溪水走 <i aria-hidden="true">↓</i></span></footer>
  </section>
</template>
