Page({ data: { orders: [] }, onShow() { this.setData({ orders: getApp().globalData.orders }) } })
