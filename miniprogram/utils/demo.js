const services = [
  { id: 1, category: 'culture', title: '苗寨古茶园 · 制茶品茗体验', price: 168, image: 'https://images.unsplash.com/photo-1517856497829-3047e3fffae1?auto=format&fit=crop&w=900&q=80', tags: ['苗族文化', '手作制茶', '半日'], intro: '走进云雾茶园，跟着寨中茶师采青、杀青、品一盏春茶。' },
  { id: 2, category: 'stay', title: '山雾木屋民宿 · 观景双床房', price: 328, image: 'https://images.unsplash.com/photo-1520984032042-162d526883e0?auto=format&fit=crop&w=900&q=80', tags: ['山景', '含早餐', '苗寨'], intro: '推窗见山，夜晚在火塘边听山风与故事。' },
  { id: 3, category: 'food', title: '长桌宴 · 苗家酸汤与糯米饭', price: 88, image: 'https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=900&q=80', tags: ['长桌宴', '在地风味', '可拼桌'], intro: '把山野风味端上长桌，体验苗家待客的热闹与温度。' },
  { id: 4, category: 'travel', title: '乌冬山谷 · 茶园慢行接驳', price: 49, image: 'https://images.unsplash.com/photo-1473445361085-b9a07f55608b?auto=format&fit=crop&w=900&q=80', tags: ['接驳', '山谷慢行', '预约制'], intro: '连接村口、茶园与体验点，留出看山、拍照和呼吸的时间。' },
  { id: 5, category: 'culture', title: '苗绣纹样 · 手作体验课', price: 128, image: 'https://images.unsplash.com/photo-1594140225556-fb0ae6c49813?auto=format&fit=crop&w=900&q=80', tags: ['苗绣', '非遗体验', '亲子'], intro: '认识纹样里的祝福，在绣娘带领下完成一枚专属布贴。' }
]
const posts = [{ id: 1, author: '山里喝茶的人', title: '在乌冬，喝到一杯有山雾味道的茶', image: services[0].image, text: '茶师说，慢一点，才能听见叶子在锅里的声音。' }, { id: 2, author: '阿苗的旅行册', title: '苗寨长桌宴的正确打开方式', image: services[2].image, text: '别急着拍照，先和身边的人碰一碗米酒。' }]
module.exports = { services, posts }
