const STATIC_ROUTES = new Map([
  ['/', { name: 'home' }],
  ['/explore', { name: 'explore', section: 'product' }],
  ['/explore/product', { name: 'explore', section: 'product' }],
  ['/explore/food', { name: 'explore', section: 'food' }],
  ['/explore/stay', { name: 'explore', section: 'stay' }],
  ['/explore/travel', { name: 'explore', section: 'travel' }],
  ['/explore/community', { name: 'explore', section: 'community' }],
  ['/community', { name: 'explore', section: 'community' }],
  ['/guide', { name: 'guide' }],
  ['/my', { name: 'my' }],
  ['/checkout', { name: 'checkout' }],
  ['/admin', { name: 'admin' }]
])

export function parseHashRoute(hash = location.hash) {
  const raw = hash.startsWith('#') ? hash.slice(1) || '/' : hash || '/'
  const [path, rawQuery = ''] = raw.split('?', 2)
  const query = new URLSearchParams(rawQuery)
  const focusFoodId = query.get('foodId') || ''
  const focusProductId = query.get('productId') || ''
  const focusRoomId = query.get('roomId') || ''
  const focusPlaceId = query.get('placeId') || ''
  const allowedKeys = new Set(['foodId', 'productId', 'roomId', 'placeId'])
  if ([...query.keys()].some(key => !allowedKeys.has(key))) return { name: 'not-found', path: raw }
  const focused = [[focusFoodId, '/explore/food'], [focusProductId, '/explore/product'], [focusRoomId, '/explore/stay'], [focusPlaceId, '/explore/travel']]
  if (focused.filter(([id]) => id).length > 1 || focused.some(([id, expectedPath]) => id && path !== expectedPath)) return { name: 'not-found', path: raw }
  return STATIC_ROUTES.has(path)
    ? { ...STATIC_ROUTES.get(path), path: raw, focusFoodId, focusProductId, focusRoomId, focusPlaceId }
    : { name: 'not-found', path: raw }
}
