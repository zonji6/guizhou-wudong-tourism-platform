const ALLOWED_IMAGE_NUMBERS = {
  product: new Set([
    13, 14, 15, 16, 17, 18, 19, 20,
    22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
    36, 38
  ]),
  stay: new Set(Array.from({ length: 85 }, (_, index) => index + 39)),
  food: new Set(Array.from({ length: 68 }, (_, index) => index + 124)),
  place: new Set([192, 204, 208, 213, 219, 223]),
  route: new Set([203, 204, 216]),
  culture: new Set([193, 194, 196, 199, 201, 202])
}

const PLACE_IMAGES = new Map([
  ['43000000-0000-0000-0000-000000000002', 192],
  ['43000000-0000-0000-0000-000000000101', 204],
  ['43000000-0000-0000-0000-000000000102', 213],
  ['43000000-0000-0000-0000-000000000103', 219],
  ['43000000-0000-0000-0000-000000000104', 223],
  ['43000000-0000-0000-0000-000000000105', 208]
])

const ROUTE_GUIDE_IMAGES = new Map([
  ['44000000-0000-0000-0000-000000000101', 203],
  ['44000000-0000-0000-0000-000000000102', 204],
  ['44000000-0000-0000-0000-000000000103', 216]
])

const CULTURE_ARTICLE_IMAGES = new Map([
  ['45000000-0000-0000-0000-000000000101', 193],
  ['45000000-0000-0000-0000-000000000102', 194],
  ['45000000-0000-0000-0000-000000000103', 196],
  ['45000000-0000-0000-0000-000000000104', 201]
])

export function contentMediaUrl(value, kind) {
  if (typeof value !== 'string' || !value.trim()) return ''
  const filename = value.trim().split(/[?#]/, 1)[0].replaceAll('\\', '/').split('/').pop()
  const match = /^image(\d+)\.(?:png|jpe?g)$/i.exec(filename || '')
  if (!match) return ''

  const imageNumber = Number(match[1])
  if (!Number.isSafeInteger(imageNumber) || imageNumber < 1) return ''
  const allowed = ALLOWED_IMAGE_NUMBERS[kind]
  if (!allowed?.has(imageNumber)) return ''
  return `/images/wudong-content/image${imageNumber}.jpg`
}

export function cultureArticleMediaUrl(articleId) {
  const imageNumber = CULTURE_ARTICLE_IMAGES.get(articleId)
  return imageNumber ? contentMediaUrl(`image${imageNumber}.jpg`, 'culture') : ''
}

export function placeMediaUrl(placeId) {
  const imageNumber = PLACE_IMAGES.get(placeId)
  return imageNumber ? contentMediaUrl(`image${imageNumber}.jpg`, 'place') : ''
}

export function postMediaUrl(postId) {
  const routeImageNumber = ROUTE_GUIDE_IMAGES.get(postId)
  if (routeImageNumber) return contentMediaUrl(`image${routeImageNumber}.jpg`, 'route')
  return cultureArticleMediaUrl(postId)
}
