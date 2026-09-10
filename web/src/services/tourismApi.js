import { request } from './api'
import { adminRequestOptions, newRequestKey, userRequestOptions } from './authSession'

const ORDER_PATHS = {
  product: '/api/product-orders',
  food: '/api/food-orders',
  stay: '/api/stay-bookings'
}

function expectObject(value, label = '数据') {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${label}格式不完整。`)
  return value
}

function expectList(value, label = '列表') {
  if (!Array.isArray(value)) throw new Error(`${label}格式不完整。`)
  return value
}

export const listProducts = async () => expectList(await request('/api/products'), '商品目录')
export const listFoodMerchants = async () => expectList(await request('/api/food-merchants'), '餐食店铺')
export const listFoods = async merchantId => expectList(await request(`/api/foods?merchantId=${encodeURIComponent(merchantId)}`), '餐食菜单')
export const listStays = async () => expectList(await request('/api/stays'), '住宿目录')
export const listPlaces = async () => expectObject(await request('/api/places'), '示意地图')
export const listPosts = async () => expectList(await request('/api/posts'), '社区内容')

export async function createPost(body) {
  return expectObject(await request('/api/posts', userRequestOptions({ method: 'POST', body: JSON.stringify(body) })), '社区投稿')
}

export async function quoteOrder(kind, selection) {
  if (!ORDER_PATHS[kind]) throw new Error('不支持这种订单类型。')
  return expectObject(await request(`/api/order-quotes/${kind}`, userRequestOptions({ method: 'POST', body: JSON.stringify(selection) })), '订单核价')
}

export async function createOrder(kind, body, requestKey) {
  if (!ORDER_PATHS[kind]) throw new Error('不支持这种订单类型。')
  return expectObject(await request(ORDER_PATHS[kind], userRequestOptions({ method: 'POST', idempotencyKey: requestKey, body: JSON.stringify(body) })), '订单回执')
}

export async function loadMyWorkspace() {
  const paths = {
    productOrders: '/api/me/product-orders',
    foodOrders: '/api/me/food-orders',
    stayBookings: '/api/me/stay-bookings',
    itineraries: '/api/me/itineraries',
    foodDrafts: '/api/me/food-drafts',
    stayDrafts: '/api/me/stay-drafts',
    legacyRecords: '/api/me/legacy-records'
  }
  const entries = await Promise.all(Object.entries(paths).map(async ([key, path]) => [key, expectList(await request(path, userRequestOptions()), key)]))
  return Object.fromEntries(entries)
}

export async function saveDraft(draftType, id, expectedVersion, content, requestKey) {
  const segment = draftType === 'FOOD' ? 'food-drafts' : draftType === 'STAY' ? 'stay-drafts' : ''
  if (!segment) throw new Error('不支持这种草稿类型。')
  return expectObject(await request(`/api/me/${segment}/${encodeURIComponent(id)}`, userRequestOptions({
    method: 'PUT',
    idempotencyKey: requestKey,
    body: JSON.stringify({ expectedVersion, content })
  })), '草稿保存回执')
}

export async function reconcileDraft(draftType, requestKey, original) {
  const operationType = draftType === 'FOOD' ? 'SAVE_FOOD_DRAFT' : draftType === 'STAY' ? 'SAVE_STAY_DRAFT' : ''
  if (!operationType) throw new Error('不支持这种草稿类型。')
  return expectObject(await request(`/api/me/write-results/${operationType}/${encodeURIComponent(requestKey)}/reconcile`, userRequestOptions({
    method: 'POST',
    body: JSON.stringify(original)
  })), '草稿核对结果')
}

export async function submitDraft(draftType, id, expectedVersion, expectedQuoteFingerprint, requestKey) {
  const segment = draftType === 'FOOD' ? 'food-drafts' : draftType === 'STAY' ? 'stay-drafts' : ''
  if (!segment) throw new Error('不支持这种草稿类型。')
  return expectObject(await request(`/api/me/${segment}/${encodeURIComponent(id)}/submit`, userRequestOptions({
    method: 'POST',
    idempotencyKey: requestKey,
    body: JSON.stringify({ expectedVersion, expectedQuoteFingerprint })
  })), '草稿提交回执')
}

export async function loadAdminKnowledge() {
  const [sources, documents] = await Promise.all([
    request('/api/admin/knowledge-sources', adminRequestOptions()),
    request('/api/admin/knowledge-documents', adminRequestOptions())
  ])
  return { sources: expectList(sources, '知识来源'), documents: expectList(documents, '知识文档') }
}

export async function loadAdminOperations() {
  const catalogKinds = ['merchants', 'products', 'foods', 'stays', 'room-types', 'places']
  const orderKinds = ['products', 'foods', 'stays']
  const entries = await Promise.all([
    ...catalogKinds.map(async kind => [`catalog:${kind}`, expectList(await request(`/api/admin/${kind}`, adminRequestOptions()), `${kind}目录`)]),
    ...orderKinds.map(async kind => [`orders:${kind}`, expectList(await request(`/api/admin/orders/${kind}`, adminRequestOptions()), `${kind}订单`)]),
    request('/api/posts', adminRequestOptions()).then(value => ['posts', expectList(value, '社区内容')])
  ])
  return Object.fromEntries(entries)
}

export async function updateAdminOrderStatus(kind, order, status) {
  return expectObject(await request(`/api/admin/orders/${kind}/${encodeURIComponent(order.id)}/status`, adminRequestOptions({
    method: 'POST',
    body: JSON.stringify({ expectedStatus: order.status, status })
  })), '订单状态回执')
}

export async function publishKnowledge(document) {
  return expectObject(await request(`/api/admin/knowledge-documents/${encodeURIComponent(document.id)}/publications`, adminRequestOptions({
    method: 'POST',
    idempotencyKey: newRequestKey(),
    body: JSON.stringify({ expectedVersion: document.version })
  })), '发布任务')
}

export { newRequestKey }
