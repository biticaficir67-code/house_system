import { createStore } from 'vuex'

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
