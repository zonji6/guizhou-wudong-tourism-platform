<script setup>
import { computed, ref, watch } from 'vue'
import SafeImage from '../components/common/SafeImage.vue'

const props = defineProps({
  kind: { type: String, default: 'product' },
  items: { type: Array, default: () => [] },
  loading: Boolean,
  error: String
})
defineEmits(['switch-kind', 'open-item', 'retry'])

const keyword = ref('')
const activeTag = ref('')

const catalogKinds = [
  { id: 'product', label: '乌东好物', path: 'products', eyebrow: '把山里的手艺带回去' },
  { id: 'food', label: '在地风味', path: 'foods', eyebrow: '坐下来，吃一顿寨里的饭' },
  { id: 'stay', label: '山居住宿', path: 'stays', eyebrow: '听着溪声，在木楼里住一晚' }
]

const currentKind = computed(() => catalogKinds.find(item => item.id === props.kind) || catalogKinds[0])
const availableTags = computed(() => [...new Set(props.items.flatMap(item => Array.isArray(item.tags) ? item.tags : []))].slice(0, 8))
const visibleItems = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase('zh-CN')
  return props.items.filter(item => {
    const tags = Array.isArray(item.tags) ? item.tags : []
    const matchesTag = !activeTag.value || tags.includes(activeTag.value)
    const haystack = [item.name, item.description, item.merchantName, item.locationText, ...tags]
      .filter(value => typeof value === 'string')
      .join(' ')
      .toLocaleLowerCase('zh-CN')
    return matchesTag && (!query || haystack.includes(query))
  })
})

watch(() => props.kind, () => {
  keyword.value = ''
  activeTag.value = ''
})

function itemPrice(item) {
  if (props.kind !== 'stay') {
    if (item.price === null || item.price === undefined || item.price === '') return null
    const amount = Number(item.price)
    return Number.isFinite(amount) ? amount : null
  }
  const prices = Array.isArray(item.roomTypes)
    ? item.roomTypes
        .filter(room => room.price !== null && room.price !== undefined && room.price !== '')
        .map(room => Number(room.price))
        .filter(Number.isFinite)
    : []
  return prices.length ? Math.min(...prices) : null
}

function subline(item) {
  if (props.kind === 'product') return item.pickupPoint || '取货点待确认'
  if (props.kind === 'food') return item.visitTimeText || '到店时间待确认'
  return item.locationText || '住宿位置待确认'
}
</script>

<template>
  <main class="page resources-page catalog-v2">
    <header class="catalog-v2__intro">
      <div>
        <p class="eyebrow">{{ currentKind.eyebrow }}</p>
        <h1>在乌东，慢慢选择</h1>
        <p>这里展示服务方已发布的内容。价格与接待条件以提交订单时的页面信息为准。</p>
      </div>
      <a class="orders-shortcut" href="#/orders"><span>我的</span>三类订单</a>
    </header>

    <nav class="catalog-v2__tabs" aria-label="乌东服务分类">
      <button
        v-for="item in catalogKinds"
        :key="item.id"
        type="button"
        :class="{ active: kind === item.id }"
        @click="$emit('switch-kind', item.id)"
      >
        <span>{{ item.label }}</span>
        <small>{{ item.id === 'product' ? '带走' : item.id === 'food' ? '到店' : '住下' }}</small>
      </button>
    </nav>

    <section v-if="!loading && !error && items.length" class="catalog-v2__tools" aria-label="筛选当前目录">
      <label>
        <span>在当前分类中寻找</span>
        <input v-model="keyword" type="search" placeholder="输入名称、商家或标签">
      </label>
      <div v-if="availableTags.length" class="catalog-v2__tags">
        <button type="button" :class="{ active: !activeTag }" @click="activeTag = ''">全部</button>
        <button
          v-for="tag in availableTags"
          :key="tag"
          type="button"
          :class="{ active: activeTag === tag }"
          @click="activeTag = activeTag === tag ? '' : tag"
        >{{ tag }}</button>
      </div>
    </section>

    <section v-if="loading" class="catalog-state" aria-live="polite">
      <span class="catalog-state__ripple" aria-hidden="true"></span>
      <p class="eyebrow">沿溪查找</p>
      <h2>正在读取{{ currentKind.label }}</h2>
      <p>只呈现服务方已经发布的真实目录。</p>
    </section>

    <section v-else-if="error" class="catalog-state catalog-state--error" role="alert">
      <p class="eyebrow">山路暂歇</p>
      <h2>暂时没能读取{{ currentKind.label }}</h2>
      <p>{{ error }}</p>
      <button type="button" class="ghost" @click="$emit('retry')">重新读取</button>
    </section>

    <section v-else-if="items.length && !visibleItems.length" class="catalog-state">
      <p class="eyebrow">换一条小路</p>
      <h2>没有匹配的内容</h2>
      <p>试试清空关键词或标签，目录本身仍在。</p>
      <button type="button" class="ghost" @click="keyword = ''; activeTag = ''">清空筛选</button>
    </section>

    <section v-else-if="visibleItems.length" class="catalog-v2__grid" :aria-label="currentKind.label">
      <article v-for="(item, index) in visibleItems" :key="item.id" class="catalog-v2__card" :style="{ '--card-index': index }">
        <button type="button" @click="$emit('open-item', item)">
          <SafeImage
            :src="item.imageUrl"
            :alt="`${item.name}的服务图片`"
            :label="item.name"
            loading="lazy"
          />
          <span class="catalog-v2__card-copy">
            <span class="catalog-v2__meta">
              <i v-if="item.demoData === true">演示数据</i>
              <small>{{ item.merchantName || '乌东在地服务方' }}</small>
            </span>
            <strong>{{ item.name }}</strong>
            <span>{{ item.description || '详情由服务方持续补充。' }}</span>
            <span v-if="Array.isArray(item.tags) && item.tags.length" class="tag-row">
              <small v-for="tag in item.tags.slice(0, 3)" :key="tag">{{ tag }}</small>
            </span>
            <span class="catalog-v2__card-foot">
              <small>{{ subline(item) }}</small>
              <b v-if="itemPrice(item) !== null">¥{{ itemPrice(item) }}<em v-if="kind === 'stay'">起</em></b>
            </span>
          </span>
        </button>
      </article>
    </section>

    <section v-else class="catalog-state">
      <p class="eyebrow">仍在生长</p>
      <h2>{{ currentKind.label }}还没有已发布内容</h2>
      <p>这不是演示占位。可以切换其他分类，稍后再回来看看。</p>
    </section>
  </main>
</template>
