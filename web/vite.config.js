import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

function onlyAnonymousCookies(cookie = '') {
  return cookie
    .split(';')
    .map(value => value.trim())
    .filter(value => /^WD_ANON_(?:LANE|[0-9a-f]{32})=/.test(value))
    .join('; ')
}

function configureAiProxy(proxy, anonymous) {
  proxy.on('proxyReqWs', (proxyRequest, request, socket) => {
    if (request.headers.origin !== 'http://127.0.0.1:5174') {
      proxyRequest.destroy()
      socket.write('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n')
      socket.destroy()
      return
    }
    const incomingCookie = String(proxyRequest.getHeader('cookie') || '')
    proxyRequest.removeHeader('cookie')
    if (anonymous) {
      const cookie = onlyAnonymousCookies(incomingCookie)
      if (cookie) proxyRequest.setHeader('cookie', cookie)
    }
  })
}

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 5174,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: false
      },
      '/ai/ws/user': {
        target: 'ws://127.0.0.1:8000',
        ws: true,
        changeOrigin: false,
        rewrite: () => '/ws/web/user',
        configure: proxy => configureAiProxy(proxy, false)
      },
      '/ai/ws/anonymous': {
        target: 'ws://127.0.0.1:8000',
        ws: true,
        changeOrigin: false,
        rewrite: () => '/ws/web/anonymous',
        configure: proxy => configureAiProxy(proxy, true)
      }
    }
  }
})
