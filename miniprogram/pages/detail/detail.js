const { request } = require('../../utils/api')

const PATHS = { product: '/api/products', food: '/api/foods', stay: '/api/room-types', travel: '/api/places' }

Page({
  data: { item: null, kind: '', loading: false, error: '', imageFailed: false },
  onLoad(options) {
    const kind = options.kind
    const id = options.id
    if (!PATHS[kind] || !id) { this.setData({ error: '详情链接缺少 v3 资源类型或标识。' }); return }
    this.setData({ kind, loading: true })
    request(`${PATHS[kind]}/${encodeURIComponent(id)}`).then(item => {
      if (item?.id !== id) throw new Error('详情与当前链接不一致。')
      this.setData({ item })
    }).catch(reason => this.setData({ error: reason?.message || '详情暂时无法读取。' })).finally(() => this.setData({ loading: false }))
  },
  imageError() { this.setData({ imageFailed: true }) }
})
