<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <div class="page-head">
      <h2>客户管理</h2>
    </div>
    <div class="box1">
      <!-- 客户列表：查询、新增、修改；需求摘要来自历史描述或需求匹配 -->
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword"></el-input>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部" style="width:140px">
            <el-option label="在用" value="ACTIVE"></el-option>
            <el-option label="停用" value="INACTIVE"></el-option>
            <el-option label="成交" value="DEAL"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="search">查询</el-button>
          <el-button type="primary" @click="openAdd">新增客户</el-button>
          <el-button @click="importVisible = true">表格导入</el-button>
        </el-form-item>
      </el-form>
      <el-table class="list-table" :data="tableData" stripe border>
        <el-table-column prop="id" label="编号" width="80" align="center"></el-table-column>
        <el-table-column prop="name" label="姓名" width="120"></el-table-column>
        <el-table-column prop="phone" label="电话" width="140"></el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <span :class="scope.row.status === 'ACTIVE' ? 'st-on' : (scope.row.status === 'DEAL' ? 'st-ok' : 'st-off')">{{ customerStatus(scope.row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="demandDesc" label="需求摘要" min-width="280" show-overflow-tooltip>
          <template #default="scope">
            <span :class="scope.row.demandDesc ? '' : 'mute'">{{ scope.row.demandDesc || '暂无需求' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="430" align="center" class-name="op-col">
          <template #default="scope">
            <div class="op-btns">
              <el-button size="small" type="warning" @click="openEdit(scope.row)">修改</el-button>
              <el-button size="small" type="primary" @click="openWorkspace(scope.row)">需求智能匹配</el-button>
              <el-button v-if="scope.row.status === 'ACTIVE'" size="small" type="info" @click="setStatus(scope.row, 'INACTIVE')">停用</el-button>
              <el-button v-else-if="scope.row.status === 'INACTIVE'" size="small" type="primary" @click="setStatus(scope.row, 'ACTIVE')">启用</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:12px" layout="prev, pager, next" :page-size="size" :current-page="page" :total="total" @current-change="pageChange"></el-pagination>
    </div>
    <el-dialog :title="formTitle" v-model="formVisible" width="560px">
      <el-form ref="addForm" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="门店" prop="storeId">
          <el-select v-model="form.storeId" placeholder="请选择门店" style="width:100%" @change="onStoreChange">
            <el-option v-for="s in stores" :key="s.id" :label="s.name" :value="s.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="isAdmin" label="经纪人" prop="brokerId">
          <el-select v-model="form.brokerId" placeholder="请选择经纪人" style="width:100%">
            <el-option v-for="b in brokers" :key="b.id" :label="b.brokerName || b.name" :value="b.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name"></el-input>
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone"></el-input>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tagsText" placeholder="多个标签用逗号分隔"></el-input>
        </el-form-item>
        <el-form-item label="备注">
          <el-input type="textarea" v-model="form.remark"></el-input>
        </el-form-item>
        <div class="demand-title">购房需求（写入需求摘要，保存后可匹配房源）</div>
        <el-form-item label="意向区域">
          <el-input v-model="form.districtsText" placeholder="多个区域用逗号分隔，如西湖,滨江"></el-input>
        </el-form-item>
        <el-form-item label="预算(万)">
          <el-input v-model="form.minPriceWan" style="width:120px"></el-input>
          <span class="range-split">-</span>
          <el-input v-model="form.maxPriceWan" style="width:120px"></el-input>
        </el-form-item>
        <el-form-item label="面积(㎡)">
          <el-input v-model="form.minArea" style="width:120px"></el-input>
          <span class="range-split">-</span>
          <el-input v-model="form.maxArea" style="width:120px"></el-input>
        </el-form-item>
        <el-form-item label="室数">
          <el-input v-model="form.minRooms" style="width:120px"></el-input>
          <span class="range-split">-</span>
          <el-input v-model="form.maxRooms" style="width:120px"></el-input>
        </el-form-item>
        <el-form-item label="装修">
          <el-select v-model="form.decoration" clearable placeholder="不限" style="width:100%">
            <el-option label="毛坯" value="ROUGH"></el-option>
            <el-option label="简装" value="SIMPLE"></el-option>
            <el-option label="精装" value="FINE"></el-option>
            <el-option label="其他" value="OTHER"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="计划购房">
          <el-date-picker v-model="form.expectedPurchaseDate" type="date" value-format="YYYY-MM-DD" placeholder="选填" style="width:100%"></el-date-picker>
        </el-form-item>
        <el-form-item label="需求说明">
          <el-input type="textarea" v-model="form.notes" placeholder="也可直接写摘要，如刚需三房，预算300-400万"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="save(false)">确定</el-button>
          <el-button type="warning" @click="save(true)">保存并需求匹配</el-button>
          <el-button @click="formVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
    <CustomerWorkspace v-if="workspaceVisible" :customer="workspaceCustomer" @close="workspaceVisible=false" @changed="init"></CustomerWorkspace>
    <BusinessImportDialog v-if="importVisible" v-model="importVisible" initial-kind="CUSTOMER" @imported="init" />
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
import CustomerWorkspace from '@/components/CustomerWorkspace.vue'
import BusinessImportDialog from '@/components/BusinessImportDialog.vue'
import { wanToYuan } from '@/business.js'
// 客户档案；新增时一并写需求，列表展示需求摘要
export default {
  name: 'Customer',
  components: { NavMain, CustomerWorkspace, BusinessImportDialog },
  data() {
    return {
      importVisible: this.$route.query.import === '1',
      searchForm: { keyword: '', status: '' },
      tableData: [],
      page: 1,
      size: 20,
      total: 0,
      formVisible: false,
      formTitle: '新增客户',
      isEdit: false,
      form: {},
      stores: [],
      brokers: [],
      workspaceVisible: false,
      workspaceCustomer: {},
      rules: {
        storeId: [{ required: true, message: '请选择门店', trigger: 'change' }],
        brokerId: [{ required: true, message: '请选择经纪人', trigger: 'change' }],
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
    loadBrokers(storeId) {
      if (!storeId) {
        this.brokers = []
        return
      }
      this.$axios.get('/brokers', { params: { storeId: storeId } }).then((res) => {
        var list = res.data.data || []
        this.brokers = Array.isArray(list) ? list : (list.records || [])
        if (this.isAdmin && !this.form.brokerId && this.brokers.length) this.form.brokerId = this.brokers[0].id
      }).catch(() => {
        this.brokers = []
      })
    },
    onStoreChange(val) {
      this.form.brokerId = ''
      this.loadBrokers(val)
    },
    errMsg(err) {
      if (err.response && err.response.data && err.response.data.message) return err.response.data.message
      return '请求失败'
    },
    blankForm() {
      return {
        storeId: this.isAdmin ? (this.stores[0] ? this.stores[0].id : '') : this.$store.state.storeId,
        brokerId: this.isAdmin ? '' : this.$store.state.brokerId,
        name: '',
        phone: '',
        tagsText: '',
        remark: '',
        districtsText: '',
        minPriceWan: '',
        maxPriceWan: '',
        minArea: '',
        maxArea: '',
        minRooms: '',
        maxRooms: '',
        decoration: '',
        expectedPurchaseDate: '',
        notes: ''
      }
    },
    // 把详情里的需求填回新增/修改表单
    fillDemand(form, demand, demandDesc) {
      var d = demand || {}
      form.districtsText = Array.isArray(d.districts) ? d.districts.join(',') : ''
      form.minPriceWan = d.minPrice != null ? Number(d.minPrice) / 10000 : ''
      form.maxPriceWan = d.maxPrice != null ? Number(d.maxPrice) / 10000 : ''
      form.minArea = d.minArea != null ? d.minArea : ''
      form.maxArea = d.maxArea != null ? d.maxArea : ''
      form.minRooms = d.minRooms != null ? d.minRooms : ''
      form.maxRooms = d.maxRooms != null ? d.maxRooms : ''
      form.decoration = d.decoration || ''
      form.expectedPurchaseDate = d.expectedPurchaseDate || ''
      form.notes = d.notes || (!demand && demandDesc ? demandDesc : '')
    },
    demandFilled() {
      var f = this.form
      return !!(f.districtsText || f.minPriceWan || f.maxPriceWan || f.minArea || f.maxArea || f.minRooms || f.maxRooms || f.decoration || f.expectedPurchaseDate || f.notes)
    },
    buildDemand() {
      var f = this.form
      var districts = (f.districtsText || '').split(/[,，]/).map(function (s) { return s.trim() }).filter(Boolean)
      return {
        districts: districts,
        minPrice: wanToYuan(f.minPriceWan),
        maxPrice: wanToYuan(f.maxPriceWan),
        minArea: f.minArea === '' || f.minArea == null ? null : Number(f.minArea),
        maxArea: f.maxArea === '' || f.maxArea == null ? null : Number(f.maxArea),
        minRooms: f.minRooms === '' || f.minRooms == null ? null : Number(f.minRooms),
        maxRooms: f.maxRooms === '' || f.maxRooms == null ? null : Number(f.maxRooms),
        decoration: f.decoration || null,
        expectedPurchaseDate: f.expectedPurchaseDate || null,
        notes: f.notes || null
      }
    },
    init() {
      this.$axios.get('/customers', {
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
      this.formTitle = '新增客户'
      this.form = this.blankForm()
      this.loadBrokers(this.form.storeId)
      this.formVisible = true
    },
    openEdit(row) {
      this.isEdit = true
      this.formTitle = '修改客户'
      this.$axios.get('/customers/' + row.id).then((res) => {
        var data = res.data.data || row
        this.form = Object.assign(this.blankForm(), data)
        this.form.tagsText = Array.isArray(data.tags) ? data.tags.join(',') : (data.tags || '')
        this.fillDemand(this.form, data.demand, data.demandDesc)
        this.loadBrokers(this.form.storeId)
        this.formVisible = true
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    openWorkspace(row) {
      this.workspaceCustomer = { id: row.id, name: row.name }
      this.workspaceVisible = true
    },
    save(goMatch) {
      this.$refs['addForm'].validate((valid) => {
        if (!valid) {
          this.$alert('表单验证失败', { confirmButtonText: '确定' })
          return false
        }
        var body = {
          name: this.form.name,
          phone: this.form.phone,
          remark: this.form.remark,
          tags: (this.form.tagsText || '').split(/[,，]/).map(function (s) { return s.trim() }).filter(Boolean),
          storeId: this.form.storeId ? Number(this.form.storeId) : null,
          brokerId: this.form.brokerId ? Number(this.form.brokerId) : (this.$store.state.brokerId ? Number(this.$store.state.brokerId) : null),
          version: this.form.version
        }
        var demand = null
        try {
          if (this.demandFilled()) demand = this.buildDemand()
        } catch (e) {
          this.$alert(e.message || '需求填写有误', { confirmButtonText: '确定' })
          return false
        }
        if (!this.isEdit && demand) body.demand = demand
        var req = this.isEdit
          ? this.$axios.put('/customers/' + this.form.id, body)
          : this.$axios.post('/customers', body)
        req.then((res) => {
          var saved = (res.data && res.data.data) || {}
          var id = saved.id || this.form.id
          var after = () => {
            this.$alert(this.isEdit ? '修改成功' : '添加成功', { confirmButtonText: '确定' })
            this.formVisible = false
            this.init()
            if (goMatch) this.openWorkspace({ id: id, name: this.form.name })
          }
          if (this.isEdit && demand) {
            this.$axios.put('/customers/' + id + '/demand', demand).then(after).catch((err) => {
              this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
            })
          } else {
            after()
          }
        }).catch((err) => {
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        })
      })
    },
    customerStatus(status) {
      if (status === 'INACTIVE') return '停用'
      if (status === 'DEAL') return '成交'
      return '在用'
    },
    setStatus(row, status) {
      var tip = status === 'INACTIVE' ? '停用后客户档案和需求都保留，只是客户改为停用。确定停用？' : '确定重新启用该客户？'
      this.$confirm(tip, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.$axios.put('/customers/' + row.id + '/status', {
          status: status,
          reason: status === 'INACTIVE' ? '停用客户' : '启用客户',
          version: row.version
        }).then(() => {
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
.demand-title {
  margin: 4px 0 12px 0;
  padding-left: 12px;
  border-left: 3px solid #b57920;
  color: #2b2b2b;
  font-size: 14px;
  font-weight: 600;
}
.range-split {
  display: inline-block;
  width: 24px;
  text-align: center;
  color: #96a3bf;
}
.mute {
  color: #96a3bf;
}
.op-btns {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  white-space: nowrap;
}
.op-btns :deep(.el-button) {
  margin: 0;
  white-space: nowrap;
}
.list-table :deep(.op-col .cell) {
  overflow: visible;
  white-space: nowrap;
}
</style>
