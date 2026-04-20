<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import loginBackground from '../assets/login_img.webp'
import { useAuthStore } from '../stores/auth'

const email = ref('')
const password = ref('')
const rememberMe = ref(false)
const showPassword = ref(false)
const formError = ref('')
const loading = ref(false)
const fieldErrors = reactive({
  email: '',
  password: '',
})

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const pageBackgroundStyle = {
  backgroundImage: `url(${loginBackground})`,
}

async function submit(): Promise<void> {
  if (loading.value) {
    return
  }

  clearAllErrors()
  if (!validateForm()) {
    return
  }

  loading.value = true
  try {
    await auth.login(email.value, password.value)
    const redirect =
      (route.query.redirect as string) || (auth.user?.role === 'MENTOR' ? '/mentor' : '/trainee')
    await router.replace(redirect)
  } catch (e) {
    formError.value = mapLoginErrorMessage(e)
  } finally {
    loading.value = false
  }
}

function validateForm(): boolean {
  let ok = true
  if (!email.value.trim()) {
    fieldErrors.email = 'Email is required.'
    ok = false
  }
  if (!password.value.trim()) {
    fieldErrors.password = 'Password is required.'
    ok = false
  }
  return ok
}

function clearAllErrors(): void {
  formError.value = ''
  fieldErrors.email = ''
  fieldErrors.password = ''
}

function onEmailInput(): void {
  fieldErrors.email = ''
  formError.value = ''
}

function onPasswordInput(): void {
  fieldErrors.password = ''
  formError.value = ''
}

function mapLoginErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Login failed. Please try again.'
  }
  if (error.httpStatus === 401) {
    return 'Invalid email or password.'
  }
  if (error.httpStatus === 403) {
    return 'Your account is deactivated. Please contact your mentor or administrator.'
  }
  if (error.httpStatus >= 500) {
    return 'The server is temporarily unavailable. Please try again later.'
  }
  return error.message || 'Login failed. Please try again.'
}

function togglePassword(): void {
  showPassword.value = !showPassword.value
}
</script>

<template>
  <div class="login-page" :style="pageBackgroundStyle">
    <div class="login-shell">
      <section class="form-panel">
        <p class="eyebrow">Training Platform</p>
        <h1>Login</h1>
        <p class="subtitle">Login to access your trainee or mentor workspace.</p>

        <form class="form-card" @submit.prevent="submit">
          <label>
            Email
            <input
              v-model="email"
              type="email"
              autocomplete="username"
              required
              :disabled="loading"
              :aria-invalid="fieldErrors.email ? 'true' : 'false'"
              @input="onEmailInput"
            />
            <span v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</span>
          </label>

          <label>
            Password
            <div class="password-wrap">
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                required
                :disabled="loading"
                :aria-invalid="fieldErrors.password ? 'true' : 'false'"
                @input="onPasswordInput"
              />
              <button
                type="button"
                class="toggle-password"
                :aria-label="showPassword ? 'Hide password' : 'Show password'"
                :disabled="loading"
                @click="togglePassword"
              >
                {{ showPassword ? 'Hide' : 'Show' }}
              </button>
            </div>
            <span v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</span>
          </label>

          <label class="remember">
            <input v-model="rememberMe" type="checkbox" :disabled="loading" />
            <span>Remember me</span>
          </label>

          <p v-if="formError" class="error" role="alert" aria-live="polite">{{ formError }}</p>

          <button class="submit-btn" type="submit" :disabled="loading">
            {{ loading ? 'Signing in...' : 'Login' }}
          </button>
          <p class="helper-note">Secure sign-in for mentors and trainees.</p>
        </form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background-color: #111827;
  background-position: center;
  background-repeat: no-repeat;
  background-size: cover;
  isolation: isolate;
}

.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 12% 10%, rgba(122, 90, 248, 0.28), transparent 35%),
    linear-gradient(140deg, rgba(17, 24, 39, 0.68) 10%, rgba(86, 36, 208, 0.55) 55%, rgba(17, 24, 39, 0.8) 100%);
  z-index: -1;
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
  align-items: flex-start;
  width: min(460px, 100%);
  background: color-mix(in srgb, var(--auth-surface) 95%, transparent);
  border: 1px solid color-mix(in srgb, var(--auth-primary-500) 14%, var(--auth-border));
  border-radius: var(--auth-radius-lg);
  padding: 2.2rem 1.9rem;
  box-shadow: var(--auth-card-shadow);
  backdrop-filter: blur(3px);
  transition: transform var(--auth-transition-base), box-shadow var(--auth-transition-base);
}

