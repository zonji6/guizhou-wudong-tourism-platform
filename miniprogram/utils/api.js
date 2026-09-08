function request(path, method = 'GET', data = {}) {
  const config = getApp().globalData
  return new Promise((resolve, reject) => wx.request({
    url: config.apiBase + path,
    method,
    data,
    header: { 'content-type': 'application/json' },
    success(response) {
      const payload = response.data
      if (response.statusCode >= 200 && response.statusCode < 300 && payload?.success) resolve(payload.data)
      else reject(new Error(payload?.message || '本机服务暂不可用'))
    },
    fail: reject
  }))
}
module.exports = { request }
