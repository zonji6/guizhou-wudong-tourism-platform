import { request } from './api'
import { ensureVisitorId } from './visitorIdentity'

const LIST_PATHS = {
  product: '/api/products',
  food: '/api/foods',
  stay: '/api/stays'
}
const DETAIL_PATHS = {
  product: '/api/products',
  food: '/api/foods',
  stay: '/api/stays'
}
const ORDER_PATHS = {
  product: '/api/product-orders',
  food: '/api/food-orders',
  stay: '/api/stay-bookings'
}
const MY_ORDER_PATHS = {
  product: '/api/me/product-orders',
  food: '/api/me/food-orders',
  stay: '/api/me/stay-bookings'
}
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

class TourismClientError extends Error {
  constructor(message, code) {
    super(message)
    this.name = 'TourismClientError'
    this.code = code
  }
}

function pathFor(map, kind) {
  const path = map[kind]
  if (!path) throw new TourismClientError('暂不支持这种服务类型。', 'UNSUPPORTED_CATALOG_KIND')
  return path
}

function uuid(value) {
  if (typeof value !== 'string' || !UUID_PATTERN.test(value)) {
    throw new TourismClientError('当前页面的服务标识无效，请返回目录重新选择。', 'INVALID_RESOURCE_ID')
  }
  return value
}

function visitorHeaders(visitorId = ensureVisitorId()) {
  return { 'X-Visitor-Id': uuid(visitorId) }
}

function expectObject(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new TourismClientError('服务返回了不完整的详情，请稍后重试。', 'INVALID_API_OBJECT')
  }
  return value
}

function expectList(value) {
  if (!Array.isArray(value)) {
    throw new TourismClientError('服务返回了不完整的列表，请稍后重试。', 'INVALID_API_LIST')
  }
  return value
}

export async function listCatalog(kind) {
  return expectList(await request(pathFor(LIST_PATHS, kind)))
}

export async function getCatalog(kind, id) {
  return expectObject(await request(`${pathFor(DETAIL_PATHS, kind)}/${encodeURIComponent(uuid(id))}`))
}

export async function getRoomType(id) {
  return expectObject(await request(`/api/room-types/${encodeURIComponent(uuid(id))}`))
}

export async function createOrder(kind, targetId, form, target) {
  const contactName = String(form.contactName || '').trim()
  const contactPhone = String(form.contactPhone || '').trim()
  const note = String(form.note || '').trim() || null
  let body
  if (kind === 'product') {
    body = {
      productId: uuid(targetId),
      quantity: Number(form.quantity),
      pickupPoint: String(target?.pickupPoint || ''),
      contactName,
      contactPhone,
      note,
      sourceThreadId: null
    }
  } else if (kind === 'food') {
    body = {
      foodItemId: uuid(targetId),
      visitAt: String(form.visitAt || ''),
      peopleCount: Number(form.peopleCount),
      contactName,
      contactPhone,
      note,
      sourceThreadId: null
    }
  } else if (kind === 'stay') {
    body = {
      roomTypeId: uuid(targetId),
      checkInDate: String(form.checkInDate || ''),
      peopleCount: Number(form.peopleCount),
      contactName,
      contactPhone,
      note,
      sourceThreadId: null
    }
  } else {
    throw new TourismClientError('暂不支持提交这种订单。', 'UNSUPPORTED_ORDER_KIND')
  }
  return expectObject(await request(pathFor(ORDER_PATHS, kind), {
    method: 'POST',
    headers: visitorHeaders(),
    body: JSON.stringify(body)
  }))
}

export async function getMyOrders(kind, visitorId = ensureVisitorId()) {
  return expectList(await request(pathFor(MY_ORDER_PATHS, kind), { headers: visitorHeaders(visitorId) }))
}
