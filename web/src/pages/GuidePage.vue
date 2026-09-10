<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { anonymousWebSocketUrl, CONTRACT_VERSION, userWebSocketUrl } from '../services/api'
import { anonymousWebState, authState, ensureAnonymousWebSession } from '../services/authSession'
import { adoptDraft, adoptItinerary } from '../services/tourismApi'

const ASSISTANT_CONTRACT = 'assistant-card-v3-draft-r1'
const EVENT_CONTRACT = 'assistant-event-v3-draft-r1'
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const AUTH_FAILED_CODES = new Set(['AUTH_REQUIRED', 'AUTH_EXPIRED', 'SESSION_REVOKED', 'ANONYMOUS_REQUIRED', 'ANONYMOUS_EXPIRED', 'ANONYMOUS_REVOKED', 'ORIGIN_REJECTED', 'FORBIDDEN', 'AUTH_MODE_MISMATCH', 'CONTRACT_INCOMPATIBLE', 'RATE_LIMITED', 'AUTH_STATE_UNAVAILABLE'])
const stageLabels = { UNDERSTANDING: '理解需求', RETRIEVING: '检索乌东资料', VERIFYING_KNOWLEDGE: '核对资料版本', PLANNING: '匹配体验', PREPARING_RESULT: '整理建议', PERSISTING_RESULT: '保存本轮结果' }

const status = ref('idle')
const message = ref('')
const prompt = ref('周末两位，想在乌东体验苗族文化和茶旅，请安排两天一夜的慢游建议。')
const threadId = ref('')
const checkpointRevision = ref(null)
const runId = ref('')
const busy = ref(false)
const activeStage = ref('')
const card = ref(null)
const conversation = ref([])
const savingItinerary = ref(false)
let socket = null
let leaving = false
let terminalFrame = false

const userMode = computed(() => Boolean(authState.user.account))
const workspaceReady = computed(() => status.value === 'ready')
const references = computed(() => Array.isArray(card.value?.references) ? card.value.references : [])
const actions = computed(() => Array.isArray(card.value?.actions) ? card.value.actions : [])

const uuid = () => crypto.randomUUID().toLowerCase()

function exactKeys(value, keys) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const actual = Object.keys(value).sort()
  const expected = [...keys].sort()
  return actual.length === expected.length && actual.every((key, index) => key === expected[index])
}

function validTimestamp(value) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(value) && Number.isFinite(Date.parse(value))
}

function validAuthOk(frame, expectedMode) {
  return exactKeys(frame, ['type', 'authVersion', 'contractVersion', 'connectionId', 'mode', 'authExpiresAt']) &&
    frame.type === 'auth_ok' && frame.authVersion === 'wudong-ws-auth-v1' && frame.contractVersion === CONTRACT_VERSION &&
    frame.mode === expectedMode && UUID_PATTERN.test(frame.connectionId) && validTimestamp(frame.authExpiresAt)
}

function validAuthFailed(frame) {
  const rateLimited = frame?.code === 'RATE_LIMITED'
  const keys = ['type', 'authVersion', 'contractVersion', 'code', 'message', 'retryable', ...(rateLimited ? ['retryAfterSeconds'] : [])]
  return exactKeys(frame, keys) && frame.type === 'auth_failed' && frame.authVersion === 'wudong-ws-auth-v1' &&
    frame.contractVersion === CONTRACT_VERSION && AUTH_FAILED_CODES.has(frame.code) && typeof frame.message === 'string' &&
    (!rateLimited || Number.isInteger(frame.retryAfterSeconds) && frame.retryAfterSeconds > 0)
}

function validSessionState(frame) {
  return exactKeys(frame, ['assistantContractVersion', 'tourismContractVersion', 'stateVersion', 'type', 'threadId', 'logicalExpiresAt', 'checkpointRevision', 'run', 'canStartNewRun', 'lastCardStatus', 'lastCard', 'currentSolution', 'previousSolution']) &&
    frame.type === 'session_state' && frame.assistantContractVersion === ASSISTANT_CONTRACT && frame.tourismContractVersion === CONTRACT_VERSION &&
    frame.stateVersion === 'assistant-session-state-v3-draft-r1' && UUID_PATTERN.test(frame.threadId) && validTimestamp(frame.logicalExpiresAt) &&
    (frame.checkpointRevision === null || Number.isInteger(frame.checkpointRevision) && frame.checkpointRevision > 0) && typeof frame.canStartNewRun === 'boolean'
}

