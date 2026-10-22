import { createApp } from 'vue'
import axios from 'axios'
import App from './App.vue'
import router from './router'
import store from './store'
import installElementPlus from './plugins/element'
import './assets/theme.css'

const vapp = createApp(App)
// 请求都走 /api，开发时由 Vite 转到 8080
axios.defaults.baseURL = '/api'
axios.interceptors.request.use(function (config) {
  var token = window.sessionStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})
axios.interceptors.response.use(
  function (res) {
    return res
  },
  function (err) {
    // token 失效回登录页
    if (err.response && err.response.status === 401) {
      store.commit('logout')
      router.push('/login')
    }
    return Promise.reject(err)
  }
)
vapp.config.globalProperties.$axios = axios
vapp.config.productionTip = false
installElementPlus(vapp)
vapp.use(store).use(router).mount('#app')

// 未登录不能进业务页
router.beforeEach((to, from, next) => {
  if (!to.matched.some(m => m.meta.auth)) {
    if (store.state.isLogin == '0') {
      alert('您没有登录，无权访问！')
      next({
        path: '/login',
        query: { redirect: to.fullPath }
      })
    } else {
      next()
    }
  } else {
    next()
  }
})
