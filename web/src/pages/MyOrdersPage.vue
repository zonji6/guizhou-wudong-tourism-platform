<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { authState, login, logout, prepareAuth, refresh, registerUser } from '../services/authSession'
import { loadMyWorkspace, newRequestKey, quoteOrder, reconcileDraft, saveDraft, submitDraft } from '../services/tourismApi'

const mode = ref('login')
const form = reactive({ username: '', password: '', nickname: '' })
const workspace = ref(null)
const busy = ref(false)
const message = ref('')
const active = ref('orders')
const draftEditor = ref(null)
const draftSave = reactive({ status: '', saving: false, paused: false, unknown: false, lastErrorCode: '', editRevision: 0, savedRevision: 0, queued: false, pending: null })
const draftSubmit = reactive({ quote: null, oldQuote: null, status: '', submitting: false, attempt: null })
let draftTimer
const orders = computed(() => workspace.value ? [...workspace.value.productOrders, ...workspace.value.foodOrders, ...workspace.value.stayBookings] : [])

function errorText(reason, fallback) {
  return reason?.message || fallback
}

async function authenticate() {
  busy.value = true
  message.value = ''
  try {
    if (mode.value === 'register') {
      await registerUser(form)
      message.value = '账号已创建，请使用同一用户名和密码登录 Web 或小程序。'
      mode.value = 'login'
      return
    }
    await login('user', form)
    form.password = ''
    await reload()
  } catch (reason) {
    message.value = errorText(reason, '登录操作失败。')
  } finally {
    busy.value = false
  }
}

async function restoreSession() {
  busy.value = true
  message.value = ''
  try {
    await refresh('user')
    await reload()
  } catch (reason) {
    message.value = errorText(reason, '当前浏览器没有可恢复的登录。')
  } finally {
    busy.value = false
  }
}

async function reload() {
  if (!authState.user.account) return
  busy.value = true
  message.value = ''
  try {
    workspace.value = await loadMyWorkspace()
  } catch (reason) {
    message.value = errorText(reason, '“我的”暂时无法读取。')
  } finally {
    busy.value = false
  }
}

async function signOut() {
  busy.value = true
  message.value = ''
  try {
    await logout('user')
    workspace.value = null
    message.value = '服务端已确认退出当前浏览器登录。'
  } catch (reason) {
    message.value = `${errorText(reason, '退出结果未知。')} 页面内存不会冒充服务端已撤销。`
  } finally {
    busy.value = false
  }
}

function orderTitle(order) {
  return order.productName || order.merchantName || `${order.stayPropertyName || ''} ${order.roomTypeName || ''}`.trim() || '乌东订单'
}

function orderSummary(order) {
  if (order.orderType === 'PRODUCT') return `${order.quantity} 份 · ${order.pickupPoint}`
  if (order.orderType === 'FOOD') return `${order.items?.map(item => `${item.resourceName || item.foodItemName}×${item.quantity}`).join('、')} · ${order.peopleCount} 人`
  return `${order.checkInDate} 至 ${order.checkOutDate} · ${order.nights} 晚 · ${order.roomCount} 间 · ${order.peopleCount} 人`
}

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}

function openDraft(draft) {
  if (draftEditor.value) {
    draftSave.status = '请先确认当前草稿的保存状态并关闭编辑器。'
    return
  }
  clearTimeout(draftTimer)
  draftEditor.value = { id: draft.id, draftType: draft.draftType, version: draft.version, content: clone(draft.content) }
  Object.assign(draftSave, { status: '尚未修改', saving: false, paused: false, unknown: false, lastErrorCode: '', editRevision: 0, savedRevision: 0, queued: false, pending: null })
  Object.assign(draftSubmit, { quote: null, oldQuote: null, status: '', submitting: false, attempt: null })
}

function finishCloseDraft() {
  clearTimeout(draftTimer)
  draftEditor.value = null
}

