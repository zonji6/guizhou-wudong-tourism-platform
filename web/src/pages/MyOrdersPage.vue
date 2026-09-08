<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  orders: { type: Object, default: () => ({ product: [], food: [], stay: [] }) },
  errors: { type: Object, default: () => ({ product: '', food: '', stay: '' }) },
  loading: { type: Object, default: () => ({ product: false, food: false, stay: false }) },
  identityError: String
})
defineEmits(['retry', 'reload'])

const activeKind = ref('product')
const kinds = [
  { id: 'product', label: '好物订单', empty: '还没有取货订单' },
  { id: 'food', label: '风味订单', empty: '还没有到店订单' },
  { id: 'stay', label: '住宿预约', empty: '还没有住宿预约' }
]
const current = computed(() => kinds.find(item => item.id === activeKind.value) || kinds[0])
const currentOrders = computed(() => Array.isArray(props.orders[activeKind.value]) ? props.orders[activeKind.value] : [])

function titleOf(order) {
  if (activeKind.value === 'product') return order.productName
  if (activeKind.value === 'food') return order.foodItemName
  return order.roomTypeName
}

function primaryLine(order) {
  if (activeKind.value === 'product') return `${order.quantity} 份 · ${order.pickupPoint || '取货点待确认'}`
  if (activeKind.value === 'food') return `${formatDateTime(order.visitAt)} · ${order.peopleCount} 人`
  return `${order.checkInDate || '入住日期待确认'} · ${order.peopleCount} 人`
}

function formatDateTime(value) {
  return typeof value === 'string' ? value.replace('T', ' ') : '时间待确认'
}

function statusLabel(status) {
  return {
    PENDING_PICKUP: '待取货',
    PICKED_UP: '已取货',
    PENDING_VISIT: '待到店',
    PENDING_CONFIRMATION: '待民宿确认',
    CONFIRMED: '已确认',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }[status] || '状态待核对'
}

function shortId(id) {
  return typeof id === 'string' && id.length > 12 ? `…${id.slice(-8)}` : (id || '待生成')
}
</script>

<template>
  <main class="page my-orders-page">
    <header class="my-orders-page__head">
      <div>
        <p class="eyebrow">当前浏览器的匿名演示身份</p>
        <h1>我的订单</h1>
        <p>三类订单分别读取；某一类暂时失败，不影响查看其他类型。</p>
      </div>
      <button type="button" class="ghost" :disabled="Object.values(loading).some(Boolean)" @click="$emit('reload')">全部刷新</button>
    </header>

    <aside class="privacy-note">
      <span aria-hidden="true">叶</span>
      <p><b>匿名演示身份</b>由浏览器生成并保存在本机，提交或查单时会发送给本机服务端用于订单归属；它不是登录认证。页面不会另行保存联系人和电话。</p>
    </aside>

    <nav class="orders-tabs" aria-label="订单类型">
      <button
        v-for="kind in kinds"
        :key="kind.id"
        type="button"
        :class="{ active: activeKind === kind.id }"
        @click="activeKind = kind.id"
      >
        {{ kind.label }}
        <small>{{ orders[kind.id]?.length || 0 }}</small>
        <i v-if="errors[kind.id]" aria-label="读取异常">!</i>
      </button>
    </nav>

    <section v-if="identityError" class="catalog-state catalog-state--error" role="alert">
      <h2>无法识别本机游客身份</h2>
      <p>{{ identityError }}</p>
      <p>为保护订单边界，身份无效时不会展示旧订单。</p>
    </section>

    <section v-else-if="loading[activeKind]" class="catalog-state" aria-live="polite">
      <span class="catalog-state__ripple" aria-hidden="true"></span>
      <h2>正在读取{{ current.label }}</h2>
    </section>

    <section v-else-if="errors[activeKind]" class="catalog-state catalog-state--error" role="alert">
      <h2>{{ current.label }}暂时无法读取</h2>
      <p>{{ errors[activeKind] }}</p>
      <button type="button" class="ghost" @click="$emit('retry', activeKind)">重试这一类</button>
    </section>

    <section v-else-if="currentOrders.length" class="orders-list">
      <article v-for="order in currentOrders" :key="order.id" class="order-card-v2">
        <header>
          <div>
            <p><span v-if="order.demoData === true">演示数据</span>订单 {{ shortId(order.id) }}</p>
            <h2>{{ titleOf(order) || '乌东订单' }}</h2>
          </div>
          <strong :data-status="order.status">{{ statusLabel(order.status) }}</strong>
        </header>
        <p class="order-card-v2__primary">{{ primaryLine(order) }}</p>
        <p v-if="order.note" class="order-card-v2__note">备注：{{ order.note }}</p>
        <footer>
          <small>提交于 {{ formatDateTime(order.createdAt) }}</small>
          <span>服务状态以运营方后续处理为准</span>
        </footer>
      </article>
    </section>

    <section v-else class="catalog-state">
      <p class="eyebrow">山路尚空</p>
      <h2>{{ current.empty }}</h2>
      <p>从公开目录选择服务并完成提交后，订单会出现在这里。</p>
      <a class="primary" :href="`#/resources/${activeKind === 'product' ? 'products' : activeKind === 'food' ? 'foods' : 'stays'}`">去看看{{ current.label }}</a>
    </section>
  </main>
</template>
