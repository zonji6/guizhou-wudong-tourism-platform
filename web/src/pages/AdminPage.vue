<script setup>
import SafeImage from '../components/common/SafeImage.vue'

defineProps({
  tab: { type: String, default: 'orders' },
  orders: { type: Array, default: () => [] },
  services: { type: Array, default: () => [] },
  actionError: String,
  updatingOrderId: String
})
defineEmits(['change-tab', 'update-order', 'return-home'])

function statusLabel(status) {
  return {
    PENDING_CONFIRMATION: '待游客确认',
    CONFIRMED: '已确认',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }[status] || '状态待核对'
}

function canUpdate(status) {
  return status === 'CONFIRMED' || status === 'PROCESSING'
}

function updateLabel(status) {
  return status === 'CONFIRMED' ? '开始处理' : '标记完成'
}

function shortOrderId(id) {
  return typeof id === 'string' && id.length > 8 ? `…${id.slice(-8)}` : (id || '待生成')
}

function tagsOf(item) {
  return Array.isArray(item?.tags) ? item.tags : []
}

function categoryLabel(category) {
  return {
    culture: '文化茶旅',
    stay: '苗寨住宿',
    food: '寨味餐食',
    travel: '山野出行'
  }[category] || '乌东体验'
}
</script>

<template>
  <aside class="admin-aside">
    <a class="brand" href="#/">贵州乌东 · 运营</a>
    <nav aria-label="后台栏目">
      <button type="button" :class="{ active: tab === 'orders' }" @click="$emit('change-tab', 'orders')">预约订单</button>
      <button type="button" :class="{ active: tab === 'content' }" @click="$emit('change-tab', 'content')">资源与社区</button>
      <button type="button" :class="{ active: tab === 'ai-summary' }" @click="$emit('change-tab', 'ai-summary')">AI 运行摘要</button>
    </nav>
    <button type="button" class="admin-return" @click="$emit('return-home')">返回游客端</button>
  </aside>

  <main class="admin-main">
    <header class="admin-head">
      <div>
        <p class="eyebrow">本机答辩演示后台</p>
        <h1>{{ tab === 'orders' ? '预约订单' : tab === 'content' ? '资源与社区' : 'AI 运行摘要' }}</h1>
      </div>
      <span class="demo-badge">演示环境</span>
    </header>

    <p v-if="actionError" class="form-error" role="alert">{{ actionError }}</p>

    <section v-if="tab === 'orders'" class="admin-table-wrap">
      <div v-if="orders.length" class="admin-table" role="table" aria-label="预约订单">
        <div class="table-row table-row--orders table-row--header" role="row">
          <span role="columnheader">订单</span>
          <span role="columnheader">体验项目</span>
          <span role="columnheader">日期 / 人数</span>
          <span role="columnheader">状态</span>
          <span role="columnheader">操作</span>
        </div>
        <div v-for="order in orders" :key="order.id" class="table-row table-row--orders" role="row">
          <span role="cell"><small>{{ shortOrderId(order.id) }}</small><i v-if="order.demoData === true">演示</i></span>
          <strong role="cell">{{ order.name }}</strong>
          <span role="cell">{{ order.date }} · {{ order.people }} 人</span>
          <span role="cell"><em class="status">{{ statusLabel(order.status) }}</em></span>
          <span role="cell">
            <button
              v-if="canUpdate(order.status)"
              type="button"
              :disabled="Boolean(updatingOrderId)"
              @click="$emit('update-order', order)"
            >
              {{ updatingOrderId === order.id ? '更新中…' : updatingOrderId ? '请稍候' : updateLabel(order.status) }}
            </button>
            <small v-else>无需操作</small>
          </span>
        </div>
      </div>
      <div v-else class="admin-empty">
        <h2>还没有已确认预约</h2>
        <p>游客完成页面确认后，订单会出现在这里。</p>
      </div>
    </section>

    <section v-else-if="tab === 'content'" class="admin-content">
      <header class="admin-section-head">
        <div><h2>游客端服务资源</h2><p>当前只读展示本机服务数据。</p></div>
        <span>{{ services.length }} 项</span>
      </header>
      <article v-for="item in services" :key="item.id" class="admin-resource">
        <SafeImage
          :src="item.image"
          :alt="item.imageAlt || '服务实景暂缺'"
          :label="item.title"
          loading="lazy"
        />
        <div>
          <p><span v-if="item.demoData === true">演示数据</span>{{ categoryLabel(item.category) }}</p>
          <h3>{{ item.title }}</h3>
          <small>{{ tagsOf(item).join(' · ') || '标签待整理' }}</small>
        </div>
        <strong v-if="typeof item.price === 'number'">¥{{ item.price }}</strong>
      </article>
    </section>

    <section v-else class="admin-summary">
      <header>
        <span class="demo-data-label">静态演示</span>
        <h2>演示摘要，不对应本次会话 / 非 LangSmith Trace</h2>
        <p>这些内容只用于说明答辩流程，不代表当前服务已经产生实时运行数据。</p>
      </header>
      <div class="admin-summary__grid">
        <article>
          <span>01</span>
          <h3>需求整理</h3>
          <p>示意用户提出茶旅、人数和日期偏好后，向导如何整理为可执行需求。</p>
        </article>
        <article>
          <span>02</span>
          <h3>资料与服务</h3>
          <p>示意从本机演示资料与服务台账中寻找依据，不展示内部参数或原始内容。</p>
        </article>
        <article>
          <span>03</span>
          <h3>行程与确认</h3>
          <p>示意生成结构化建议；预约先保持待确认，最终由游客在页面完成确认。</p>
        </article>
      </div>
    </section>
  </main>
</template>
