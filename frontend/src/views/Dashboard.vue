<template>
  <div class="page-wrap dashboard-page">
    <NavMain v-if="!preview" />
    <div v-else class="report-preview-header">
      <strong>二手房业务 · 数据看板</strong>
      <span>示例数据</span>
      <router-link to="/login">返回登录</router-link>
    </div>
    <div class="reports-host">
      <div id="reports-root" ref="reportsRoot"></div>
    </div>
  </div>
</template>
<script>
import NavMain from '@/components/NavMain.vue'
import { ElMessage } from 'element-plus'
import reportEngine from '@/reports/reportEngine.mjs'
import '@/reports/report-base.css'
import '@/reports/report-layout.css'

export default {
  name: 'Dashboard',
  components: { NavMain },
  props: { preview: { type: Boolean, default: false } },
  mounted() {
    this.mountReports()
  },
  beforeUnmount() {
    reportEngine.dispose()
  },
  watch: {
    preview() {
      this.mountReports()
    }
  },
  methods: {
    mountReports() {
      reportEngine.mount(this.$refs.reportsRoot, {
        real: !this.preview,
        toast: message => ElMessage({ message, type: 'info' }),
        api: async path => {
          if (this.preview) throw new Error('示例预览不能访问业务接口')
          try {
            const response = await this.$axios.get(path, { timeout: 35000 })
            if (response.data.code !== 0) throw new Error(response.data.message || '报表请求失败')
            return response.data.data
          } catch (error) {
            throw new Error(error.response?.data?.message || error.message || '报表服务暂不可用')
          }
        }
      })
    }
  }
}
</script>
