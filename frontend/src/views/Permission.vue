<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <div class="page-head">
      <h2>权限和角色管理</h2>
    </div>
    <div class="box1">
      <el-table :data="applyList" stripe border>
        <el-table-column prop="username" label="用户名"></el-table-column>
        <el-table-column prop="displayName" label="姓名"></el-table-column>
        <el-table-column prop="accountStatus" label="状态" width="120">
          <template #default="scope">
            <span v-if="scope.row.accountStatus === 'PENDING'" class="st-wait">待审核</span>
            <span v-else-if="scope.row.accountStatus === 'ACTIVE'" class="st-ok">已通过</span>
            <span v-else-if="scope.row.accountStatus === 'REJECTED'" class="st-no">已拒绝</span>
            <span v-else class="st-off">{{ scope.row.accountStatus }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="scope">
            <el-button size="small" type="primary" @click="pass(scope.row)" :disabled="scope.row.accountStatus !== 'PENDING'">通过</el-button>
            <el-button size="small" type="info" @click="reject(scope.row)" :disabled="scope.row.accountStatus !== 'PENDING'">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
export default {
  name: 'Permission',
  components: { NavMain },
  data() {
    return {
      applyList: [],
      stores: []
    }
  },
  created() {
    if (this.$store.state.role !== 'ADMIN') {
      this.$alert('只有管理员可以进入本页', { confirmButtonText: '确定' })
      this.$router.push('/dashboard')
      return
    }
    this.init()
  },
  methods: {
    errMsg(err) {
      if (err.response && err.response.data && err.response.data.message) return err.response.data.message
      return '请求失败'
    },
    init() {
      this.$axios.get('/users', { params: { page: 1, size: 50 } }).then((res) => {
        var d = res.data.data || {}
        this.applyList = d.records || []
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
      this.$axios.get('/stores').then((res) => {
        this.stores = res.data.data || []
        if (this.stores.records) this.stores = this.stores.records
      }).catch(() => {})
    },
    firstStoreId() {
      if (!this.stores.length) return null
      return this.stores[0].id
    },
    pass(row) {
      var storeId = this.firstStoreId()
      if (!storeId) {
        this.$alert('请先有门店', { confirmButtonText: '确定' })
        return
      }
      this.$axios.put('/users/' + row.id + '/review', {
        approved: true,
        storeId: storeId,
        reason: '通过',
        version: row.version
      }).then(() => {
        this.$alert('已通过', { confirmButtonText: '确定' })
        this.init()
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    reject(row) {
      var storeId = this.firstStoreId()
      this.$axios.put('/users/' + row.id + '/review', {
        approved: false,
        storeId: storeId,
        reason: '拒绝',
        version: row.version
      }).then(() => {
        this.$alert('已拒绝', { confirmButtonText: '确定' })
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
