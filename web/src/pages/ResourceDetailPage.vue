<script setup>
import SafeImage from '../components/common/SafeImage.vue'

const props = defineProps({
  service: Object,
  categoryLabel: String
})
defineEmits(['book'])

function hasServiceId() {
  return typeof props.service?.id === 'string' && props.service.id.trim().length > 0
}

function tagsOf() {
  return Array.isArray(props.service?.tags) ? props.service.tags : []
}

function hasPrice(value) {
  return typeof value === 'number' && Number.isFinite(value)
}
</script>

<template>
  <main v-if="service" class="page detail resource-detail-page">
    <SafeImage
      class="detail-image"
      :src="service.image"
      :alt="service.imageAlt || '服务实景暂缺'"
      :label="service.title"
      loading="eager"
    />
    <article class="detail-copy">
      <div class="detail-kicker">
        <span>{{ categoryLabel }}</span>
        <i v-if="service.demoData === true">演示数据</i>
      </div>
      <h1>{{ service.title }}</h1>
      <div v-if="tagsOf().length" class="tag-row">
        <span v-for="tag in tagsOf()" :key="tag">{{ tag }}</span>
      </div>
      <p class="intro">{{ service.intro }}</p>
      <dl class="service-facts">
        <template v-if="service.merchantName"><dt>服务方</dt><dd>{{ service.merchantName }}</dd></template>
        <template v-if="service.durationText"><dt>参考时长</dt><dd>{{ service.durationText }}</dd></template>
        <template v-if="service.locationText"><dt>地点说明</dt><dd>{{ service.locationText }}</dd></template>
      </dl>
      <p v-if="hasPrice(service.price)" class="price">¥{{ service.price }} <small>起</small></p>
      <p class="service-notice">服务时间、价格与可预约情况请以服务方确认结果为准。</p>
      <button v-if="hasServiceId()" type="button" class="primary" @click="$emit('book', service)">预约这项体验</button>
    </article>
  </main>
  <main v-else class="page route-empty-state">
    <p class="eyebrow">没有匹配到可操作内容</p>
    <h1>未找到这项乌东体验</h1>
    <p>链接可能已失效，也可能不是有效的服务标识。请返回“游乌东”重新选择。</p>
    <a class="ghost" href="#/resources">返回游乌东</a>
  </main>
</template>
