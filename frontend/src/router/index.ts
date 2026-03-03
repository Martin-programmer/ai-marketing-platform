import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true }
    },
    { path: '/', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
    { path: '/clients', name: 'clients', component: () => import('@/views/ClientsView.vue') },
    { path: '/clients/:id', name: 'client-detail', component: () => import('@/views/ClientDetailView.vue') },
    { path: '/creatives', name: 'creatives', component: () => import('@/views/CreativesView.vue') },
    { path: '/campaigns', name: 'campaigns', component: () => import('@/views/CampaignsView.vue') },
    { path: '/suggestions', name: 'suggestions', component: () => import('@/views/SuggestionsView.vue') },
    { path: '/reports', name: 'reports', component: () => import('@/views/ReportsView.vue') },
    { path: '/meta', name: 'meta', component: () => import('@/views/MetaView.vue') },
    {
      path: '/team',
      name: 'team',
      component: () => import('@/views/TeamView.vue'),
      meta: { requiredRole: ['AGENCY_ADMIN', 'OWNER_ADMIN'] }
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('@/views/AdminView.vue'),
      meta: { requiredRole: ['OWNER_ADMIN'] }
    },
  ]
})

// Navigation guard
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('accessToken')
  const isPublic = to.meta.public === true

  if (!isPublic && !token) {
    next('/login')
    return
  }

  if (to.path === '/login' && token) {
    next('/')
    return
  }

  // Role-based guard
  const requiredRole = to.meta.requiredRole as string[] | undefined
  if (requiredRole && requiredRole.length > 0) {
    const userStr = localStorage.getItem('user')
    const user = userStr ? JSON.parse(userStr) : null
    const userRole = user?.role || ''
    if (!requiredRole.includes(userRole)) {
      next('/')
      return
    }
  }

  next()
})

export default router
