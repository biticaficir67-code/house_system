<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <div class="page-head">
      <h2>带看管理</h2>
    </div>
    <div class="box1">
      <!-- 按客户、小区关键字和状态查询 -->
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
        <el-table-column label="操作" width="340">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openDetail(scope.row)">详情</el-button>
            <el-button size="small" type="warning" v-if="scope.row.status === 'SCHEDULED'" @click="openEdit(scope.row)">改期</el-button>
            <el-button size="small" type="success" v-if="scope.row.status === 'SCHEDULED'" @click="complete(scope.row)">完成</el-button>
            <el-button size="small" type="info" v-if="scope.row.status === 'SCHEDULED'" @click="cancelShow(scope.row)">取消</el-button>
            <el-button size="small" type="warning" v-if="scope.row.status === 'COMPLETED'" @click="openFeedback(scope.row)">回访</el-button>
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

    <!-- 新增要选房源客户经纪人；修改只能改期 -->
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
        <el-descriptions-item label="客户意向" v-if="current.intention">{{ current.intention }}</el-descriptions-item>
        <el-descriptions-item label="回访内容" v-if="current.feedback">{{ current.feedback }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog title="带看回访" v-model="feedbackVisible" width="480px">
      <el-form ref="feedbackForm" :model="feedbackForm" :rules="feedbackRules" label-width="110px">
        <el-form-item label="客户意向" prop="intention">
          <el-select v-model="feedbackForm.intention" placeholder="请选择" style="width:100%">
            <el-option label="高" value="HIGH"></el-option>
            <el-option label="中" value="MEDIUM"></el-option>
            <el-option label="低" value="LOW"></el-option>
            <el-option label="暂不确定" value="UNKNOWN"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="回访内容" prop="content">
          <el-input type="textarea" v-model="feedbackForm.content" rows="3"></el-input>
        </el-form-item>
        <el-form-item label="下次联系" prop="nextContactAt">
          <el-input v-model="feedbackForm.nextContactAt" placeholder="2026-11-01T10:00:00"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveFeedback">确定</el-button>
          <el-button @click="feedbackVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
// 带看预约：查询、详情、新增、改期、完成、取消
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
      feedbackVisible: false,
      isEdit: false,
      formTitle: '新增带看',
      form: {},
      current: {},
      feedbackForm: {},
      rules: {
        houseId: [{ required: true, message: '请选择房源', trigger: 'change' }],
        customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
        brokerId: [{ required: true, message: '请选择经纪人', trigger: 'change' }],
        appointmentTime: [{ required: true, message: '请输入预约时间', trigger: 'blur' }]
      },
      feedbackRules: {
        intention: [{ required: true, message: '请选择客户意向', trigger: 'change' }],
        content: [{ required: true, message: '请填写回访内容', trigger: 'blur' }],
        nextContactAt: [{ required: true, message: '请填写下次联系时间', trigger: 'blur' }]
      }
    }
  },
  computed: {
    // 新增带看只能选在售房源
    saleHouses() {
      return this.houseList.filter(function (h) {
        return h.status === 'ON_SALE'
      })
    },
    // 客户下拉跟房源门店对齐
    activeCustomers() {
      var storeId = this.currentHouseStore()
      return this.customerList.filter(function (c) {
        if (c.status && c.status !== 'ACTIVE') return false
        if (storeId && String(c.storeId) !== String(storeId)) return false
        return true
      })
    },
    // 经纪人下拉跟房源门店对齐
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
    // 后端英文状态改成中文
    showStatus(s) {
      if (s === 'SCHEDULED') return '待看'
      if (s === 'COMPLETED') return '已完成'
      if (s === 'CANCELLED') return '已取消'
      return s || '--'
    },
    // 预约时间去掉 T，只显示到分钟
    timeText(row) {
      var t = (row && (row.appointmentTime || row.showTime || row.completedTime)) || ''
      if (!t) return '--'
      return String(t).replace('T', ' ').substring(0, 16)
    },
    // 用来按时间从近到远排序
    timeVal(row) {
      var t = row.appointmentTime || row.showTime || row.completedTime || ''
      var n = Date.parse(t)
      return isNaN(n) ? 0 : n
    },
    // 列表上的房源写成 编号+小区+区域+面积
    houseLabel(h) {
      if (!h) return ''
      var bits = ['#' + h.id]
      if (h.communityName) bits.push(h.communityName)
      if (h.district) bits.push(h.district)
      if (h.buildingAreaSqm) bits.push(h.buildingAreaSqm + '㎡')
      return bits.join('　')
    },
    // 客户和经纪人要跟所选房源同一门店
    currentHouseStore() {
      var id = this.form && this.form.houseId
      if (!id) return ''
      var hit = this.houseList.filter(function (h) {
        return String(h.id) === String(id)
      })[0]
      return hit ? hit.storeId : ''
    },
    // 换房源后门店变了，客户和经纪人重选
    onHouseChange() {
      this.form.customerId = ''
      this.form.brokerId = ''
    },
    // 接口按页返回，这里把各页拼成完整列表
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
    // 当前页切一段出来给表格
    applyPage() {
      var start = (this.page - 1) * this.size
      this.tableData = this.filteredRows.slice(start, start + this.size)
    },
    // 前台按关键字和状态过滤后再分页
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
    // 同时拉带看、房源、客户，用来显示姓名和小区
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
    // 翻页只切本地已经拼好的列表
    pageChange(p) {
      this.page = p
      this.applyPage()
    },
    // 新增带看要选房源、客户、经纪人
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
    // 改期要带 version，避免两人同时改冲突
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
    // 详情再拉一次，带上取消原因和回访
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
    // 新增走 POST，改期走 PUT
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
    // 把待看改成已完成
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
    // 待看记录改为已取消
    cancelShow(row) {
      var that = this
      this.$prompt('请填写取消原因', '取消带看', {
        confirmButtonText: '确定',
        cancelButtonText: '返回',
        inputPattern: /\S+/,
        inputErrorMessage: '取消原因不能为空'
      }).then(function (r) {
        return that.$axios.get('/showings/' + row.id).then(function (res) {
          var d = res.data.data || row
          return that.$axios.put('/showings/' + row.id + '/cancel', {
            reason: r.value,
            version: d.version
          })
        })
      }).then(function () {
        that.$alert('已取消该带看', { confirmButtonText: '确定' })
        that.init()
      }).catch(function (err) {
        if (err === 'cancel' || err === 'close') return
        that.$alert(that.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    // 已完成带看补一条客户回访
    openFeedback(row) {
      var that = this
      this.$axios.get('/showings/' + row.id).then(function (res) {
        var d = res.data.data || row
        var n = new Date()
        n.setDate(n.getDate() + 3)
        var t = n.getFullYear() + '-' + String(n.getMonth() + 1).padStart(2, '0') + '-' + String(n.getDate()).padStart(2, '0') + 'T10:00:00'
        that.feedbackForm = {
          id: d.id,
          version: d.version,
          intention: 'MEDIUM',
          content: '',
          nextContactAt: t
        }
        that.feedbackVisible = true
      }).catch(function (err) {
        that.$alert(that.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    saveFeedback() {
      var that = this
      this.$refs['feedbackForm'].validate(function (valid) {
        if (!valid) {
          that.$alert('表单验证失败', { confirmButtonText: '确定' })
          return false
        }
        that.$axios.post('/showings/' + that.feedbackForm.id + '/follow-ups', {
          content: that.feedbackForm.content,
          intention: that.feedbackForm.intention,
          nextContactAt: that.feedbackForm.nextContactAt,
          version: that.feedbackForm.version
        }).then(function () {
          that.$alert('回访已保存', { confirmButtonText: '确定' })
          that.feedbackVisible = false
          that.init()
        }).catch(function (err) {
          that.$alert(that.errMsg(err), { confirmButtonText: '确定' })
        })
      })
    }
  }
}
</script>
<style>
</style>
