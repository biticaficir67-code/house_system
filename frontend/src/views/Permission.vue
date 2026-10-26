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
      <el-pagination v-if="total > 50" style="margin-top:12px" layout="prev,pager,next" :total="total" :page-size="50" v-model:current-page="page" @current-change="loadUsers" />
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
      <p class="hint">开通时填写初始密码；登录名默认按档案编号生成，也可调整。审核时确认所属门店并填写意见。</p>
    </div>
    <el-dialog :title="review.approved ? '通过经纪人申请' : '退回经纪人申请'" v-model="reviewVisible" width="470px">
      <p>{{ review.displayName }} · {{ review.username }}</p>
      <el-form label-width="90px">
        <el-form-item v-if="review.approved" label="所属门店"><el-select v-model="review.storeId" :disabled="!!review.originalStoreId" placeholder="请选择门店"><el-option v-for="s in stores.filter(s=>s.enabled)" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item>
        <el-form-item label="审核意见"><el-input type="textarea" v-model="review.reason" :rows="3" maxlength="255" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reviewVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveReview">确认审核</el-button></template>
    </el-dialog>
    <el-dialog title="开通经纪人登录" v-model="openingVisible" width="470px">
      <p>{{ opening.displayName }} · {{ storeText(opening.storeId) }}</p>
      <el-form label-width="90px"><el-form-item v-if="openingChoose" label="经纪人"><el-select :model-value="opening.brokerId" @change="selectOpening"><el-option v-for="b in unboundList" :key="b.id" :label="b.brokerName || b.name" :value="b.id" /></el-select></el-form-item><el-form-item label="登录名"><el-input v-model="opening.username" maxlength="50" /></el-form-item><el-form-item label="初始密码"><el-input v-model="opening.password" type="password" show-password autocomplete="new-password" placeholder="10至72位，请单独告知经纪人" /></el-form-item></el-form>
      <template #footer><el-button @click="openingVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveOpening">确认开通</el-button></template>
    </el-dialog>
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
      accountStatus: '', page: 1, total: 0, saving: false,
      reviewVisible: false, review: {}, openingVisible: false, openingChoose: false, opening: {}
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
    // 门店编号换成门店名称
    storeText(storeId) {
      var i
      for (i = 0; i < this.stores.length; i++) {
        if (String(this.stores[i].id) === String(storeId)) return this.stores[i].name
      }
      return storeId || '--'
    },
    // 角色列表里取出管理员或经纪人
    roleText(row) {
      var roles = row.roles || []
      if (!roles.length) return '--'
      return roles[0].code === 'ADMIN' ? '管理员' : '经纪人'
    },
    // 未开通档案按编号生成登录名，如 bk03
    loginName(broker) {
      var n = String(broker.id)
      if (n.length < 2) n = '0' + n
      return 'bk' + n
    },
    // 同时拉门店、角色、登录账号和未开通档案
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
      this.loadUsers()
      this.$axios.get('/brokers', { params: { unbound: true } }).then(function (res) {
        that.unboundList = res.data.data || []
        if (!Array.isArray(that.unboundList)) that.unboundList = that.unboundList.records || []
      }).catch(function () {
        that.unboundList = []
      })
    },
    async loadUsers() {
      try { const r = await this.$axios.get('/users', { params: {page:this.page,size:50,accountStatus:this.accountStatus||undefined} }); this.userList = r.data.data.records; this.total = r.data.data.total }
      catch(error) { this.$message.error(this.errMsg(error)) }
    },
    openOne(broker, choose = false) {
      this.openingChoose = choose
      this.opening = {username:this.loginName(broker),password:'',displayName:broker.brokerName || broker.name,phone:broker.phone || '',storeId:broker.storeId,brokerId:broker.id}
      this.openingVisible = true
    },
    openUnbound() {
      if (!this.unboundList.length) return this.$message.info('没有未开通的档案')
      this.openOne(this.unboundList[0], true)
    },
    selectOpening(id) {
      const broker = this.unboundList.find(item => String(item.id) === String(id))
      if (broker) this.openOne(broker, true)
    },
    async saveOpening() {
      if (!this.brokerRoleId) return this.$message.warning('未找到经纪人角色，请刷新后重试')
      if (!/^[a-zA-Z0-9_.-]{3,50}$/.test(this.opening.username) || this.opening.password.length<10 || this.opening.password.length>72) return this.$message.warning('登录名需3至50位字母、数字或_.-，密码需10至72位')
      this.saving=true
      try { await this.$axios.post('/users', {...this.opening,roleIds:[this.brokerRoleId]}); this.opening.password='';this.openingVisible=false;this.$message.success('已开通，请将账号信息单独告知经纪人');this.init() }
      catch(error) { this.$message.error(this.errMsg(error)) }
      finally {this.saving=false}
    },
    pass(row) { this.review={...row,approved:true,storeId:row.storeId||'',originalStoreId:row.storeId,reason:''};this.reviewVisible=true },
    reject(row) { this.review={...row,approved:false,reason:''};this.reviewVisible=true },
    async saveReview() {
      if (!this.review.reason.trim() || this.review.reason.length>255) return this.$message.warning('请填写255字以内的审核意见')
      if (this.review.approved && !this.review.storeId) return this.$message.warning('请选择该经纪人的门店')
      this.saving=true
      try { await this.$axios.put('/users/'+this.review.id+'/review',{approved:this.review.approved,storeId:this.review.approved?this.review.storeId:undefined,reason:this.review.reason,version:this.review.version});this.reviewVisible=false;this.$message.success('审核已保存');this.init() }
      catch(error) { this.$message.error(this.errMsg(error));if(error.response?.status===409){this.reviewVisible=false;this.init()} }
      finally {this.saving=false}
    },
    resetPwd(row) {
      var that = this
      this.$prompt('请输入新密码（至少10位）', '重置密码：' + row.username, {
        confirmButtonText: '确定',
        cancelButtonText: '返回',
        inputType: 'password',
        inputPattern: /^.{10,72}$/,
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
