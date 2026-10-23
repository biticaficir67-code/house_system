<template>
  <div class="page-wrap">
    <NavMain></NavMain>
    <div class="page-head">
      <h2>权限和角色管理</h2>
      <div class="sub">登录账号与经纪人档案对应；未开通的档案可在本页开通</div>
    </div>
    <div class="box1">
      <el-form :inline="true">
        <el-form-item>
          <el-button type="primary" @click="openUnbound" :disabled="!unboundList.length">为未开通档案开通登录</el-button>
        </el-form-item>
      </el-form>
      <!-- 已有登录账号：审核、重置密码 -->
      <div class="sec-title">登录账号</div>
      <el-table :data="userList" stripe border>
        <el-table-column prop="username" label="用户名" width="140"></el-table-column>
        <el-table-column prop="displayName" label="姓名" width="120"></el-table-column>
        <el-table-column label="身份" width="100">
          <template #default="scope">{{ roleText(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="门店" width="120">
          <template #default="scope">{{ storeText(scope.row.storeId) }}</template>
        </el-table-column>
        <el-table-column prop="brokerId" label="经纪人档案" width="110"></el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <span v-if="scope.row.accountStatus === 'PENDING'" class="st-wait">待审核</span>
            <span v-else-if="scope.row.accountStatus === 'ACTIVE'" class="st-ok">已启用</span>
            <span v-else-if="scope.row.accountStatus === 'REJECTED'" class="st-no">已拒绝</span>
            <span v-else class="st-off">{{ scope.row.accountStatus }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="scope">
            <el-button size="small" type="primary" v-if="scope.row.accountStatus === 'PENDING'" @click="pass(scope.row)">通过</el-button>
            <el-button size="small" type="info" v-if="scope.row.accountStatus === 'PENDING'" @click="reject(scope.row)">拒绝</el-button>
            <el-button size="small" type="warning" v-if="scope.row.accountStatus === 'ACTIVE' && roleText(scope.row) === '经纪人'" @click="resetPwd(scope.row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>
      <!-- 有业务档案但还没有登录账号 -->
      <div class="sec-title" style="margin-top:22px">未开通登录的经纪人档案</div>
      <el-table :data="unboundList" stripe border>
        <el-table-column prop="id" label="档案编号" width="100"></el-table-column>
        <el-table-column prop="brokerName" label="姓名" width="120"></el-table-column>
        <el-table-column prop="phone" label="电话" width="140"></el-table-column>
        <el-table-column label="门店" width="140">
          <template #default="scope">{{ storeText(scope.row.storeId) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openOne(scope.row)">开通登录</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="hint">新开通账号的初始密码均为 Broker123456，登录名按档案编号生成，例如档案 3 为 bk03。</p>
    </div>
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
// 管理员查看登录账号，给已有经纪人档案开通登录
export default {
  name: 'Permission',
  components: { NavMain },
  data() {
    return {
      userList: [],
      unboundList: [],
      stores: [],
      brokerRoleId: null,
      defaultPwd: 'Broker123456'
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
    storeText(storeId) {
      var i
      for (i = 0; i < this.stores.length; i++) {
        if (String(this.stores[i].id) === String(storeId)) return this.stores[i].name
      }
      return storeId || '--'
    },
    roleText(row) {
      var roles = row.roles || []
      if (!roles.length) return '--'
      return roles[0].code === 'ADMIN' ? '管理员' : '经纪人'
    },
    loginName(broker) {
      var n = String(broker.id)
      if (n.length < 2) n = '0' + n
      return 'bk' + n
    },
    init() {
      var that = this
      this.$axios.get('/stores').then(function (res) {
        var list = res.data.data || []
        that.stores = Array.isArray(list) ? list : (list.records || [])
      }).catch(function () {
        that.stores = []
      })
      this.$axios.get('/roles').then(function (res) {
        var list = res.data.data || []
        var i
        that.brokerRoleId = null
        for (i = 0; i < list.length; i++) {
          if (list[i].code === 'BROKER') that.brokerRoleId = list[i].id
        }
      }).catch(function () {})
      this.$axios.get('/users', { params: { page: 1, size: 50 } }).then(function (res) {
        var d = res.data.data || {}
        that.userList = d.records || []
      }).catch(function (err) {
        that.$alert(that.errMsg(err), { confirmButtonText: '确定' })
      })
      this.$axios.get('/brokers', { params: { unbound: true } }).then(function (res) {
        that.unboundList = res.data.data || []
        if (!Array.isArray(that.unboundList)) that.unboundList = that.unboundList.records || []
      }).catch(function () {
        that.unboundList = []
      })
    },
    // 给一条经纪人档案开通登录账号
    openOne(broker) {
      var that = this
      if (!this.brokerRoleId) {
        this.$alert('未找到经纪人角色', { confirmButtonText: '确定' })
        return Promise.reject()
      }
      return this.$axios.post('/users', {
        username: this.loginName(broker),
        password: this.defaultPwd,
        displayName: broker.brokerName || broker.name,
        phone: broker.phone || '',
        storeId: Number(broker.storeId),
        brokerId: Number(broker.id),
        roleIds: [Number(this.brokerRoleId)]
      }).then(function () {
        return true
      }).catch(function (err) {
        that.$alert(that.errMsg(err), { confirmButtonText: '确定' })
        return Promise.reject(err)
      })
    },
    openUnbound() {
      var that = this
      if (!this.unboundList.length) {
        this.$alert('没有未开通的档案', { confirmButtonText: '确定' })
        return
      }
      var run = function () {
        var chain = Promise.resolve()
        that.unboundList.forEach(function (b) {
          chain = chain.then(function () {
            return that.openOne(b)
          })
        })
        return chain.then(function () {
          that.$alert('已开通，初始密码 Broker123456', { confirmButtonText: '确定' })
          that.init()
        }).catch(function () {
          that.init()
        })
      }
      if (this.brokerRoleId) {
        run()
        return
      }
      this.$axios.get('/roles').then(function (res) {
        var list = res.data.data || []
        var i
        for (i = 0; i < list.length; i++) {
          if (list[i].code === 'BROKER') that.brokerRoleId = list[i].id
        }
        if (!that.brokerRoleId) {
          that.$alert('未找到经纪人角色', { confirmButtonText: '确定' })
          return
        }
        run()
      })
    },
    firstStoreId() {
      if (!this.stores.length) return null
      return this.stores[0].id
    },
    pass(row) {
      var storeId = row.storeId || this.firstStoreId()
      if (!storeId) {
        this.$alert('请先有门店', { confirmButtonText: '确定' })
        return
      }
      this.$axios.put('/users/' + row.id + '/review', {
        approved: true,
        storeId: Number(storeId),
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
      var storeId = row.storeId || this.firstStoreId()
      this.$axios.put('/users/' + row.id + '/review', {
        approved: false,
        storeId: storeId ? Number(storeId) : undefined,
        reason: '拒绝',
        version: row.version
      }).then(() => {
        this.$alert('已拒绝', { confirmButtonText: '确定' })
        this.init()
      }).catch((err) => {
        this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
      })
    },
    resetPwd(row) {
      var that = this
      this.$prompt('请输入新密码（至少10位）', '重置密码：' + row.username, {
        confirmButtonText: '确定',
        cancelButtonText: '返回',
        inputPattern: /.{10,}/,
        inputErrorMessage: '密码至少10位'
      }).then(function (r) {
        return that.$axios.put('/users/' + row.id + '/password', { password: r.value })
      }).then(function () {
        that.$alert('密码已重置', { confirmButtonText: '确定' })
      }).catch(function (err) {
        if (err === 'cancel' || err === 'close') return
        that.$alert(that.errMsg(err), { confirmButtonText: '确定' })
      })
    }
  }
}
</script>
<style scoped>
.sec-title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
}
.hint {
  margin-top: 12px;
  color: #96a3bf;
  font-size: 12px;
}
</style>
