<template>
  <div class="login-page">
    <div class="login-visual">
      <div class="visual-mask"></div>
      <div class="visual-copy">
        <div class="visual-line"></div>
        <p class="visual-cn">二手房业务工作台</p>
      </div>
    </div>
    <div class="login-side">
      <div class="login-box">
        <div class="login-title">账号登录</div>
        <el-form ref="loginForm" :model="loginForm" :rules="rules" label-width="72px">
          <el-form-item label="用户名" prop="uname">
            <el-input v-model="loginForm.uname" placeholder="请输入用户名"></el-input>
          </el-form-item>
          <el-form-item label="密码" prop="upwd">
            <el-input v-model="loginForm.upwd" type="password" placeholder="请输入密码" show-password></el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="login-btn" @click="login(loginForm)" :loading="loadingbut">{{ loadingbuttext }}</el-button>
            <el-button @click="cancel">重置</el-button>
          </el-form-item>
          <div class="reg-link"><span @click="goReg">经纪人注册</span><span @click="openApplication" style="margin-left:18px">查看申请进度</span></div>
        </el-form>
      </div>
    </div>
    <el-dialog title="经纪人注册" v-model="regVisible" width="400px" align-center>
      <el-form ref="regForm" :model="regForm" :rules="regRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="regForm.username"></el-input>
        </el-form-item>
        <el-form-item label="姓名" prop="displayName">
          <el-input v-model="regForm.displayName"></el-input>
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="regForm.phone"></el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="regForm.password" type="password" show-password></el-input>
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="regForm.confirmPassword" type="password" show-password></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="register" :loading="applicationBusy">提交注册</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
    <el-dialog title="经纪人申请进度" v-model="applicationVisible" width="520px" align-center>
      <template v-if="!application">
        <p class="application-hint">使用注册时的账号密码查询申请；审核通过后请从登录入口进入系统。</p>
        <el-form label-width="85px" @submit.prevent="applicationSession">
          <el-form-item label="用户名"><el-input v-model="applicationLogin.username" autocomplete="username" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="applicationLogin.password" type="password" show-password autocomplete="current-password" /></el-form-item>
          <el-form-item><el-button type="primary" :loading="applicationBusy" @click="applicationSession">查询申请</el-button></el-form-item>
        </el-form>
      </template>
      <template v-else>
        <el-alert :title="'当前状态：'+({PENDING:'等待管理员审核',REJECTED:'已退回，请修改后重新提交',ACTIVE:'已通过，请登录'}[application.accountStatus] || application.accountStatus)" :type="application.accountStatus==='REJECTED'?'warning':'info'" :closable="false" />
        <p>{{ application.displayName }} · {{ application.username }}</p><p v-if="application.reviewReason || application.rejectReason">审核意见：{{ application.reviewReason || application.rejectReason }}</p>
        <el-form v-if="application.accountStatus==='REJECTED'" label-width="95px">
          <el-form-item label="姓名"><el-input v-model="reapply.displayName" maxlength="64" /></el-form-item>
          <el-form-item label="电话"><el-input v-model="reapply.phone" maxlength="32" /></el-form-item>
          <el-form-item label="新密码"><el-input v-model="reapply.password" type="password" show-password placeholder="可留空；修改时需10至72位" /></el-form-item>
          <el-form-item label="确认新密码"><el-input v-model="reapply.confirmPassword" type="password" show-password /></el-form-item>
          <el-form-item><el-button type="primary" :loading="applicationBusy" @click="resubmit">修改并重新申请</el-button></el-form-item>
        </el-form>
        <el-button :loading="applicationBusy" @click="refreshApplication">刷新进度</el-button>
        <el-button @click="clearApplication">切换申请账号</el-button>
        <el-timeline v-if="applicationHistory.length" style="margin-top:24px"><el-timeline-item v-for="item in applicationHistory" :key="item.id" :timestamp="String(item.createdAt||'').replace('T',' ')">{{ item.reason || item.action }}</el-timeline-item></el-timeline>
      </template>
    </el-dialog>
  </div>
