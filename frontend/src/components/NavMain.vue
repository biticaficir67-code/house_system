<template>
  <div class="nav-wrap">
    <div class="brand-name">二手房业务</div>
    <el-menu
      :default-active="activeIndex"
      class="el-menu-demo"
      mode="horizontal"
      background-color="#ffffff"
      text-color="#2B2B2B"
      active-text-color="#B57920"
      router>
      <el-menu-item index="/dashboard">数据看板</el-menu-item>
      <el-menu-item index="/house">房源管理</el-menu-item>
      <el-menu-item index="/owner">业主管理</el-menu-item>
      <el-menu-item index="/customer">客户管理</el-menu-item>
      <el-menu-item index="/showing">带看管理</el-menu-item>
      <el-menu-item v-if="isAdmin" index="/permission">权限和角色管理</el-menu-item>
      <el-menu-item index="/login" @click="quit">退出</el-menu-item>
    </el-menu>
  </div>
</template>
<script>
export default {
  name: 'NavMain',
  data() {
    return {
      activeIndex: this.$route.path
    }
  },
  computed: {
    isAdmin() {
      return this.$store.state.role === 'ADMIN'
    }
  },
  watch: {
    $route(to) {
      this.activeIndex = to.path
    }
  },
  methods: {
    quit() {
      this.$axios.post('/auth/logout').catch(function () {})
      this.$store.commit('logout')
    }
  }
}
</script>
<style>
.nav-wrap {
  display: flex;
  align-items: center;
  background: #fff;
  border-top: 3px solid #b57920;
  border-bottom: 1px solid #c6c6c7;
  padding-left: 20px;
}
.brand-name {
  font-size: 14px;
  letter-spacing: 1px;
  color: #2b2b2b;
  margin-right: 16px;
  white-space: nowrap;
  flex-shrink: 0;
}
.nav-wrap .el-menu-demo {
  flex: 1;
  border-bottom: none !important;
}
</style>