function closeDraft() {
  if (draftSave.saving) {
    draftSave.status = '保存请求仍在处理，暂不能关闭编辑器；关闭页面也不会撤销已发请求。'
    return
  }
  if (draftSubmit.submitting || draftSubmit.attempt) {
    draftSubmit.status = '提交结果尚待核对；请先用同一请求键重试，不能丢弃本页提交上下文。'
    return
  }
  if ((draftSave.editRevision !== draftSave.savedRevision || draftSave.paused) && !window.confirm('仍有未确认保存的内容。关闭后页面内容不会保存到本地，仍要关闭吗？')) return
  finishCloseDraft()
}

function nullableText(value) {
  const text = typeof value === 'string' ? value.trim() : ''
  return text || null
}

function nullableInteger(value) {
  return value === '' || value === null || value === undefined ? null : Number(value)
}

function normalizedDraftContent() {
  const editor = draftEditor.value
  const content = editor.content
  if (editor.draftType === 'FOOD') {
    return {
      merchantId: content.merchantId,
      items: content.items.map(item => ({ foodItemId: item.foodItemId, quantity: Number(item.quantity) })),
      visitAt: nullableText(content.visitAt)?.replace(/^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})$/, '$1:00') || null,
      peopleCount: nullableInteger(content.peopleCount),
      contactName: nullableText(content.contactName),
      contactPhone: nullableText(content.contactPhone),
      note: nullableText(content.note)
    }
  }
  return {
    roomTypeId: content.roomTypeId,
    checkInDate: nullableText(content.checkInDate),
    checkOutDate: nullableText(content.checkOutDate),
    roomCount: nullableInteger(content.roomCount),
    peopleCount: nullableInteger(content.peopleCount),
    contactName: nullableText(content.contactName),
    contactPhone: nullableText(content.contactPhone),
    note: nullableText(content.note)
  }
}

function scheduleDraftSave() {
  if (!draftEditor.value) return
  draftSave.editRevision += 1
  draftSave.status = draftSave.paused ? '保存已暂停，页面内容仍保留。' : '等待停止编辑…'
  Object.assign(draftSubmit, { quote: null, oldQuote: null, status: '内容已修改，请等待最新版本保存后重新核价。', attempt: null })
  clearTimeout(draftTimer)
  draftSave.queued = false
  if (!draftSave.paused) draftTimer = setTimeout(() => { draftTimer = undefined; saveDraftNow() }, 800)
}

async function saveDraftNow() {
  if (!draftEditor.value || draftSave.paused) return
  if (draftSave.savedRevision === draftSave.editRevision) return
  if (draftSave.saving) { draftSave.queued = true; return }
  const editor = draftEditor.value
  const content = normalizedDraftContent()
  const revision = draftSave.editRevision
  const expectedVersion = editor.version
  const requestKey = newRequestKey()
  draftSave.saving = true
  draftSave.status = '正在保存…'
  draftSave.pending = { requestKey, revision, original: { targetId: editor.id, expectedVersion, content } }
  try {
    const receipt = await saveDraft(editor.draftType, editor.id, expectedVersion, content, requestKey)
    const committedVersion = receipt.committedVersion
    draftSave.savedRevision = revision
    draftSave.pending = null
    if (!Number.isInteger(committedVersion) || receipt.resource?.version !== committedVersion) {
      clearTimeout(draftTimer)
      draftSave.queued = false
      draftSave.paused = true
      draftSave.unknown = false
      draftSave.lastErrorCode = 'VERSION_CONFLICT'
      if (Number.isInteger(committedVersion)) editor.version = committedVersion
      draftSave.status = '本次保存已提交，但服务端已有更高版本；已暂停，不能用旧页面内容覆盖跨端修改。'
      return
    }
    editor.version = committedVersion
    draftSave.lastErrorCode = ''
    draftSave.status = revision === draftSave.editRevision ? `已保存版本 ${editor.version}` : '还有新修改等待保存…'
  } catch (reason) {
    draftSave.paused = true
    draftSave.unknown = reason?.status === 0 || ['WRITE_RESULT_UNAVAILABLE', 'INVALID_RESPONSE', 'CONTRACT_INCOMPATIBLE'].includes(reason?.code)
    draftSave.lastErrorCode = reason?.code || ''
    draftSave.status = draftSave.unknown ? '保存结果未知，已暂停；请主动核对后再继续。' : `${reason?.message || '保存失败。'} 已暂停，未覆盖页面内容。`
  } finally {
    draftSave.saving = false
    if (draftSave.queued && !draftSave.paused) {
      draftSave.queued = false
      draftTimer = setTimeout(saveDraftNow, 0)
    }
  }
}

