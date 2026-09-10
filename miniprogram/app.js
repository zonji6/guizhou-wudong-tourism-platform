const { restoreAuthState } = require('./utils/auth')

App({
  globalData: {
    apiBase: 'http://127.0.0.1:8080',
    aiWsBase: 'ws://127.0.0.1:8002',
    resourceCategory: 'product',
    checkoutSelection: null,
    auth: null,
    anonymous: null
  },
  onLaunch() {
    restoreAuthState(this.globalData)
  }
})
