<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <div class="page-head">
      <h2>带看管理</h2>
    </div>
    <div class="box1">
      <el-form :inline="true">
        <el-form-item>
          <el-button type="primary" @click="openAdd">新增带看</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="tableData" stripe border>
        <el-table-column prop="id" label="编号" width="80"></el-table-column>
        <el-table-column prop="houseId" label="房源" width="90"></el-table-column>
        <el-table-column prop="customerId" label="客户" width="90"></el-table-column>
        <el-table-column prop="appointmentTime" label="预约时间"></el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <span :class="scope.row.status === 'SCHEDULED' ? 'st-wait' : (scope.row.status === 'COMPLETED' ? 'st-ok' : 'st-off')">{{ showStatus(scope.row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="scope">
            <el-button size="small" type="success" v-if="scope.row.status === 'SCHEDULED'" @click="complete(scope.row)">完成</el-button>
            <el-button size="small" type="info" v-if="scope.row.status === 'SCHEDULED'" @click="cancelRow(scope.row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:12px" layout="prev, pager, next" :page-size="size" :current-page="page" :total="total" @current-change="pageChange"></el-pagination>
    </div>
    <el-dialog title="新增带看" v-model="formVisible" width="460px">
      <el-form ref="form" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="房源编号" prop="houseId">
          <el-input v-model="form.houseId"></el-input>
        </el-form-item>
        <el-form-item label="客户编号" prop="customerId">
          <el-input v-model="form.customerId"></el-input>
        </el-form-item>
        <el-form-item label="预约时间" prop="appointmentTime">
          <el-input v-model="form.appointmentTime" placeholder="2026-12-01T15:00:00"></el-input>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveForm">确定</el-button>
          <el-button @click="formVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
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
      page: 1,
      size: 20,
      total: 0,
      formVisible: false,
      form: {},
      rules: {
        houseId: [{ required: true, message: '请输入房源编号', trigger: 'blur' }],
        customerId: [{ required: true, message: '请输入客户编号', trigger: 'blur' }],
        appointmentTime: [{ required: true, message: '请输入预约时间', trigger: 'blur' }]
      }
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
      return s
    },
    init() {
      this.$axios.get('/showings', { params: { page: this.page, size: this.size } }).then((res) => {
        var d = res.data.data || {}
        this.tableData = d.records || []
        this.total = d.total || 0
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    pageChange(p) {
      this.page = p
      this.init()
    },
    openAdd() {
      this.form = {
        houseId: '',
        customerId: '',
        appointmentTime: '',
        remark: ''
      }
      this.formVisible = true
    },
    saveForm() {
      this.$refs['form'].validate((valid) => {
        if (!valid) {
          this.$alert('表单验证失败', { confirmButtonText: '确定' })
          return false
        }
        this.$axios.post('/showings', {
          houseId: Number(this.form.houseId),
          customerId: Number(this.form.customerId),
          brokerId: Number(this.$store.state.brokerId),
          appointmentTime: this.form.appointmentTime,
          remark: this.form.remark
        }).then(() => {
          this.$alert('添加成功', { confirmButtonText: '确定' })
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
    cancelRow(row) {
      this.$axios.get('/showings/' + row.id).then((res) => {
        var d = res.data.data || row
        return this.$axios.put('/showings/' + row.id + '/cancel', {
          reason: '取消预约',
          version: d.version
        })
      }).then(() => {
        this.$alert('已取消', { confirmButtonText: '确定' })
        this.init()
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    }
  }
}
</script>
<style>
</style>
