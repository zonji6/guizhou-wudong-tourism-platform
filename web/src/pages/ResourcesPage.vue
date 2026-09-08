<script setup>
import SafeImage from '../components/common/SafeImage.vue'

defineProps({
  services: { type: Array, default: () => [] },
  categories: { type: Array, default: () => [] },
  activeCategory: { type: String, default: 'all' }
})
defineEmits(['filter', 'open-service'])

function tagsOf(item) {
  return Array.isArray(item?.tags) ? item.tags : []
}

function hasPrice(value) {
  return typeof value === 'number' && Number.isFinite(value)
}

function categoryLabel(category) {
  return {
    culture: '文化茶旅',
    stay: '苗寨住宿',
    food: '寨味餐食',
    travel: '山野出行'
  }[category] || '乌东体验'
}
</script>

<template>
  <main class="page resources-page">
    <header class="page-intro">
      <p class="eyebrow">沿溪寻一段自己的路</p>
      <h1>游乌东</h1>
      <p>从茶旅、苗寨日常到食宿出行，先看清演示信息，再决定下一步。</p>
    </header>

    <nav class="filter" aria-label="服务分类">
      <button type="button" :class="{ active: activeCategory === 'all' }" @click="$emit('filter', 'all')">全部</button>
      <button
        v-for="category in categories"
        :key="category.id"
        type="button"
        :class="{ active: activeCategory === category.id }"
        @click="$emit('filter', category.id)"
      >
        {{ category.label }}
      </button>
    </nav>

    <div v-if="services.length" class="service-grid service-grid--catalog">
      <article v-for="item in services" :key="item.id" class="service-card service-card--catalog">
        <button type="button" class="service-card__open" @click="$emit('open-service', item.id)">
          <SafeImage
            :src="item.image"
            :alt="item.imageAlt || '服务实景暂缺'"
            :label="item.title"
            loading="lazy"
          />
          <span class="service-card__body">
            <span class="service-card__meta">
              <span><i v-if="item.demoData === true">演示数据</i>{{ categoryLabel(item.category) }}</span>
              <em v-if="item.durationText">{{ item.durationText }}</em>
            </span>
            <strong>{{ item.title }}</strong>
            <span v-if="tagsOf(item).length" class="tag-row">
              <small v-for="tag in tagsOf(item)" :key="tag">{{ tag }}</small>
            </span>
            <span class="service-card__foot">
              <small>{{ item.locationText || item.merchantName || '地点待服务方确认' }}</small>
              <b v-if="hasPrice(item.price)">¥{{ item.price }} 起</b>
            </span>
          </span>
        </button>
      </article>
    </div>
    <section v-else class="empty-state">
      <h2>这一类体验还在整理</h2>
      <p>可以切换其他分类，或请乌东向导换一种方式安排。</p>
    </section>

    <p class="page-demo-note">页面中的价格、地点与可预约情况可能为演示数据，请以服务方确认结果为准。</p>
  </main>
</template>
