<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { authState } from '../services/authSession'
import { createOrder, newRequestKey, quoteOrder } from '../services/tourismApi'

const props = defineProps({ selection: Object })
const emit = defineEmits(['back', 'go-my'])
const form = reactive({ quantity: 1, visitAt: '', checkInDate: '', checkOutDate: '', roomCount: 1, peopleCount: 1, contactName: '', contactPhone: '', note: '' })
const quote = ref(null)
const previousTotal = ref('')
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const receipt = ref(null)
const needsReconfirm = ref(false)
const attempt = ref(null)
const today = new Date().toISOString().slice(0, 10)
const kind = computed(() => props.selection?.kind || '')

function localDateTime(value) {
  return value?.length === 16 ? `${value}:00` : value
}

function resetResult() {
  quote.value = null
  previousTotal.value = ''
  needsReconfirm.value = false
  receipt.value = null
  attempt.value = null
  error.value = ''
}

function quoteBody() {
  if (kind.value === 'product') return { productId: props.selection.resource.id, quantity: Number(form.quantity), pickupPoint: props.selection.resource.pickupPoint }
  if (kind.value === 'food') return { merchantId: props.selection.merchant.id, items: props.selection.items.map(item => ({ foodItemId: item.foodItemId, quantity: item.quantity })), visitAt: localDateTime(form.visitAt), peopleCount: Number(form.peopleCount) }
  if (kind.value === 'stay') return { roomTypeId: props.selection.resource.id, checkInDate: form.checkInDate, checkOutDate: form.checkOutDate, roomCount: Number(form.roomCount), peopleCount: Number(form.peopleCount) }
  throw new Error('请返回目录重新选择。')
}

function validateQuote() {
  if (!authState.user.account) return '请先到“我的”登录平台账号。'
  if (kind.value === 'product' && (!Number.isInteger(Number(form.quantity)) || Number(form.quantity) < 1)) return '商品数量必须是正整数。'
  if (kind.value === 'food') {
    if (!props.selection.items?.length || props.selection.items.some(item => !Number.isInteger(Number(item.quantity)) || Number(item.quantity) < 1)) return '每种餐食份数必须是正整数。'
    if (!form.visitAt || !Number.isInteger(Number(form.peopleCount)) || Number(form.peopleCount) < 1) return '请填写到店时间和正整数人数。'
  }
  if (kind.value === 'stay') {
    if (!form.checkInDate || !form.checkOutDate) return '请填写入住和离店日期。'
    if (form.checkOutDate <= form.checkInDate) return '离店日期必须晚于入住日期。'
    if (![form.roomCount, form.peopleCount].every(value => Number.isInteger(Number(value)) && Number(value) > 0)) return '房间数和入住人数必须是正整数。'
    const capacity = Number(props.selection.resource.maxGuestsPerRoom) * Number(form.roomCount)
    if (Number(form.peopleCount) > capacity) return `当前选择最多容纳 ${capacity} 人。`
  }
  return ''
}

async function getQuote() {
  if (attempt.value || submitting.value) return
  error.value = validateQuote()
  if (error.value) return
  loading.value = true
  try {
    quote.value = await quoteOrder(kind.value, quoteBody())
    previousTotal.value = quote.value.totalAmount
    needsReconfirm.value = false
    attempt.value = null
  } catch (reason) {
    error.value = reason?.message || '暂时无法核价。'
  } finally {
    loading.value = false
  }
}

function orderBody() {
  const body = quoteBody()
  return {
    ...body,
    contactName: form.contactName.trim(),
    contactPhone: form.contactPhone.trim(),
    note: form.note.trim() || null,
    sourceThreadId: null,
    expectedQuoteFingerprint: quote.value.quoteFingerprint
  }
}

async function submit() {
  if (!quote.value || needsReconfirm.value) return
  if (!form.contactName.trim() || !form.contactPhone.trim()) {
    error.value = '请填写联系人和联系电话。'
    return
  }
  if (!/^(?=.*\d)[0-9 +()\-]+$/.test(form.contactPhone.trim())) {
    error.value = '联系电话只能包含数字、空格、+、- 和括号，且至少有一位数字。'
    return
  }
  if (!attempt.value) attempt.value = { requestKey: newRequestKey(), kind: kind.value, body: orderBody() }
  const currentAttempt = attempt.value
  error.value = ''
  submitting.value = true
  try {
    receipt.value = await createOrder(currentAttempt.kind, currentAttempt.body, currentAttempt.requestKey)
  } catch (reason) {
    if (reason?.code === 'QUOTE_CHANGED' && reason.details?.currentQuote) {
      previousTotal.value = quote.value?.totalAmount || ''
      quote.value = reason.details.currentQuote
      needsReconfirm.value = true
      attempt.value = null
      error.value = '报价已变化，请核对新金额后再次确认。'
    } else {
      error.value = reason?.message || '订单提交失败；再次点击会沿用本次请求键安全重试。'
    }
  } finally {
    submitting.value = false
  }
}