function emptyConditions() {
  return { travelDate: null, visitAt: null, checkInDate: null, checkOutDate: null, roomCount: null, peopleCount: null, preferences: [], selectedTargets: [], merchantItems: null }
}

function closeSocket() {
  if (socket && socket.readyState === WebSocket.OPEN) socket.close(1000, 'page_leave')
  socket = null
}

async function connect() {
  if (status.value === 'connecting' || workspaceReady.value) return
  const previous = socket
  socket = null
  previous?.close(1000, 'new_connection')
  terminalFrame = false
  status.value = 'connecting'
  message.value = userMode.value ? '正在连接账号向导…' : '正在准备免登录预览…'
  try {
    if (userMode.value) {
      if (!authState.user.accessToken) throw new Error('登录状态已失效，请先到“我的”恢复登录。')
    } else {
      await ensureAnonymousWebSession()
      if (!UUID_PATTERN.test(anonymousWebState.threadId)) throw new Error('匿名向导会话未能确认。')
    }
  } catch (reason) {
    status.value = 'error'
    message.value = reason?.message || '向导会话暂时无法准备。'
    return
  }
  const connection = new WebSocket(userMode.value ? userWebSocketUrl() : anonymousWebSocketUrl())
  socket = connection
  const requestedMode = userMode.value ? 'USER' : 'ANONYMOUS_WEB'
  const expectedMode = userMode.value ? 'USER' : 'ANONYMOUS'
  connection.addEventListener('open', () => {
    const frame = { type: 'auth', authVersion: 'wudong-ws-auth-v1', contractVersion: CONTRACT_VERSION, mode: requestedMode }
    if (userMode.value) frame.accessToken = authState.user.accessToken
    connection.send(JSON.stringify(frame))
  })
  connection.addEventListener('message', event => handleMessage(event.data, connection, expectedMode))
  connection.addEventListener('error', () => {
    if (connection !== socket || leaving || terminalFrame) return
    status.value = 'error'
    message.value = '乌东向导暂未准备好。'
  })
  connection.addEventListener('close', () => {
    if (connection !== socket) return
    socket = null
    if (leaving || terminalFrame) return
    status.value = 'error'
    busy.value = false
    message.value = '向导连接已关闭；可以手动重新连接。'
  })
}

function handleMessage(raw, connection, expectedMode) {
  let frame
  try { frame = JSON.parse(raw) } catch (_) { frame = null }
  if (validAuthOk(frame, expectedMode)) {
    message.value = '正在确认本次向导会话…'
    connection.send(JSON.stringify({ type: 'session_state_request', assistantContractVersion: ASSISTANT_CONTRACT, tourismContractVersion: CONTRACT_VERSION, threadId: null, runId: null, lastEventSequence: 0 }))
    return
  }
  if (validSessionState(frame)) {
    threadId.value = frame.threadId
    checkpointRevision.value = frame.checkpointRevision
    card.value = frame.lastCard
    runId.value = frame.run?.runId || ''
    status.value = 'ready'
    message.value = userMode.value ? '账号向导已就绪。' : '免登录向导已就绪。'
    return
  }
  if (validAuthFailed(frame)) {
    terminalFrame = true
    status.value = frame.retryable ? 'error' : 'blocked'
    busy.value = false
    message.value = `${frame.message}${frame.code === 'RATE_LIMITED' ? `（${frame.retryAfterSeconds} 秒后可重试）` : ''}`
    return
  }
  if (!frame || frame.assistantContractVersion !== ASSISTANT_CONTRACT || frame.tourismContractVersion !== CONTRACT_VERSION || frame.eventVersion !== EVENT_CONTRACT || frame.threadId !== threadId.value || !UUID_PATTERN.test(frame.runId || '')) {
    terminalFrame = true
    status.value = 'blocked'
    busy.value = false
    message.value = '收到无法确认的向导消息，已停止展示。'
    connection.close(4403, 'CONTRACT_INCOMPATIBLE')
    return
  }
  runId.value = frame.runId
  if (frame.type === 'run_started') {
    activeStage.value = 'UNDERSTANDING'
    busy.value = true
  } else if (frame.type === 'progress') {
    activeStage.value = frame.data?.status === 'COMPLETED' ? '' : frame.data?.stage || activeStage.value
  } else if (frame.type === 'card_ready' && frame.data?.card?.cardVersion === '3.0') {
    card.value = frame.data.card
    checkpointRevision.value = frame.data.checkpointRevision
    conversation.value.push({ role: 'assistant', content: frame.data.card.summary || frame.data.card.title })
  } else if (['completed', 'interrupted', 'failed', 'stopped'].includes(frame.type)) {
    checkpointRevision.value = frame.data?.checkpointRevision ?? checkpointRevision.value
    busy.value = false
    activeStage.value = ''
    message.value = frame.type === 'completed' ? '本轮建议已整理完毕。' : frame.type === 'stopped' ? '本轮生成已停止，已有内容会保留。' : '本轮生成中断，可调整需求后重试。'
  }
}

