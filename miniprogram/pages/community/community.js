const { request } = require('../../utils/api')
const { authState, userOptions } = require('../../utils/auth')

Page({
  data: { posts: [], places: [], postTypes: ['日常记录', '示意路线攻略'], loggedIn: false, formOpen: false, postType: 'MOMENT', title: '', content: '', tags: '', routeSummary: '', placeId: '', loading: false, error: '', message: '' },
  onShow() { this.getTabBar()?.setData({ selected: 3 }); this.setData({ loggedIn: Boolean(authState().account) }); this.load() },
  load() {
    this.setData({ loading: true, error: '' })
    Promise.all([request('/api/posts'), request('/api/places')]).then(([posts, map]) => this.setData({ posts: posts.map(post => ({ ...post, tagText: (post.tags || []).join(' · ') })), places: map.places || [] })).catch(reason => this.setData({ error: reason?.message || '寨里内容暂时无法读取。' })).finally(() => this.setData({ loading: false }))
  },
  toggleForm() { this.setData({ formOpen: !this.data.formOpen, message: '' }) },
  setType(event) { this.setData({ postType: event.detail.value === '1' ? 'ROUTE_GUIDE' : 'MOMENT' }) },
  input(event) { this.setData({ [event.currentTarget.dataset.field]: event.detail.value }) },
  placeChange(event) { const place = this.data.places[Number(event.detail.value)]; this.setData({ placeId: place?.id || '' }) },
  submit() {
    if (!authState().account) { this.setData({ message: '请先到“我的”登录平台账号。' }); return }
    const tags = this.data.tags.split(/[,，]/).map(tag => tag.trim()).filter(Boolean)
    const data = this.data.postType === 'MOMENT'
      ? { postType: 'MOMENT', content: this.data.content.trim(), tags }
      : { postType: 'ROUTE_GUIDE', title: this.data.title.trim(), content: this.data.content.trim(), tags, routeSummary: this.data.routeSummary.trim(), routeNodes: [{ sequence: 1, placeId: this.data.placeId, note: null }] }
    this.setData({ loading: true, message: '' })
    request('/api/posts', userOptions({ method: 'POST', data })).then(() => { this.setData({ formOpen: false, title: '', content: '', tags: '', routeSummary: '', placeId: '', message: '已发布到寨里。' }); this.load() }).catch(reason => this.setData({ message: reason?.message || '发布失败。', loading: false }))
  },
  toProfile() { wx.switchTab({ url: '/pages/profile/profile' }) }
})
