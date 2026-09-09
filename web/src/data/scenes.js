const localThumb = name => `/images/wudong-local/${name}-thumb.jpg`
const localLarge = name => `/images/wudong-local/${name}-large.jpg`

export const scenes = [
  { id: 'mountain', label: '山', src: localThumb('mountain'), expandedAsset: localLarge('mountain'), alt: '云雾覆盖的层叠山岭与山路行人', expandedAlt: '云雾覆盖的层叠山岭与山路行人', desktopFocus: '62% 55%', mobileFocus: '68% 58%', sourceStatus: 'local-review', story: '从湿润山色进入乌东的慢行节奏。', action: { label: '查看山野路线', path: '/resources', category: 'travel' } },
  { id: 'water', label: '水', src: localThumb('water'), expandedAsset: localLarge('water'), alt: '风雨桥、溪水与雾中村寨', expandedAlt: '风雨桥、溪水与雾中村寨', desktopFocus: '50% 55%', mobileFocus: '50% 58%', sourceStatus: 'local-review', story: '桥与水把山路接进村寨日常。', action: { label: '开始沿溪漫游', path: '/resources', category: 'travel' } },
  { id: 'village', label: '寨', src: localThumb('village'), expandedAsset: localLarge('village-aerial'), alt: '标有乌东苗寨名称的村寨全景', expandedAlt: '山谷中沿溪分布的木楼、道路与田地', desktopFocus: '50% 58%', mobileFocus: '50% 62%', sourceStatus: 'local-review', story: '沿木楼之间的路径理解一寨一谷。', action: { label: '查看村寨导览', path: '/resources', category: 'culture' } },
  { id: 'tea', label: '茶', src: localThumb('tea'), expandedAsset: localLarge('tea'), alt: '茶园里正在采茶的人', expandedAlt: '茶园里正在采茶的人', desktopFocus: '49% 50%', mobileFocus: '47% 52%', sourceStatus: 'portrait-review', story: '从采茶、制茶到递出一盏热茶。', action: { label: '进入茶旅体验', path: '/resources', category: 'culture' } },
  { id: 'people', label: '人', src: localThumb('people'), expandedAsset: localLarge('people'), alt: '门前相视而笑的一老一幼', expandedAlt: '门前相视而笑的一老一幼', desktopFocus: '61% 48%', mobileFocus: '60% 48%', sourceStatus: 'portrait-review', story: '劳作、待客与笑声组成寨里的温度。', action: { label: '看寨里人物故事', path: '/community' } }
]

export const journeySections = [
  { id: 'creek', eyebrow: '沿溪入寨', title: '水声把路引向木楼', body: '溪水绕过石头，树影落在水面。把脚步放慢一点，听听山里的声音。', image: localLarge('creek'), alt: '石间溪流与林木', sourceStatus: 'local-review', tone: 'cool' },
  { id: 'village', eyebrow: '一寨一谷', title: '屋舍顺着山谷生长', body: '木楼、田地和溪流挨在一起。走进这片山谷，也给一顿热饭、一晚山居留些时间。', image: localLarge('village-aerial'), alt: '山谷中沿溪分布的木楼与田地', sourceStatus: 'local-review', tone: 'neutral' },
  { id: 'people', eyebrow: '人在寨中', title: '山静，人也热烈', body: '田间有人忙着劳作，门前有人笑着说话。风景之外，这些平常时刻也值得记下来。', image: localLarge('labor'), alt: '田野里劳作的人', sourceStatus: 'portrait-review', tone: 'warm' }
]