function sendPrompt(text = prompt.value) {
  const userText = String(text || '').trim()
  if (!userText || !workspaceReady.value || busy.value || !socket || socket.readyState !== WebSocket.OPEN) return
  card.value = null
  runId.value = ''
  activeStage.value = 'UNDERSTANDING'
  busy.value = true
  message.value = '乌东向导正在读取你的需求。'
  conversation.value.push({ role: 'user', content: userText })
  socket.send(JSON.stringify({ type: 'generate', assistantContractVersion: ASSISTANT_CONTRACT, tourismContractVersion: CONTRACT_VERSION, clientRequestId: uuid(), threadId: threadId.value, expectedCheckpointRevision: checkpointRevision.value, userText, pageAction: null, selectedTarget: null, baseResource: null, conditions: emptyConditions(), retrievalMode: 'KEYWORD_DEMO' }))
}

function cancelRun() {
  if (!busy.value || !runId.value || !socket || socket.readyState !== WebSocket.OPEN) return
  socket.send(JSON.stringify({ type: 'cancel_run', assistantContractVersion: ASSISTANT_CONTRACT, tourismContractVersion: CONTRACT_VERSION, cancelRequestId: uuid(), threadId: threadId.value, runId: runId.value }))
  message.value = '正在请求停止本轮生成…'
}

function askSuggestion(text) { prompt.value = text; sendPrompt(text) }

async function followAction(action) {
  if (action?.action === 'RETRY') return sendPrompt()
  if (action?.action === 'SAVE_ITINERARY') {
    if (!userMode.value) {
      message.value = '免登录预览不能保存个人行程；请先到“我的”登录后，再由账号向导重新生成并确认。'
      return
    }
    const candidate = card.value?.data?.candidate
    if (!candidate || action.candidateRef?.candidateId !== candidate.candidateRef?.candidateId) {
      message.value = '当前行程候选无法确认，请重新生成后再保存。'
      return
    }
    savingItinerary.value = true
    try {
      await adoptItinerary(candidate)
      message.value = '行程已保存到“我的”，你可以继续查看和调整。'
    } catch (reason) {
      message.value = reason?.message || '保存行程失败。'
    } finally {
      savingItinerary.value = false
    }
    return
  }
  if (['ADOPT_FOOD_DRAFT', 'ADOPT_STAY_DRAFT'].includes(action?.action)) {
    if (!userMode.value) {
      message.value = '免登录预览不能保存食宿草稿；请先到“我的”登录后重新生成并确认。'
      return
    }
    const candidate = card.value?.data?.candidate
    const expectedType = action.action === 'ADOPT_FOOD_DRAFT' ? 'FOOD_DRAFT' : 'STAY_DRAFT'
    if (!candidate || candidate.candidateType !== expectedType || action.candidateRef?.candidateId !== candidate.candidateRef?.candidateId) {
      message.value = '当前食宿候选无法确认，请重新生成后再保存。'
      return
    }
    savingItinerary.value = true
    try {
      await adoptDraft(expectedType === 'FOOD_DRAFT' ? 'FOOD' : 'STAY', candidate)
      message.value = '方案已保存为草稿，请到“我的”补齐联系人、核价后再正式提交。'
    } catch (reason) {
      message.value = reason?.message || '保存草稿失败。'
    } finally {
      savingItinerary.value = false
    }
    return
  }
  let destination = { PRODUCT: '/explore/product', FOOD: '/explore/food', STAY: '/explore/stay', PLACE: '/explore/travel', ROUTE_GUIDE: '/explore/travel' }[action?.targetType]
  if (action?.targetType === 'FOOD' && action?.targetId) destination += `?foodId=${encodeURIComponent(action.targetId)}`
  if (destination) location.hash = destination
  else message.value = userMode.value ? '请到“我的”查看并确认已生成的个人成果。' : '登录后可把这份方案保存到“我的”；当前仅为免登录预览。'
}

