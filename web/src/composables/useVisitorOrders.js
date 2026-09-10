import { ref } from 'vue'
import { getMyOrders } from '../services/tourismApi'
import { ensureVisitorId } from '../services/visitorIdentity'

const KINDS = ['product', 'food', 'stay']

export function useVisitorOrders() {
  const orders = ref({ product: [], food: [], stay: [] })
  const errors = ref({ product: '', food: '', stay: '' })
  const loading = ref({ product: false, food: false, stay: false })
  const identityError = ref('')
  const sequences = { product: 0, food: 0, stay: 0 }

  function setAllIdentityErrors(message) {
    identityError.value = message
    for (const kind of KINDS) {
      sequences[kind] += 1
      orders.value[kind] = []
      errors.value[kind] = message
      loading.value[kind] = false
    }
  }

  async function loadKind(kind, visitorId) {
    const sequence = ++sequences[kind]
    orders.value[kind] = []
    loading.value[kind] = true
    errors.value[kind] = ''
    try {
      const result = await getMyOrders(kind, visitorId)
      if (sequence === sequences[kind]) orders.value[kind] = result
    } catch (reason) {
      if (sequence === sequences[kind]) {
        orders.value[kind] = []
        errors.value[kind] = reason?.message || '这一类订单暂时无法读取。'
      }
    } finally {
      if (sequence === sequences[kind]) loading.value[kind] = false
    }
  }

  async function loadAll() {
    let visitorId
    try {
      visitorId = ensureVisitorId()
      identityError.value = ''
    } catch (reason) {
      setAllIdentityErrors(reason?.message || '无法建立本机游客身份。')
      return
    }
    await Promise.allSettled(KINDS.map(kind => loadKind(kind, visitorId)))
  }

  async function retry(kind) {
    try {
      const visitorId = ensureVisitorId()
      identityError.value = ''
      await loadKind(kind, visitorId)
    } catch (reason) {
      setAllIdentityErrors(reason?.message || '无法建立本机游客身份。')
    }
  }

  return { orders, errors, loading, identityError, loadAll, retry }
}
