<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <div class="page-head">
      <h2>房源管理</h2>
      <div class="sub">左侧列表，右侧按小区标注位置</div>
    </div>
    <div class="box1">
      <!-- 按小区、区域筛选 -->
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="小区">
          <el-input v-model="searchForm.communityName"></el-input>
        </el-form-item>
        <el-form-item label="区域">
          <el-input v-model="searchForm.district"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button @click="search">查询</el-button>
          <el-button type="primary" @click="openAdd">新增房源</el-button>
          <el-button @click="importVisible = true">表格导入</el-button>
          <el-button @click="goMap">房源地图</el-button>
        </el-form-item>
      </el-form>
      <div class="house-split">
        <!-- 左侧列表：增删改查和文案标签 -->
        <div class="house-list">
          <el-table class="house-table" :data="tableData" stripe border max-height="560" highlight-current-row @row-click="onRowClick">
            <el-table-column prop="id" label="编号" width="70" align="center" header-align="center"></el-table-column>
            <el-table-column label="房源信息" min-width="200" align="center" header-align="center">
              <template #default="scope">
                <div class="house-name">{{ scope.row.communityName }}　{{ scope.row.district }}</div>
                <div class="house-sub">{{ roomText(scope.row) }} / {{ scope.row.buildingAreaSqm }}㎡ / {{ priceWan(scope.row.listingPriceYuan) }}万</div>
                <div class="tag-row">
                  <span class="tag" v-for="(t, i) in scope.row.advantages" :key="'a'+i">{{ t }}</span>
                  <span class="tag tag-warn" v-for="(t, i) in scope.row.disadvantages" :key="'d'+i">{{ t }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center" header-align="center">
              <template #default="scope">
                <span :class="statusClass(scope.row.status)">{{ statusText(scope.row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="250" align="center" header-align="center">
              <template #default="scope">
                <div class="ops">
                  <el-button size="small" type="primary" @click.stop="openDetail(scope.row)">详情</el-button>
                  <el-button size="small" type="warning" @click.stop="openEdit(scope.row)">修改</el-button>
                  <el-button size="small" type="warning" v-if="isAdmin" @click.stop="verify(scope.row)">核验</el-button>
                  <el-button size="small" type="info" v-if="scope.row.status === 'ON_SALE'" @click.stop="off(scope.row)">下架</el-button>
                  <el-button size="small" type="success" v-else-if="scope.row.status === 'OFF'" @click.stop="onSale(scope.row)">上架</el-button>
                  <el-button size="small" plain type="primary" @click.stop="goPrice(scope.row)">价格建议</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="house-page"
            style="margin-top:12px"
            layout="prev, pager, next"
            :page-size="size"
            :current-page="page"
            :total="total"
            @current-change="pageChange">
          </el-pagination>
        </div>
        <!-- 右侧小区地图 -->
        <div class="house-map-side">
          <div class="map-cap">{{ mapCap }}</div>
          <div id="houseMap" class="map-canvas"></div>
        </div>
      </div>
    </div>

    <!-- 新增或修改房源，小区失焦后自动认位置 -->
    <el-dialog :title="formTitle" v-model="formVisible" width="640px">
      <el-form ref="form" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="门店" prop="storeId">
          <el-select v-model="form.storeId" placeholder="请选择门店" style="width:100%" @change="onStoreChange">
            <el-option v-for="s in stores" :key="s.id" :label="s.name" :value="s.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="经纪人">
          <el-select v-model="form.brokerId" placeholder="可空" clearable style="width:100%">
            <el-option v-for="b in brokers" :key="b.id" :label="b.brokerName || b.name" :value="b.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="城市" prop="city">
          <el-input v-model="form.city" @blur="fillLocation"></el-input>
        </el-form-item>
        <el-form-item label="区域" prop="district">
          <el-input v-model="form.district" @blur="fillLocation"></el-input>
        </el-form-item>
        <el-form-item label="小区名称" prop="communityName">
          <el-input v-model="form.communityName" @blur="fillLocation"></el-input>
          <div class="loc-hint">{{ locHint }}</div>
        </el-form-item>
        <el-form-item label="面积㎡" prop="buildingAreaSqm">
          <el-input v-model="form.buildingAreaSqm"></el-input>
        </el-form-item>
        <el-form-item label="室">
          <el-input v-model="form.rooms"></el-input>
        </el-form-item>
        <el-form-item label="厅">
          <el-input v-model="form.halls"></el-input>
        </el-form-item>
        <el-form-item label="卫">
          <el-input v-model="form.bathrooms"></el-input>
        </el-form-item>
        <el-form-item label="楼层档" prop="floorLevel">
          <el-select v-model="form.floorLevel">
            <el-option label="低" value="LOW"></el-option>
            <el-option label="中" value="MIDDLE"></el-option>
            <el-option label="高" value="HIGH"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="总楼层" prop="totalFloors">
          <el-input v-model="form.totalFloors"></el-input>
        </el-form-item>
        <el-form-item label="朝向" prop="orientation">
          <el-select v-model="form.orientation" multiple>
            <el-option label="东" value="E"></el-option>
            <el-option label="南" value="S"></el-option>
            <el-option label="西" value="W"></el-option>
            <el-option label="北" value="N"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="装修" prop="decoration">
          <el-select v-model="form.decoration">
            <el-option label="毛坯" value="ROUGH"></el-option>
            <el-option label="简装" value="SIMPLE"></el-option>
            <el-option label="精装" value="FINE"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="用途" prop="propertyUsage">
          <el-select v-model="form.propertyUsage" style="width:100%">
            <el-option label="普通住宅" value="RESIDENTIAL"></el-option>
            <el-option label="商住" value="MIXED_USE"></el-option>
            <el-option label="别墅" value="VILLA"></el-option>
            <el-option label="车库" value="GARAGE"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="挂牌价(元)" prop="listingPriceYuan">
          <el-input v-model="form.listingPriceYuan"></el-input>
        </el-form-item>
        <el-form-item label="挂牌日期"><el-date-picker v-model="form.listingDate" type="date" value-format="YYYY-MM-DD" placeholder="选择挂牌日期" /></el-form-item>
        <el-form-item label="电梯"><el-select v-model="form.hasElevator" clearable placeholder="未知"><el-option label="有电梯" :value="true" /><el-option label="无电梯" :value="false" /></el-select></el-form-item>
        <el-form-item label="核心卖点">
          <el-input type="textarea" v-model="form.coreSelling" rows="2"></el-input>
        </el-form-item>
        <el-form-item label="小区介绍">
          <el-input type="textarea" v-model="form.communityIntro" rows="2"></el-input>
        </el-form-item>
        <el-form-item label="周边配套">
          <el-input type="textarea" v-model="form.surrounding" rows="2"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveForm">确定</el-button>
          <el-button @click="formVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- 详情里展示入库后的文案智能分析结果 -->
    <el-dialog title="房源详情" v-model="detailVisible" width="70%">
      <div v-if="readiness && current.status !== 'DEAL'" class="readiness-card">
        <div class="readiness-title"><strong>上架检查</strong><span>{{ readiness.completionPercent }}% 已完成</span></div>
        <el-progress :percentage="readiness.completionPercent" :stroke-width="7" />
        <div class="readiness-tags"><el-tag v-for="check in readiness.checks" :key="check.key" :type="check.passed ? 'success' : 'info'" size="small">{{ check.passed ? '✓' : '待补' }} {{ check.label }}</el-tag></div>
        <p>{{ readiness.missing.length ? '下一步：'+readiness.missing.join('、') : '上架条件已齐备' }}</p>
      </div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="小区">{{ current.communityName }}</el-descriptions-item>
        <el-descriptions-item label="区域">{{ current.district }}</el-descriptions-item>
        <el-descriptions-item label="面积">{{ current.buildingAreaSqm }}</el-descriptions-item>
        <el-descriptions-item label="挂牌价">{{ current.listingPriceYuan }} 元</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(current.status) }}</el-descriptions-item>
        <el-descriptions-item label="核验">{{ current.verificationStatus }}</el-descriptions-item>
      </el-descriptions>
      <HouseMedia v-if="detailVisible && current.id" :key="current.id" :house-id="current.id" :media="current.media || []" :version="current.version" :readonly="current.status === 'DEAL'" :refresh="reloadCurrent" />
      <p class="slot-title">核心卖点</p>
      <p>{{ current.coreSelling }}</p>
      <p class="slot-title">小区介绍</p>
      <p>{{ current.communityIntro }}</p>
      <p class="slot-title">周边配套</p>
      <p>{{ current.surrounding }}</p>
      <p class="slot-title">文案智能分析</p>
      <p v-if="ai.summary">{{ ai.summary }}</p>
      <div>
        <span class="tag" v-for="(t, i) in ai.advantages" :key="'da'+i">{{ t }}</span>
        <span class="tag tag-warn" v-for="(t, i) in ai.disadvantages" :key="'dd'+i">{{ t }}</span>
      </div>
      <el-button type="primary" style="margin-top:8px" @click="retryAi">重新分析</el-button>
    </el-dialog>
    <BusinessImportDialog v-if="importVisible" v-model="importVisible" initial-kind="HOUSE" @imported="imported" />
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
import BusinessImportDialog from '@/components/BusinessImportDialog.vue'
import HouseMedia from '@/components/HouseMedia.vue'
import { nullableNumber } from '@/business'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
// 房源列表 + 小区地图，保存后请求文案分析
export default {
  name: 'House',
  components: { NavMain, HouseMedia, BusinessImportDialog },
  data() {
    return {
      importVisible: this.$route.query.import === '1',
      searchForm: { communityName: '', district: '' },
      tableData: [],
      allHouses: [],
      page: 1,
      size: 10,
      total: 0,
      detailVisible: false,
      formVisible: false,
      formTitle: '新增房源',
      isEdit: false,
      current: {},
      readiness: null,
      ai: { advantages: [], disadvantages: [] },
      stores: [],
      brokers: [],
      form: {},
      locHint: '填写小区后自动识别位置',
      map: null,
      layer: null,
      mapCap: '地图加载中',
      rules: {
        storeId: [{ required: true, message: '请选择门店', trigger: 'change' }],
        city: [{ required: true, message: '请输入城市', trigger: 'blur' }],
        district: [{ required: true, message: '请输入区域', trigger: 'blur' }],
        communityName: [{ required: true, message: '请输入小区', trigger: 'blur' }],
        buildingAreaSqm: [{ required: true, message: '请输入面积', trigger: 'blur' }],
        floorLevel: [{ required: true, message: '请选择楼层档', trigger: 'change' }],
        totalFloors: [{ required: true, message: '请输入总楼层', trigger: 'blur' }],
        orientation: [{ required: true, type: 'array', min: 1, message: '请选择朝向', trigger: 'change' }],
        decoration: [{ required: true, message: '请选择装修', trigger: 'change' }],
        propertyUsage: [{ required: true, message: '请输入用途', trigger: 'blur' }]
      }
    }
  },
  computed: {
    isAdmin() {
      return this.$store.state.role === 'ADMIN'
    }
  },
  created() {
    this.loadStores()
    this.init()
    this.loadAllHouses()
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
  watch: {
    '$route.query.import'(value) { if (value === '1') this.importVisible = true }
  },
  methods: {
    imported() { this.init(); this.loadAllHouses() },
    // 取出后端返回的提示文字
    errMsg(err) {
      if (err.response && err.response.data && err.response.data.message) return err.response.data.message
      return '请求失败'
    },
    // 挂牌价按万元显示
    priceWan(yuan) {
      if (yuan == null || yuan === '') return '--'
      return (Number(yuan) / 10000).toFixed(1)
    },
    // 列表上写成几室几厅
    roomText(row) {
      var r = row.rooms != null ? row.rooms : '-'
      var h = row.halls != null ? row.halls : '-'
      return r + '室' + h + '厅'
    },
    // 状态码换成中文
    statusText(s) {
      if (s === 'ON_SALE') return '在售'
      if (s === 'DEAL') return '成交'
      return '下架'
    },
    statusClass(s) {
      return s === 'ON_SALE' ? 'st-on' : 'st-off'
    },
    // 下拉门店，管理员新增时必选
    loadStores() {
      this.$axios.get('/stores').then((res) => {
        var list = res.data.data || []
        this.stores = Array.isArray(list) ? list : (list.records || [])
      }).catch(() => {
        this.stores = []
      })
    },
    // 按门店拉可用经纪人
    loadBrokers(storeId) {
      if (!storeId) {
        this.brokers = []
        return
      }
      this.$axios.get('/brokers', { params: { storeId: storeId } }).then((res) => {
        var list = res.data.data || []
        this.brokers = Array.isArray(list) ? list : (list.records || [])
      }).catch(() => {
        this.brokers = []
      })
    },
    // 换门店后经纪人要重选
    onStoreChange(val) {
      this.form.brokerId = ''
      this.loadBrokers(val)
    },
    // 分析接口有时给数组，有时给 JSON 字符串
    asList(v) {
      if (Array.isArray(v)) return v
      if (typeof v === 'string' && v) {
        try {
          var p = JSON.parse(v)
          return Array.isArray(p) ? p : []
        } catch (e) {
          return []
        }
      }
      return []
    },
    // 地图需要全部有坐标的房源，分页拼起来
    loadPages() {
      var that = this
      return this.$axios.get('/houses', { params: { page: 1, size: 100 } }).then(function (res) {
        var d = res.data.data || {}
        var records = d.records || []
        var total = d.total || 0
        var pages = Math.ceil(total / 100) || 1
        var reqs = []
        var i
        for (i = 2; i <= pages; i++) {
          reqs.push(that.$axios.get('/houses', { params: { page: i, size: 100 } }))
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
    // 右侧地图用全量房源，不跟表格分页走
    loadAllHouses() {
      this.loadPages().then((list) => {
        this.allHouses = list || []
        this.drawMap()
      }).catch(() => {})
    },
    // 高德路网底图，坐标和库里经纬度一致
    initMap() {
      if (this.map) return
      this.map = L.map('houseMap', {
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
    // 地图点位跟着查询条件走
    mapHouses() {
      var kw = (this.searchForm.communityName || '').trim()
      var district = (this.searchForm.district || '').trim()
      return this.allHouses.filter(function (h) {
        var lng = Number(h.longitude)
        var lat = Number(h.latitude)
        if (!lng || !lat) return false
        if (kw && String(h.communityName || '').indexOf(kw) === -1) return false
        if (district && String(h.district || '').indexOf(district) === -1) return false
        return true
      })
    },
    // 同一小区多套房合成一个点，点数表示套数
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
    // 有在售房的小区用主色，其余用灰色
    pinColor(g) {
      var on = 0
      g.houses.forEach(function (h) {
        if (h.status === 'ON_SALE') on++
      })
      return on > 0 ? '#B57920' : '#96A3BF'
    },
    // 按当前查询条件重画右侧圆点
    drawMap() {
      if (!this.map || !this.layer) return
      this.layer.clearLayers()
      var list = this.mapHouses()
      var groups = this.groupByCommunity(list)
      this.mapCap = '已标注 ' + groups.length + ' 个小区，共 ' + list.length + ' 套'
      var that = this
      groups.forEach(function (g) {
        var n = g.houses.length
        var r = n > 4 ? 13 : (n > 1 ? 10 : 8)
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
        marker.on('click', function () {
          that.map.setView([g.lat, g.lng], 15)
        })
        that.layer.addLayer(marker)
      })
      if (groups.length) {
        var pts = groups.map(function (g) {
          return [g.lat, g.lng]
        })
        this.map.fitBounds(pts, { padding: [24, 24], maxZoom: 14 })
      }
      this.$nextTick(function () {
        if (that.map) that.map.invalidateSize()
      })
    },
    // 点表格一行，地图飞到该小区
    onRowClick(row) {
      var lng = Number(row.longitude)
      var lat = Number(row.latitude)
      if (!this.map || !lng || !lat) return
      this.map.setView([lat, lng], 15)
    },
    // 库里已有同名小区就直接复用坐标
    findKnownLocation(city, district, name) {
      var hits = this.allHouses.filter(function (h) {
        if (!h.longitude || !h.latitude) return false
        if (name && h.communityName !== name) return false
        if (district && h.district && h.district !== district) return false
        return true
      })
      if (!hits.length) return null
      var lng = 0
      var lat = 0
      hits.forEach(function (h) {
        lng += Number(h.longitude)
        lat += Number(h.latitude)
      })
      return { lng: lng / hits.length, lat: lat / hits.length, from: '已有小区' }
    },
    // 检索不到时落到杭州市区大致中心
    districtCenter(district) {
      var table = {
        西湖: [30.2595, 120.1300],
        滨江: [30.2086, 120.2117],
        上城: [30.2425, 120.1693],
        拱墅: [30.3190, 120.1410],
        萧山: [30.1847, 120.2640],
        余杭: [30.2741, 120.0013],
        临平: [30.4180, 120.2980],
        钱塘: [30.3200, 120.4800],
        富阳: [30.0480, 119.9600],
        临安: [30.2340, 119.7250]
      }
      return table[district] || [30.27, 120.15]
    },
    // 开放地图是WGS84，底图用国测局坐标，这里做一次转换
    wgsToGcj(lat, lng) {
      var a = 6378245.0
      var ee = 0.00669342162296594323
      var dLat = this.transformLat(lng - 105.0, lat - 35.0)
      var dLng = this.transformLng(lng - 105.0, lat - 35.0)
      var radLat = lat / 180.0 * Math.PI
      var magic = Math.sin(radLat)
      magic = 1 - ee * magic * magic
      var sqrtMagic = Math.sqrt(magic)
      dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * Math.PI)
      dLng = (dLng * 180.0) / (a / sqrtMagic * Math.cos(radLat) * Math.PI)
      return { lat: lat + dLat, lng: lng + dLng }
    },
    transformLat(x, y) {
      var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
      ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
      ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
      ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0
      return ret
    },
    transformLng(x, y) {
      var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
      ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
      ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
      ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
      return ret
    },
    // 把识别到的经纬度写进表单
    applyLoc(lng, lat, from) {
      this.form.longitude = Number(Number(lng).toFixed(6))
      this.form.latitude = Number(Number(lat).toFixed(6))
      this.locHint = '已识别（' + from + '）：' + this.form.longitude + ', ' + this.form.latitude
    },
    // 填写小区后自动认位置，保存时一并提交经纬度
    fillLocation() {
      var city = (this.form.city || '').trim()
      var district = (this.form.district || '').trim()
      var name = (this.form.communityName || '').trim()
      if (!name) {
        this.locHint = '填写小区后自动识别位置'
        return Promise.resolve()
      }
      var known = this.findKnownLocation(city, district, name)
      if (known) {
        this.applyLoc(known.lng, known.lat, known.from)
        return Promise.resolve()
      }
      this.locHint = '正在识别小区位置…'
      var q = city + district + name
      var that = this
      return fetch('https://nominatim.openstreetmap.org/search?format=json&limit=1&q=' + encodeURIComponent(q), {
        headers: { 'Accept-Language': 'zh-CN' }
      }).then(function (res) {
        return res.json()
      }).then(function (arr) {
        if (arr && arr[0]) {
          var gcj = that.wgsToGcj(Number(arr[0].lat), Number(arr[0].lon))
          that.applyLoc(gcj.lng, gcj.lat, '地图检索')
          return
        }
        var c = that.districtCenter(district)
        that.applyLoc(c[1], c[0], district + '区域中心')
      }).catch(function () {
        var c = that.districtCenter(district)
        that.applyLoc(c[1], c[0], district + '区域中心')
      })
    },
    // 分页查房源，并给每行补文案分析标签
    init() {
      var that = this
      this.$axios.get('/houses', {
        params: {
          page: this.page,
          size: this.size,
          communityName: this.searchForm.communityName || undefined,
          district: this.searchForm.district || undefined
        }
      }).then((res) => {
        if (res.data.code !== 0) return
        var data = res.data.data || {}
        this.tableData = data.records || []
        this.total = data.total || 0
        this.tableData.forEach(function (row) {
          that.$axios.get('/ai/houses/' + row.id + '/text-analysis').then((r) => {
            var a = (r.data && r.data.data) || {}
            that.$set ? that.$set(row, 'advantages', a.advantages || []) : (row.advantages = a.advantages || [])
            row.advantages = that.asList(a.advantages)
            row.disadvantages = that.asList(a.disadvantages)
          }).catch(() => {})
        })
        this.drawMap()
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    // 查询从第一页开始，地图同步刷新
    search() {
      this.page = 1
      this.init()
      this.drawMap()
    },
    pageChange(p) {
      this.page = p
      this.init()
    },
    // 新增默认杭州，经纪人账号带上门店
    openAdd() {
      this.isEdit = false
      this.formTitle = '新增房源'
      var defaultStore = this.isAdmin
        ? (this.stores[0] ? this.stores[0].id : '')
        : this.$store.state.storeId
      this.form = {
        city: '杭州',
        district: '',
        communityName: '',
        buildingAreaSqm: '',
        rooms: 3,
        halls: 2,
        bathrooms: 1,
        floorLevel: 'MIDDLE',
        totalFloors: 18,
        orientation: ['S'],
        decoration: 'FINE',
        propertyUsage: 'RESIDENTIAL',
        listingPriceYuan: '',
        coreSelling: '',
        communityIntro: '',
        surrounding: '',
        storeId: defaultStore,
        brokerId: this.isAdmin ? '' : this.$store.state.brokerId,
        longitude: null,
        latitude: null
      }
      this.locHint = '填写小区后自动识别位置'
      this.loadBrokers(defaultStore)
      this.formVisible = true
    },
    // 修改时带回原记录，没有坐标再认一次位置
    openEdit(row) {
      this.isEdit = true
      this.formTitle = '修改房源'
      this.$axios.get('/houses/' + row.id).then((res) => {
        this.form = Object.assign({}, res.data.data || row)
        if (typeof this.form.orientation === 'string') {
          try {
            this.form.orientation = JSON.parse(this.form.orientation)
          } catch (e) {
            this.form.orientation = ['S']
          }
        }
        if (!this.form.orientation) this.form.orientation = ['S']
        if (this.form.longitude && this.form.latitude) {
          this.locHint = '已识别位置：' + this.form.longitude + ', ' + this.form.latitude
        } else {
          this.locHint = '填写小区后自动识别位置'
          this.fillLocation()
        }
        this.loadBrokers(this.form.storeId)
        this.formVisible = true
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    // 表单字段整理成后端要的数字和经纬度
    bodyOfForm() {
      var f = this.form
      return {
        city: f.city,
        district: f.district,
        communityName: f.communityName,
        buildingAreaSqm: nullableNumber(f.buildingAreaSqm),
        rooms: nullableNumber(f.rooms),
        halls: nullableNumber(f.halls),
        bathrooms: nullableNumber(f.bathrooms),
        floorLevel: f.floorLevel,
        totalFloors: nullableNumber(f.totalFloors),
        orientation: f.orientation,
        decoration: f.decoration,
        hasElevator: f.hasElevator === '' ? null : f.hasElevator,
        propertyUsage: f.propertyUsage,
        listingPriceYuan: nullableNumber(f.listingPriceYuan),
        listingDate: f.listingDate,
        coreSelling: f.coreSelling,
        communityIntro: f.communityIntro,
        surrounding: f.surrounding,
        storeId: f.storeId || null,
        brokerId: f.brokerId || null,
        longitude: nullableNumber(f.longitude),
        latitude: nullableNumber(f.latitude),
        businessArea: f.businessArea,
        communityId: f.communityId,
        buildingType: f.buildingType,
        layoutStructure: f.layoutStructure,
        constructionStructure: f.constructionStructure,
        ownershipType: f.ownershipType,
        internalAreaSqm: f.internalAreaSqm,
        sourceListingId: f.sourceListingId,
        version: f.version
      }
    },
    // 先认位置再提交，成功后跑文案分析
    saveForm() {
      this.$refs['form'].validate((valid) => {
        if (!valid) {
          this.$alert('表单验证失败', { confirmButtonText: '确定' })
          return false
        }
        var that = this
        this.fillLocation().then(function () {
          var req = that.isEdit
            ? that.$axios.put('/houses/' + that.form.id, that.bodyOfForm())
            : that.$axios.post('/houses', that.bodyOfForm())
          req.then((res) => {
            if (res.data.code !== 0) {
              that.$alert(res.data.message, { confirmButtonText: '确定' })
              return
            }
            that.$alert(that.isEdit ? '修改成功' : '添加成功', { confirmButtonText: '确定' })
            that.formVisible = false
            // 后端在事务提交后分析，页面不重复触发相同文案请求。
            that.init()
            that.loadAllHouses()
          }).catch((err) => {
            that.$alert(that.errMsg(err), { confirmButtonText: '确定' })
          })
        })
      })
    },
    // 详情里读已保存的文案分析结果
    async openDetail(row) {
      try {
        const [house, checks] = await Promise.all([this.$axios.get('/houses/' + row.id), this.$axios.get('/houses/' + row.id + '/readiness')])
        this.current = house.data.data || {}
        this.readiness = checks.data.data
        const analysis = this.current.textAnalysis || {}
        this.ai = { summary: analysis.summary, advantages: this.asList(analysis.advantages), disadvantages: this.asList(analysis.disadvantages) }
        this.detailVisible = true
      } catch (error) { this.$alert(this.errMsg(error), { confirmButtonText: '确定' }) }
    },
    async reloadCurrent() {
      const id = this.current.id
      const [house, checks] = await Promise.all([this.$axios.get('/houses/' + id), this.$axios.get('/houses/' + id + '/readiness')])
      if (String(this.current.id) !== String(id)) return
      this.current = house.data.data
      this.readiness = checks.data.data
      const analysis = this.current.textAnalysis || {}
      this.ai = { summary: analysis.summary, advantages: this.asList(analysis.advantages), disadvantages: this.asList(analysis.disadvantages) }
      this.init()
      this.loadAllHouses()
    },
    // 手动重试文案分析
    retryAi() {
      this.$axios.post('/ai/houses/' + this.current.id + '/text-analysis').then((res) => {
        var a = res.data.data || {}
        this.ai = {
          summary: a.summary,
          advantages: this.asList(a.advantages),
          disadvantages: this.asList(a.disadvantages)
        }
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    // 跳转到价格建议页
    goPrice(row) {
      this.$router.push('/price-advice/' + row.id)
    },
    // 管理员核验通过后才能上架
    verify(row) {
      this.$axios.get('/houses/' + row.id).then((res) => {
        var d = res.data.data || row
        return this.$axios.put('/houses/' + row.id + '/verification', {
          approved: true,
          reason: '核验通过',
          version: d.version
        })
      }).then(() => {
        this.$alert('核验通过', { confirmButtonText: '确定' })
        this.init()
        this.loadAllHouses()
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    // 在售改为下架
    off(row) {
      this.$confirm('确定下架该房源吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.$axios.get('/houses/' + row.id).then((res) => {
          var d = res.data.data || row
          return this.$axios.put('/houses/' + row.id + '/status', {
            status: 'OFF',
            reason: '下架',
            version: d.version
          })
        }).then(() => {
          this.$alert('操作成功', { confirmButtonText: '确定' })
          this.init()
          this.loadAllHouses()
        }).catch((err) => {
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        })
      }).catch(() => {})
    },
    // 下架房源重新上架
    onSale(row) {
      this.$axios.get('/houses/' + row.id).then((res) => {
        var d = res.data.data || row
        return this.$axios.put('/houses/' + row.id + '/status', {
          status: 'ON_SALE',
          reason: '上架',
          version: d.version
        })
      }).then(() => {
        this.$alert('已上架', { confirmButtonText: '确定' })
        this.init()
        this.loadAllHouses()
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    // 打开整页地图
    goMap() {
      this.$router.push('/house-map')
    }
  }
}
</script>
<style scoped>
p { text-align: left; }
.house-name { text-align: center; color: #2b2b2b; font-weight: 600; }
.house-sub { text-align: center; color: #96a3bf; font-size: 12px; margin: 4px 0; }
.tag-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px;
}
.ops {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}
.house-page {
  display: flex;
  justify-content: center;
}
.house-list :deep(.el-table th.el-table__cell) {
  text-align: center;
}
.house-list :deep(.el-table .cell) {
  text-align: center;
}
.house-split {
  display: flex;
  gap: 12px;
  align-items: stretch;
}
.house-list {
  flex: 1;
  min-width: 0;
}
.house-map-side {
  width: 42%;
  min-width: 360px;
  display: flex;
  flex-direction: column;
}
.map-cap {
  font-size: 12px;
  color: #96a3bf;
  margin-bottom: 6px;
}
.map-canvas {
  flex: 1;
  min-height: 520px;
  border: 1px solid #c6c6c7;
  border-radius: 4px;
}
.loc-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #96a3bf;
}
</style>

<style scoped>
.readiness-card{padding:15px;border:1px solid #e4e7eb;border-radius:8px;margin-bottom:18px;background:#fafbfc}.readiness-title{display:flex;justify-content:space-between;margin-bottom:10px;color:#657180;font-size:13px}.readiness-tags{display:flex;gap:7px;flex-wrap:wrap;margin-top:12px}.readiness-card p{font-size:12px;color:#7b8490;margin-bottom:0}
</style>
