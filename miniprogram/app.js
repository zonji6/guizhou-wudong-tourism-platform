App({
  globalData: {
    apiBase: 'http://127.0.0.1:8080',
    aiWsUrl: 'ws://127.0.0.1:8000/ws/assistant',
    resourceCategory: null,
    currentItinerary: null,
    pendingBooking: null,
    assistantSession: { threadId: `mini-${Date.now()}`, messages: [], currentCard: null, previousCard: null },
    orders: [{ id: 'WD20260908001', name: '苗寨古茶园 · 制茶品茗体验', date: '2026-09-20', people: 2, status: 'PENDING_CONFIRMATION', demoData: true }]
  }
})
