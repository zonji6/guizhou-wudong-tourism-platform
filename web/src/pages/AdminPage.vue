<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { authState, login, logout, prepareAuth, refresh } from '../services/authSession'
import { loadAdminKnowledge, loadAdminOperations, publishKnowledge, updateAdminOrderStatus } from '../services/tourismApi'

const form = reactive({ username: '', password: '' })
const data = reactive({ sources: [], documents: [], operations: {} })
const tab = ref('dashboard')
const busy = ref(false)
const message = ref('')
const publishingId = ref('')
const statusUpdating = ref('')

const count = key => Array.isArray(data.operations[key]) ? data.operations[key].length : 0
const allOrders = computed(() => ['products', 'foods', 'stays'].flatMap(kind => (data.operations[`orders:${kind}`] || []).map(order => ({ ...order, kind }))))
const dashboardCards = computed(() => [
  { label: '公开商品', value: count('catalog:products'), note: '含演示标识的目录记录' },
  { label: '餐食菜品', value: count('catalog:foods'), note: `${count('catalog:merchants')} 家店铺可维护` },
  { label: '住宿房型', value: count('catalog:room-types'), note: `${count('catalog:stays')} 处住宿主体` },
  { label: '待处理订单', value: allOrders.value.filter(item => /^PENDING/.test(item.status || '')).length, note: `全部订单 ${allOrders.value.length} 笔` },
  { label: '已发布知识', value: data.documents.filter(item => item.visibility === 'PUBLISHED').length, note: `来源主档 ${data.sources.length} 条` },
  { label: '社区内容', value: count('posts'), note: '游客端公开可见内容' }
])
const catalogGroups = computed(() => [
  { label: '商品', key: 'catalog:products', hint: '伴手礼与茶旅商品' }, { label: '餐饮店铺', key: 'catalog:merchants', hint: '同店菜单归属' },
  { label: '菜品饮品', key: 'catalog:foods', hint: '可供店内组合' }, { label: '住宿', key: 'catalog:stays', hint: '住宿主体' },
  { label: '房型', key: 'catalog:room-types', hint: '入住方案' }, { label: '地点', key: 'catalog:places', hint: '仅水彩示意地图' }
])

function displayName(item) { return item.name || item.title || item.merchantName || item.resourceName || '未命名记录' }
function orderAction(order) {
  const options = { products: { PENDING_PICKUP: ['PICKED_UP', '已取货'] }, foods: { PENDING_VISIT: ['COMPLETED', '已到店完成'] }, stays: { PENDING_CONFIRMATION: ['CONFIRMED', '确认入住'], CONFIRMED: ['COMPLETED', '完成入住'] } }
  return options[order.kind]?.[order.status] || null
}
async function signIn() { busy.value = true; message.value = ''; try { await login('admin', form); form.password = ''; await reload() } catch (reason) { message.value = reason?.message || '后台登录失败。' } finally { busy.value = false } }
async function restoreSession() { busy.value = true; message.value = ''; try { await refresh('admin'); await reload() } catch (reason) { message.value = reason?.message || '没有可恢复的后台登录。' } finally { busy.value = false } }
async function reload() {
  if (!authState.admin.account) return
  busy.value = true; message.value = ''
  try { const [knowledge, operations] = await Promise.all([loadAdminKnowledge(), loadAdminOperations()]); data.sources = knowledge.sources; data.documents = knowledge.documents; data.operations = operations } catch (reason) { message.value = reason?.message || '运营数据暂时无法读取。' } finally { busy.value = false }
}
async function signOut() { busy.value = true; try { await logout('admin'); data.sources = []; data.documents = []; data.operations = {}; message.value = '后台会话已由服务端撤销。' } catch (reason) { message.value = `${reason?.message || '退出结果未知。'} 未冒充服务端已撤销。` } finally { busy.value = false } }
async function publish(document) { publishingId.value = document.id; message.value = ''; try { const result = await publishKnowledge(document); const task = result.task || result; await reload(); message.value = `发布任务 ${task.taskId || ''}：${task.status || '已受理'}${result.replayed ? '（安全重放）' : ''}` } catch (reason) { message.value = reason?.message || '发布请求失败。' } finally { publishingId.value = '' } }
async function advanceOrder(order) { const action = orderAction(order); if (!action) return; statusUpdating.value = `${order.kind}:${order.id}`; message.value = ''; try { await updateAdminOrderStatus(order.kind, order, action[0]); await reload(); message.value = `订单已更新为“${action[1]}”。` } catch (reason) { message.value = reason?.message || '订单状态更新失败。' } finally { statusUpdating.value = '' } }

