<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { authState, login, logout, prepareAuth, refresh } from '../services/authSession'
import { createAdminCatalog, createKnowledgeDocument, createKnowledgeSource, loadAdminKnowledge, loadAdminOperations, patchAdminCatalog, publishKnowledge, updateAdminCatalogStatus, updateAdminOrderStatus, updateKnowledgeDocument, updateKnowledgeSource } from '../services/tourismApi'

const form = reactive({ username: '', password: '' })
const data = reactive({ sources: [], documents: [], operations: {} })
const tab = ref('dashboard')
const busy = ref(false)
const message = ref('')
const publishingId = ref('')
const statusUpdating = ref('')
const catalogSaving = ref(false)
const knowledgeSaving = ref(false)
const selectedCatalogKind = ref('')
const selectedCatalogItem = ref(null)
const expandedCatalogGroups = reactive({})
const selectedKnowledgeDocument = ref(null)
const selectedKnowledgeSource = ref(null)
const knowledgeDocumentEditorOpen = ref(false)
const knowledgeSourceEditorOpen = ref(false)
const sourceBindingIds = ref([])
const catalogForm = reactive({ parentId: '', name: '', description: '', tags: '', imageUrl: '', contactPhone: '', pickupPoint: '', visitTimeText: '', locationText: '', itemType: 'DISH', maxGuestsPerRoom: 1, priceAmount: '', priceNote: '', category: '', schematicX: '', schematicY: '' })
const knowledgeForm = reactive({ candidateCode: '', title: '', content: '', tags: '', region: '贵州雷山乌东苗寨', periodText: '资料整理时期待核验', evidenceCategory: '资料整理', usageLimitations: '演示资料，需以现场与权威资料复核', demoData: true })
const sourceForm = reactive({ sourceKey: '', title: '', url: '', publisher: '', sourceKind: '资料整理', publicationDateText: '', readAt: '', locator: '', readStatus: 'SEARCH_SNIPPET_ONLY' })

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
function formTags(item) { return Array.isArray(item.tags) ? item.tags.join('，') : '' }
function openCatalogItem(kind, item) {
  selectedCatalogKind.value = kind
  selectedCatalogItem.value = item
  Object.assign(catalogForm, {
    name: item.name || '', description: item.description || '', tags: formTags(item), imageUrl: item.imageUrl || '',
    contactPhone: item.contactPhone || '', pickupPoint: item.pickupPoint || '', visitTimeText: item.visitTimeText || '', locationText: item.locationText || '',
    itemType: item.itemType || 'DISH', maxGuestsPerRoom: item.maxGuestsPerRoom || 1,
    priceAmount: item.demoPrice?.amount || '', priceNote: item.demoPrice?.simulationNote || '', category: item.category || '',
    schematicX: item.schematicPosition?.x || '', schematicY: item.schematicPosition?.y || ''
  })
}
function openCatalogCreate(kind) {
  selectedCatalogKind.value = kind
  selectedCatalogItem.value = null
  Object.assign(catalogForm, {
    parentId: '', name: '', description: '', tags: '', imageUrl: '', contactPhone: '', pickupPoint: '', visitTimeText: '', locationText: '',
    itemType: 'DISH', maxGuestsPerRoom: 1, priceAmount: '', priceNote: '', category: '', schematicX: '', schematicY: ''
  })
}
function visibleCatalogItems(group) {
  const items = data.operations[group.key] || []
  return expandedCatalogGroups[group.key] ? items : items.slice(0, 4)
}
function toggleCatalogGroup(key) { expandedCatalogGroups[key] = !expandedCatalogGroups[key] }
function splitTags(value) { return String(value || '').split(/[，,]/).map(item => item.trim()).filter(Boolean) }
function openKnowledgeCreate() {
  knowledgeDocumentEditorOpen.value = true
  selectedKnowledgeDocument.value = null
  sourceBindingIds.value = []
  Object.assign(knowledgeForm, { candidateCode: '', title: '', content: '', tags: '', region: '贵州雷山乌东苗寨', periodText: '资料整理时期待核验', evidenceCategory: '资料整理', usageLimitations: '演示资料，需以现场与权威资料复核', demoData: true })
}
function openKnowledgeDocument(document) {
  knowledgeDocumentEditorOpen.value = true
  const draft = document.draft || {}
  selectedKnowledgeDocument.value = document
  sourceBindingIds.value = (draft.sourceSnapshots || []).map(item => item.sourceId).filter(Boolean)
  Object.assign(knowledgeForm, { candidateCode: document.candidateCode || '', title: draft.title || '', content: draft.content || '', tags: (draft.tags || []).join('，'), region: draft.region || '', periodText: draft.periodText || '', evidenceCategory: draft.evidenceCategory || '', usageLimitations: (draft.usageLimitations || []).join('，'), demoData: draft.demoData !== false })
}
function sourcePayload() {
  const reviewed = ['FULL_TEXT_REVIEWED', 'ARCHIVED_COPY_REVIEWED'].includes(sourceForm.readStatus)
  if (reviewed && !sourceForm.readAt) throw new Error('已审读来源需填写 ISO 格式的阅读时间。')
  return { sourceKey: sourceForm.sourceKey.trim(), title: sourceForm.title.trim(), url: sourceForm.url.trim() || null, publisher: sourceForm.publisher.trim() || null, sourceKind: sourceForm.sourceKind.trim(), publicationDateText: sourceForm.publicationDateText.trim() || null, readAt: reviewed ? sourceForm.readAt.trim() : null, locator: sourceForm.locator.trim() || null, readStatus: sourceForm.readStatus }
}
function openSourceCreate() {
  knowledgeSourceEditorOpen.value = true
  selectedKnowledgeSource.value = null
  Object.assign(sourceForm, { sourceKey: '', title: '', url: '', publisher: '', sourceKind: '资料整理', publicationDateText: '', readAt: '', locator: '', readStatus: 'SEARCH_SNIPPET_ONLY' })
}
function openKnowledgeSource(source) {
  knowledgeSourceEditorOpen.value = true
  selectedKnowledgeSource.value = source
  Object.assign(sourceForm, { sourceKey: source.sourceKey || '', title: source.title || '', url: source.url || '', publisher: source.publisher || '', sourceKind: source.sourceKind || '', publicationDateText: source.publicationDateText || '', readAt: source.readAt || '', locator: source.locator || '', readStatus: source.readStatus || 'SEARCH_SNIPPET_ONLY' })
}
function knowledgePayload() {
  return { candidateCode: knowledgeForm.candidateCode.trim(), draft: { title: knowledgeForm.title.trim(), content: knowledgeForm.content.trim(), tags: splitTags(knowledgeForm.tags), region: knowledgeForm.region.trim() || null, periodText: knowledgeForm.periodText.trim() || null, evidenceCategory: knowledgeForm.evidenceCategory.trim() || null, usageLimitations: splitTags(knowledgeForm.usageLimitations), demoData: Boolean(knowledgeForm.demoData), sourceBindings: data.sources.filter(source => sourceBindingIds.value.includes(source.id)).map(source => ({ sourceId: source.id, expectedSourceVersion: source.version })) } }
}
function catalogTags() { return catalogForm.tags.split(/[，,]/).map(value => value.trim()).filter(Boolean) }
function priceUnit(kind) { return kind === 'products' ? 'ITEM' : kind === 'foods' ? 'PORTION' : 'ROOM_NIGHT' }
function demoPrice(kind) {
  const amount = catalogForm.priceAmount.trim()
  if (amount && (!Number.isFinite(Number(amount)) || Number(amount) < 0)) throw new Error('演示价格需为不小于 0 的数字。')
  return amount ? { amount: Number(amount).toFixed(2), currency: 'CNY', unit: priceUnit(kind), simulationNote: catalogForm.priceNote.trim() || '本机演示价格，以现场为准' } : null
}
function schematicPosition() {
  const x = catalogForm.schematicX.trim(); const y = catalogForm.schematicY.trim()
  if (!x && !y) return null
  if (!x || !y || !Number.isFinite(Number(x)) || !Number.isFinite(Number(y)) || Number(x) < 0 || Number(x) > 1 || Number(y) < 0 || Number(y) > 1) throw new Error('示意坐标需同时填写 0 到 1 之间的横、纵坐标。')
  return { x: Number(x).toFixed(4), y: Number(y).toFixed(4) }
}
function catalogPayload(kind) {
  const common = { name: catalogForm.name.trim(), description: catalogForm.description.trim(), tags: catalogTags(), imageUrl: catalogForm.imageUrl.trim() || null }
  if (kind === 'merchants') return { ...common, contactPhone: catalogForm.contactPhone.trim() || null }
  if (kind === 'products') return { ...common, pickupPoint: catalogForm.pickupPoint.trim(), demoPrice: demoPrice(kind) }
  if (kind === 'foods') return { ...common, itemType: catalogForm.itemType, visitTimeText: catalogForm.visitTimeText.trim() || null, demoPrice: demoPrice(kind) }
  if (kind === 'stays') return { ...common, locationText: catalogForm.locationText.trim() || null }
  if (kind === 'room-types') return { name: common.name, description: common.description, imageUrl: common.imageUrl, maxGuestsPerRoom: Number(catalogForm.maxGuestsPerRoom), demoPrice: demoPrice(kind) }
  return { ...common, category: catalogForm.category.trim(), schematicPosition: schematicPosition() }
}
function catalogCreatePayload(kind) {
  const body = catalogPayload(kind)
  if (kind === 'products' || kind === 'foods' || kind === 'stays') return { ...body, merchantId: catalogForm.parentId }
  if (kind === 'room-types') return { ...body, stayPropertyId: catalogForm.parentId }
  return body
}
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
async function saveKnowledgeDocument() {
  const existing = Boolean(selectedKnowledgeDocument.value)
  knowledgeSaving.value = true; message.value = ''
  try { const saved = existing ? await updateKnowledgeDocument(selectedKnowledgeDocument.value, knowledgePayload()) : await createKnowledgeDocument(knowledgePayload()); await reload(); openKnowledgeDocument(saved); message.value = existing ? '知识草稿已保存。' : '知识草稿已创建，发布前仍可修改。' } catch (reason) { message.value = reason?.message || '知识草稿保存失败。' } finally { knowledgeSaving.value = false }
}
async function saveKnowledgeSource() {
  const existing = Boolean(selectedKnowledgeSource.value)
  knowledgeSaving.value = true; message.value = ''
  try { const saved = existing ? await updateKnowledgeSource(selectedKnowledgeSource.value, sourcePayload()) : await createKnowledgeSource(sourcePayload()); await reload(); openKnowledgeSource(saved); message.value = existing ? '知识来源已保存。' : '知识来源已创建。' } catch (reason) { message.value = reason?.message || '知识来源保存失败。' } finally { knowledgeSaving.value = false }
}
async function advanceOrder(order) { const action = orderAction(order); if (!action) return; statusUpdating.value = `${order.kind}:${order.id}`; message.value = ''; try { await updateAdminOrderStatus(order.kind, order, action[0]); await reload(); message.value = `订单已更新为“${action[1]}”。` } catch (reason) { message.value = reason?.message || '订单状态更新失败。' } finally { statusUpdating.value = '' } }
async function saveCatalogItem() {
  const item = selectedCatalogItem.value; const kind = selectedCatalogKind.value
  if (!kind) return
  catalogSaving.value = true; message.value = ''
  try {
    const updated = item ? await patchAdminCatalog(kind, item, catalogPayload(kind)) : await createAdminCatalog(kind, catalogCreatePayload(kind))
    await reload(); openCatalogItem(kind, updated); message.value = item ? '目录记录已保存。' : '目录记录已创建，初始状态为未发布。'
  } catch (reason) { message.value = reason?.message || '目录保存失败。' } finally { catalogSaving.value = false }
}
async function setCatalogStatus(status) {
  const item = selectedCatalogItem.value; const kind = selectedCatalogKind.value
  if (!item || !kind || item.catalogStatus === status) return
  catalogSaving.value = true; message.value = ''
  try { const updated = await updateAdminCatalogStatus(kind, item, status); await reload(); openCatalogItem(kind, updated); message.value = `目录状态已更新为 ${status}。` } catch (reason) { message.value = reason?.message || '目录状态更新失败。' } finally { catalogSaving.value = false }
}

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
          <section v-else-if="tab === 'catalog'" class="v3-admin-catalog">
            <header class="v3-section-heading"><p class="eyebrow">五模块资源</p><h2>目录与内容管理</h2><p>编辑、新建、发布、下架与归档都会调用 Java 领域接口；演示数据和示意地图的边界会继续保留。</p></header>
            <div class="v3-admin-catalog-grid">
              <article v-for="group in catalogGroups" :key="group.key">
                <small>{{ group.hint }}</small><strong>{{ count(group.key) }}</strong><h3>{{ group.label }}</h3>
                <button class="v3-admin-add" type="button" @click="openCatalogCreate(group.key.replace('catalog:', ''))">＋ 新建{{ group.label }}</button>
                <ul><li v-for="item in visibleCatalogItems(group)" :key="item.id"><button type="button" @click="openCatalogItem(group.key.replace('catalog:', ''), item)">{{ displayName(item) }}</button><i>{{ item.catalogStatus }}</i></li></ul>
                <button v-if="count(group.key) > 4" class="v3-text-button" type="button" @click="toggleCatalogGroup(group.key)">{{ expandedCatalogGroups[group.key] ? '收起目录' : `查看其余 ${count(group.key) - 4} 条` }}</button>
              </article>
            </div>
            <section v-if="selectedCatalogKind" class="v3-admin-editor">
              <header>
                <div><p class="eyebrow">{{ selectedCatalogItem ? `正在维护 · ${selectedCatalogKind}` : `新建目录 · ${selectedCatalogKind}` }}</p><h2>{{ selectedCatalogItem?.name || '填写一条新记录' }}</h2><p>{{ selectedCatalogItem ? `当前版本 ${selectedCatalogItem.version} · ${selectedCatalogItem.catalogStatus}` : '创建后默认为未发布，确认内容后再发布到游客端。' }}</p></div>
                <button class="ghost" type="button" @click="selectedCatalogKind = ''; selectedCatalogItem = null">收起</button>
              </header>
              <form @submit.prevent="saveCatalogItem">
                <label>名称<input v-model="catalogForm.name" maxlength="120" required></label>
                <label>简介<textarea v-model="catalogForm.description" maxlength="1000" required></textarea></label>
                <label v-if="selectedCatalogKind !== 'room-types'">标签（逗号分隔）<input v-model="catalogForm.tags" maxlength="400"></label>
                <label>图片路径（可留空）<input v-model="catalogForm.imageUrl" maxlength="500"></label>
                <label v-if="!selectedCatalogItem && ['products', 'foods', 'stays'].includes(selectedCatalogKind)">所属商家<select v-model="catalogForm.parentId" required><option disabled value="">请选择商家</option><option v-for="merchant in (data.operations['catalog:merchants'] || [])" :key="merchant.id" :value="merchant.id">{{ merchant.name }}</option></select></label>
                <label v-if="!selectedCatalogItem && selectedCatalogKind === 'room-types'">所属住宿<select v-model="catalogForm.parentId" required><option disabled value="">请选择住宿</option><option v-for="stay in (data.operations['catalog:stays'] || [])" :key="stay.id" :value="stay.id">{{ stay.name }}</option></select></label>
                <label v-if="selectedCatalogKind === 'merchants'">联系信息（内部维护项）<input v-model="catalogForm.contactPhone" maxlength="32"></label>
                <label v-if="selectedCatalogKind === 'products'">自提说明<input v-model="catalogForm.pickupPoint" maxlength="160" required></label>
                <label v-if="selectedCatalogKind === 'foods'">餐食类型<select v-model="catalogForm.itemType"><option value="DISH">菜品</option><option value="DRINK">饮品</option><option value="SET">套餐</option></select></label>
                <label v-if="selectedCatalogKind === 'foods'">到店说明<input v-model="catalogForm.visitTimeText" maxlength="160"></label>
                <label v-if="selectedCatalogKind === 'stays'">位置说明<input v-model="catalogForm.locationText" maxlength="160"></label>
                <label v-if="selectedCatalogKind === 'room-types'">每间演示容量<input v-model.number="catalogForm.maxGuestsPerRoom" type="number" min="1" required></label>
                <label v-if="selectedCatalogKind === 'places'">地点类别<input v-model="catalogForm.category" maxlength="64" required></label>
                <template v-if="selectedCatalogKind === 'places'"><label>示意横坐标（0-1）<input v-model="catalogForm.schematicX" inputmode="decimal" placeholder="留空则不绘制"></label><label>示意纵坐标（0-1）<input v-model="catalogForm.schematicY" inputmode="decimal" placeholder="留空则不绘制"></label></template>
                <template v-if="['products', 'foods', 'room-types'].includes(selectedCatalogKind)"><label>演示价格（元）<input v-model="catalogForm.priceAmount" inputmode="decimal" placeholder="留空即无演示价"></label><label>演示价格说明<input v-model="catalogForm.priceNote" maxlength="200" placeholder="例如：本机演示价格，以现场为准"></label></template>
                <footer><button class="primary" :disabled="catalogSaving">{{ catalogSaving ? '保存中…' : selectedCatalogItem ? '保存修改' : '创建未发布记录' }}</button><template v-if="selectedCatalogItem"><button v-for="status in ['PUBLISHED', 'UNPUBLISHED', 'ARCHIVED']" :key="status" class="ghost" type="button" :disabled="catalogSaving || selectedCatalogItem.catalogStatus === status" @click="setCatalogStatus(status)">{{ status === 'PUBLISHED' ? '发布' : status === 'UNPUBLISHED' ? '下架' : '归档' }}</button></template></footer>
              </form>
            </section>
          </section>
          <section v-else-if="tab === 'documents'" class="v3-admin-list">
            <header class="v3-section-heading"><div><p class="eyebrow">RAG 知识</p><h2>草稿、生效版本与发布任务</h2></div><button class="primary" type="button" @click="openKnowledgeCreate">新建知识草稿</button></header>
            <section v-if="knowledgeDocumentEditorOpen" class="v3-knowledge-editor"><header><div><p class="eyebrow">{{ selectedKnowledgeDocument ? '编辑知识草稿' : '新建知识草稿' }}</p><h2>{{ selectedKnowledgeDocument?.draft?.title || '先记录一条可追溯的资料' }}</h2></div><button class="ghost" type="button" @click="knowledgeDocumentEditorOpen = false">收起</button></header><form @submit.prevent="saveKnowledgeDocument"><label>候选编号<input v-model="knowledgeForm.candidateCode" maxlength="40" pattern="[A-Za-z0-9_-]+" required></label><label>标题<input v-model="knowledgeForm.title" maxlength="120" required></label><label class="wide">正文<textarea v-model="knowledgeForm.content" maxlength="30000" required></textarea></label><label>标签（逗号分隔）<input v-model="knowledgeForm.tags" maxlength="400"></label><label>地域范围<input v-model="knowledgeForm.region" maxlength="200"></label><label>时间范围<input v-model="knowledgeForm.periodText" maxlength="200"></label><label>证据类别<input v-model="knowledgeForm.evidenceCategory" maxlength="200" required></label><label class="wide">使用限制（逗号分隔）<input v-model="knowledgeForm.usageLimitations" maxlength="1200"></label><label class="v3-check"><input v-model="knowledgeForm.demoData" type="checkbox">演示资料（保留明确标识）</label><fieldset class="v3-source-bindings"><legend>引用来源版本</legend><p>发布时会冻结所选来源的当前版本；未选择来源的草稿不能作为完整公开知识发布。</p><label v-for="source in data.sources" :key="source.id" class="v3-check"><input v-model="sourceBindingIds" type="checkbox" :value="source.id">{{ source.title }} · v{{ source.version }}</label></fieldset><footer><button class="primary" :disabled="knowledgeSaving">{{ knowledgeSaving ? '保存中…' : selectedKnowledgeDocument ? '保存草稿' : '创建草稿' }}</button></footer></form></section>
            <article v-for="document in data.documents" :key="document.id"><header><span>{{ document.visibility }}</span><b>行版本 {{ document.version }} · 草稿修订 {{ document.draftRevision }}</b></header><h2>{{ document.draft?.title || document.liveSnapshot?.title || '未命名知识' }}</h2><p>{{ document.draft?.content }}</p><dl><dt>当前任务</dt><dd>{{ document.currentTaskId || '无' }}</dd><dt>生效快照</dt><dd>{{ document.liveSnapshot ? `修订 ${document.liveSnapshot.draftRevision}` : '尚未发布' }}</dd></dl><footer><button class="ghost" type="button" :disabled="!document.editable" @click="openKnowledgeDocument(document)">编辑草稿</button><button class="primary" :disabled="!document.editable || publishingId" @click="publish(document)">{{ publishingId === document.id ? '正在请求…' : document.editable ? '发起发布任务' : '发布进行中，不可编辑' }}</button></footer></article><p v-if="!data.documents.length" class="v3-state">还没有知识文档。</p>
          </section>
          <section v-else class="v3-admin-list">
            <header class="v3-section-heading"><div><p class="eyebrow">资料脉络</p><h2>来源主档</h2></div><button class="primary" type="button" @click="openSourceCreate">新建来源</button></header>
            <section v-if="knowledgeSourceEditorOpen" class="v3-knowledge-editor"><header><div><p class="eyebrow">{{ selectedKnowledgeSource ? '编辑来源主档' : '新建来源主档' }}</p><h2>{{ selectedKnowledgeSource?.title || '记录可追溯资料来源' }}</h2></div><button class="ghost" type="button" @click="knowledgeSourceEditorOpen = false">收起</button></header><form @submit.prevent="saveKnowledgeSource"><label>来源编号<input v-model="sourceForm.sourceKey" maxlength="40" pattern="[A-Za-z0-9_-]+" required></label><label>标题<input v-model="sourceForm.title" maxlength="200" required></label><label class="wide">公开链接（可留空）<input v-model="sourceForm.url" type="url" maxlength="2048"></label><label>发布方<input v-model="sourceForm.publisher" maxlength="200"></label><label>来源类型<input v-model="sourceForm.sourceKind" maxlength="80" required></label><label>发布日期文本<input v-model="sourceForm.publicationDateText" maxlength="80"></label><label>资料定位<input v-model="sourceForm.locator" maxlength="300"></label><label>审读状态<select v-model="sourceForm.readStatus"><option value="UNREVIEWED">未审读</option><option value="SEARCH_SNIPPET_ONLY">摘要检索</option><option value="FULL_TEXT_REVIEWED">全文审读</option><option value="ARCHIVED_COPY_REVIEWED">存档副本审读</option></select></label><label v-if="['FULL_TEXT_REVIEWED', 'ARCHIVED_COPY_REVIEWED'].includes(sourceForm.readStatus)" class="wide">审读时间（ISO 格式）<input v-model="sourceForm.readAt" placeholder="2026-09-11T08:00:00Z"></label><footer><button class="primary" :disabled="knowledgeSaving">{{ knowledgeSaving ? '保存中…' : selectedKnowledgeSource ? '保存来源' : '创建来源' }}</button></footer></form></section>
            <article v-for="source in data.sources" :key="source.id"><header><span>{{ source.readStatus }}</span><b>来源版本 {{ source.version }}</b></header><h2>{{ source.title }}</h2><p>{{ source.publisher || '发布方待补' }} · {{ source.sourceKind }}</p><footer><small>{{ source.locator || '定位信息待补' }}</small><button class="ghost" type="button" @click="openKnowledgeSource(source)">编辑来源</button></footer></article><p v-if="!data.sources.length" class="v3-state">还没有知识来源。</p>
          </section>
        </template>
      </template>
      <p v-if="message" class="v3-error" role="status">{{ message }}</p>
    </main>
  </div>
</template>
