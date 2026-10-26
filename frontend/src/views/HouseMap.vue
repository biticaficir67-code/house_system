<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <div class="page-head">
      <h2>房源地图</h2>
      <el-button @click="goHouse">返回房源管理</el-button>
    </div>
    <div class="box1 map-box">
      <!-- 整页地图可按区域、状态筛选 -->
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="区域">
          <el-select v-model="searchForm.district" clearable placeholder="全部" style="width:140px" @change="drawMap">
            <el-option v-for="d in districts" :key="d" :label="d" :value="d"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部" style="width:140px" @change="drawMap">
            <el-option label="在售" value="ON_SALE"></el-option>
            <el-option label="下架" value="OFF"></el-option>
            <el-option label="成交" value="DEAL"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="drawMap">查询</el-button>
        </el-form-item>
        <span class="map-tip">已标注 {{ pinCount }} 个小区，共 {{ houseCount }} 套房源</span>
      </el-form>
      <div id="fullHouseMap" class="map-canvas"></div>
    </div>
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
// 小区位置全图，可按区域和状态筛选
export default {
  name: 'HouseMap',
  components: { NavMain },
  data() {
    return {
      map: null,
      layer: null,
      allHouses: [],
      searchForm: { district: '', status: '' },
      pinCount: 0,
      houseCount: 0
    }
  },
  computed: {
    districts() {
      var set = {}
      this.allHouses.forEach(function (h) {
        if (h.district) set[h.district] = 1
      })
      return Object.keys(set).sort()
    }
  },
  created() {
    this.loadHouses()
  },
  mounted() {
    this.initMap()
  },
  beforeUnmount() {
    if (this.map) {
      this.map.remove()
      this.map = null
    }
  },
  methods: {
    errMsg(err) {
      if (err.response && err.response.data && err.response.data.message) return err.response.data.message
      return '请求失败'
    },
    // 返回房源管理
    goHouse() {
      this.$router.push('/house')
    },
    // 把分页房源全部拉齐再打点
    loadPages(url) {
      var that = this
      return this.$axios.get(url, { params: { page: 1, size: 100 } }).then(function (res) {
        var d = res.data.data || {}
        var records = d.records || []
        var total = d.total || 0
        var pages = Math.ceil(total / 100) || 1
        var reqs = []
        var i
        for (i = 2; i <= pages; i++) {
          reqs.push(that.$axios.get(url, { params: { page: i, size: 100 } }))
        }
        if (!reqs.length) return records
        return Promise.all(reqs).then(function (rest) {
          rest.forEach(function (r) {
            var more = (r.data.data && r.data.data.records) || []
            records = records.concat(more)
          })
          return records
        })
      })
    },
    // 拉齐全部房源后再画点
    loadHouses() {
      this.loadPages('/houses').then((list) => {
        this.allHouses = list || []
        this.drawMap()
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    // 底图用高德路网，和房源经纬度同一套坐标
    initMap() {
      if (this.map) return
      this.map = L.map('fullHouseMap', {
        center: [30.27, 120.15],
        zoom: 11,
        zoomControl: true
      })
      L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', {
        subdomains: ['1', '2', '3', '4'],
        maxZoom: 18,
        attribution: '高德地图'
      }).addTo(this.map)
      this.layer = L.layerGroup().addTo(this.map)
      var map = this.map
      this.$nextTick(function () {
        map.invalidateSize()
      })
      this.drawMap()
    },
    // 去掉没有坐标的，再按区域和状态筛
    filteredHouses() {
      var district = this.searchForm.district
      var status = this.searchForm.status
      return this.allHouses.filter(function (h) {
        var lng = Number(h.longitude)
        var lat = Number(h.latitude)
        if (!lng || !lat) return false
        if (district && h.district !== district) return false
        if (status && h.status !== status) return false
        return true
      })
    },
    // 一个小区只标一个点，提示套数
    groupByCommunity(list) {
      var map = {}
      list.forEach(function (h) {
        var key = (h.communityName || '未命名小区') + '|' + (h.district || '')
        if (!map[key]) {
          map[key] = {
            name: h.communityName || '未命名小区',
            district: h.district || '',
            lngSum: 0,
            latSum: 0,
            houses: []
          }
        }
        map[key].lngSum += Number(h.longitude)
        map[key].latSum += Number(h.latitude)
        map[key].houses.push(h)
      })
      return Object.keys(map).map(function (k) {
        var g = map[k]
        var n = g.houses.length
        g.lng = g.lngSum / n
        g.lat = g.latSum / n
        return g
      })
    },
    // 有在售的小区用主色
    pinColor(g) {
      var on = 0
      g.houses.forEach(function (h) {
        if (h.status === 'ON_SALE') on++
      })
      return on > 0 ? '#B57920' : '#96A3BF'
    },
    // 按筛选条件重画圆点
    drawMap() {
      if (!this.map || !this.layer) return
      this.layer.clearLayers()
      var list = this.filteredHouses()
      this.houseCount = list.length
      var groups = this.groupByCommunity(list)
      this.pinCount = groups.length
      var that = this
      groups.forEach(function (g) {
        var n = g.houses.length
        var r = n > 4 ? 14 : (n > 1 ? 11 : 8)
        var marker = L.circleMarker([g.lat, g.lng], {
          radius: r,
          color: '#ffffff',
          weight: 2,
          fillColor: that.pinColor(g),
          fillOpacity: 0.92
        })
        const tooltip = document.createElement('span')
        tooltip.textContent = g.name + '（' + n + '套）'
        marker.bindTooltip(tooltip, { direction: 'top', offset: [0, -8] })
        that.layer.addLayer(marker)
      })
      if (groups.length) {
        var pts = groups.map(function (g) {
          return [g.lat, g.lng]
        })
        this.map.fitBounds(pts, { padding: [30, 30], maxZoom: 14 })
      }
    }
  }
}
</script>
<style>
.map-box {
  padding-bottom: 16px;
}
.map-canvas {
  height: calc(100vh - 220px);
  min-height: 560px;
  border: 1px solid #c6c6c7;
  border-radius: 4px;
  margin-top: 8px;
}
.map-tip {
  color: #96a3bf;
  font-size: 13px;
  margin-left: 8px;
}
</style>