function acceptChangedQuote() {
  needsReconfirm.value = false
  error.value = ''
}

watch(() => props.selection, resetResult)
</script>

<template>
  <main class="page v3-checkout">
    <header class="page-intro"><p class="eyebrow">先核价，再提交</p><h1>{{ selection?.title || '订单确认' }}</h1><p>核价只计算本机演示金额，不锁定库存、餐位或房态；正式提交后才会生成订单。</p></header>
    <section v-if="!selection" class="v3-state"><h2>当前没有待确认内容</h2><p>刷新页面不会重放上次提交，请返回五模块目录重新选择。</p><button class="primary" @click="emit('back')">返回逛乌东</button></section>
    <section v-else-if="receipt" class="v3-receipt"><p class="eyebrow">服务端已受理</p><h2>{{ receipt.resource?.productName || receipt.resource?.merchantName || receipt.resource?.roomTypeName || '乌东订单' }}</h2><p>订单编号：{{ receipt.resource?.id }}</p><p>状态：{{ receipt.resource?.status }}</p><p>金额：¥{{ receipt.resource?.totalAmount }} {{ receipt.resource?.currency }}</p><small>{{ receipt.replayed ? '本次显示为同一请求的安全重放结果。' : '订单与操作回执已一同提交。' }}</small><button class="primary" @click="emit('go-my')">到“我的”查看</button></section>
    <section v-else class="v3-checkout-layout">
      <form class="v3-order-form" @submit.prevent="getQuote" @input="resetResult">
        <fieldset :disabled="submitting || Boolean(attempt)">
        <h2>1. 选择条件</h2>
        <label v-if="kind === 'product'">商品数量<input v-model.number="form.quantity" type="number" min="1" step="1" required></label>
        <template v-else-if="kind === 'food'"><div class="v3-selection-lines"><p v-for="item in selection.items" :key="item.foodItemId"><b>{{ item.resource.name }}</b><span>{{ item.quantity }} 份</span></p></div><label>到店时间<input v-model="form.visitAt" type="datetime-local" required></label><label>到店人数<input v-model.number="form.peopleCount" type="number" min="1" step="1" required></label></template>
        <template v-else><p>房型：{{ selection.resource.name }} · 每间演示容量 {{ selection.resource.maxGuestsPerRoom }} 人</p><label>入住日期<input v-model="form.checkInDate" type="date" :min="today" required></label><label>离店日期<input v-model="form.checkOutDate" type="date" :min="form.checkInDate || today" required></label><label>房间数<input v-model.number="form.roomCount" type="number" min="1" step="1" required></label><label>入住人数<input v-model.number="form.peopleCount" type="number" min="1" step="1" required></label></template>
        <button class="primary" :disabled="loading">{{ loading ? '核价中…' : '获取本机模拟核价' }}</button>
        </fieldset>
      </form>

      <section class="v3-quote-card">
        <h2>2. 核对与提交</h2>
        <template v-if="quote"><p class="v3-price v3-price--large">¥{{ quote.totalAmount }} <small>{{ quote.currency }}</small></p><p v-if="needsReconfirm && previousTotal">此前显示：¥{{ previousTotal }}</p><ul><li v-for="line in quote.lines" :key="line.sequence">{{ line.resourceName }} × {{ line.quantity }}：¥{{ line.lineAmount }}</li></ul><p class="v3-notice">{{ quote.notice }}</p><button v-if="needsReconfirm" class="primary" @click="acceptChangedQuote">我已核对新报价</button><form v-else class="v3-contact-form" @submit.prevent="submit"><fieldset :disabled="submitting || Boolean(attempt)"><label>联系人<input v-model="form.contactName" maxlength="80" required></label><label>联系电话<input v-model="form.contactPhone" maxlength="32" required></label><label>备注<textarea v-model="form.note" maxlength="500"></textarea></label></fieldset><button class="primary" :disabled="submitting">{{ submitting ? '提交中…' : attempt ? '用同一请求键重试提交' : '确认并提交订单' }}</button></form></template>
        <p v-else class="v3-muted">先填写左侧条件并获取报价。</p>
        <p v-if="error" class="v3-error" role="alert">{{ error }}</p>
      </section>
    </section>
  </main>
</template>