onMounted(() => prepareAuth('admin').catch(reason => { message.value = reason?.message || '后台安全通道暂不可用。' }))
</script>

<template>
  <div class="v3-admin-shell">
    <aside class="v3-admin-aside"><a href="#/" class="brand">贵州乌东 · 运营</a><p class="v3-admin-aside-note">山水、寨、茶与人的本机演示台账</p><template v-if="authState.admin.account"><nav><button v-for="item in [{ key: 'dashboard', label: '运营总览' }, { key: 'orders', label: '订单处理' }, { key: 'catalog', label: '目录概览' }, { key: 'documents', label: '知识发布' }, { key: 'sources', label: '来源主档' }]" :key="item.key" :class="{ active: tab === item.key }" @click="tab = item.key">{{ item.label }}</button></nav><button class="ghost" @click="signOut">退出后台</button></template><a href="#/" class="v3-admin-return">返回游客首页</a></aside>
    <main class="v3-admin-main">
      <header class="v3-admin-heading"><p class="eyebrow">独立 ADMIN 登录通道</p><h1>{{ authState.admin.account ? '乌东运营台' : '运营后台登录' }}</h1><p>{{ authState.admin.account ? '目录、订单与知识分域管理；所有价格、地点与资源演示数据均保留原有标识。' : '运营入口与游客“我的”分离，不提供游客侧注册或找回。' }}</p></header>
      <section v-if="!authState.admin.account" class="v3-admin-login"><form @submit.prevent="signIn"><h2>登录运营台</h2><label>管理员用户名<input v-model="form.username" autocomplete="username" required></label><label>密码<input v-model="form.password" type="password" autocomplete="current-password" required></label><button class="primary" :disabled="busy || !authState.admin.ready">{{ !authState.admin.ready ? '正在准备安全通道…' : busy ? '处理中…' : '登录 ADMIN' }}</button></form><aside><h2>独立管理边界</h2><p>运营账号不在游客网页中创建；后台与游客端的令牌和权限彼此独立。</p><button class="ghost" :disabled="busy || !authState.admin.ready" @click="restoreSession">恢复后台登录</button></aside></section>
      <template v-else>
        <section class="v3-account-bar"><div><p class="eyebrow">ADMIN · 本机演示</p><h2>{{ authState.admin.account.nickname }}</h2><small>@{{ authState.admin.account.username }}</small></div><div><button class="ghost" :disabled="busy" @click="restoreSession">更新登录</button><button class="ghost" :disabled="busy" @click="reload">刷新台账</button></div></section>
        <section v-if="busy && !Object.keys(data.operations).length" class="v3-state">正在读取运营台账…</section>
        <template v-else>
          <section v-if="tab === 'dashboard'" class="v3-admin-dashboard"><div class="v3-admin-hero"><div><p class="eyebrow">今日看板 · 数据随刷新更新</p><h2>沿着溪流，看见乌东的每一次相遇</h2><p>这是本机答辩演示的运营视图：目录、订单、社区与知识分别来自业务接口，不展示虚构 GMV 或实时客流。</p></div><span>乌东<br>运营</span></div><div class="v3-admin-metrics"><article v-for="card in dashboardCards" :key="card.label"><small>{{ card.label }}</small><strong>{{ card.value }}</strong><p>{{ card.note }}</p></article></div><section class="v3-admin-pulse"><header><div><p class="eyebrow">处理提醒</p><h2>订单与知识状态</h2></div><button class="ghost" @click="tab = 'orders'">查看订单</button></header><div><article><b>{{ allOrders.filter(item => /^PENDING/.test(item.status || '')).length }}</b><span>待推进订单</span><small>仅展示接口实际返回的订单</small></article><article><b>{{ data.documents.filter(item => item.currentTaskId).length }}</b><span>知识发布进行中</span><small>发布任务不等于已经生效</small></article><article><b>{{ count('catalog:places') }}</b><span>地图示意节点</span><small>不代表真实导航与比例</small></article></div></section></section>
          <section v-else-if="tab === 'orders'" class="v3-admin-orders"><header class="v3-section-heading"><div><p class="eyebrow">三类订单</p><h2>按状态推进服务</h2><p>商品、同店餐食、住宿各自独立处理；状态变更由服务端校验当前状态。</p></div></header><article v-for="order in allOrders" :key="`${order.kind}-${order.id}`" class="v3-admin-order"><header><span>{{ order.kind === 'products' ? '商品取货' : order.kind === 'foods' ? '到店餐食' : '住宿入住' }}</span><b>{{ order.status }}</b></header><h3>{{ displayName(order) }}</h3><p>{{ order.createdAt || '创建时间待返回' }} · {{ order.demoData ? '演示订单' : '业务订单' }}</p><footer><small>#{{ order.id }}</small><button v-if="orderAction(order)" class="primary" :disabled="statusUpdating" @click="advanceOrder(order)">{{ statusUpdating === `${order.kind}:${order.id}` ? '正在更新…' : orderAction(order)[1] }}</button><span v-else>当前状态无需后台推进</span></footer></article><p v-if="!allOrders.length" class="v3-state">暂无订单。游客完成提交后会在此处出现。</p></section>
          <section v-else-if="tab === 'catalog'" class="v3-admin-catalog"><header class="v3-section-heading"><p class="eyebrow">五模块资源</p><h2>目录与内容概览</h2><p>当前展示数据库的后台目录记录；后续编辑入口应继续走 Java 领域接口。</p></header><div class="v3-admin-catalog-grid"><article v-for="group in catalogGroups" :key="group.key"><small>{{ group.hint }}</small><strong>{{ count(group.key) }}</strong><h3>{{ group.label }}</h3><ul><li v-for="item in (data.operations[group.key] || []).slice(0, 4)" :key="item.id">{{ displayName(item) }}</li></ul><p v-if="count(group.key) > 4">另有 {{ count(group.key) - 4 }} 条记录</p></article></div></section>
          <section v-else-if="tab === 'documents'" class="v3-admin-list"><header class="v3-section-heading"><p class="eyebrow">RAG 知识</p><h2>草稿、生效版本与发布任务</h2></header><article v-for="document in data.documents" :key="document.id"><header><span>{{ document.visibility }}</span><b>行版本 {{ document.version }} · 草稿修订 {{ document.draftRevision }}</b></header><h2>{{ document.draft?.title || document.liveSnapshot?.title || '未命名知识' }}</h2><p>{{ document.draft?.content }}</p><dl><dt>当前任务</dt><dd>{{ document.currentTaskId || '无' }}</dd><dt>生效快照</dt><dd>{{ document.liveSnapshot ? `修订 ${document.liveSnapshot.draftRevision}` : '尚未发布' }}</dd></dl><button class="primary" :disabled="!document.editable || publishingId" @click="publish(document)">{{ publishingId === document.id ? '正在请求…' : document.editable ? '发起发布任务' : '发布进行中，不可编辑' }}</button></article><p v-if="!data.documents.length" class="v3-state">还没有知识文档。</p></section>
          <section v-else class="v3-admin-list"><header class="v3-section-heading"><p class="eyebrow">资料脉络</p><h2>来源主档</h2></header><article v-for="source in data.sources" :key="source.id"><header><span>{{ source.readStatus }}</span><b>来源版本 {{ source.version }}</b></header><h2>{{ source.title }}</h2><p>{{ source.publisher || '发布方待补' }} · {{ source.sourceKind }}</p><footer>{{ source.locator || '定位信息待补' }}</footer></article><p v-if="!data.sources.length" class="v3-state">还没有知识来源。</p></section>
        </template>
      </template>
      <p v-if="message" class="v3-error" role="status">{{ message }}</p>
    </main>
  </div>
</template>
