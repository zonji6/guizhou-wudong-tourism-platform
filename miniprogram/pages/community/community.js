const { request } = require('../../utils/api')
const { authState, userOptions } = require('../../utils/auth')
const { postMediaUrl } = require('../../utils/contentMedia')
function postView(post) {
  const content = String(post.content || '')
  const characters = Array.from(content)
  const displayCoverUrl = postMediaUrl(post.id)
  return {
    ...post,
    kindLabel: post.postType === 'ROUTE_GUIDE' ? '示意路线攻略' : post.legacyData === true ? '文化文章' : '寨里动态',
    tagText: (post.tags || []).join(' · '),
    excerpt: `${characters.slice(0, 90).join('')}${characters.length > 90 ? '…' : ''}`,
    displayCoverUrl,
    imageNote: displayCoverUrl ? '原始资料参考图，公开使用范围待确认' : '',
    routeNodes: (post.routeNodes || []).map((node, index) => ({ ...node, viewKey: `${node.sequence}-${index}` }))
  }
}

function routePlaceViews(places, selectedIds) {
  return (places || []).map(place => ({ ...place, routeOrder: selectedIds.indexOf(place.id) + 1 }))
}

Page({
  data: { posts: [], places: [], routePlaces: [], routePlaceIds: [], postTypes: ['日常记录', '示意路线攻略'], loggedIn: false, formOpen: false, postType: 'MOMENT', title: '', content: '', tags: '', routeSummary: '', loading: false, error: '', message: '' },
  onShow() { this.getTabBar()?.setData({ selected: 3 }); this.setData({ loggedIn: Boolean(authState().account) }); this.load() },
  load() {
    this.setData({ loading: true, error: '' })
    Promise.all([request('/api/posts'), request('/api/places')]).then(([posts, map]) => {
      const places = map.places || []
      this.setData({ posts: posts.map(postView), places, routePlaces: routePlaceViews(places, this.data.routePlaceIds) })
    }).catch(reason => this.setData({ error: reason?.message || '寨里内容暂时无法读取。' })).finally(() => this.setData({ loading: false }))
  },
  toggleForm() { this.setData({ formOpen: !this.data.formOpen, message: '' }) },
  setType(event) { this.setData({ postType: event.detail.value === '1' ? 'ROUTE_GUIDE' : 'MOMENT' }) },
  input(event) { this.setData({ [event.currentTarget.dataset.field]: event.detail.value }) },
  toggleRoutePlace(event) {
    const placeId = event.currentTarget.dataset.id
    const ids = this.data.routePlaceIds.slice()
    const index = ids.indexOf(placeId)
    if (index >= 0) ids.splice(index, 1)
    else if (ids.length < 12) ids.push(placeId)
    else { this.setData({ message: '一篇示意路线攻略最多选择 12 个公开地点。' }); return }
    this.setData({ routePlaceIds: ids, routePlaces: routePlaceViews(this.data.places, ids), message: '' })
  },
  submit() {
    if (!authState().account) { this.setData({ message: '请先到“我的”登录平台账号。' }); return }
    if (this.data.postType === 'ROUTE_GUIDE' && !this.data.routePlaceIds.length) { this.setData({ message: '请至少选择一个公开地点作为示意路线节点。' }); return }
    const tags = this.data.tags.split(/[,，]/).map(tag => tag.trim()).filter(Boolean)
    const data = this.data.postType === 'MOMENT'
      ? { postType: 'MOMENT', content: this.data.content.trim(), tags }
      : { postType: 'ROUTE_GUIDE', title: this.data.title.trim(), content: this.data.content.trim(), tags, routeSummary: this.data.routeSummary.trim(), routeNodes: this.data.routePlaceIds.map((placeId, index) => ({ sequence: index + 1, placeId, note: null })) }
    this.setData({ loading: true, message: '' })
    request('/api/posts', userOptions({ method: 'POST', data })).then(() => { this.setData({ formOpen: false, title: '', content: '', tags: '', routeSummary: '', routePlaceIds: [], routePlaces: routePlaceViews(this.data.places, []), message: '已发布到寨里。' }); this.load() }).catch(reason => this.setData({ message: reason?.message || '发布失败。', loading: false }))
  },
  openPost(event) { wx.navigateTo({ url: `/pages/detail/detail?kind=community&id=${encodeURIComponent(event.currentTarget.dataset.id)}` }) },
  toProfile() { wx.switchTab({ url: '/pages/profile/profile' }) }
})
