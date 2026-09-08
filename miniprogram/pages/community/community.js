const { posts, normalizePost } = require('../../utils/demo')
const { request } = require('../../utils/api')

Page({
  data: { posts, imageErrors: {} },
  onShow() { this.getTabBar()?.setData({ selected: 3 }) },
  onLoad() { request('/api/posts').then(items => { if (Array.isArray(items) && items.length) this.setData({ posts: items.map(normalizePost) }) }).catch(() => {}) },
  imageError(event) { const id = event.currentTarget.dataset.id; this.setData({ imageErrors: { ...this.data.imageErrors, [id]: true } }) }
})
