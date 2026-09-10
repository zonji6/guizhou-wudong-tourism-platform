export const categories = [
  { id: 'culture', label: '苗韵茶旅', icon: '☘' }, { id: 'stay', label: '暖居民宿', icon: '⌂' },
  { id: 'food', label: '寨味餐食', icon: '◌' }, { id: 'travel', label: '山野出行', icon: '⌁' }
]
const local = name => `/images/wudong-local/${name}-thumb.jpg`
const serviceId = number => `10000000-0000-0000-0000-00000000000${number}`
const missingImageAlt = category => ({ stay: '住宿服务实景暂缺', food: '餐食服务实景暂缺', culture: '苗绣手作服务实景暂缺', travel: '接驳服务实景暂缺' }[category] || '服务实景暂缺')
export const services = [
  { id: serviceId(1), category: 'culture', title: '苗寨古茶园 · 制茶品茗体验', price: 168, image: local('tea'), imageAlt: '身着民族服饰的姑娘们在山间茶园俯身采茶', tags: ['苗族文化', '手作制茶', '半日'], intro: '走进云雾茶园，跟着寨中茶师采青、杀青、品一盏春茶。', demoData: true },
  { id: serviceId(2), category: 'culture', title: '山茶待客 · 采茶体验', price: 128, image: local('tea'), imageAlt: '身着民族服饰的姑娘们在山间茶园俯身采茶', tags: ['采茶', '品茗', '半日'], intro: '循着茶香认识采茶、制茶和递茶的日常。', demoData: true },
  { id: serviceId(3), category: 'stay', title: '山雾木屋民宿 · 观景双床房', price: 328, image: '', imageAlt: missingImageAlt('stay'), tags: ['山景', '含早餐', '苗寨'], intro: '推窗见山，夜晚在火塘边听山风与故事。', demoData: true },
  { id: serviceId(4), category: 'food', title: '长桌宴 · 苗家酸汤与糯米饭', price: 88, image: '', imageAlt: missingImageAlt('food'), tags: ['长桌宴', '在地风味', '可拼桌'], intro: '把山野风味端上长桌，体验苗家待客的热闹与温度。', demoData: true },
  { id: serviceId(5), category: 'culture', title: '苗绣纹样 · 手作体验课', price: 128, image: '', imageAlt: missingImageAlt('culture'), tags: ['苗绣', '非遗体验', '亲子'], intro: '认识纹样里的祝福，在绣娘带领下完成一枚专属布贴。', demoData: true },
  { id: serviceId(6), category: 'travel', title: '乌东山谷 · 沿溪慢行接驳', price: 49, image: '', imageAlt: missingImageAlt('travel'), tags: ['接驳', '山谷慢行', '预约制'], intro: '连接村口、茶园与体验点，留出看山、拍照和呼吸的时间。', demoData: true }
]
export function normalizeService(item = {}) {
  const fallback = services.find(service => service.id === String(item.id))
  const category = item.category || fallback?.category || 'culture'
  const tags = Array.isArray(item.tags) ? item.tags : typeof item.tags === 'string' ? item.tags.split(',').map(tag => tag.trim()).filter(Boolean) : fallback?.tags || []
  const hasCompleteApiImage = Boolean(item.imageUrl && item.imageAlt)
  return { ...fallback, ...item, id: String(item.id || fallback?.id || ''), title: item.name || item.title || fallback?.title || '乌东体验', category, image: hasCompleteApiImage ? item.imageUrl : fallback?.image || '', imageAlt: hasCompleteApiImage ? item.imageAlt : fallback?.imageAlt || missingImageAlt(category), intro: item.description || item.intro || fallback?.intro || '', price: item.price ?? fallback?.price ?? 0, tags, demoData: item.demoData ?? fallback?.demoData ?? true }
}
export const posts = [
  { id: '20000000-0000-0000-0000-000000000001', author: '山里喝茶的人', title: '在乌东，喝到一杯有山雾味道的茶', cover: local('tea'), coverAlt: '身着民族服饰的姑娘们在山间茶园俯身采茶', text: '茶师说，慢一点，才能听见叶子在锅里的声音。', likes: 126, demoData: true },
  { id: '20000000-0000-0000-0000-000000000002', author: '阿苗的旅行册', title: '苗寨长桌宴的正确打开方式', cover: '', coverAlt: '寨里分享配图暂缺', text: '别急着拍照，先和身边的人碰一碗米酒。', likes: 88, demoData: true }
]
export const allowedCardTypes = ['itinerary', 'service_recommendation', 'knowledge_answer', 'clarifying_question', 'pending_booking', 'error']
export const demoAssistantCard = { type: 'itinerary', title: '贵州乌东苗族文化茶旅 · 两日建议', summary: '这是一份用于答辩操作的静态示例，请以服务方确认结果为准。', data: { demoMode: true, retrievalMode: 'keyword_demo', days: [{ day: 1, theme: '从茶叶走进村寨', items: [{ time: '上午', title: '苗族文化茶旅半日体验', summary: '示例节点', serviceId: services[0].id, demoData: true }, { time: '傍晚', title: '苗家长桌宴体验', summary: '示例节点', serviceId: services[3].id, demoData: true }] }, { day: 2, theme: '在寨里慢下来', items: [{ time: '上午', title: '苗绣香囊手作', summary: '示例节点', serviceId: services[4].id, demoData: true }] }], notice: '服务时间、价格与可预约情况请以服务方确认结果为准。' }, sources: [{ title: '贵州乌东苗族文化茶旅体验说明', document_id: '30000000-0000-0000-0000-000000000001' }] }
