<script setup>
import SafeImage from '../components/common/SafeImage.vue'

defineProps({ posts: { type: Array, default: () => [] } })

function tagsOf(post) {
  return Array.isArray(post?.tags) ? post.tags : []
}

function dateLabel(value) {
  if (typeof value !== 'string' || !value.trim()) return ''
  return value.slice(0, 10)
}
</script>

<template>
  <main class="page community-page">
    <header class="page-intro">
      <p class="eyebrow">山里人的日常，也是一段旅程</p>
      <h1>寨里分享</h1>
      <p>从一盏茶、一顿饭和一次慢行里，认识乌东更真实的温度。</p>
    </header>

    <div v-if="posts.length" class="post-grid">
      <article v-for="post in posts" :key="post.id" class="post-card">
        <SafeImage
          :src="post.cover"
          :alt="post.coverAlt || '寨里分享配图暂缺'"
          :label="post.title"
          loading="lazy"
        />
        <div>
          <p class="post-card__meta">
            <span>{{ post.author || '乌东村寨记录者' }}</span>
            <span v-if="dateLabel(post.publishedAt)">{{ dateLabel(post.publishedAt) }}</span>
            <i v-if="post.demoData === true">演示数据</i>
          </p>
          <h2>{{ post.title }}</h2>
          <p>{{ post.text }}</p>
          <div v-if="tagsOf(post).length" class="tag-row">
            <span v-for="tag in tagsOf(post)" :key="tag">{{ tag }}</span>
          </div>
          <small v-if="typeof post.likes === 'number'">{{ post.likes }} 人觉得温暖</small>
        </div>
      </article>
    </div>
    <section v-else class="empty-state">
      <h2>寨里的故事还在整理</h2>
      <p>当前没有可展示的社区内容。</p>
    </section>

    <p class="page-demo-note">社区内容用于本机答辩原型；涉及地点、人物与服务的信息仍需后续核验。</p>
  </main>
</template>