async function continueUnknownSave() {
  if (!draftEditor.value || !draftSave.pending || !draftSave.unknown) return
  draftSave.saving = true
  draftSave.queued = false
  draftSave.status = '正在核对原保存结果…'
  try {
    const pendingRevision = draftSave.pending.revision
    const result = await reconcileDraft(draftEditor.value.draftType, draftSave.pending.requestKey, draftSave.pending.original)
    if (result.outcome === 'SUCCEEDED' && result.receipt && result.receipt.resource?.version === result.receipt.committedVersion) {
      draftEditor.value.version = result.receipt.committedVersion
      draftSave.savedRevision = pendingRevision
      draftSave.paused = false
      draftSave.unknown = false
      draftSave.pending = null
      draftSave.status = '原保存已确认成功。'
      if (draftSave.editRevision !== pendingRevision) draftTimer = setTimeout(saveDraftNow, 0)
    } else if (result.outcome === 'NOT_APPLIED' && result.current?.version === draftSave.pending.original.expectedVersion && result.current?.editable === true) {
      draftSave.paused = false
      draftSave.unknown = false
      draftSave.pending = null
      draftSave.status = '原保存已确认未执行，正在用新请求键继续。'
      draftTimer = setTimeout(saveDraftNow, 0)
    } else {
      draftSave.status = result.outcome === 'SUCCEEDED' ? '原保存已成功，但服务端已有更高版本；请保留页面内容并手动核对跨端修改。' : '仍不能安全继续，请保留页面内容并手动刷新核对。'
    }
  } catch (reason) {
    draftSave.status = reason?.message || '核对结果仍未知，继续保持暂停。'
  } finally { draftSave.saving = false }
}

function continueDraftSave() {
  if (draftSave.unknown) { continueUnknownSave(); return }
  if (['VERSION_CONFLICT', 'DRAFT_ALREADY_SUBMITTED'].includes(draftSave.lastErrorCode)) {
    draftSave.status = '服务端版本或状态已变化，不能盲目重试；请保留内容并关闭后手动读取最新版。'
    return
  }
  draftSave.paused = false
  draftSave.pending = null
  draftSave.queued = false
  draftSave.status = '正在用新请求键重试当前内容…'
  draftTimer = setTimeout(saveDraftNow, 0)
}

function draftQuoteSelection() {
  const content = normalizedDraftContent()
  if (draftEditor.value.draftType === 'FOOD') {
    return { merchantId: content.merchantId, items: content.items, visitAt: content.visitAt, peopleCount: content.peopleCount }
  }
  return { roomTypeId: content.roomTypeId, checkInDate: content.checkInDate, checkOutDate: content.checkOutDate, roomCount: content.roomCount, peopleCount: content.peopleCount }
}

async function quoteCurrentDraft() {
  if (!draftEditor.value) return
  if (draftSubmit.attempt) {
    draftSubmit.status = '提交结果尚待核对；请先用同一请求键重试，不能重新核价。'
    return
  }
  if (draftSave.saving || draftSave.queued || draftSave.paused || draftSave.savedRevision !== draftSave.editRevision) {
    draftSubmit.status = '只有当前页面最新改动已由服务端确认保存后，才能核价并提交。'
    return
  }
  draftSubmit.submitting = true
  draftSubmit.status = '正在按已保存草稿核价…'
  try {
    const kind = draftEditor.value.draftType.toLowerCase()
    const quote = await quoteOrder(kind, draftQuoteSelection())
    Object.assign(draftSubmit, { quote, oldQuote: null, status: '核价完成；请核对模拟金额后再次确认提交。', attempt: null })
  } catch (reason) {
    draftSubmit.status = reason?.message || '草稿核价失败。'
  } finally { draftSubmit.submitting = false }
}