</template>
<script>
import { errorText } from '@/business'
// 申请会话与业务登录分别保存，待审核账号仅能查询、修正自己的申请。
export default {
  name: 'Login',
  data() {
    return {
      loginForm: {
        uname: '',
        upwd: ''
      },
      rules: {
        uname: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        upwd: [{ required: true, message: '请输入密码', trigger: 'blur' }]
      },
      loadingbut: false,
      loadingbuttext: '登录',
      regVisible: false,
      applicationVisible: false,
      applicationBusy: false,
      application: null,
      applicationHistory: [],
      applicationLogin: { username: '', password: '' },
      reapply: { displayName: '', phone: '', password: '', confirmPassword: '' },
      regForm: {
        username: '',
        displayName: '',
        phone: '',
        password: '',
        confirmPassword: ''
      },
      regRules: {
        username: [{ required: true, pattern: /^[a-zA-Z0-9_.-]{3,50}$/, message: '用户名需3至50位字母、数字或_.-', trigger: 'blur' }],
        displayName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
        password: [{ required: true, min: 10, max: 72, message: '密码需10至72位', trigger: 'blur' }],
        confirmPassword: [{ validator: (rule, value, callback) => value && value === this.regForm.password ? callback() : callback(new Error('两次密码不一致')), trigger: 'blur' }]
      }
    }
  },
  methods: {
    errMsg(err) {
      if (err.response && err.response.data && err.response.data.message) {
        return err.response.data.message
      }
      return '请求失败'
    },
    // 调 /auth/login，成功后把角色和 token 放进 Vuex
    login(loginForm) {
      this.$refs['loginForm'].validate((valid) => {
        if (!valid) {
          this.$alert('表单验证失败', { confirmButtonText: '确定' })
          return false
        }
        this.loadingbut = true
        this.loadingbuttext = '登录中...'
        this.$axios.post('/auth/login', {
          username: loginForm.uname,
          password: loginForm.upwd
        }, {applicationOnly: true}).then((res) => {
          this.loadingbut = false
          this.loadingbuttext = '登录'
          if (res.data.code !== 0) {
            this.$alert(res.data.message || '登录失败', { confirmButtonText: '确定' })
            return
          }
          var data = res.data.data
          var user = data.user || {}
          var roles = user.roles || []
          var role = 'BROKER'
          if (roles.indexOf('ADMIN') !== -1 || roles.includes && roles.includes('ADMIN')) {
            role = 'ADMIN'
          }
          if (Object.prototype.toString.call(roles) === '[object Array]') {
            role = roles.indexOf('ADMIN') !== -1 ? 'ADMIN' : 'BROKER'
          }
          this.$store.commit('changeLogin', {
            uname: user.displayName || user.username || loginForm.uname,
            role: role,
            token: data.token,
            brokerId: user.brokerId ? String(user.brokerId) : '',
            storeId: user.storeId ? String(user.storeId) : ''
          })
          var path = this.$route.query.redirect
          this.$router.replace({ path: path === '/' || path === undefined ? '/dashboard' : path })
        }).catch((err) => {
          this.loadingbut = false
          this.loadingbuttext = '登录'
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        })
      })
    },
    // 打开经纪人注册窗口
    goReg() {
      this.regVisible = true
    },
    // 提交经纪人注册申请
    register() {
      this.$refs['regForm'].validate((valid) => {
        if (!valid) {
          this.$alert('表单验证失败', { confirmButtonText: '确定' })
          return
        }
        this.applicationBusy = true
        this.$axios.post('/auth/register', {
          username: this.regForm.username,
          password: this.regForm.password,
          confirmPassword: this.regForm.confirmPassword,
          displayName: this.regForm.displayName,
          phone: this.regForm.phone
        }, {applicationOnly: true}).then((res) => {
          if (res.data.code !== 0) {
            this.$alert(res.data.message || '注册失败', { confirmButtonText: '确定' })
            return
          }
          this.acceptTicket(res.data.data)
          this.regForm.password = ''; this.regForm.confirmPassword = ''
          this.$message.success('注册已提交，请等待管理员审核')
          this.regVisible = false
          this.applicationVisible = true
        }).catch((err) => {
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        }).finally(() => { this.applicationBusy = false })
      })
    },
    acceptTicket(data) {
      window.sessionStorage.setItem('applicationToken', data.applicationToken)
      this.setApplication(data.application)
      this.applicationHistory = []
    },
    setApplication(data) {
      this.application = data
      this.reapply = { displayName: data.displayName || '', phone: data.phone || '', password: '', confirmPassword: '' }
    },
    async openApplication() {
      this.applicationVisible = true
      if (window.sessionStorage.getItem('applicationToken')) await this.refreshApplication()
    },
    clearApplication() {
      window.sessionStorage.removeItem('applicationToken'); this.application = null; this.applicationHistory = []; this.applicationLogin.password = ''; this.reapply.password = ''; this.reapply.confirmPassword = ''
    },
    async applicationSession() {
      if (!this.applicationLogin.username || !this.applicationLogin.password) return this.$message.warning('请输入注册账号和密码')
      this.applicationBusy = true
      try { const r = await this.$axios.post('/auth/application-session', this.applicationLogin, {applicationOnly:true}); this.acceptTicket(r.data.data); this.applicationLogin.password = ''; await this.refreshApplication() }
      catch(error) { this.$message.error(errorText(error)) }
      finally { this.applicationBusy = false }
    },
    async refreshApplication() {
      const token = window.sessionStorage.getItem('applicationToken'); if (!token) return
      this.applicationBusy = true
      try { const r = await this.$axios.get('/auth/application', {applicationOnly:true, headers:{'X-Application-Token':token}}); this.setApplication(r.data.data.application); this.applicationHistory = r.data.data.history?.records || r.data.data.history || [] }
      catch(error) { if (error.response?.status===401) this.clearApplication(); this.$message.info(error.response?.status===401 ? '申请凭证已变化或已审核，请重新查询，已通过的账号可直接登录' : errorText(error)) }
      finally { this.applicationBusy = false }
    },
    async resubmit() {
      const form = this.reapply
      if (!form.displayName.trim()) return this.$message.warning('请填写姓名')
      if ((form.password || form.confirmPassword) && (form.password.length < 10 || form.password.length > 72 || form.password !== form.confirmPassword)) return this.$message.warning('新密码需10至72位，且两次输入一致')
      this.applicationBusy = true
      try { const r = await this.$axios.put('/auth/application', {displayName:form.displayName,phone:form.phone,version:this.application.version,...(form.password?{password:form.password,confirmPassword:form.confirmPassword}:{})}, {applicationOnly:true,headers:{'X-Application-Token':window.sessionStorage.getItem('applicationToken')}}); this.acceptTicket(r.data.data); this.$message.success('已重新提交申请') }
      catch(error) { if(error.response?.status===401)this.clearApplication(); this.$message.error(errorText(error)) }
      finally { this.applicationBusy = false }
    },
    // 清空登录表单
    cancel() {
      this.$refs['loginForm'].resetFields()
    }
  }
}
</script>
<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  background: #1c1916;
}
.login-visual {
  flex: 1.35;
  position: relative;
  background-color: #1c1916;
  background-image: url("https://images.unsplash.com/photo-1600210492486-724fe5c67fb0?auto=format&fit=crop&w=2000&q=80");
  background-size: cover;
  background-position: center;
}
.visual-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(18, 16, 14, .55) 0%, rgba(18, 16, 14, .18) 55%, rgba(18, 16, 14, .45) 100%);
}
.visual-copy {
  position: absolute;
  left: 48px;
  bottom: 56px;
  color: #fff;
  z-index: 1;
  max-width: 420px;
  text-align: left;
}
.visual-line {
  width: 36px;
  height: 2px;
  background: #f7b500;
  margin-bottom: 16px;
}
.visual-cn {
  margin: 10px 0 12px;
  font-size: 28px;
  font-weight: 600;
  letter-spacing: 2px;
}
.login-side {
  width: 440px;
  flex-shrink: 0;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.login-box {
  width: 320px;
}
.login-title {
  font-size: 24px;
  color: #2b2b2b;
  letter-spacing: 1px;
  margin-bottom: 24px;
}
.login-btn {
  min-width: 88px;
}
.reg-link {
  text-align: right;
  color: #b57920;
  cursor: pointer;
  font-size: 12px;
}
@media (max-width: 900px) {
  .login-visual {
    display: none;
  }
  .login-side {
    width: 100%;
  }
}
</style>
