<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <div class="page-head">
      <h2>带看管理</h2>
    </div>
    <div class="box1">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="客户或小区"></el-input>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部" style="width:140px">
            <el-option label="待看" value="SCHEDULED"></el-option>
            <el-option label="已完成" value="COMPLETED"></el-option>
            <el-option label="已取消" value="CANCELLED"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="search">查询</el-button>
          <el-button type="primary" @click="openAdd">新增带看</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="tableData" stripe border>
        <el-table-column prop="id" label="编号" width="80"></el-table-column>
        <el-table-column label="房源" min-width="220">
          <template #default="scope">{{ scope.row.houseText }}</template>
        </el-table-column>
        <el-table-column label="客户" min-width="110">
          <template #default="scope">{{ scope.row.customerName }}</template>
        </el-table-column>
        <el-table-column label="预约时间" width="160">
          <template #default="scope">{{ timeText(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <span :class="scope.row.status === 'SCHEDULED' ? 'st-wait' : (scope.row.status === 'COMPLETED' ? 'st-ok' : 'st-off')">{{ showStatus(scope.row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openDetail(scope.row)">详情</el-button>
            <el-button size="small" type="warning" v-if="scope.row.status === 'SCHEDULED'" @click="openEdit(scope.row)">修改</el-button>
            <el-button size="small" type="success" v-if="scope.row.status === 'SCHEDULED'" @click="complete(scope.row)">完成</el-button>
            <el-button size="small" type="info" v-if="scope.row.status === 'SCHEDULED'" @click="del(scope.row)">删除</el-button>
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

    <el-dialog :title="formTitle" v-model="formVisible" width="520px">
      <el-form ref="form" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="房源" prop="houseId" v-if="!isEdit">
          <el-select v-model="form.houseId" filterable placeholder="请选择在售房源" style="width:100%" @change="onHouseChange">
            <el-option v-for="h in saleHouses" :key="h.id" :label="houseLabel(h)" :value="h.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="客户" prop="customerId" v-if="!isEdit">
          <el-select v-model="form.customerId" filterable placeholder="请选择客户" style="width:100%">
            <el-option v-for="c in activeCustomers" :key="c.id" :label="c.name" :value="c.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="经纪人" prop="brokerId" v-if="!isEdit">
          <el-select v-model="form.brokerId" filterable placeholder="请选择同门店经纪人" style="width:100%">
            <el-option v-for="b in formBrokers" :key="b.id" :label="b.brokerName || b.name" :value="b.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="预约时间" prop="appointmentTime">
          <el-input v-model="form.appointmentTime" placeholder="2026-12-20T15:00:00"></el-input>
        </el-form-item>
        <el-form-item label="备注">
          <el-input type="textarea" v-model="form.remark" rows="2"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveForm">确定</el-button>
          <el-button @click="formVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>

    <el-dialog title="带看详情" v-model="detailVisible" width="560px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="编号">{{ current.id }}</el-descriptions-item>
        <el-descriptions-item label="房源">{{ current.houseText }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ current.customerName }}</el-descriptions-item>
        <el-descriptions-item label="预约时间">{{ timeText(current) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ showStatus(current.status) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ current.remark || '--' }}</el-descriptions-item>
        <el-descriptions-item label="取消原因" v-if="current.cancelReason">{{ current.cancelReason }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
export default {
  name: 'Showing',
  components: { NavMain },
  data() {
    return {
      tableData: [],
      allRows: [],
      filteredRows: [],
      houseList: [],
      customerList: [],
      brokerList: [],
      page: 1,
      size: 20,
      total: 0,
      searchForm: { keyword: '', status: '' },
      formVisible: false,
      detailVisible: false,
      isEdit: false,
      formTitle: '新增带看',
      form: {},
      current: {},
      rules: {
        houseId: [{ required: true, message: '请选择房源', trigger: 'change' }],
        customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
        brokerId: [{ required: true, message: '请选择经纪人', trigger: 'change' }],
        appointmentTime: [{ required: true, message: '请输入预约时间', trigger: 'blur' }]
      }
    }
  },
  computed: {
    saleHouses() {
      return this.houseList.filter(function (h) {
        return h.status === 'ON_SALE'
      })
    },
    activeCustomers() {
      var storeId = this.currentHouseStore()
      return this.customerList.filter(function (c) {
        if (c.status && c.status !== 'ACTIVE') return false
        if (storeId && String(c.storeId) !== String(storeId)) return false
        return true
      })
    },
    formBrokers() {
      var storeId = this.currentHouseStore()
      return this.brokerList.filter(function (b) {
        if (!storeId) return true
        return String(b.storeId) === String(storeId)
      })
    }
  },
  created() {
    this.init()
  },
  methods: {
    errMsg(err) {
      if (err.response && err.response.data && err.response.data.message) return err.response.data.message
      return '请求失败'
    },
    showStatus(s) {
      if (s === 'SCHEDULED') return '待看'
      if (s === 'COMPLETED') return '已完成'
      if (s === 'CANCELLED') return '已取消'
      return s || '--'
    },
    timeText(row) {
      var t = (row && (row.appointmentTime || row.showTime || row.completedTime)) || ''
      if (!t) return '--'
      return String(t).replace('T', ' ').substring(0, 16)
    },
    timeVal(row) {
      var t = row.appointmentTime || row.showTime || row.completedTime || ''
      var n = Date.parse(t)
      return isNaN(n) ? 0 : n
    },
    houseLabel(h) {
      if (!h) return ''
      var bits = ['#' + h.id]
      if (h.communityName) bits.push(h.communityName)
      if (h.district) bits.push(h.district)
      if (h.buildingAreaSqm) bits.push(h.buildingAreaSqm + '㎡')
      return bits.join('　')
    },
    currentHouseStore() {
      var id = this.form && this.form.houseId
      if (!id) return ''
      var hit = this.houseList.filter(function (h) {
        return String(h.id) === String(id)
      })[0]
      return hit ? hit.storeId : ''
    },
    onHouseChange() {
      this.form.customerId = ''
      this.form.brokerId = ''
    },
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
    applyPage() {
      var start = (this.page - 1) * this.size
      this.tableData = this.filteredRows.slice(start, start + this.size)
    },
    applyFilter() {
      var kw = (this.searchForm.keyword || '').trim()
      var st = this.searchForm.status || ''
      this.filteredRows = this.allRows.filter(function (row) {
        if (st && row.status !== st) return false
        if (!kw) return true
        var text = (row.houseText || '') + ' ' + (row.customerName || '')
        return text.indexOf(kw) !== -1
      })
      this.total = this.filteredRows.length
      this.applyPage()
    },
    init() {
      var that = this
      Promise.all([
        this.loadPages('/showings'),
        this.loadPages('/houses'),
        this.loadPages('/customers'),
        this.$axios.get('/brokers').then(function (res) {
          var list = res.data.data || []
          return Array.isArray(list) ? list : (list.records || [])
        }).catch(function () { return [] })
      ]).then(function (arr) {
        var shows = arr[0] || []
        that.houseList = arr[1] || []
        that.customerList = arr[2] || []
        that.brokerList = arr[3] || []
        var hMap = {}
        var cMap = {}
        that.houseList.forEach(function (h) {
          hMap[String(h.id)] = h
        })
        that.customerList.forEach(function (c) {
          cMap[String(c.id)] = c
        })
        shows.forEach(function (row) {
          var h = hMap[String(row.houseId)]
          var c = cMap[String(row.customerId)]
          row.houseText = h ? that.houseLabel(h) : ('#' + row.houseId)
          row.customerName = c && c.name ? c.name : (row.customerName || ('客户#' + row.customerId))
        })
        shows.sort(function (a, b) {
          return that.timeVal(b) - that.timeVal(a)
        })
        that.allRows = shows
        that.applyFilter()
      }).catch(function (err) {
        that.$alert(that.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    search() {
      this.page = 1
      this.applyFilter()
    },
    pageChange(p) {
      this.page = p
      this.applyPage()
    },
    openAdd() {
      this.isEdit = false
      this.formTitle = '新增带看'
      this.form = {
        houseId: '',
        customerId: '',
        brokerId: this.$store.state.brokerId || '',
        appointmentTime: '',
        remark: ''
      }
      this.formVisible = true
    },
    openEdit(row) {
      this.isEdit = true
      this.formTitle = '修改带看'
      this.$axios.get('/showings/' + row.id).then((res) => {
        var d = res.data.data || row
        this.form = {
          id: d.id,
          appointmentTime: d.appointmentTime || d.showTime || '',
          remark: d.remark || '',
          version: d.version
        }
        this.formVisible = true
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    openDetail(row) {
      this.$axios.get('/showings/' + row.id).then((res) => {
        var d = res.data.data || {}
        this.current = Object.assign({}, row, d)
        this.detailVisible = true
      }).catch(() => {
        this.current = row
        this.detailVisible = true
      })
    },
    saveForm() {
      this.$refs['form'].validate((valid) => {
        if (!valid) {
          this.$alert('表单验证失败', { confirmButtonText: '确定' })
          return false
        }
        var req
        if (this.isEdit) {
          req = this.$axios.put('/showings/' + this.form.id, {
            appointmentTime: this.form.appointmentTime,
            remark: this.form.remark,
            version: this.form.version
          })
        } else {
          req = this.$axios.post('/showings', {
            houseId: Number(this.form.houseId),
            customerId: Number(this.form.customerId),
            brokerId: Number(this.form.brokerId),
            appointmentTime: this.form.appointmentTime,
            remark: this.form.remark
          })
        }
        req.then(() => {
          this.$alert(this.isEdit ? '修改成功' : '添加成功', { confirmButtonText: '确定' })
          this.formVisible = false
          this.init()
        }).catch((err) => {
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        })
      })
    },
    complete(row) {
      this.$axios.get('/showings/' + row.id).then((res) => {
        var d = res.data.data || row
        var now = new Date()
        var t = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0') + 'T' + String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0') + ':00'
        return this.$axios.put('/showings/' + row.id + '/complete', {
          actualTime: t,
          version: d.version,
          remark: '完成带看'
        })
      }).then(() => {
        this.$alert('操作成功', { confirmButtonText: '确定' })
        this.init()
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    del(row) {
      this.$confirm('确定删除该带看吗？删除后状态会变为已取消。', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        return this.$axios.get('/showings/' + row.id)
      }).then((res) => {
        var d = res.data.data || row
        return this.$axios.put('/showings/' + row.id + '/cancel', {
          reason: '删除预约',
          version: d.version
        })
      }).then(() => {
        this.$alert('已取消该带看', { confirmButtonText: '确定' })
        this.init()
      }).catch((err) => {
        if (err === 'cancel' || err === 'close') return
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    }
  }
}
</script>
<style>
</style>