async function submitCurrentDraft() {
  if (!draftEditor.value || !draftSubmit.quote || draftSubmit.submitting) return
  if (draftSave.saving || draftSave.queued || draftSave.paused || draftSave.savedRevision !== draftSave.editRevision) {
    draftSubmit.status = '最新改动尚未确认保存，不能提交。'
    return
  }
  if (!draftSubmit.attempt) {
    draftSubmit.attempt = {
      requestKey: newRequestKey(),
      expectedVersion: draftEditor.value.version,
      expectedQuoteFingerprint: draftSubmit.quote.quoteFingerprint
    }
  }
  const attempt = { ...draftSubmit.attempt }
  draftSubmit.submitting = true
  draftSubmit.status = '正在提交已保存草稿…'
  try {
    const receipt = await submitDraft(draftEditor.value.draftType, draftEditor.value.id, attempt.expectedVersion, attempt.expectedQuoteFingerprint, attempt.requestKey)
    const orderId = receipt.resource?.id || receipt.resourceId
    const successMessage = `草稿已由服务端确认提交${orderId ? `，正式订单 ${orderId}` : ''}。`
    finishCloseDraft()
    await reload()
    message.value = successMessage
  } catch (reason) {
    if (reason?.code === 'QUOTE_CHANGED' && reason.details?.currentQuote) {
      draftSubmit.oldQuote = draftSubmit.quote
      draftSubmit.quote = reason.details.currentQuote
      draftSubmit.attempt = null
      draftSubmit.status = '报价已变化：旧请求键已终结。请比较新旧金额，再明确确认用新请求键提交。'
    } else {
      draftSubmit.status = `${reason?.message || '提交结果暂未确认。'} 重试将沿用同一请求键与原请求内容。`
    }
  } finally { draftSubmit.submitting = false }
}

onMounted(() => prepareAuth('user').catch(reason => { message.value = errorText(reason, '登录安全通道暂不可用。') }))
onBeforeUnmount(() => clearTimeout(draftTimer))
</script>

