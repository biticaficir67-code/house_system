<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <div class="page-head">
      <h2>客户管理</h2>
    </div>
    <div class="box1">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button @click="search">查询</el-button>
          <el-button type="primary" @click="openAdd">新增客户</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="tableData" stripe border>
        <el-table-column prop="id" label="编号" width="80"></el-table-column>
        <el-table-column prop="name" label="姓名"></el-table-column>
        <el-table-column prop="phone" label="电话"></el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <span :class="scope.row.status === 'ACTIVE' ? 'st-on' : (scope.row.status === 'DEAL' ? 'st-ok' : 'st-off')">{{ scope.row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" type="warning" @click="openEdit(scope.row)">修改</el-button>
            <el-button size="small" type="info" @click="del(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:12px" layout="prev, pager, next" :page-size="size" :current-page="page" :total="total" @current-change="pageChange"></el-pagination>
    </div>
    <el-dialog :title="formTitle" v-model="formVisible" width="460px">
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="门店" prop="storeId">
          <el-select v-model="form.storeId" placeholder="请选择门店" style="width:100%">
            <el-option v-for="s in stores" :key="s.id" :label="s.name" :value="s.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name"></el-input>
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone"></el-input>
        </el-form-item>
        <el-form-item label="备注">
          <el-input type="textarea" v-model="form.remark"></el-input>
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
  name: 'Customer',
  components: { NavMain },
  data() {
    return {
      searchForm: { keyword: '' },
      tableData: [],
      page: 1,
      size: 20,
      total: 0,
      formVisible: false,
      formTitle: '新增客户',
      isEdit: false,
      form: {},
      stores: [],
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
  methods: {
    loadStores() {
      this.$axios.get('/stores').then((res) => {
        var list = res.data.data || []
        this.stores = Array.isArray(list) ? list : (list.records || [])
      }).catch(() => {
        this.stores = []
      })
    },
    errMsg(err) {
      if (err.response && err.response.data && err.response.data.message) return err.response.data.message
      return '请求失败'
    },
    init() {
      this.$axios.get('/customers', {
        params: { keyword: this.searchForm.keyword || undefined, page: this.page, size: this.size }
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
      this.form = {
        storeId: this.isAdmin ? (this.stores[0] ? this.stores[0].id : '') : this.$store.state.storeId
      }
      this.formVisible = true
    },
    openEdit(row) {
      this.isEdit = true
      this.formTitle = '修改客户'
      this.$axios.get('/customers/' + row.id).then((res) => {
        this.form = Object.assign({}, res.data.data || row)
        this.formVisible = true
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
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
          remark: this.form.remark,
          storeId: this.form.storeId ? Number(this.form.storeId) : null,
          version: this.form.version
        }
        var req = this.isEdit
          ? this.$axios.put('/customers/' + this.form.id, body)
          : this.$axios.post('/customers', body)
        req.then(() => {
          this.$alert(this.isEdit ? '修改成功' : '添加成功', { confirmButtonText: '确定' })
          this.formVisible = false
          this.init()
        }).catch((err) => {
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        })
      })
    },
    del(row) {
      this.$confirm('确定删除吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.$axios.delete('/customers/' + row.id).then(() => {
          this.$alert('删除成功', { confirmButtonText: '确定' })
          this.init()
        }).catch((err) => {
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        })
      }).catch(() => {})
    }
  }
}
</script>
<style>
</style>
