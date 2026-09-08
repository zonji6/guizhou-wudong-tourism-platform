const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const STATIC_ROUTES = new Map([
  ['/', { name: 'home' }],
  ['/community', { name: 'community' }],
  ['/assistant', { name: 'assistant' }],
  ['/orders', { name: 'orders' }],
  ['/admin', { name: 'admin' }]
])

const PLURAL_KIND = { products: 'product', foods: 'food', stays: 'stay' }

function decodedUuid(value) {
  try {
    const decoded = decodeURIComponent(value)
    return UUID_PATTERN.test(decoded) ? decoded : ''
  } catch (_) {
    return ''
  }
}

export function parseHashRoute(hash = location.hash) {
  const path = hash.startsWith('#') ? hash.slice(1) || '/' : hash || '/'
  if (path.includes('?')) return { name: 'not-found', path }
  if (STATIC_ROUTES.has(path)) return { ...STATIC_ROUTES.get(path), path }
  if (path === '/resources') return { name: 'catalog', kind: 'product', path }

  let match = path.match(/^\/resources\/(products|foods|stays)$/)
  if (match) return { name: 'catalog', kind: PLURAL_KIND[match[1]], path }

  match = path.match(/^\/(products|foods|stays)\/([^/]+)$/)
  if (match) {
    const id = decodedUuid(match[2])
    return id ? { name: 'detail', kind: PLURAL_KIND[match[1]], id, path } : { name: 'not-found', path }
  }

  match = path.match(/^\/confirm\/(product|food|stay)\/([^/]+)$/)
  if (match) {
    const id = decodedUuid(match[2])
    return id ? { name: 'confirm', kind: match[1], id, path } : { name: 'not-found', path }
  }

  return { name: 'not-found', path }
}

export function catalogPath(kind) {
  const plural = { product: 'products', food: 'foods', stay: 'stays' }[kind]
  return plural ? `/resources/${plural}` : '/not-found'
}

export function detailPath(kind, id) {
  return `/${{ product: 'products', food: 'foods', stay: 'stays' }[kind]}/${encodeURIComponent(id)}`
}

export function confirmPath(kind, id) {
  return `/confirm/${kind}/${encodeURIComponent(id)}`
}
