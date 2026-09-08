const local = name => `/assets/wudong-local/${name}.jpg`
const services = [
  { id: '7f378c5d-5a9e-4f24-8985-1c8e60508671', category: 'culture', title: '苗寨古茶园 · 制茶品茗体验', price: 168, image: local('tea'), imageAlt: '茶园里正在采茶的人', tags: ['苗族文化', '手作制茶', '半日'], intro: '走进云雾茶园，跟着寨中茶师采青、杀青、品一盏春茶。', demoData: true },
  { id: 'e3639c5b-bd47-4ef0-b0d5-bce4a67fc31f', category: 'stay', title: '山雾木屋民宿 · 观景双床房', price: 328, image: local('village'), imageAlt: '山谷中的木楼村寨', tags: ['山景', '含早餐', '苗寨'], intro: '推窗见山，夜晚在火塘边听山风与故事。', demoData: true },
  { id: '2f55fe41-644c-42f9-9d19-dcb9ff0a2fc3', category: 'food', title: '长桌宴 · 苗家酸汤与糯米饭', price: 88, image: local('people'), imageAlt: '门前相视而笑的一老一幼', tags: ['长桌宴', '在地风味', '可拼桌'], intro: '把山野风味端上长桌，体验苗家待客的热闹与温度。', demoData: true },
  { id: '998120e3-0dcf-44bb-aa31-9edca499a397', category: 'travel', title: '乌东山谷 · 沿溪慢行接驳', price: 49, image: local('water'), imageAlt: '风雨桥、溪水与雾中村寨', tags: ['接驳', '山谷慢行', '预约制'], intro: '连接村口、茶园与体验点，留出看山、拍照和呼吸的时间。', demoData: true }
]
const posts = [{ id: 'feae56d1-3789-49dc-b9ac-c50b5d665d4e', author: '山里喝茶的人', title: '在乌东，喝到一杯有山雾味道的茶', image: local('tea'), imageAlt: '茶园里正在采茶的人', text: '茶师说，慢一点，才能听见叶子在锅里的声音。', likes: 126, demoData: true }, { id: '8175084a-2a8c-4bcc-a9ee-b3d070db38d4', author: '阿苗的旅行册', title: '苗寨长桌宴的正确打开方式', image: local('people'), imageAlt: '门前相视而笑的一老一幼', text: '别急着拍照，先和身边的人碰一碗米酒。', likes: 88, demoData: true }]
function normalizeService(item) { return { ...item, id: String(item.id), title: item.name || item.title, image: item.imageUrl || item.image, imageAlt: item.imageAlt || item.title || '乌东服务实景', intro: item.description || item.intro, tags: item.tags || [] } }
module.exports = { services, posts, normalizeService }
