<script setup>
import { computed, ref, watch } from 'vue'
import SafeImage from '../components/common/SafeImage.vue'
import { catalogPath } from '../router/hashRoutes'

const props = defineProps({
  kind: { type: String, default: 'product' },
  item: Object,
  loading: Boolean,
  error: String
})
defineEmits(['confirm', 'retry'])

const selectedRoomId = ref('')

const kindCopy = {
  product: { eyebrow: '乌东好物', action: '登记取货', priceUnit: '份' },
  food: { eyebrow: '在地风味', action: '预约到店', priceUnit: '份' },
  stay: { eyebrow: '山居住宿', action: '预约入住', priceUnit: '晚' }
}

const currentCopy = computed(() => kindCopy[props.kind] || kindCopy.product)
const rooms = computed(() => Array.isArray(props.item?.roomTypes) ? props.item.roomTypes : [])

watch([() => props.item?.id, rooms], () => {
  selectedRoomId.value = rooms.value.some(room => room.id === selectedRoomId.value)
    ? selectedRoomId.value
    : (rooms.value[0]?.id || '')
}, { immediate: true })

function price(value) {
  if (value === null || value === undefined || value === '') return null
  const amount = Number(value)
  return Number.isFinite(amount) ? amount : null
}

function confirmTarget() {
  if (!props.item) return
  if (props.kind === 'stay') {
    const room = rooms.value.find(value => value.id === selectedRoomId.value)
    if (room) return room
    return
  }
  return props.item
}
</script>

<template>
  <main class="page resource-detail-v2">
    <a class="detail-back" :href="`#${catalogPath(kind)}`">← 返回{{ currentCopy.eyebrow }}</a>

    <section v-if="loading" class="catalog-state" aria-live="polite">
      <span class="catalog-state__ripple" aria-hidden="true"></span>
      <p class="eyebrow">走近一点</p>
      <h1>正在读取详情</h1>
    </section>

    <section v-else-if="error" class="catalog-state catalog-state--error" role="alert">
      <p class="eyebrow">这一页暂时没有打开</p>
      <h1>详情读取失败</h1>
      <p>{{ error }}</p>
      <button type="button" class="ghost" @click="$emit('retry')">重新读取</button>
    </section>

    <article v-else-if="item" class="resource-detail-v2__body">
      <div class="resource-detail-v2__visual">
        <SafeImage
          :src="item.imageUrl"
          :alt="`${item.name}的服务图片`"
          :label="item.name"
          loading="eager"
        />
        <span v-if="item.demoData === true" class="demo-data-label">演示数据</span>
      </div>

      <div class="resource-detail-v2__copy">
        <p class="eyebrow">{{ currentCopy.eyebrow }} · {{ item.merchantName || '乌东在地服务方' }}</p>
        <h1>{{ item.name }}</h1>
        <div v-if="Array.isArray(item.tags) && item.tags.length" class="tag-row">
          <span v-for="tag in item.tags" :key="tag">{{ tag }}</span>
        </div>
        <p class="resource-detail-v2__description">{{ item.description || '服务方正在补充这项内容的详细说明。' }}</p>

        <dl v-if="kind !== 'stay'" class="resource-detail-v2__facts">
          <template v-if="kind === 'product'">
            <dt>取货地点</dt><dd>{{ item.pickupPoint || '待服务方确认' }}</dd>
          </template>
          <template v-else>
            <dt>接待时段</dt><dd>{{ item.visitTimeText || '待服务方确认' }}</dd>
          </template>
          <dt>服务方</dt><dd>{{ item.merchantName || '乌东在地服务方' }}</dd>
        </dl>

        <p v-if="kind !== 'stay' && price(item.price) !== null" class="resource-detail-v2__price">
          <small>参考价格</small><strong>¥{{ price(item.price) }}</strong><span>/ {{ currentCopy.priceUnit }}</span>
        </p>

        <section v-if="kind === 'stay'" class="room-picker">
          <header>
            <div><p class="eyebrow">选择房型</p><h2>今夜住在哪一间</h2></div>
            <span>{{ item.locationText || '住宿位置待确认' }}</span>
          </header>
          <label v-for="room in rooms" :key="room.id" :class="{ active: selectedRoomId === room.id }">
            <input v-model="selectedRoomId" type="radio" name="roomType" :value="room.id">
            <SafeImage :src="room.imageUrl" :alt="`${room.name}房型图片`" :label="room.name" loading="lazy" />
            <span>
              <strong>{{ room.name }}</strong>
              <small>{{ room.description || '房型详情待服务方补充。' }}</small>
              <em>最多 {{ room.maxGuests }} 人</em>
            </span>
            <b v-if="price(room.price) !== null">¥{{ price(room.price) }}<small>/ 晚</small></b>
          </label>
          <p v-if="!rooms.length" class="service-notice">当前住宿还没有可预约的已发布房型。</p>
        </section>

        <aside class="resource-detail-v2__notice">
          <b>下单前说明</b>
          <p>联系人与电话只随本次订单提交，不由页面另行保存。订单提交成功后可在“我的订单”中凭当前浏览器的匿名演示身份查看。</p>
        </aside>

        <button
          type="button"
          class="primary resource-detail-v2__action"
          :disabled="kind === 'stay' && !selectedRoomId"
          @click="$emit('confirm', kind, confirmTarget())"
        >
          {{ currentCopy.action }}
        </button>
      </div>
    </article>

    <section v-else class="catalog-state">
      <p class="eyebrow">没有匹配到公开内容</p>
      <h1>这项内容可能已经下架</h1>
      <p>请返回目录重新选择。</p>
    </section>
  </main>
</template>
