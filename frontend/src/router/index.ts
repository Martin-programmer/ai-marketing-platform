import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
    { path: '/clients', name: 'clients', component: () => import('@/views/ClientsView.vue') },
    { path: '/clients/:id', name: 'client-detail', component: () => import('@/views/ClientDetailView.vue') },
    { path: '/creatives', name: 'creatives', component: () => import('@/views/CreativesView.vue') },
    { path: '/campaigns', name: 'campaigns', component: () => import('@/views/CampaignsView.vue') },
    { path: '/suggestions', name: 'suggestions', component: () => import('@/views/SuggestionsView.vue') },
    { path: '/reports', name: 'reports', component: () => import('@/views/ReportsView.vue') },
    { path: '/meta', name: 'meta', component: () => import('@/views/MetaView.vue') },
  ]
})

export default router
