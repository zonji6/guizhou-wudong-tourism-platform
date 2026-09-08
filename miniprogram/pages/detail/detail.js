const { services } = require('../../utils/demo')
Page({ data: { item: services[0] }, onLoad(options) { this.setData({ item: services.find(item => item.id === Number(options.id)) || services[0] }) }, booking() { wx.navigateTo({ url: '/pages/booking/booking?id=' + this.data.item.id }) } })
