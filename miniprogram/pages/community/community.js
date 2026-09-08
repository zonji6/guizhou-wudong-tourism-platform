const { posts } = require('../../utils/demo')
const { request } = require('../../utils/api')

const postById = posts.reduce((result, post) => ({ ...result, [post.id]: post }), {})
const normalizePost = item => {
  const id = String(item.id || '')
  const fallback = postById[id] || {}
  const apiCover = item.coverUrl && item.coverAlt ? item.coverUrl : ''
  return { ...fallback, ...item, id: id || fallback.id || '', author: item.authorName || item.author || fallback.author || '', cover: apiCover || fallback.cover || '', coverAlt: apiCover ? item.coverAlt : fallback.coverAlt || '', text: item.content || item.text || fallback.text || '' }
}

Page({
  data: { posts, imageErrors: {} },
  onShow() { this.getTabBar()?.setData({ selected: 3 }) },
  onLoad() { request('/api/posts').then(items => { if (Array.isArray(items) && items.length) this.setData({ posts: items.map(normalizePost) }) }).catch(() => {}) },
  imageError(event) { const id = event.currentTarget.dataset.id; this.setData({ imageErrors: { ...this.data.imageErrors, [id]: true } }) }
})
