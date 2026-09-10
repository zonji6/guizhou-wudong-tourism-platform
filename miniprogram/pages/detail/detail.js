const { request } = require('../../utils/api')
const { contentMediaNotice, contentMediaUrl, placeMediaUrl, postMediaUrl } = require('../../utils/contentMedia')

const KINDS = {
  product: { path: '/api/products', label: '乌东好物' },
  stay: { path: '/api/stays', label: '山居住宿' },
  travel: { path: '/api/places', label: '寨中地点' },
  community: { path: '/api/posts', label: '文化手账' }
}

function priceView(item) {
  if (item?.demoPrice) return { amount: item.demoPrice.amount, note: item.demoPrice.simulationNote, kind: '演示价' }
  if (item?.referencePrice) return { amount: item.referencePrice.amount, note: `参考来源：${item.referencePrice.sourceTitle}`, kind: '参考价' }
  return null
}

function imageView(value, kind) {
  return { displayImageUrl: contentMediaUrl(value, kind), imageNote: contentMediaNotice(value, kind) }
}

function detailView(item, kind) {
  if (kind === 'community') {
    const displayImageUrl = postMediaUrl(item.id)
    return {
      ...item,
      displayImageUrl, imageNote: displayImageUrl ? '原始资料参考图，公开使用范围待确认' : '', displayName: item.title || '山里片刻',
      routeNodes: (item.routeNodes || []).map((node, index) => ({ ...node, viewKey: `${node.sequence}-${index}`, displayNote: node.note || (node.drawable ? '水彩示意节点' : '仅文字节点') }))
    }
  }
  if (kind === 'stay') {
    return {
      ...item,
      ...imageView(item.imageUrl, 'stay'),
      roomTypes: (item.roomTypes || []).map(room => ({ ...room, ...imageView(room.imageUrl, 'stay'), priceView: priceView(room) }))
    }
  }
  if (kind === 'travel') {
    const displayImageUrl = placeMediaUrl(item.id)
    return { ...item, displayImageUrl, imageNote: displayImageUrl ? '原始资料参考图，公开使用范围待确认' : '', displayName: item.name }
  }
  return { ...item, ...imageView(item.imageUrl, 'product'), priceView: priceView(item), displayName: item.name }
}

Page({
  data: { item: null, kind: '', kindLabel: '', loading: false, error: '', imageFailed: false },
  onLoad(options) {
    const kind = options.kind
    const id = options.id
    const config = KINDS[kind]
    if (!config || !id) { this.setData({ error: '这条内容暂时无法打开。' }); return }
    this.setData({ kind, kindLabel: config.label, loading: true })
    request(`${config.path}/${encodeURIComponent(id)}`).then(item => {
      if (item?.id !== id) throw new Error('读取到的内容与当前选择不一致。')
      this.setData({ item: detailView(item, kind) })
    }).catch(reason => this.setData({ error: reason?.message || '详情暂时无法读取。' })).finally(() => this.setData({ loading: false }))
  },
  imageError() { this.setData({ imageFailed: true }) },
  checkoutProduct() {
    const resource = this.data.item
    if (!resource?.orderable) return
    getApp().globalData.checkoutSelection = { kind: 'product', title: resource.name, resource }
    wx.navigateTo({ url: '/pages/booking/booking' })
  },
  checkoutStay(event) {
    const property = this.data.item
    const resource = property?.roomTypes?.find(room => room.id === event.currentTarget.dataset.id)
    if (!resource?.orderable) return
    getApp().globalData.checkoutSelection = { kind: 'stay', title: `${property.name} · ${resource.name}`, property, resource }
    wx.navigateTo({ url: '/pages/booking/booking' })
  }
})
