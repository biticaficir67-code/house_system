<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <div class="page-head">
      <h2>业主管理</h2>
    </div>
    <div class="box1">
      <!-- 业主列表：名下房源能看出具体哪几套，停用只改业主状态 -->
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword"></el-input>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部" style="width:140px">
            <el-option label="在用" value="ACTIVE"></el-option>
            <el-option label="停用" value="INACTIVE"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="search">查询</el-button>
          <el-button type="primary" @click="openAdd">新增业主</el-button>
          <el-button @click="importVisible = true">表格导入</el-button>
        </el-form-item>
      </el-form>
      <el-table class="list-table" :data="tableData" stripe border>
        <el-table-column prop="id" label="编号" width="80" align="center"></el-table-column>
        <el-table-column prop="name" label="姓名" width="110"></el-table-column>
        <el-table-column prop="phone" label="电话" width="140"></el-table-column>
        <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip></el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <span :class="scope.row.status === 'INACTIVE' ? 'st-off' : 'st-on'">{{ ownerStatus(scope.row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="名下房源" min-width="220">
          <template #default="scope">
            <div v-if="scope.row.houses && scope.row.houses.length" class="cell-line">
              <span class="cell-text" :title="houseLine(scope.row)">{{ houseLine(scope.row) }}</span>
              <el-button size="small" type="primary" link @click="openHouses(scope.row)">详情</el-button>
            </div>
            <span v-else class="mute">暂无匹配</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="scope">
            <el-button size="small" type="warning" @click="openEdit(scope.row)">修改</el-button>
            <el-button v-if="scope.row.status !== 'INACTIVE'" size="small" type="info" @click="setStatus(scope.row, 'INACTIVE')">停用</el-button>
            <el-button v-else size="small" type="primary" @click="setStatus(scope.row, 'ACTIVE')">启用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:12px" layout="prev, pager, next" :page-size="size" :current-page="page" :total="total" @current-change="pageChange"></el-pagination>
    </div>
    <el-dialog :title="formTitle" v-model="formVisible" width="640px">
      <el-form ref="form" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="门店" prop="storeId">
          <el-select v-model="form.storeId" placeholder="请选择门店" style="width:100%" @change="onStoreChange">
            <el-option v-for="s in stores" :key="s.id" :label="s.name" :value="s.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name"></el-input>
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone"></el-input>
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address"></el-input>
        </el-form-item>
        <el-form-item label="备注">
          <el-input type="textarea" v-model="form.remark"></el-input>
        </el-form-item>
        <el-form-item label="匹配房源">
          <el-select v-model="form.houseIds" multiple filterable placeholder="选择该业主名下的房源" style="width:100%">
            <el-option v-for="h in houseOptions" :key="h.id" :label="houseOptionLabel(h)" :value="Number(h.id)"></el-option>
          </el-select>
          <div class="hint">可多选。下架、成交仍算名下房源，不会随业主停用被删掉。改到别人名下会从原业主解绑。</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveForm">确定</el-button>
          <el-button @click="formVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
    <el-dialog :title="houseTitle" v-model="houseVisible" width="720px">
      <el-table :data="houseRows" stripe border>
        <el-table-column prop="id" label="编号" width="70"></el-table-column>
        <el-table-column prop="communityName" label="小区" min-width="140"></el-table-column>
        <el-table-column prop="district" label="区域" width="90"></el-table-column>
        <el-table-column label="户型" width="90">
          <template #default="scope">{{ (scope.row.rooms != null ? scope.row.rooms : '-') + '室' }}</template>
        </el-table-column>
        <el-table-column label="面积" width="90">
          <template #default="scope">{{ scope.row.buildingAreaSqm != null ? scope.row.buildingAreaSqm + '㎡' : '—' }}</template>
        </el-table-column>
        <el-table-column label="挂牌价" width="100">
          <template #default="scope">{{ priceWan(scope.row.listingPriceYuan) }}</template>
        </el-table-column>
        <el-table-column label="房源状态" width="90">
          <template #default="scope">
            <span :class="scope.row.status === 'ON_SALE' ? 'st-on' : (scope.row.status === 'DEAL' ? 'st-ok' : 'st-off')">{{ houseStatus(scope.row.status) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <p v-if="!houseRows.length" class="hint">还没有匹配房源，可在修改业主里勾选。</p>
    </el-dialog>
    <BusinessImportDialog v-if="importVisible" v-model="importVisible" initial-kind="OWNER" @imported="init" />
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
import BusinessImportDialog from '@/components/BusinessImportDialog.vue'
import { moneyWan } from '@/business.js'
// 业主与名下房源：列表看具体套数，新增修改时勾选匹配
export default {
  name: 'Owner',
  components: { NavMain, BusinessImportDialog },
  data() {
    return {
      importVisible: this.$route.query.import === '1',
      searchForm: { keyword: '', status: '' },
      tableData: [],
      page: 1,
      size: 20,
      total: 0,
      formVisible: false,
      formTitle: '新增业主',
      isEdit: false,
      form: {},
      stores: [],
      houseOptions: [],
      houseVisible: false,
      houseTitle: '名下房源',
      houseRows: [],
      rules: {
        storeId: [{ required: true, message: '请选择门店', trigger: 'change' }],
        name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
        phone: [{ required: true, message: '请输入电话', trigger: 'blur' }]
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
  watch: {
    '$route.query.import'(value) { if (value === '1') this.importVisible = true }
  },
  methods: {
    loadStores() {
      this.$axios.get('/stores').then((res) => {
        var list = res.data.data || []
        this.stores = Array.isArray(list) ? list : (list.records || [])
      }).catch(() => {
        this.stores = []
      })
    },
    loadHouseOptions(storeId, ownerId) {
      if (!storeId) {
        this.houseOptions = []
        return
      }
      this.$axios.get('/owners/house-options', {
        params: { storeId: storeId, ownerId: ownerId || undefined }
      }).then((res) => {
        this.houseOptions = res.data.data || []
      }).catch(() => {
        this.houseOptions = []
      })
    },
    onStoreChange(val) {
      this.form.houseIds = []
      this.loadHouseOptions(val, this.isEdit ? this.form.id : undefined)
    },
    errMsg(err) {
      if (err.response && err.response.data && err.response.data.message) return err.response.data.message
      return '请求失败'
    },
    ownerStatus(status) {
      return status === 'INACTIVE' ? '停用' : '在用'
    },
    houseStatus(status) {
      if (status === 'ON_SALE') return '在售'
      if (status === 'DEAL') return '成交'
      return '下架'
    },
    houseLine(row) {
      return (row.houses || []).map((h) => (h.communityName || '房源') + '（' + this.houseStatus(h.status) + '）').join('、')
    },
    priceWan(yuan) {
      var t = moneyWan(yuan)
      return t === '—' ? '—' : t + '万'
    },
    houseOptionLabel(h) {
      var text = (h.communityName || '房源') + ' · ' + this.houseStatus(h.status)
      if (h.rooms != null) text += ' · ' + h.rooms + '室'
      if (h.listingPriceYuan != null) text += ' · ' + this.priceWan(h.listingPriceYuan)
      if (h.ownerId && (!this.form.id || Number(h.ownerId) !== Number(this.form.id))) {
        text += '（现属' + (h.ownerName || h.ownerId) + '）'
      }
      return text
    },
    init() {
      this.$axios.get('/owners', {
        params: {
          keyword: this.searchForm.keyword || undefined,
          status: this.searchForm.status || undefined,
          page: this.page,
          size: this.size
        }
      }).then((res) => {
        var d = res.data.data || {}
        this.tableData = d.records || []
        this.total = d.total || 0
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
      this.formTitle = '新增业主'
      this.form = {
        storeId: this.isAdmin ? (this.stores[0] ? this.stores[0].id : '') : this.$store.state.storeId,
        houseIds: []
      }
      this.loadHouseOptions(this.form.storeId)
      this.formVisible = true
    },
    openEdit(row) {
      this.isEdit = true
      this.formTitle = '修改业主'
      this.$axios.get('/owners/' + row.id).then((res) => {
        var data = res.data.data || row
        this.form = Object.assign({ houseIds: [] }, data)
        this.form.houseIds = (data.houses || []).map(function (h) { return Number(h.id) })
        this.loadHouseOptions(this.form.storeId, this.form.id)
        this.formVisible = true
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    openHouses(row) {
      this.houseTitle = (row.name || '业主') + ' · 名下房源'
      this.houseRows = row.houses || []
      if (!this.houseRows.length && row.id) {
        this.$axios.get('/owners/' + row.id).then((res) => {
          this.houseRows = (res.data.data && res.data.data.houses) || []
          this.houseVisible = true
        }).catch((err) => {
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        })
        return
      }
      this.houseVisible = true
    },
    saveForm() {
      this.$refs['form'].validate((valid) => {
        if (!valid) {
          this.$alert('表单验证失败', { confirmButtonText: '确定' })
          return false
        }
        var body = {
          name: this.form.name,
          phone: this.form.phone,
          address: this.form.address,
          remark: this.form.remark,
          storeId: this.form.storeId ? Number(this.form.storeId) : null,
          version: this.form.version,
          houseIds: this.form.houseIds || []
        }
        var req = this.isEdit
          ? this.$axios.put('/owners/' + this.form.id, body)
          : this.$axios.post('/owners', body)
        req.then(() => {
          this.$alert(this.isEdit ? '修改成功' : '添加成功', { confirmButtonText: '确定' })
          this.formVisible = false
          this.init()
        }).catch((err) => {
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        })
      })
    },
    setStatus(row, status) {
      var tip = status === 'INACTIVE' ? '停用后业主档案和名下房源都保留，只是业主改为停用。确定停用？' : '确定重新启用该业主？'
      this.$confirm(tip, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.$axios.put('/owners/' + row.id + '/status', { status: status, version: row.version }).then(() => {
          this.$alert(status === 'INACTIVE' ? '已停用' : '已启用', { confirmButtonText: '确定' })
          this.init()
        }).catch((err) => {
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        })
      }).catch(() => {})
    }
  }
}
</script>
<style scoped>
.hint {
  color: #96a3bf;
  font-size: 12px;
  line-height: 1.5;
  margin-top: 6px;
}
.mute {
  color: #96a3bf;
}
.cell-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.cell-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
