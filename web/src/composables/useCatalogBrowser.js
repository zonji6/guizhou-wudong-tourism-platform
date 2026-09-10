import { ref } from 'vue'
import { getCatalog, listCatalog } from '../services/tourismApi'

export function useCatalogBrowser() {
  const kind = ref('product')
  const items = ref([])
  const selected = ref(null)
  const loading = ref(false)
  const error = ref('')
  let requestSequence = 0

  async function loadList(nextKind) {
    const sequence = ++requestSequence
    kind.value = nextKind
    items.value = []
    selected.value = null
    loading.value = true
    error.value = ''
    try {
      const result = await listCatalog(nextKind)
      if (sequence === requestSequence) items.value = result
    } catch (reason) {
      if (sequence === requestSequence) error.value = reason?.message || '目录暂时无法读取。'
    } finally {
      if (sequence === requestSequence) loading.value = false
    }
  }

  async function loadDetail(nextKind, id) {
    const sequence = ++requestSequence
    kind.value = nextKind
    selected.value = null
    loading.value = true
    error.value = ''
    try {
      const result = await getCatalog(nextKind, id)
      if (sequence === requestSequence && result.id === id) selected.value = result
      else if (sequence === requestSequence) throw new Error('目录详情与当前地址不一致。')
    } catch (reason) {
      if (sequence === requestSequence) error.value = reason?.message || '目录详情暂时无法读取。'
    } finally {
      if (sequence === requestSequence) loading.value = false
    }
  }

  function cancel() {
    requestSequence += 1
    loading.value = false
  }

  return { kind, items, selected, loading, error, loadList, loadDetail, cancel }
}