.form-panel:hover {
  transform: translateY(-2px);
  box-shadow: var(--auth-card-shadow-hover);
}

.eyebrow {
  margin: 0 0 0.55rem;
  color: var(--auth-primary-500);
  font-size: 0.78rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 700;
  width: min(420px, 100%);
}

h1 {
  margin: 0;
  font-size: clamp(1.9rem, 3.4vw, 2.2rem);
  line-height: 1.2;
  color: var(--auth-text-primary);
  width: min(420px, 100%);
}

.subtitle {
  margin: 0.55rem 0 1.65rem;
  color: var(--auth-text-secondary);
  width: min(420px, 100%);
  font-size: 0.97rem;
}

.form-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  width: min(420px, 100%);
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.42rem;
  font-size: 0.9rem;
  color: var(--auth-text-primary);
  font-weight: 500;
}

input[type='email'],
input[type='password'],
input[type='text'] {
  width: 100%;
  border: 1px solid var(--auth-border);
  border-radius: var(--auth-radius-md);
  background: var(--auth-surface);
  padding: 0.72rem 0.78rem;
  min-height: 44px;
  font-size: 0.96rem;
  color: var(--auth-text-primary);
  transition: border-color var(--auth-transition-fast), box-shadow var(--auth-transition-fast),
    background-color var(--auth-transition-fast);
}

input[type='email']:hover,
input[type='password']:hover,
input[type='text']:hover {
  border-color: var(--auth-border-strong);
}

input:focus {
  outline: none;
  border-color: var(--auth-primary-500);
  box-shadow: 0 0 0 3px var(--auth-focus-ring);
}

input[aria-invalid='true'] {
  border-color: var(--auth-danger);
}

input:disabled {
  background: var(--auth-surface-muted);
  cursor: not-allowed;
}

.password-wrap {
  position: relative;
}

.password-wrap input {
  padding-right: 5rem;
}

.toggle-password {
  position: absolute;
  right: 0.45rem;
  top: 50%;
  transform: translateY(-50%);
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--auth-primary-600);
  cursor: pointer;
  font-size: 0.79rem;
  font-weight: 600;
  padding: 0.22rem 0.42rem;
  transition: all var(--auth-transition-fast);
}

.toggle-password:hover:not(:disabled) {
  background: color-mix(in srgb, var(--auth-primary-500) 10%, white);
  border-color: color-mix(in srgb, var(--auth-primary-500) 16%, transparent);
}

.toggle-password:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--auth-focus-ring);
}

.toggle-password:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.remember {
  flex-direction: row;
  align-items: center;
  gap: 0.45rem;
  margin-top: 0.2rem;
  color: var(--auth-text-secondary);
  font-weight: 400;
}

.remember input {
  margin: 0;
}

.submit-btn {
  margin-top: 0.55rem;
  width: 100%;
  border: 0;
  border-radius: var(--auth-radius-md);
  background: linear-gradient(135deg, var(--auth-primary-500), var(--auth-accent-500));
  color: #ffffff;
  font-weight: 600;
  font-size: 0.95rem;
  padding: 0.78rem 0.9rem;
  cursor: pointer;
  transition: transform var(--auth-transition-fast), filter var(--auth-transition-fast),
    box-shadow var(--auth-transition-fast);
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.02);
  box-shadow: 0 12px 24px rgba(86, 36, 208, 0.28);
}

.submit-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--auth-focus-ring), 0 12px 24px rgba(86, 36, 208, 0.28);
}

.submit-btn:disabled {
  opacity: 0.62;
  cursor: not-allowed;
  box-shadow: none;
}

.error {
  margin: 0;
  color: var(--auth-danger);
  font-size: 0.88rem;
  background: color-mix(in srgb, var(--auth-danger) 9%, white);
  border: 1px solid color-mix(in srgb, var(--auth-danger) 20%, transparent);
  border-radius: 8px;
  padding: 0.5rem 0.62rem;
}

.field-error {
  color: var(--auth-danger);
  font-size: 0.82rem;
  line-height: 1.3;
}

.helper-note {
  margin: 0.35rem 0 0;
  font-size: 0.77rem;
  color: var(--auth-text-secondary);
  text-align: center;
}

@media (max-width: 960px) {
  .login-page {
    padding: 1rem;
  }

  .form-panel {
    padding: 1.75rem 1.1rem;
  }

  h1 {
    font-size: 1.75rem;
  }
}
</style>
