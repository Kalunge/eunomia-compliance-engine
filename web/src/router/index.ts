import { createRouter, createWebHistory } from 'vue-router'

// Sprint 0 ships only the placeholder landing. The full route map
// (login, dashboard, regulations, policies, settings) lands in Sprint 1.
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
    },
  ],
})
