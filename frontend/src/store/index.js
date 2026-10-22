import { createStore } from 'vuex'

// Vuex 只放登录态，刷新后从 sessionStorage 还原
export default createStore({
  state: {
    isLogin: window.sessionStorage.getItem('user') == null ? '0' : window.sessionStorage.getItem('user'),
    role: window.sessionStorage.getItem('role') || 'BROKER',
    uname: window.sessionStorage.getItem('user') || '',
    token: window.sessionStorage.getItem('token') || '',
    brokerId: window.sessionStorage.getItem('brokerId') || '',
    storeId: window.sessionStorage.getItem('storeId') || ''
  },
  mutations: {
    // 登录成功写入账号、角色和 token
    changeLogin(state, data) {
      state.isLogin = data.uname
      state.role = data.role
      state.uname = data.uname
      state.token = data.token
      state.brokerId = data.brokerId || ''
      state.storeId = data.storeId || ''
      window.sessionStorage.setItem('user', data.uname)
      window.sessionStorage.setItem('role', data.role)
      window.sessionStorage.setItem('token', data.token)
      window.sessionStorage.setItem('brokerId', state.brokerId)
      window.sessionStorage.setItem('storeId', state.storeId)
    },
    // 退出时清掉本地登录信息
    logout(state) {
      state.isLogin = '0'
      state.role = 'BROKER'
      state.uname = ''
      state.token = ''
      state.brokerId = ''
      state.storeId = ''
      window.sessionStorage.removeItem('user')
      window.sessionStorage.removeItem('role')
      window.sessionStorage.removeItem('token')
      window.sessionStorage.removeItem('brokerId')
      window.sessionStorage.removeItem('storeId')
    }
  }
})
