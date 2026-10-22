import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import House from '../views/House.vue'
import Owner from '../views/Owner.vue'
import Customer from '../views/Customer.vue'
import Showing from '../views/Showing.vue'
import Permission from '../views/Permission.vue'
import PriceAdvice from '../views/PriceAdvice.vue'

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
    path: '/house',
    component: House
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
