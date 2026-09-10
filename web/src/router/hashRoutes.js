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
  if ([...query.keys()].some(key => key !== 'foodId')) return { name: 'not-found', path: raw }
  if (focusFoodId && path !== '/explore/food') return { name: 'not-found', path: raw }
  return STATIC_ROUTES.has(path) ? { ...STATIC_ROUTES.get(path), path: raw, focusFoodId } : { name: 'not-found', path: raw }
}
