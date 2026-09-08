const local = name => `/assets/wudong-local/${name}.jpg`

const services = [
  { id: '10000000-0000-0000-0000-000000000001', category: 'culture', categoryLabel: '茶旅', title: '苗寨古茶园 · 制茶品茗体验', price: 168, image: local('tea'), imageAlt: '茶园里正在采茶的人', tags: ['苗族文化', '手作制茶', '半日'], intro: '走进云雾茶园，跟着寨中茶师采青、杀青、品一盏春茶。', demoData: true },
  { id: '10000000-0000-0000-0000-000000000002', category: 'culture', categoryLabel: '茶旅', title: '山茶待客 · 采茶体验', price: 128, image: local('tea'), imageAlt: '茶园里正在采茶的人', tags: ['采茶', '品茗', '半日'], intro: '循着茶香认识采茶、制茶和递茶的日常。', demoData: true },
  { id: '10000000-0000-0000-0000-000000000003', category: 'stay', categoryLabel: '暖居民宿', title: '山雾木屋民宿 · 观景双床房', price: 328, image: '', imageAlt: '乌东暖居民宿实景暂缺', tags: ['山景', '含早餐', '苗寨'], intro: '推窗见山，夜晚在火塘边听山风与故事。', demoData: true },
  { id: '10000000-0000-0000-0000-000000000004', category: 'food', categoryLabel: '寨味餐食', title: '长桌宴 · 苗家酸汤与糯米饭', price: 88, image: '', imageAlt: '乌东寨味餐食实景暂缺', tags: ['长桌宴', '在地风味', '可拼桌'], intro: '把山野风味端上长桌，体验苗家待客的热闹与温度。', demoData: true },
  { id: '10000000-0000-0000-0000-000000000005', category: 'culture', categoryLabel: '苗韵茶旅', title: '苗绣纹样 · 手作体验课', price: 128, image: '', imageAlt: '乌东苗韵茶旅实景暂缺', tags: ['苗绣', '非遗体验', '亲子'], intro: '认识纹样里的祝福，在绣娘带领下完成一枚专属布贴。', demoData: true },
  { id: '10000000-0000-0000-0000-000000000006', category: 'travel', categoryLabel: '山野出行', title: '乌东山谷 · 沿溪慢行接驳', price: 49, image: '', imageAlt: '乌东山野出行实景暂缺', tags: ['接驳', '山谷慢行', '预约制'], intro: '连接村口、茶园与体验点，留出看山、拍照和呼吸的时间。', demoData: true }
]

const serviceById = services.reduce((result, item) => ({ ...result, [item.id]: item }), {})
const tagsOf = tags => Array.isArray(tags) ? tags : typeof tags === 'string' ? tags.split(',').map(tag => tag.trim()).filter(Boolean) : []

function normalizeService(item = {}) {
  const id = String(item.id || item.serviceId || '')
  const fallback = serviceById[id] || {}
  const apiImage = item.imageUrl && item.imageAlt ? item.imageUrl : ''
  return {
    ...fallback, ...item, id: id || fallback.id || '', title: item.name || item.title || fallback.title || '', categoryLabel: item.categoryLabel || fallback.categoryLabel || '服务',
    image: apiImage || fallback.image || '', imageAlt: apiImage ? item.imageAlt : fallback.imageAlt || '',
    tags: tagsOf(item.tags || fallback.tags), intro: item.description || item.intro || fallback.intro || '',
    demoData: typeof item.demoData === 'boolean' ? item.demoData : fallback.demoData === true
  }
}

const posts = [
  { id: '20000000-0000-0000-0000-000000000001', author: '山里喝茶的人', title: '在乌东，喝到一杯有山雾味道的茶', cover: local('tea'), coverAlt: '茶园里正在采茶的人', text: '茶师说，慢一点，才能听见叶子在锅里的声音。', likes: 126, demoData: true },
  { id: '20000000-0000-0000-0000-000000000002', author: '阿苗的旅行册', title: '苗寨长桌宴的正确打开方式', cover: '', coverAlt: '乌东寨味餐食分享配图暂缺', text: '别急着拍照，先和身边的人碰一碗米酒。', likes: 88, demoData: true }
]

const demoAssistantCard = {
  type: 'itinerary', title: '苗族文化茶旅 · 示例行程', summary: '这是用户主动开启的演示结果，请以服务方确认结果为准。',
  data: { demoMode: true, retrievalMode: 'keyword_demo', days: [
    { title: '第一天', items: [{ title: '古茶园采茶与制茶品茗', serviceId: services[0].id, demoData: true }] },
    { title: '第二天', items: [{ title: '山茶待客与品茗体验', serviceId: services[1].id, demoData: true }] }
  ] },
  notice: '演示模式：请以服务方确认结果为准。', sources: [{ title: '乌东古茶园体验指南', document_id: 'wudong-tea-guide-demo' }], demoData: true
}

module.exports = { services, posts, normalizeService, demoAssistantCard }
