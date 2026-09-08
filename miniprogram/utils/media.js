const asset = name => `/assets/wudong-local/${name}.jpg`

const media = {
  mountain: asset('mountain'), water: asset('water'), village: asset('village'),
  villageAerial: asset('village-aerial'), tea: asset('tea'), people: asset('people'),
  creek: asset('creek'), labor: asset('labor')
}

module.exports = { media }
