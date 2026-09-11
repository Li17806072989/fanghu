<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { landingPath, saveSession } from '../api'
import '../login.css'

const mode = ref('login')
const form = reactive({ username: '', password: '', confirmPassword: '', captchaId: '', captcha: '', accountType: 'supplier' })
const message = ref('')
const error = ref('')
const image = ref('')
const submitting = ref(false)

function clearCredentials() {
  form.username = ''
  form.password = ''
  form.confirmPassword = ''
  form.captcha = ''
}

async function refreshCaptcha() {
  const response = await fetch('/api/auth/captcha')
  const data = await response.json()
  form.captchaId = data.captchaId
  image.value = data.imageBase64
}

async function login() {
  submitting.value = true; error.value = ''; message.value = ''
  const response = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form) })
  if (response.ok) {
    const data = await response.json()
    saveSession(data)
    clearCredentials()
    window.location.replace(landingPath(data.roles))
  } else {
    error.value = (await response.json()).message || '登录失败，请检查账号、密码和验证码'
    await refreshCaptcha()
  }
  submitting.value = false
}

async function register() {
  error.value = ''; message.value = ''
  if (form.password !== form.confirmPassword) { error.value = '两次输入的密码不一致'; return }
  submitting.value = true
  const response = await fetch('/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form) })
  if (response.ok) {
    message.value = (await response.json()).message
    clearCredentials()
    mode.value = 'login'
  } else error.value = (await response.json()).message || '账号创建失败'
  submitting.value = false
}

watch(mode, () => {
  clearCredentials()
  error.value = ''
  if (mode.value === 'login') refreshCaptcha()
})

onMounted(() => {
  clearCredentials()
  refreshCaptcha()
})
</script>

<template>
  <section class="login-page">
    <h1>{{ mode === 'login' ? '平台账号登录' : '创建平台账号' }}</h1>
    <p class="login-intro">{{ mode === 'register' ? '请选择账号主体；注册后不能切换或同时进入另一类工作台。' : '客户管理采购需求，供应商维护企业资料与解决方案。' }}</p>
    <el-segmented v-model="mode" :options="[{label:'账号登录',value:'login'},{label:'注册账号',value:'register'}]" />
    <el-form :key="mode" class="login-form" autocomplete="off" @submit.prevent="mode === 'login' ? login() : register()">
      <el-form-item v-if="mode==='register'" label="账号类型">
        <el-segmented v-model="form.accountType" :options="[{label:'供应商账号',value:'supplier'},{label:'客户账号',value:'customer'}]" />
      </el-form-item>
      <el-form-item><el-input v-model.trim="form.username" name="fanghu-account-entry" autocomplete="off" data-form-type="other" data-lpignore="true" data-1p-ignore placeholder="用户名（4至32位字母、数字或下划线）" /></el-form-item>
      <el-form-item><el-input v-model="form.password" name="fanghu-password-entry" type="password" autocomplete="new-password" data-form-type="other" data-lpignore="true" data-1p-ignore placeholder="密码（至少8位）" show-password /></el-form-item>
      <el-form-item v-if="mode==='register'"><el-input v-model="form.confirmPassword" name="fanghu-password-confirmation" type="password" autocomplete="new-password" data-form-type="other" data-lpignore="true" data-1p-ignore placeholder="再次输入密码" show-password /></el-form-item>
      <el-form-item v-else><el-input v-model="form.captcha" placeholder="验证码"><template #append><img v-if="image" :src="image" alt="验证码，点击刷新" title="点击刷新验证码" @click="refreshCaptcha" /></template></el-input></el-form-item>
      <p v-if="message" class="login-message">{{ message }}</p><p v-if="error" class="login-error">{{ error }}</p>
      <el-button type="primary" native-type="submit" :loading="submitting">{{ mode === 'login' ? '登录' : '创建账号' }}</el-button>
    </el-form>
  </section>
</template>
