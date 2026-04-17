import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import { createApp } from 'vue'
import Aura from '@primeuix/themes/aura'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import './style.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(PrimeVue, {
  ripple: true,
  theme: {
    preset: Aura,
  },
})
app.use(ToastService)

const auth = useAuthStore()
auth.restoreSession()

app.use(router)
app.mount('#app')
