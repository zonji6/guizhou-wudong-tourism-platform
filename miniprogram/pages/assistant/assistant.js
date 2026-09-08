const allowed = ['itinerary', 'service_recommendation', 'knowledge_answer', 'clarifying_question', 'pending_booking', 'error']
Page({
  data: { input: '我想体验贵州乌冬的苗族文化和茶旅', stage: '', card: null },
  change(e) { this.setData({ input: e.detail.value }) },
  quick(e) { this.setData({ input: e.currentTarget.dataset.text }); this.ask() },
  ask() {
    this.setData({ card: null, stage: '正在理解你的旅行心愿…' })
    setTimeout(() => this.setData({ stage: '正在检索贵州乌冬茶旅资料…' }), 550)
    setTimeout(() => this.setData({ stage: '正在匹配茶园、苗寨与民宿…' }), 1050)
    const fallback = setTimeout(() => {
      const asksDetails = !/(\d+).{0,4}(人|位)|周末|明天|日期/.test(this.data.input)
      const card = asksDetails ? { type:'clarifying_question', title:'为你把茶旅安排得刚刚好', text:'想先确认一下：计划哪天出发、几位同行，以及更偏好半日体验还是两天一夜慢游？', chips:['周末，两位', '两天一夜', '带孩子同行'] } : { type:'itinerary', title:'苗族文化茶旅 · 两天一夜', text:'从一盏山茶开始，走进乌冬的苗寨日常。', items:['Day 1｜古茶园采茶与制茶品茗', 'Day 1｜苗寨长桌宴与火塘夜话', 'Day 2｜苗绣纹样手作与山谷慢行'], sources:['《乌冬古茶园体验指南》', '《苗寨待客与长桌宴》'] }
      this.setData({ card: allowed.includes(card.type) ? card : { type:'error', title:'内容暂不可展示', text:'AI 返回了不支持的卡片类型。' }, stage:'方案已生成' })
    }, 1550)
    try {
      const socket = wx.connectSocket({ url: getApp().globalData.aiWsUrl })
      socket.onOpen(() => socket.send({ data: JSON.stringify({ thread_id:`mini-${Date.now()}`, user_text:this.data.input }) }))
      socket.onMessage(({ data }) => {
        let event
        try { event = JSON.parse(data) } catch (_) { return }
        const name = event.event || event.type
        if (name === 'node_started') this.setData({ stage:event.message || '正在规划乌冬行程…' })
        if (name === 'tool_finished') this.setData({ stage:event.message || '正在匹配贵州乌冬资源…' })
        if (name === 'card_ready') {
          const card = event.card || (event.data && event.data.card) || event.data
          if (card && card.type) { clearTimeout(fallback); this.setData({ card:allowed.includes(card.type) ? card : { type:'error', title:'内容暂不可展示', text:'AI 返回了不支持的卡片类型。' }, stage:'方案已生成' }) }
        }
        if (name === 'failed') { clearTimeout(fallback); this.setData({ card:{ type:'error', title:'AI 服务暂不可用', text:event.message || '已保留演示资源浏览与预约功能。' }, stage:'服务已降级' }) }
      })
      socket.onError(() => socket.close())
    } catch (_) {}
  },
  toBooking() { wx.navigateTo({ url:'/pages/booking/booking?id=1' }) }
})
