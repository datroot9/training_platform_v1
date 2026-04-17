<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import loginBackground from '../assets/login_img.webp'
import { useAuthStore } from '../stores/auth'

const email = ref('')
const password = ref('')
const rememberMe = ref(false)
const showPassword = ref(false)
const error = ref('')
const loading = ref(false)

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const pageBackgroundStyle = {
  backgroundImage: `url(${loginBackground})`,
}

async function submit(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    await auth.login(email.value, password.value)
    const redirect =
      (route.query.redirect as string) || (auth.user?.role === 'MENTOR' ? '/mentor' : '/trainee')
    await router.replace(redirect)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Login failed'
  } finally {
    loading.value = false
  }
}

function togglePassword(): void {
  showPassword.value = !showPassword.value
}
</script>

<template>
  <div class="login-page" :style="pageBackgroundStyle">
    <div class="login-shell">
      <section class="form-panel">
        <h1>Login</h1>
        <p class="subtitle">Login to access your trainee or mentor workspace.</p>

        <form class="form-card" @submit.prevent="submit">
          <label>
            Email
            <input v-model="email" type="email" autocomplete="username" required />
          </label>

          <label>
            Password
            <div class="password-wrap">
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                required
              />
              <button
                type="button"
                class="toggle-password"
                :aria-label="showPassword ? 'Hide password' : 'Show password'"
                @click="togglePassword"
              >
                {{ showPassword ? 'Hide' : 'Show' }}
              </button>
            </div>
          </label>

          <label class="remember">
            <input v-model="rememberMe" type="checkbox" />
            <span>Remember me</span>
          </label>

          <p v-if="error" class="error">{{ error }}</p>

          <button class="submit-btn" type="submit" :disabled="loading">
            {{ loading ? 'Signing in...' : 'Login' }}
          </button>
        </form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background-color: #ffffff;
  background-position: center;
  background-repeat: no-repeat;
  background-size: cover;
}

.login-shell {
  width: 100%;
  display: flex;
  justify-content: center;
}

.form-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  width: min(460px, 100%);
  background: #ffffff;
  border-radius: 16px;
  padding: 2rem 1.5rem;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.14);
}

.logo {
  margin: 0 0 1.25rem;
  font-weight: 700;
  color: #0f172a;
  width: min(420px, 100%);
}

h1 {
  margin: 0;
  font-size: 2rem;
  width: min(420px, 100%);
}

.subtitle {
  margin: 0.5rem 0 1.5rem;
  color: #64748b;
  width: min(420px, 100%);
}

.form-card {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  width: min(420px, 100%);
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.88rem;
  color: #334155;
}

input[type='email'],
input[type='password'],
input[type='text'] {
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0.6rem 0.7rem;
  font-size: 0.95rem;
}

input:focus {
  outline: 2px solid #93c5fd;
  outline-offset: 1px;
}

.password-wrap {
  position: relative;
}

.password-wrap input {
  padding-right: 4.8rem;
}

.toggle-password {
  position: absolute;
  right: 0.35rem;
  top: 50%;
  transform: translateY(-50%);
  border: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font-size: 0.8rem;
  padding: 0.15rem 0.35rem;
}

.remember {
  flex-direction: row;
  align-items: center;
  gap: 0.45rem;
  margin-top: 0.2rem;
  color: #475569;
}

.remember input {
  margin: 0;
}

.submit-btn {
  margin-top: 0.35rem;
  width: 100%;
  border: 0;
  border-radius: 6px;
  background: #2f66f6;
  color: #ffffff;
  font-weight: 600;
  padding: 0.65rem 0.8rem;
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.error {
  margin: 0;
  color: #b91c1c;
  font-size: 0.88rem;
}

@media (max-width: 960px) {
  .login-page {
    padding: 1rem;
  }

  .logo {
    margin-bottom: 1rem;
  }
}
</style>
