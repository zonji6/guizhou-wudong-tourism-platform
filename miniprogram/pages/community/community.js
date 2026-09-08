const { posts } = require('../../utils/demo')
const { request } = require('../../utils/api')
Page({ data: { posts }, onLoad() { request('/api/posts').then(items => this.setData({ posts: items.map(item => ({ ...item, author:item.authorName || item.author, image:item.coverUrl || item.image, text:item.content || item.text })) })).catch(() => {}) } })
