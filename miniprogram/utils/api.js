const config = getApp().globalData
function request(path, method = 'GET', data = {}) {
  return new Promise((resolve, reject) => wx.request({ url: config.apiBase + path, method, data, success: resolve, fail: reject }))
}
module.exports = { request }
