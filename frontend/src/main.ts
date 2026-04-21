import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import { createApp } from 'vue'
import Aura from '@primeuix/themes/aura'
import { definePreset } from '@primeuix/themes'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import './style.css'

const CreativeLearningPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#f9f3ff',
      100: '#efe2ff',
      200: '#e3c7fd',
      300: '#cd9efb',
      400: '#b770f5',
      500: '#a435f0',
      600: '#8711d8',
      700: '#6a0db0',
      800: '#4c0a80',
      900: '#2b054b',
      950: '#1a032e',
    },
  },
})

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(PrimeVue, {
  ripple: true,
  theme: {
    preset: CreativeLearningPreset,
    options: {
      darkModeSelector: '.app-dark-mode',
    },
  },
})
app.use(ToastService)

const auth = useAuthStore()
auth.restoreSession()

app.use(router)
app.mount('#app')
