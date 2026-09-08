<script setup>
import { computed, reactive, watch } from 'vue'
import SafeImage from '../components/common/SafeImage.vue'

const props = defineProps({
  kind: { type: String, default: 'product' },
  target: Object,
  loading: Boolean,
  error: String,
  submitting: Boolean,
  actionError: String,
  order: Object
})
const emit = defineEmits(['submit', 'retry', 'go-orders'])

const form = reactive({
  quantity: 1,
  visitAt: '',
  checkInDate: '',
  peopleCount: 1,
  contactName: '',
  contactPhone: '',
  note: ''
})

const kindCopy = {
  product: { eyebrow: '乌东好物', title: '登记取货', submit: '提交取货订单', dateLabel: '' },
  food: { eyebrow: '在地风味', title: '预约到店', submit: '提交到店订单', dateLabel: '到店时间' },
  stay: { eyebrow: '山居住宿', title: '预约入住', submit: '提交住宿预约', dateLabel: '入住日期' }
}
const currentCopy = computed(() => kindCopy[props.kind] || kindCopy.product)
const backHref = computed(() => {
  if (props.kind === 'stay' && props.target?.stayPropertyId) return `#/stays/${props.target.stayPropertyId}`
  if (props.target?.id) return `#/${props.kind === 'food' ? 'foods' : 'products'}/${props.target.id}`
  return `#/resources/${props.kind === 'food' ? 'foods' : props.kind === 'stay' ? 'stays' : 'products'}`
})
const today = new Date().toLocaleDateString('sv-SE')
const currentMinute = new Date()
currentMinute.setSeconds(0, 0)
const minimumVisitAt = new Date(currentMinute.getTime() - currentMinute.getTimezoneOffset() * 60_000)
  .toISOString()
  .slice(0, 16)
const capacity = computed(() => Number(props.target?.maxGuests) || null)

watch([() => props.kind, () => props.target?.id], () => {
  Object.assign(form, {
    quantity: 1,
    visitAt: '',
    checkInDate: '',
    peopleCount: 1,
    contactName: '',
    contactPhone: '',
    note: ''
  })
})

watch(() => props.order, value => {
  if (!value) return
  form.contactName = ''
  form.contactPhone = ''
  form.note = ''
})

function submit() {
  emit('submit', { ...form })
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
  }[status] || '已提交'
}

function orderTitle(value) {
  if (props.kind === 'product') return value?.productName
  if (props.kind === 'food') return value?.foodItemName
  return value?.roomTypeName
}
</script>