<template>
  <main class="page v3-my-page">
    <header class="page-intro"><p class="eyebrow">平台账号 · 跨端同一归属</p><h1>我的</h1><p>Web 与原生小程序使用同一个平台账号读取已保存行程、草稿与三类订单；联系人不会出现在本人订单投影中。</p></header>

    <section v-if="!authState.user.account" class="v3-auth-layout">
      <form class="v3-auth-card" @submit.prevent="authenticate"><div class="v3-auth-switch"><button type="button" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button><button type="button" :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button></div><label>用户名<input v-model="form.username" pattern="[A-Za-z0-9_]{3,32}" autocomplete="username" required></label><label v-if="mode === 'register'">昵称（可选）<input v-model="form.nickname" maxlength="40"></label><label>密码<input v-model="form.password" type="password" minlength="8" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required></label><button class="primary" :disabled="busy || !authState.user.ready">{{ !authState.user.ready ? '正在准备安全通道…' : busy ? '处理中…' : mode === 'login' ? '登录我的乌东' : '创建 USER 账号' }}</button><p>访问令牌只保存在当前页面内存；密码不会保存到浏览器。</p></form>
      <aside class="v3-auth-note"><h2>已经登录过这个浏览器？</h2><p>刷新令牌只存在 HttpOnly Cookie 中，可以尝试安全恢复当前会话。</p><button class="ghost" :disabled="busy || !authState.user.ready" @click="restoreSession">恢复登录</button><p>后台账号在独立入口登录，不与游客账号互相兜底。</p></aside>
    </section>

    <template v-else>
      <section class="v3-account-bar"><div><p class="eyebrow">已登录</p><h2>{{ authState.user.account.nickname }}</h2><small>@{{ authState.user.account.username }} · USER</small></div><div><button class="ghost" :disabled="busy" @click="restoreSession">更新登录</button><button class="ghost" :disabled="busy" @click="reload">刷新内容</button><button class="ghost" :disabled="busy" @click="signOut">退出</button></div></section>
      <nav class="v3-module-tabs"><button :class="{ active: active === 'orders' }" @click="active = 'orders'">三类订单</button><button :class="{ active: active === 'drafts' }" @click="active = 'drafts'">食宿草稿</button><button :class="{ active: active === 'itineraries' }" @click="active = 'itineraries'">已保存行程</button><button :class="{ active: active === 'legacy' }" @click="active = 'legacy'">旧记录</button></nav>
      <section v-if="!workspace" class="v3-state"><p>{{ busy ? '正在读取同账号数据…' : '尚未读取数据。' }}</p><button v-if="!busy" class="primary" @click="reload">读取我的内容</button></section>
      <section v-else-if="active === 'orders'" class="v3-list"><article v-for="order in orders" :key="order.id"><header><span>{{ order.orderType }}</span><b>{{ order.status }}</b></header><h2>{{ orderTitle(order) }}</h2><p>{{ orderSummary(order) }}</p><footer>¥{{ order.totalAmount }} {{ order.currency }} · {{ order.createdAt }}</footer></article><p v-if="!orders.length" class="v3-state">还没有正式订单。</p></section>
      <section v-else-if="active === 'drafts'" class="v3-list">
        <article v-for="draft in [...workspace.foodDrafts, ...workspace.stayDrafts]" :key="draft.id"><header><span>{{ draft.draftType }} 草稿</span><b>{{ draft.state }}</b></header><h2>版本 {{ draft.version }}</h2><p v-if="draft.draftType === 'FOOD'">{{ draft.content?.items?.length || 0 }} 种餐食 · {{ draft.content?.visitAt || '时间待补' }}</p><p v-else>{{ draft.content?.checkInDate || '入住待补' }} 至 {{ draft.content?.checkOutDate || '离店待补' }} · {{ draft.content?.roomCount || '房数待补' }}</p><footer v-if="draft.state === 'SUBMITTED'">已关联 {{ draft.linkedOrder?.orderType }} 订单 {{ draft.linkedOrder?.orderId }}</footer><template v-else><footer>停止编辑约 800ms 后串行保存；失败或未知会暂停且保留页面内容。</footer><button class="ghost" :disabled="Boolean(draftEditor)" @click="openDraft(draft)">继续编辑</button></template></article>
        <p v-if="!workspace.foodDrafts.length && !workspace.stayDrafts.length" class="v3-state">还没有食宿草稿。</p>
        <form v-if="draftEditor" class="v3-draft-editor" @submit.prevent @input="scheduleDraftSave">
          <header><div><p class="eyebrow">{{ draftEditor.draftType }} 草稿</p><h2>编辑版本 {{ draftEditor.version }}</h2></div><button type="button" class="ghost" @click="closeDraft">关闭</button></header>
          <template v-if="draftEditor.draftType === 'FOOD'"><p>店铺与 {{ draftEditor.content.items.length }} 个餐食项保持原草稿选择，不在编辑时跨店换绑。</p><label v-for="item in draftEditor.content.items" :key="item.foodItemId">餐食 {{ item.foodItemId }} 份数<input v-model.number="item.quantity" type="number" min="1" :disabled="draftSubmit.submitting || Boolean(draftSubmit.attempt)"></label><label>到店时间<input v-model="draftEditor.content.visitAt" type="datetime-local" :disabled="draftSubmit.submitting || Boolean(draftSubmit.attempt)"></label><label>人数<input v-model.number="draftEditor.content.peopleCount" type="number" min="1" :disabled="draftSubmit.submitting || Boolean(draftSubmit.attempt)"></label></template>
          <template v-else><p>房型保持原草稿选择：{{ draftEditor.content.roomTypeId }}</p><label>入住日期<input v-model="draftEditor.content.checkInDate" type="date" :disabled="draftSubmit.submitting || Boolean(draftSubmit.attempt)"></label><label>离店日期<input v-model="draftEditor.content.checkOutDate" type="date" :disabled="draftSubmit.submitting || Boolean(draftSubmit.attempt)"></label><label>房间数<input v-model.number="draftEditor.content.roomCount" type="number" min="1" :disabled="draftSubmit.submitting || Boolean(draftSubmit.attempt)"></label><label>人数<input v-model.number="draftEditor.content.peopleCount" type="number" min="1" :disabled="draftSubmit.submitting || Boolean(draftSubmit.attempt)"></label></template>
          <label>联系人<input v-model="draftEditor.content.contactName" maxlength="80" :disabled="draftSubmit.submitting || Boolean(draftSubmit.attempt)"></label><label>联系电话<input v-model="draftEditor.content.contactPhone" maxlength="32" :disabled="draftSubmit.submitting || Boolean(draftSubmit.attempt)"></label><label>备注<textarea v-model="draftEditor.content.note" maxlength="500" :disabled="draftSubmit.submitting || Boolean(draftSubmit.attempt)"></textarea></label>
          <p :class="{ 'v3-error': draftSave.paused }">{{ draftSave.status }}</p><button v-if="draftSave.paused" type="button" class="primary" :disabled="draftSave.saving" @click="continueDraftSave">{{ draftSave.unknown ? '继续保存（先核对原结果）' : '重新尝试保存' }}</button>
          <div class="v3-draft-submit" @input.stop><button type="button" class="ghost" :disabled="draftSubmit.submitting || Boolean(draftSubmit.attempt) || draftSave.saving || draftSave.paused || draftSave.savedRevision !== draftSave.editRevision" @click="quoteCurrentDraft">按已保存版本核价</button><template v-if="draftSubmit.quote"><p v-if="draftSubmit.oldQuote">旧报价：¥{{ draftSubmit.oldQuote.totalAmount }} {{ draftSubmit.oldQuote.currency }}</p><p>当前报价：¥{{ draftSubmit.quote.totalAmount }} {{ draftSubmit.quote.currency }} · {{ draftSubmit.quote.notice }}</p><button type="button" class="primary" :disabled="draftSubmit.submitting" @click="submitCurrentDraft">{{ draftSubmit.submitting ? '提交中…' : draftSubmit.attempt ? '用同一请求键重试提交' : draftSubmit.oldQuote ? '确认新报价并提交' : '确认该报价并提交' }}</button></template><p v-if="draftSubmit.status">{{ draftSubmit.status }}</p></div>
        </form>
      </section>
      <section v-else-if="active === 'itineraries'" class="v3-list"><article v-for="trip in workspace.itineraries" :key="trip.id"><header><span>已保存行程</span><b>v{{ trip.version }}</b></header><h2>{{ trip.content?.title }}</h2><p>{{ trip.content?.days?.length || 0 }} 天 · {{ trip.content?.travelDate || '日期待定' }} · {{ trip.content?.peopleCount || '人数待定' }}</p><footer>{{ trip.updatedAt }}</footer></article><p v-if="!workspace.itineraries.length" class="v3-state">还没有已保存行程。</p></section>
      <section v-else class="v3-list"><article v-for="record in workspace.legacyRecords" :key="record.id"><header><span>合法旧记录</span><b>只读</b></header><h2>{{ record.title || record.name || record.id }}</h2><p>{{ record.message || record.summary || '该记录保留原归属，不会自动映射 visitorId。' }}</p></article><p v-if="!workspace.legacyRecords.length" class="v3-state">没有可归属到当前账号的旧记录。</p></section>
    </template>
    <p v-if="message" class="v3-error" role="status">{{ message }}</p>
  </main>
</template>
