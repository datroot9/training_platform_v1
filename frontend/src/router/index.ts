import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/account/change-password',
      name: 'change-password',
      component: () => import('../views/ChangePasswordView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/mentor',
      component: () => import('../views/mentor/MentorLayout.vue'),
      meta: { requiresAuth: true, role: 'MENTOR' },
      children: [
        { path: '', name: 'mentor-home', component: () => import('../views/mentor/MentorHomeView.vue') },
        { path: 'trainees', name: 'mentor-trainees', component: () => import('../views/mentor/TraineesView.vue') },
        { path: 'curricula', name: 'mentor-curricula', component: () => import('../views/mentor/CurriculaView.vue') },
        {
          path: 'curricula/wizard',
          name: 'mentor-curriculum-wizard',
          component: () => import('../views/mentor/CurriculumWizardView.vue'),
        },
        {
          path: 'curricula/:id',
          name: 'mentor-curriculum-detail',
          component: () => import('../views/mentor/CurriculumDetailView.vue'),
          props: true,
        },
      ],
    },
    {
      path: '/trainee',
      component: () => import('../views/trainee/TraineeLayout.vue'),
      meta: { requiresAuth: true, role: 'TRAINEE' },
      children: [
        { path: '', name: 'trainee-home', component: () => import('../views/trainee/TraineeDashboardView.vue') },
        {
          path: 'curriculum',
          name: 'trainee-curriculum',
          component: () => import('../views/trainee/TraineeLearningPathView.vue'),
        },
        {
          path: 'assignment',
          name: 'trainee-assignment',
          component: () => import('../views/trainee/TraineeFocusView.vue'),
        },
      ],
    },
    {
      path: '/',
      name: 'root',
      redirect: () => {
        const auth = useAuthStore()
        if (!auth.isAuthenticated) return '/login'
        return auth.user?.role === 'MENTOR' ? '/mentor' : '/trainee'
      },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.isAuthenticated && to.name === 'login') {
      return auth.user?.role === 'MENTOR' ? '/mentor' : '/trainee'
    }
    return true
  }
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  const role = to.meta.role as string | undefined
  if (role && auth.user && auth.user.role !== role) {
    return auth.user.role === 'MENTOR' ? '/mentor' : '/trainee'
  }
  return true
})

export default router
