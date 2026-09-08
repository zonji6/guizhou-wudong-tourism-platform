const { services } = require('../../utils/demo')
const { request } = require('../../utils/api')
Page({ data: { item: services[0] }, onLoad(options) { const fallback = services.find(item => item.id === Number(options.id)) || services[0]; this.setData({ item: fallback }); request('/api/services/' + options.id).then(item => this.setData({ item: normalize(item) })).catch(() => {}) }, booking() { wx.navigateTo({ url: '/pages/booking/booking?id=' + this.data.item.id }) } })
function normalize(item) { return { ...item, title:item.name || item.title, image:item.imageUrl || item.image, intro:item.description || item.intro, tags:item.tags || [] } }
