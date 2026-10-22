<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <div class="page-head">
      <h2>房源管理</h2>
    </div>
    <div class="box1">
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
        </el-form-item>
      </el-form>
      <el-table :data="tableData" stripe border>
        <el-table-column prop="id" label="编号" width="80"></el-table-column>
        <el-table-column label="房源信息" min-width="240">
          <template #default="scope">
            <div class="house-name">{{ scope.row.communityName }}　{{ scope.row.district }}</div>
            <div class="house-sub">{{ roomText(scope.row) }} / {{ scope.row.buildingAreaSqm }}㎡ / {{ priceWan(scope.row.listingPriceYuan) }}万</div>
            <div>
              <span class="tag" v-for="(t, i) in scope.row.advantages" :key="'a'+i">{{ t }}</span>
              <span class="tag tag-warn" v-for="(t, i) in scope.row.disadvantages" :key="'d'+i">{{ t }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <span :class="statusClass(scope.row.status)">{{ statusText(scope.row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="360">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openDetail(scope.row)">详情</el-button>
            <el-button size="small" type="warning" @click="openEdit(scope.row)">修改</el-button>
            <el-button size="small" type="warning" v-if="isAdmin" @click="verify(scope.row)">核验</el-button>
            <el-button size="small" type="info" v-if="scope.row.status === 'ON_SALE'" @click="off(scope.row)">下架</el-button>
            <el-button size="small" type="success" v-else-if="scope.row.status === 'OFF'" @click="onSale(scope.row)">上架</el-button>
            <el-button size="small" plain type="primary" @click="goPrice(scope.row)">价格建议</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:12px"
        layout="prev, pager, next"
        :page-size="size"
        :current-page="page"
        :total="total"
        @current-change="pageChange">
      </el-pagination>
    </div>

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
          <el-input v-model="form.city"></el-input>
        </el-form-item>
        <el-form-item label="区域" prop="district">
          <el-input v-model="form.district"></el-input>
        </el-form-item>
        <el-form-item label="小区名称" prop="communityName">
          <el-input v-model="form.communityName"></el-input>
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

    <el-dialog title="房源详情" v-model="detailVisible" width="70%">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="小区">{{ current.communityName }}</el-descriptions-item>
        <el-descriptions-item label="区域">{{ current.district }}</el-descriptions-item>
        <el-descriptions-item label="面积">{{ current.buildingAreaSqm }}</el-descriptions-item>
        <el-descriptions-item label="挂牌价">{{ current.listingPriceYuan }} 元</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(current.status) }}</el-descriptions-item>
        <el-descriptions-item label="核验">{{ current.verificationStatus }}</el-descriptions-item>
      </el-descriptions>
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
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
export default {
  name: 'House',
  components: { NavMain },
  data() {
    return {
      searchForm: { communityName: '', district: '' },
      tableData: [],
      page: 1,
      size: 20,
      total: 0,
      detailVisible: false,
      formVisible: false,
      formTitle: '新增房源',
      isEdit: false,
      current: {},
      ai: { advantages: [], disadvantages: [] },
      stores: [],
      brokers: [],
      form: {},
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
  },
  methods: {
    errMsg(err) {
      if (err.response && err.response.data && err.response.data.message) return err.response.data.message
      return '请求失败'
    },
    priceWan(yuan) {
      if (yuan == null || yuan === '') return '--'
      return (Number(yuan) / 10000).toFixed(1)
    },
    roomText(row) {
      var r = row.rooms != null ? row.rooms : '-'
      var h = row.halls != null ? row.halls : '-'
      return r + '室' + h + '厅'
    },
    statusText(s) {
      if (s === 'ON_SALE') return '在售'
      if (s === 'DEAL') return '成交'
      return '下架'
    },
    statusClass(s) {
      return s === 'ON_SALE' ? 'st-on' : 'st-off'
    },
    loadStores() {
      this.$axios.get('/stores').then((res) => {
        var list = res.data.data || []
        this.stores = Array.isArray(list) ? list : (list.records || [])
      }).catch(() => {
        this.stores = []
      })
    },
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
    onStoreChange(val) {
      this.form.brokerId = ''
      this.loadBrokers(val)
    },
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
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    search() {
      this.page = 1
      this.init()
    },
    pageChange(p) {
      this.page = p
      this.init()
    },
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
        brokerId: this.isAdmin ? '' : this.$store.state.brokerId
      }
      this.loadBrokers(defaultStore)
      this.formVisible = true
    },
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
        this.loadBrokers(this.form.storeId)
        this.formVisible = true
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    bodyOfForm() {
      var f = this.form
      return {
        city: f.city,
        district: f.district,
        communityName: f.communityName,
        buildingAreaSqm: Number(f.buildingAreaSqm),
        rooms: f.rooms ? Number(f.rooms) : null,
        halls: f.halls ? Number(f.halls) : null,
        bathrooms: f.bathrooms ? Number(f.bathrooms) : null,
        floorLevel: f.floorLevel,
        totalFloors: Number(f.totalFloors),
        orientation: f.orientation,
        decoration: f.decoration,
        hasElevator: f.hasElevator,
        propertyUsage: f.propertyUsage,
        listingPriceYuan: f.listingPriceYuan ? Number(f.listingPriceYuan) : null,
        listingDate: f.listingDate,
        coreSelling: f.coreSelling,
        communityIntro: f.communityIntro,
        surrounding: f.surrounding,
        storeId: f.storeId ? Number(f.storeId) : null,
        brokerId: f.brokerId ? Number(f.brokerId) : null,
        version: f.version
      }
    },
    saveForm() {
      this.$refs['form'].validate((valid) => {
        if (!valid) {
          this.$alert('表单验证失败', { confirmButtonText: '确定' })
          return false
        }
        var req = this.isEdit
          ? this.$axios.put('/houses/' + this.form.id, this.bodyOfForm())
          : this.$axios.post('/houses', this.bodyOfForm())
        req.then((res) => {
          if (res.data.code !== 0) {
            this.$alert(res.data.message, { confirmButtonText: '确定' })
            return
          }
          var id = (res.data.data && res.data.data.id) || this.form.id
          this.$alert(this.isEdit ? '修改成功' : '添加成功', { confirmButtonText: '确定' })
          this.formVisible = false
          // 保存后立刻跑文案分析，列表标签才能跟上
          this.$axios.post('/ai/houses/' + id + '/text-analysis').then(() => {
            this.init()
          }).catch(() => {
            this.init()
          })
        }).catch((err) => {
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        })
      })
    },
    openDetail(row) {
      this.$axios.get('/houses/' + row.id).then((res) => {
        this.current = res.data.data || {}
        var a = this.current.textAnalysis || {}
        this.ai = {
          summary: a.summary,
          advantages: this.asList(a.advantages),
          disadvantages: this.asList(a.disadvantages)
        }
        this.detailVisible = true
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
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
    goPrice(row) {
      this.$router.push('/price-advice/' + row.id)
    },
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
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
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
        }).catch((err) => {
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        })
      }).catch(() => {})
    },
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
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    }
  }
}
</script>
<style scoped>
p { text-align: left; }
.house-name { text-align: left; color: #2b2b2b; font-weight: 600; }
.house-sub { text-align: left; color: #96a3bf; font-size: 12px; margin: 4px 0; }
</style>
