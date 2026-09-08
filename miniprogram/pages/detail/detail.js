const { services, normalizeService } = require('../../utils/demo')
const { request } = require('../../utils/api')

function routeId(value) {
  return typeof value === 'string' ? value.trim() : ''
}

Page({
  data: {
    item: null,
    imageFailed: false
  },

  onLoad(options) {
    const requestedId = routeId(options.id)
    this.requestedId = requestedId
    const fallback = services.find(item => String(item.id) === requestedId) || null
    this.setService(fallback)
    if (!requestedId) return

    request(`/api/services/${encodeURIComponent(requestedId)}`)
      .then(item => {
        if (this.requestedId !== requestedId) return
        const normalized = normalizeService(item)
        if (String(normalized.id) !== requestedId) return
        this.setService(normalized)
      })
      .catch(() => {})
  },

  setService(item) {
    const previous = this.data.item
    const resourceChanged = String(previous?.id || '') !== String(item?.id || '') ||
      String(previous?.image || '') !== String(item?.image || '')
    this.setData({
      item,
      imageFailed: resourceChanged ? false : this.data.imageFailed
    })
  },

  imageError() {
    this.setData({ imageFailed: true })
  },

  booking() {
    const serviceId = typeof this.data.item?.id === 'string' ? this.data.item.id.trim() : ''
    if (!serviceId) return
    wx.navigateTo({ url: `/pages/booking/booking?id=${encodeURIComponent(serviceId)}` })
  }
})