<template>
  <main class="page order-confirm-page">
    <header class="order-confirm-page__head">
      <div>
        <p class="eyebrow">{{ currentCopy.eyebrow }} · 最后一步</p>
        <h1>{{ currentCopy.title }}</h1>
        <p>这是一笔正式演示订单。提交前请核对内容，页面不会另行保存联系信息。</p>
      </div>
      <a class="ghost" :href="backHref">返回详情</a>
    </header>

    <section v-if="loading" class="catalog-state" aria-live="polite">
      <span class="catalog-state__ripple" aria-hidden="true"></span>
      <h2>正在核对服务信息</h2>
    </section>

    <section v-else-if="error" class="catalog-state catalog-state--error" role="alert">
      <h2>暂时无法准备订单</h2>
      <p>{{ error }}</p>
      <button type="button" class="ghost" @click="emit('retry')">重新读取</button>
    </section>

    <section v-else-if="order" class="order-success" aria-live="polite">
      <span class="order-success__seal">成</span>
      <p class="eyebrow">服务端已受理</p>
      <h2>{{ orderTitle(order) || target?.name || '乌东订单' }}</h2>
      <p>订单状态：<strong>{{ statusLabel(order.status) }}</strong></p>
      <dl>
        <dt>订单编号</dt><dd>{{ order.id }}</dd>
        <template v-if="kind === 'product'"><dt>数量</dt><dd>{{ order.quantity }} 份</dd></template>
        <template v-else-if="kind === 'food'"><dt>到店时间</dt><dd>{{ order.visitAt }}</dd><dt>人数</dt><dd>{{ order.peopleCount }} 人</dd></template>
        <template v-else><dt>入住日期</dt><dd>{{ order.checkInDate }}</dd><dt>人数</dt><dd>{{ order.peopleCount }} 人</dd></template>
      </dl>
      <p v-if="order.demoData === true" class="demo-data-label">演示数据</p>
      <button type="button" class="primary" @click="emit('go-orders')">查看我的订单</button>
    </section>

    <section v-else-if="target" class="order-confirm-layout">
      <aside class="order-target-card">
        <SafeImage :src="target.imageUrl" :alt="`${target.name}的服务图片`" :label="target.name" loading="eager" />
        <div>
          <span v-if="target.demoData === true" class="demo-data-label">演示数据</span>
          <h2>{{ target.name }}</h2>
          <p v-if="kind === 'product'">取货点：{{ target.pickupPoint || '待确认' }}</p>
          <p v-else-if="kind === 'food'">接待时段：{{ target.visitTimeText || '待确认' }}</p>
          <p v-else>最多入住 {{ target.maxGuests }} 人</p>
          <strong v-if="target.price !== null && target.price !== undefined && Number.isFinite(Number(target.price))">¥{{ target.price }}</strong>
        </div>
      </aside>

      <form class="order-form-v2" @submit.prevent="submit">
        <fieldset>
          <legend>服务信息</legend>
          <label v-if="kind === 'product'">
            <span>购买数量</span>
            <input v-model.number="form.quantity" type="number" min="1" step="1" required :disabled="submitting">
          </label>
          <template v-else-if="kind === 'food'">
            <label>
              <span>{{ currentCopy.dateLabel }}</span>
              <input v-model="form.visitAt" type="datetime-local" :min="minimumVisitAt" step="60" required :disabled="submitting">
            </label>
            <label>
              <span>到店人数</span>
              <input v-model.number="form.peopleCount" type="number" min="1" step="1" required :disabled="submitting">
            </label>
          </template>
          <template v-else>
            <label>
              <span>{{ currentCopy.dateLabel }}</span>
              <input v-model="form.checkInDate" type="date" :min="today" required :disabled="submitting">
            </label>
            <label>
              <span>入住人数</span>
              <input v-model.number="form.peopleCount" type="number" min="1" :max="capacity || undefined" step="1" required :disabled="submitting">
              <small v-if="capacity">该房型最多 {{ capacity }} 人</small>
            </label>
          </template>
        </fieldset>

        <fieldset>
          <legend>联系信息</legend>
          <label>
            <span>联系人</span>
            <input v-model="form.contactName" type="text" maxlength="80" autocomplete="name" required :disabled="submitting">
          </label>
          <label>
            <span>联系电话</span>
            <input v-model="form.contactPhone" type="tel" maxlength="32" autocomplete="tel" required :disabled="submitting">
          </label>
          <label class="order-form-v2__wide">
            <span>备注 <small>选填，最多 500 字</small></span>
            <textarea v-model="form.note" maxlength="500" rows="4" :disabled="submitting" placeholder="如有饮食、抵达或取货说明，可写在这里。"></textarea>
          </label>
        </fieldset>

        <p v-if="actionError" class="form-error" role="alert">{{ actionError }}</p>
        <footer>
          <p>点击提交即将订单发送给本机服务端；成功后才能在“我的订单”中看到。</p>
          <button type="submit" class="primary" :disabled="submitting">
            {{ submitting ? '正在提交…' : currentCopy.submit }}
          </button>
        </footer>
      </form>
    </section>

    <section v-else class="catalog-state">
      <h2>没有可提交的服务</h2>
      <p>请返回公开目录重新选择。</p>
      <a class="ghost" href="#/resources">返回服务目录</a>
    </section>
  </main>
</template>
