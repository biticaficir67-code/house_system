import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import House from '../views/House.vue'
import HouseMap from '../views/HouseMap.vue'
import Owner from '../views/Owner.vue'
import Customer from '../views/Customer.vue'
import Showing from '../views/Showing.vue'
import Permission from '../views/Permission.vue'
import PriceAdvice from '../views/PriceAdvice.vue'

// 各业务页路径，登录页不校验登录态
const Dashboard = () => import('../views/Dashboard.vue')

const routes = [
  {
    path: '/',
    redirect: '/login',
    meta: { auth: true }
  },
  {
    path: '/login',
    component: Login,
    meta: { auth: true }
  },
  {
    path: '/home',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    component: Dashboard
  },
  {
    path: '/reports/preview',
    component: Dashboard,
    props: { preview: true },
    // This public route renders independent samples and never calls business APIs.
    meta: { auth: true }
  },
  {
    path: '/house',
    component: House
  },
  {
    // 整页小区地图，从房源管理进入
    path: '/house-map',
    component: HouseMap
  },
  {
    path: '/addHouse',
    redirect: '/house'
  },
  {
    path: '/owner',
    component: Owner
  },
  {
    path: '/customer',
    component: Customer
  },
  {
    path: '/showing',
    component: Showing
  },
  {
    path: '/permission',
    component: Permission
  },
  {
    path: '/price-advice/:houseId',
    component: PriceAdvice
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
