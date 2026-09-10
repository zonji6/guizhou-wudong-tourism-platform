<script setup>
import { onMounted, reactive, ref } from 'vue'
import { authState, login, logout, prepareAuth, refresh } from '../services/authSession'
import { loadAdminKnowledge, publishKnowledge } from '../services/tourismApi'

const form = reactive({ username: '', password: '' })
const data = reactive({ sources: [], documents: [] })
const tab = ref('documents')
const busy = ref(false)
const message = ref('')
const publishingId = ref('')

async function signIn() {
  busy.value = true
  message.value = ''
  try {
    await login('admin', form)
    form.password = ''
    await reload()
  } catch (reason) {
    message.value = reason?.message || '后台登录失败。'
  } finally { busy.value = false }
}

async function restoreSession() {
  busy.value = true
  message.value = ''
  try { await refresh('admin'); await reload() } catch (reason) { message.value = reason?.message || '没有可恢复的后台登录。' } finally { busy.value = false }
}

async function reload() {
  if (!authState.admin.account) return
  busy.value = true
  message.value = ''
  try { Object.assign(data, await loadAdminKnowledge()) } catch (reason) { message.value = reason?.message || '知识后台暂时无法读取。' } finally { busy.value = false }
}

async function signOut() {
  busy.value = true
  try { await logout('admin'); data.sources = []; data.documents = []; message.value = '后台会话已由服务端撤销。' } catch (reason) { message.value = `${reason?.message || '退出结果未知。'} 未冒充服务端已撤销。` } finally { busy.value = false }
}

async function publish(document) {
  publishingId.value = document.id
  message.value = ''
  try {
    const result = await publishKnowledge(document)
    const task = result.task || result
    const acceptedMessage = `发布任务 ${task.taskId || ''}：${task.status || '已受理'}${result.replayed ? '（安全重放）' : ''}`
    await reload()
    message.value = acceptedMessage
  } catch (reason) {
    message.value = reason?.message || '发布请求失败。'
  } finally { publishingId.value = '' }
}

onMounted(() => prepareAuth('admin').catch(reason => { message.value = reason?.message || '后台安全通道暂不可用。' }))
</script>

<template>
  <div class="v3-admin-shell">
    <aside class="v3-admin-aside"><a href="#/" class="brand">贵州乌东 · 运营</a><template v-if="authState.admin.account"><nav><button :class="{ active: tab === 'documents' }" @click="tab = 'documents'">知识草稿</button><button :class="{ active: tab === 'sources' }" @click="tab = 'sources'">来源主档</button></nav><button class="ghost" @click="signOut">退出后台</button></template><a href="#/" class="v3-admin-return">返回游客首页</a></aside>
    <main class="v3-admin-main">
      <header><p class="eyebrow">独立 ADMIN 登录通道</p><h1>知识发布台</h1><p>工作草稿、生效快照与发布任务分别展示；保存草稿不等于公开，发布请求也不等于已经生效。</p></header>
      <section v-if="!authState.admin.account" class="v3-admin-login"><form @submit.prevent="signIn"><h2>运营后台登录</h2><label>管理员用户名<input v-model="form.username" autocomplete="username" required></label><label>密码<input v-model="form.password" type="password" autocomplete="current-password" required></label><button class="primary" :disabled="busy || !authState.admin.ready">{{ !authState.admin.ready ? '正在准备安全通道…' : busy ? '处理中…' : '登录 ADMIN' }}</button></form><aside><h2>账号不从网页创建</h2><p>ADMIN 仅通过本机交互式 Java CLI 初始化，不提供注册、找回或默认密码。</p><button class="ghost" :disabled="busy || !authState.admin.ready" @click="restoreSession">恢复后台登录</button></aside></section>
      <template v-else><section class="v3-account-bar"><div><p class="eyebrow">ADMIN</p><h2>{{ authState.admin.account.nickname }}</h2><small>@{{ authState.admin.account.username }}</small></div><div><button class="ghost" :disabled="busy" @click="restoreSession">更新登录</button><button class="ghost" :disabled="busy" @click="reload">刷新内容</button></div></section><section v-if="busy && !data.documents.length && !data.sources.length" class="v3-state">正在读取知识台账…</section><section v-else-if="tab === 'documents'" class="v3-admin-list"><article v-for="document in data.documents" :key="document.id"><header><span>{{ document.visibility }}</span><b>行版本 {{ document.version }} · 草稿修订 {{ document.draftRevision }}</b></header><h2>{{ document.draft?.title || document.liveSnapshot?.title || '未命名知识' }}</h2><p>{{ document.draft?.content }}</p><dl><dt>当前任务</dt><dd>{{ document.currentTaskId || '无' }}</dd><dt>生效快照</dt><dd>{{ document.liveSnapshot ? `修订 ${document.liveSnapshot.draftRevision}` : '尚未发布' }}</dd></dl><button class="primary" :disabled="!document.editable || publishingId" @click="publish(document)">{{ publishingId === document.id ? '正在请求…' : document.editable ? '发起发布任务' : '发布进行中，不可编辑' }}</button></article><p v-if="!data.documents.length" class="v3-state">还没有知识文档。</p></section><section v-else class="v3-admin-list"><article v-for="source in data.sources" :key="source.id"><header><span>{{ source.readStatus }}</span><b>来源版本 {{ source.version }}</b></header><h2>{{ source.title }}</h2><p>{{ source.publisher || '发布方待补' }} · {{ source.sourceKind }}</p><footer>{{ source.locator || '定位信息待补' }}</footer></article><p v-if="!data.sources.length" class="v3-state">还没有知识来源。</p></section></template>
      <p v-if="message" class="v3-error" role="status">{{ message }}</p>
    </main>
  </div>
</template>