onBeforeUnmount(() => { leaving = true; closeSocket() })
</script>

<template>
  <main class="page v3-guide-page">
    <header class="page-intro"><p class="eyebrow">乌东向导</p><h1>先确认身份，再把心愿交给山茶</h1><p>先免登录看看建议；需要保存个人成果时，再登录并由你再次确认。</p></header>
    <section class="v3-guide-card"><span class="v3-guide-leaf">叶</span><div><h2>{{ userMode ? `${authState.user.account.nickname}，准备连接账号向导` : '免登录体验乌东向导' }}</h2><p>{{ message || (userMode ? '当前将连接你的账号体验；登录失效时不会改用免登录预览。' : '免登录预览不能读取账号资料；需要保存成果时再登录并再次确认。') }}</p><button class="primary" :disabled="status === 'connecting' || workspaceReady" @click="connect">{{ workspaceReady ? '向导已就绪' : status === 'connecting' ? '连接中…' : userMode ? '连接账号向导' : '开始免登录预览' }}</button><a v-if="!userMode" class="v3-text-link" href="#/my">已有账号？去“我的”登录</a></div></section>

    <section v-if="workspaceReady" class="v3-guide-workspace" aria-label="乌东向导工作台">
      <header><div><p class="eyebrow">一叶同行 · 多智能体编排</p><h2>把想去的地方，慢慢说给我听</h2></div><span class="v3-guide-state" :class="{ busy }">{{ busy ? `正在${stageLabels[activeStage] || '整理建议'}…` : '资料演示模式' }}</span></header>
      <div v-if="conversation.length" class="v3-guide-dialogue"><p v-for="(item, index) in conversation.slice(-4)" :key="`${index}-${item.content}`" :class="`is-${item.role}`"><b>{{ item.role === 'user' ? '你' : '乌东向导' }}</b><span>{{ item.content }}</span></p></div>
      <div class="v3-guide-suggestions"><button type="button" :disabled="busy" @click="askSuggestion('周末两位，安排两天一夜的苗族文化茶旅')">两天一夜茶旅</button><button type="button" :disabled="busy" @click="askSuggestion('推荐适合亲子的苗绣体验')">亲子苗绣</button><button type="button" :disabled="busy" @click="askSuggestion('介绍一下乌东的茶旅体验')">认识乌东茶旅</button><button type="button" :disabled="busy" @click="askSuggestion('推荐乌东苗家酸汤鱼和适合两人的用餐选择')">找苗家酸汤鱼</button><button type="button" :disabled="busy" @click="askSuggestion('推荐乌东适合两人小住的民宿房型')">找两人小住</button></div>
      <label class="v3-guide-input"><span>你的旅行需求</span><textarea v-model="prompt" maxlength="2000" placeholder="例如：周末两位，想体验苗族文化和茶旅" @keydown.ctrl.enter.prevent="sendPrompt()" /></label>
      <div class="v3-guide-actions"><button class="primary" type="button" :disabled="busy || !prompt.trim()" @click="sendPrompt()">{{ busy ? '正在编排…' : '为我安排乌东之行' }}</button><button v-if="busy" class="ghost" type="button" @click="cancelRun">停止本轮生成</button><small>建议基于当前已发布资料生成；演示价格、房态与路线均不代表实时信息。</small></div>
      <ol class="v3-guide-phases"><li v-for="(label, key) in stageLabels" :key="key" :class="{ active: activeStage === key }"><i></i>{{ label }}</li></ol>
      <article v-if="card" class="v3-guide-result"><header><p class="eyebrow">{{ card.type === 'itinerary' ? '行程建议' : card.type === 'service_recommendation' ? '在地推荐' : card.type === 'knowledge_answer' ? '资料解答' : card.type === 'clarifying_question' ? '继续补充' : card.type === 'pending_booking' ? '待确认方案' : '向导说明' }}</p><h2>{{ card.title }}</h2><p>{{ card.summary }}</p></header>
        <section v-if="card.type === 'itinerary'" class="v3-guide-days"><article v-for="day in card.data?.content?.days || []" :key="day.day"><b>第 {{ day.day }} 天 · {{ day.theme || '慢游乌东' }}</b><ol><li v-for="stop in day.stops || []" :key="stop.sequence"><span>{{ stop.sequence }}</span><div><strong>{{ stop.title }}</strong><small v-if="stop.note">{{ stop.note }}</small></div></li></ol></article><p>{{ card.data?.notice }}</p></section>
        <section v-else-if="card.type === 'service_recommendation'" class="v3-guide-recommendations"><article v-for="item in card.data?.items || []" :key="item.targetId"><div><h3>{{ item.targetName }}</h3><p>{{ item.summary }}</p><span v-for="tag in item.tags || []" :key="tag">{{ tag }}</span></div><b v-if="item.demoPrice">¥{{ item.demoPrice.amount }}<small>演示价</small></b></article><p>{{ card.data?.notice }}</p></section>
        <p v-else-if="card.type === 'knowledge_answer'" class="v3-guide-answer">{{ card.data?.answer }}</p><p v-else-if="card.type === 'clarifying_question'" class="v3-guide-answer">还需要：{{ (card.data?.requiredFields || []).join('、') }}。补充后可再次生成。</p><dl v-else-if="card.type === 'pending_booking'" class="v3-guide-proposal"><dt>本轮方案</dt><dd>{{ card.data?.candidate?.candidateType === 'FOOD_DRAFT' ? '同店多菜到店方案' : '住宿入住方案' }}</dd><dt>人数</dt><dd>{{ card.data?.proposal?.peopleCount || '待补充' }}</dd><dt>说明</dt><dd>{{ card.data?.notice }}</dd></dl>
        <div v-if="actions.length" class="v3-guide-card-actions"><button v-for="action in actions" :key="`${action.action}-${action.label}`" type="button" class="ghost" :disabled="savingItinerary" @click="followAction(action)">{{ savingItinerary && action.action === 'SAVE_ITINERARY' ? '正在保存…' : action.label }}</button></div><details v-if="references.length" class="v3-guide-references"><summary>参考了 {{ references.length }} 条乌东资料</summary><ul><li v-for="reference in references" :key="reference.detailPath">{{ reference.sourceTitle }}</li></ul></details>
      </article>
    </section>
    <section class="v3-guide-links"><a href="#/explore/product"><span>01</span><b>先看看商品</b><small>公开目录无需登录</small></a><a href="#/explore/food"><span>02</span><b>同店选餐</b><small>核价前需要登录</small></a><a href="#/explore/travel"><span>03</span><b>查看示意路线</b><small>不作为真实导航</small></a></section>
  </main>
