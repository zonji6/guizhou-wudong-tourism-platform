<script setup>
import SafeImage from '../components/common/SafeImage.vue'

defineProps({
  service: Object,
  booking: { type: Object, default: () => ({ date: '', people: 1, contact: '', phone: '' }) },
  pending: Object,
  done: Boolean,
  latestOrder: Object,
  loading: Boolean,
  loadError: String,
  actionError: String,
  submitting: Boolean,
  confirming: Boolean
})
const emit = defineEmits(['update-booking', 'submit', 'confirm', 'open-admin', 'return-assistant'])

function statusLabel(status) {
  return {
    PENDING_CONFIRMATION: '待你确认',
    CONFIRMED: '已确认',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }[status] || '状态待核对'
}
</script>

<template>
  <main class="page booking-page">
    <header class="page-intro">
      <p class="eyebrow">停一步，再核对</p>
      <h1>预约确认</h1>
      <p>向导只能准备待确认预约；最后一步始终由你完成。</p>
    </header>

    <section v-if="loading" class="booking-state-card" aria-live="polite">
      <span class="booking-state-card__mark">叶</span>
      <h2>正在读取待确认预约</h2>
      <p>正在核对向导交来的同一笔预约，请稍候。</p>
    </section>

    <section v-else-if="loadError" class="booking-state-card booking-state-card--error" role="alert">
      <h2>暂时无法读取预约</h2>
      <p>{{ loadError }}</p>
      <button type="button" class="ghost" @click="emit('return-assistant')">返回乌东向导</button>
    </section>

    <section v-else-if="service && !done" class="booking-card">
      <header class="booking-service">
        <SafeImage
          :src="service.image"
          :alt="service.imageAlt || '服务实景暂缺'"
          :label="service.title"
          loading="eager"
        />
        <div>
          <span v-if="service.demoData === true" class="demo-data-label">演示数据</span>
          <h2>{{ service.title }}</h2>
          <p>请以服务方确认的时间、价格与接待条件为准。</p>
        </div>
      </header>

      <form v-if="!pending" class="booking-form" @submit.prevent="emit('submit')">
        <label>
          <span>出行日期</span>
          <input
            :value="booking.date"
            type="date"
            required
            :disabled="submitting"
            @input="emit('update-booking', { ...booking, date: $event.target.value })"
          >
        </label>
        <label>
          <span>出行人数</span>
          <input
            :value="booking.people"
            type="number"
            min="1"
            required
            :disabled="submitting"
            @input="emit('update-booking', { ...booking, people: Number($event.target.value) })"
          >
        </label>
        <label>
          <span>联系人</span>
          <input
            :value="booking.contact"
            autocomplete="name"
            required
            :disabled="submitting"
            @input="emit('update-booking', { ...booking, contact: $event.target.value })"
          >
        </label>
        <label>
          <span>联系电话</span>
          <input
            :value="booking.phone"
            type="tel"
            autocomplete="tel"
            required
            :disabled="submitting"
            @input="emit('update-booking', { ...booking, phone: $event.target.value })"
          >
        </label>
        <button type="submit" class="primary" :disabled="submitting">
          {{ submitting ? '正在提交…' : '提交待确认预约' }}
        </button>
      </form>

      <section v-else class="pending-booking-summary">
        <div class="pending-booking-summary__head">
          <div>
            <span v-if="pending.demoData === true" class="demo-data-label">演示数据</span>
            <h3>待确认预约摘要</h3>
          </div>
          <strong>{{ statusLabel(pending.status) }}</strong>
        </div>
        <dl>
          <dt>体验项目</dt><dd>{{ pending.name }}</dd>
          <dt>出行日期</dt><dd>{{ pending.date || '待核对' }}</dd>
          <dt>出行人数</dt><dd>{{ pending.people }} 人</dd>
          <dt>联系人</dt><dd>{{ pending.contact || '待核对' }}</dd>
          <dt>联系电话</dt><dd>{{ pending.phone || '待核对' }}</dd>
        </dl>
        <button
          v-if="pending.status === 'PENDING_CONFIRMATION'"
          type="button"
          class="primary"
          :disabled="confirming"
          @click="emit('confirm')"
        >
          {{ confirming ? '正在确认…' : '确认这笔预约' }}
        </button>
        <p v-else class="service-notice">这笔预约当前不可再次确认。</p>
      </section>

      <p v-if="actionError" class="form-error" role="alert">{{ actionError }}</p>
    </section>

    <section v-else-if="done" class="booking-state-card booking-state-card--success">
      <span class="booking-state-card__mark">✓</span>
      <h2>预约已确认</h2>
      <p v-if="latestOrder">{{ latestOrder.name }} · {{ latestOrder.date }} · {{ latestOrder.people }} 人</p>
      <button type="button" class="primary" @click="emit('open-admin')">去运营后台查看</button>
    </section>

    <section v-else class="booking-state-card">
      <h2>没有可预约的服务</h2>
      <p>请从“游乌东”详情页或乌东向导的结果卡片重新进入。</p>
      <button type="button" class="ghost" @click="emit('return-assistant')">返回乌东向导</button>
    </section>
  </main>
</template>
