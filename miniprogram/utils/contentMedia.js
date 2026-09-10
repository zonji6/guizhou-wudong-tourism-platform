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

const PLACE_IMAGES = {
  '43000000-0000-0000-0000-000000000002': 192,
  '43000000-0000-0000-0000-000000000101': 204,
  '43000000-0000-0000-0000-000000000102': 213,
  '43000000-0000-0000-0000-000000000103': 219,
  '43000000-0000-0000-0000-000000000104': 223,
  '43000000-0000-0000-0000-000000000105': 208
}

const ROUTE_GUIDE_IMAGES = {
  '44000000-0000-0000-0000-000000000101': 203,
  '44000000-0000-0000-0000-000000000102': 204,
  '44000000-0000-0000-0000-000000000103': 216
}

const CULTURE_ARTICLE_IMAGES = {
  '45000000-0000-0000-0000-000000000101': 193,
  '45000000-0000-0000-0000-000000000102': 194,
  '45000000-0000-0000-0000-000000000103': 196,
  '45000000-0000-0000-0000-000000000104': 201
}

function contentImageNumber(value) {
  if (typeof value !== 'string' || !value.trim()) return ''
  const filename = value.trim().split(/[?#]/, 1)[0].replace(/\\/g, '/').split('/').pop()
  const match = /^image(\d+)\.(?:png|jpe?g)$/i.exec(filename || '')
  if (!match) return null

  const imageNumber = Number(match[1])
  return Number.isSafeInteger(imageNumber) && imageNumber > 0 ? imageNumber : null
}

function contentMediaUrl(value, kind) {
  const allowed = ALLOWED_IMAGE_NUMBERS[kind]
  if (!allowed) return ''
  const imageNumber = contentImageNumber(value)
  if (!imageNumber) return ''
  if (!allowed.has(imageNumber)) return ''
  return `http://127.0.0.1:5174/images/wudong-content/image${imageNumber}.jpg`
}

function contentMediaNotice(value, kind) {
  const imageNumber = contentImageNumber(value)
  if (!imageNumber || !contentMediaUrl(value, kind)) return ''
  if (kind === 'product' && (imageNumber === 36 || imageNumber === 38)) return '茶品资料示意，不代表当前商品实物'
  return '原始资料参考图，公开使用范围待确认'
}

function cultureArticleMediaUrl(articleId) {
  if (typeof articleId !== 'string' || !articleId) return ''
  const imageNumber = CULTURE_ARTICLE_IMAGES[articleId]
  return imageNumber ? contentMediaUrl(`image${imageNumber}.jpg`, 'culture') : ''
}

function placeMediaUrl(placeId) {
  const imageNumber = PLACE_IMAGES[placeId]
  return imageNumber ? contentMediaUrl(`image${imageNumber}.jpg`, 'place') : ''
}

function postMediaUrl(postId) {
  const routeImageNumber = ROUTE_GUIDE_IMAGES[postId]
  if (routeImageNumber) return contentMediaUrl(`image${routeImageNumber}.jpg`, 'route')
  return cultureArticleMediaUrl(postId)
}

module.exports = { contentMediaNotice, contentMediaUrl, cultureArticleMediaUrl, placeMediaUrl, postMediaUrl }
