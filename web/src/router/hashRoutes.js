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
  const path = hash.startsWith('#') ? hash.slice(1) || '/' : hash || '/'
  if (path.includes('?')) return { name: 'not-found', path }
  return STATIC_ROUTES.has(path) ? { ...STATIC_ROUTES.get(path), path } : { name: 'not-found', path }
}