</template>

<style scoped>
.v3-guide-workspace{display:grid;gap:1.1rem;margin-top:1.2rem;padding:clamp(1.2rem,3vw,2rem);border:1px solid rgba(71,91,75,.16);border-radius:1.35rem;background:linear-gradient(145deg,#fffef9,#edf4ef);box-shadow:0 1.1rem 2.5rem rgba(49,95,75,.08)}.v3-guide-workspace>header{display:flex;justify-content:space-between;gap:1rem;align-items:start}.v3-guide-workspace h2,.v3-guide-result h2,.v3-guide-result h3{margin:.2rem 0;color:#294c50}.v3-guide-state{padding:.45rem .7rem;border-radius:999px;color:#497269;background:#e0eee3;font-size:.78rem;white-space:nowrap}.v3-guide-state.busy{color:#315f4b;background:#d8e8ec}.v3-guide-dialogue{display:grid;gap:.55rem}.v3-guide-dialogue p{display:grid;grid-template-columns:4.4rem 1fr;gap:.65rem;margin:0;padding:.75rem .9rem;border-radius:.75rem;background:#fffdf8;color:#526762}.v3-guide-dialogue .is-user{background:#e6f0ea}.v3-guide-dialogue b{color:#c6734d}.v3-guide-suggestions,.v3-guide-card-actions{display:flex;flex-wrap:wrap;gap:.55rem}.v3-guide-suggestions button,.v3-guide-card-actions button{padding:.48rem .75rem;border:1px solid #d4ded3;border-radius:999px;color:#315f4b;background:#fffdf8;cursor:pointer}.v3-guide-input{display:grid;gap:.45rem;color:#315f4b;font-weight:650}.v3-guide-input textarea{min-height:7rem;resize:vertical;padding:.85rem;border:1px solid #d8ded5;border-radius:.8rem;color:#294c50;background:#fff;font:inherit}.v3-guide-actions{display:flex;flex-wrap:wrap;align-items:center;gap:.8rem}.v3-guide-actions small{color:#718077}.v3-guide-phases{display:flex;flex-wrap:wrap;gap:.55rem;margin:0;padding:0;list-style:none}.v3-guide-phases li{display:flex;align-items:center;gap:.35rem;padding:.38rem .6rem;border-radius:999px;color:#718077;background:#f5f3ea;font-size:.78rem}.v3-guide-phases i{width:.45rem;height:.45rem;border-radius:50%;background:#c8d6cc}.v3-guide-phases .active{color:#315f4b;background:#dcebea}.v3-guide-phases .active i{background:#397f7a;box-shadow:0 0 0 .22rem rgba(57,127,122,.14)}.v3-guide-result{display:grid;gap:1rem;padding:1.2rem;border:1px solid #d5e0d6;border-radius:1rem;background:#fffdf8}.v3-guide-result header>p,.v3-guide-result header>h2{margin:.25rem 0}.v3-guide-days{display:grid;gap:.7rem}.v3-guide-days article{padding:.85rem;border-left:3px solid #a9c8ad;background:#f6faf5}.v3-guide-days ol{display:grid;gap:.55rem;margin:.7rem 0 0;padding:0;list-style:none}.v3-guide-days li{display:flex;gap:.6rem}.v3-guide-days li>span{display:grid;flex:0 0 1.45rem;height:1.45rem;place-items:center;border-radius:50%;color:#fff;background:#397f7a;font-size:.76rem}.v3-guide-days small{display:block;margin-top:.2rem;color:#65776f}.v3-guide-recommendations{display:grid;gap:.7rem}.v3-guide-recommendations article{display:flex;justify-content:space-between;gap:1rem;padding:.9rem;border:1px solid #e1e5dc;border-radius:.75rem}.v3-guide-recommendations p{margin:.25rem 0 .5rem;color:#607168}.v3-guide-recommendations span{display:inline-block;margin:.15rem .25rem 0 0;padding:.18rem .42rem;border-radius:999px;color:#557b6d;background:#edf5ed;font-size:.72rem}.v3-guide-recommendations>b{color:#c6734d;white-space:nowrap}.v3-guide-recommendations>b small{display:block;color:#718077;font-weight:400}.v3-guide-answer{margin:0;line-height:1.9;white-space:pre-line}.v3-guide-proposal{display:grid;grid-template-columns:5.5rem 1fr;gap:.5rem;margin:0}.v3-guide-proposal dt{color:#718077}.v3-guide-proposal dd{margin:0}.v3-guide-references{color:#557b6d}.v3-guide-references ul{margin:.6rem 0 0;padding-left:1.1rem}@media(max-width:760px){.v3-guide-workspace>header,.v3-guide-actions{align-items:stretch;flex-direction:column}.v3-guide-state{align-self:start}.v3-guide-dialogue p{grid-template-columns:1fr;gap:.2rem}.v3-guide-recommendations article{display:block}.v3-guide-recommendations>b{display:block;margin-top:.6rem}}
</style>
