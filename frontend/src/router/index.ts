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
    {
      path: '/accept-invite',
      name: 'accept-invite',
      component: () => import('@/views/AcceptInviteView.vue'),
      meta: { public: true }
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('@/views/ForgotPasswordView.vue'),
      meta: { public: true }
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('@/views/ResetPasswordView.vue'),
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
    { path: '/audit', name: 'audit', component: () => import('@/views/AuditView.vue') },
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
    // Owner routes
    {
      path: '/owner',
      name: 'owner-dashboard',
      component: () => import('@/views/owner/OwnerDashboardView.vue'),
      meta: { requiredRole: ['OWNER_ADMIN'] }
    },
    {
      path: '/owner/agencies',
      name: 'owner-agencies',
      component: () => import('@/views/owner/OwnerDashboardView.vue'),
      meta: { requiredRole: ['OWNER_ADMIN'] }
    },
    {
      path: '/owner/agencies/:id',
      name: 'owner-agency-detail',
      component: () => import('@/views/owner/OwnerAgencyDetailView.vue'),
      meta: { requiredRole: ['OWNER_ADMIN'] }
    },
    // Client Portal routes (CLIENT_USER only)
    {
      path: '/portal',
      name: 'portal-dashboard',
      component: () => import('@/views/portal/PortalDashboardView.vue'),
      meta: { requiredRole: ['CLIENT_USER'] }
    },
    {
      path: '/portal/reports',
      name: 'portal-reports',
      component: () => import('@/views/portal/PortalReportsView.vue'),
      meta: { requiredRole: ['CLIENT_USER'] }
    },
    {
      path: '/portal/campaigns',
      name: 'portal-campaigns',
      component: () => import('@/views/portal/PortalCampaignsView.vue'),
      meta: { requiredRole: ['CLIENT_USER'] }
    },
    {
      path: '/portal/suggestions',
      name: 'portal-suggestions',
      component: () => import('@/views/portal/PortalSuggestionsView.vue'),
      meta: { requiredRole: ['CLIENT_USER'] }
    },
    {
      path: '/portal/profile',
      name: 'portal-profile',
      component: () => import('@/views/portal/PortalProfileView.vue'),
      meta: { requiredRole: ['CLIENT_USER'] }
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
    const userStr = localStorage.getItem('user')
    const u = userStr ? JSON.parse(userStr) : null
    if (u?.role === 'CLIENT_USER') {
      next('/portal')
    } else if (u?.role === 'OWNER_ADMIN') {
      next('/owner')
    } else {
      next('/')
    }
    return
  }

  // CLIENT_USER can only access /portal/* and /login
  const userStr = localStorage.getItem('user')
  const user = userStr ? JSON.parse(userStr) : null
  if (token && user?.role === 'CLIENT_USER' && !to.path.startsWith('/portal') && to.path !== '/login') {
    next('/portal')
    return
  }

  // OWNER_ADMIN: redirect / to /owner
  if (token && user?.role === 'OWNER_ADMIN' && to.path === '/') {
    next('/owner')
    return
  }

  // Agency users cannot access /portal/* or /owner/*
  if (token && user && user.role !== 'CLIENT_USER' && to.path.startsWith('/portal')) {
    next('/')
    return
  }
  if (token && user && user.role !== 'OWNER_ADMIN' && to.path.startsWith('/owner')) {
    next('/')
    return
  }

  // Role-based guard
  const requiredRole = to.meta.requiredRole as string[] | undefined
  if (requiredRole && requiredRole.length > 0) {
    const userRole = user?.role || ''
    if (!requiredRole.includes(userRole)) {
      if (userRole === 'CLIENT_USER') {
        next('/portal')
      } else {
        next('/')
      }
      return
    }
  }

  next()
})

export default router
