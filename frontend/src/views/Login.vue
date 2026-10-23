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
          <div class="reg-link" @click="goReg">经纪人注册</div>
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
          <el-button type="primary" @click="register">提交注册</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>
<script>
// 登录拿 token，经纪人可以在本页提交注册
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
      regForm: {
        username: '',
        displayName: '',
        phone: '',
        password: '',
        confirmPassword: ''
      },
      regRules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        displayName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
        password: [{ required: true, min: 10, message: '密码至少10位', trigger: 'blur' }],
        confirmPassword: [{ required: true, message: '请再次输入密码', trigger: 'blur' }]
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
        }).then((res) => {
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
        this.$axios.post('/auth/register', {
          username: this.regForm.username,
          password: this.regForm.password,
          confirmPassword: this.regForm.confirmPassword,
          displayName: this.regForm.displayName,
          phone: this.regForm.phone
        }).then((res) => {
          if (res.data.code !== 0) {
            this.$alert(res.data.message || '注册失败', { confirmButtonText: '确定' })
            return
          }
          this.$alert('提交成功', { confirmButtonText: '确定' })
          this.regVisible = false
        }).catch((err) => {
          this.$alert(this.errMsg(err), { confirmButtonText: '确定' })
        })
      })
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
