<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { api, landingPath, loadNativeSession, readJson } from '../api'
import '../login.css'

const mode = ref('login')
const form = reactive({ username: '', password: '', confirmPassword: '', phone: '', uuid: '', captcha: '', smsCode: '', accountType: 'supplier' })
const message = ref('')
const error = ref('')
const image = ref('')
const submitting = ref(false)
const smsSending = ref(false)
const smsCountdown = ref(0)
let smsTimer

const phonePattern = /^1[3-9]\d{9}$/

function clearCredentials() {
  form.username = ''
  form.password = ''
  form.confirmPassword = ''
  form.phone = ''
  form.captcha = ''
  form.smsCode = ''
}

function stopSmsCountdown() {
  if (smsTimer) window.clearInterval(smsTimer)
  smsTimer = undefined
  smsCountdown.value = 0
}

function startSmsCountdown(seconds = 60) {
  stopSmsCountdown()
  smsCountdown.value = Math.max(1, Number(seconds) || 60)
  smsTimer = window.setInterval(() => {
    smsCountdown.value -= 1
    if (smsCountdown.value <= 0) stopSmsCountdown()
  }, 1000)
}

async function sendSmsCode() {
  error.value = ''; message.value = ''
  const phone = form.phone.trim()
  const purpose = mode.value === 'register' ? 'register' : 'recovery'
  if (!phonePattern.test(phone)) { error.value = '请输入11位有效手机号'; return }
  smsSending.value = true
  try {
    const response = await api('/api/sms/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone, purpose })
    })
    const body = await readJson(response, {})
    if (!response.ok) throw new Error(body.msg || body.message || '后端服务暂不可用，请稍后重试')
    if (mode.value !== (purpose === 'register' ? 'register' : 'forgot') || form.phone.trim() !== phone) return
    startSmsCountdown(body.retryAfter || 60)
    message.value = '短信验证码已发送，请注意查收'
  } catch (e) {
    error.value = e.message || '短信验证码发送失败，请稍后重试'
  } finally {
    smsSending.value = false
  }
}

async function refreshCaptcha() {
  try {
    const response = await api('/api/auth/captcha')
    const body = await readJson(response, {})
    if (!response.ok) throw new Error(body.msg || body.message || '后端服务暂不可用，请稍后重试')
    form.uuid = body.captchaId || body.data?.captchaId || body.data?.uuid || ''
    image.value = body.imageBase64 || body.data?.imageBase64 || body.data?.img || ''
  } catch (e) {
    image.value = ''
    error.value = e.message || '验证码获取失败，请刷新重试'
  }
}

async function login() {
  submitting.value = true; error.value = ''; message.value = ''
  try {
    if (!form.username.trim() || !form.password || !form.captcha.trim()) throw new Error('请输入账号、密码和验证码')
    const response = await api('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: form.username, password: form.password, captchaId: form.uuid, captcha: form.captcha }) })
    const body = await readJson(response, {})
    if (!response.ok || !body.token) {
      throw new Error(body.msg || body.message || '登录失败，请检查账号、密码和验证码')
    }
    const session = await loadNativeSession(body.token)
    clearCredentials()
    window.location.replace(landingPath(session.roles))
  } catch (e) {
    error.value = e.message || '登录失败，请稍后重试'
    await refreshCaptcha()
  } finally {
    submitting.value = false
  }
}

async function register() {
  error.value = ''; message.value = ''
  if (!form.username.trim() || !form.password) { error.value = '请输入用户名和密码'; return }
  if (form.password !== form.confirmPassword) { error.value = '两次输入的密码不一致'; return }
  if (!phonePattern.test(form.phone.trim())) { error.value = '请输入11位有效手机号'; return }
  if (!/^\d{6}$/.test(form.smsCode.trim())) { error.value = '请输入6位短信验证码'; return }
  submitting.value = true
  const payload = { username: form.username, password: form.password, accountType: form.accountType, phone: form.phone, smsCode: form.smsCode }
  try {
    const response = await api('/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
    const body = await readJson(response, {})
    if (!response.ok) throw new Error(body.msg || body.message || '账号创建失败')
    clearCredentials(); mode.value = 'login'; await nextTick()
    message.value = body.message || body.msg || '账号创建成功，请登录'
  } catch (e) { error.value = e.message || '账号创建失败' } finally { submitting.value = false }
}

async function recoverPassword() {
  error.value = ''; message.value = ''
  if (!form.username.trim()) { error.value = '请输入用户名'; return }
  if (!phonePattern.test(form.phone.trim())) { error.value = '请输入账号绑定的11位手机号'; return }
  if (!/^\d{6}$/.test(form.smsCode.trim())) { error.value = '请输入6位短信验证码'; return }
  if (form.password !== form.confirmPassword) { error.value = '两次输入的新密码不一致'; return }
  if (form.password.length < 8) { error.value = '新密码至少需要8位'; return }
  submitting.value = true
  try {
    const request = await api('/api/auth/password/recovery/request', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: form.username, phone: form.phone, smsCode: form.smsCode }) })
    const requestBody = await readJson(request, {})
    if (!request.ok || !requestBody.token) throw new Error(requestBody.msg || requestBody.message || '身份验证失败')
    const confirm = await api('/api/auth/password/recovery/confirm', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token: requestBody.token, newPassword: form.password }) })
    const confirmBody = await readJson(confirm, {})
    if (!confirm.ok) throw new Error(confirmBody.msg || confirmBody.message || '密码重置失败')
    clearCredentials(); mode.value = 'login'; await nextTick()
    message.value = '密码已重置，请使用新密码登录'
  } catch (e) {
    error.value = e.message || '密码重置失败，请稍后重试'
  } finally { submitting.value = false }
}

watch(mode, () => {
  stopSmsCountdown()
  clearCredentials()
  error.value = ''
  message.value = ''
  if (mode.value === 'login') refreshCaptcha()
})

onMounted(() => {
  clearCredentials()
  refreshCaptcha()
})

onBeforeUnmount(stopSmsCountdown)
</script>

<template>
  <section class="login-page">
    <h1>{{ mode === 'login' ? '平台账号登录' : mode === 'register' ? '创建平台账号' : '找回密码' }}</h1>
    <p class="login-intro">{{ mode === 'register' ? '请选择账号主体，并验证手机号后创建账号。' : mode === 'forgot' ? '使用用户名和账号绑定的手机号验证身份。' : '客户管理采购需求，供应商维护企业资料与解决方案。' }}</p>
    <el-segmented v-model="mode" :options="[{label:'账号登录',value:'login'},{label:'注册账号',value:'register'},{label:'找回密码',value:'forgot'}]" />
    <el-form :key="mode" class="login-form" autocomplete="off" @submit.prevent="mode === 'login' ? login() : mode === 'register' ? register() : recoverPassword()">
      <el-form-item v-if="mode==='register'" label="账号类型">
        <el-segmented v-model="form.accountType" :options="[{label:'供应商账号',value:'supplier'},{label:'客户账号',value:'customer'}]" />
      </el-form-item>
      <el-form-item><el-input v-model.trim="form.username" name="fanghu-account-entry" autocomplete="off" data-form-type="other" data-lpignore="true" data-1p-ignore placeholder="用户名（4至32位字母、数字或下划线）" /></el-form-item>
      <el-form-item v-if="mode!=='login'"><el-input v-model.trim="form.phone" name="fanghu-phone" inputmode="numeric" autocomplete="tel" maxlength="11" placeholder="手机号" /></el-form-item>
      <el-form-item><el-input v-model="form.password" name="fanghu-password-entry" type="password" autocomplete="new-password" data-form-type="other" data-lpignore="true" data-1p-ignore placeholder="密码（至少8位）" show-password /></el-form-item>
      <el-form-item v-if="mode!=='login'"><el-input v-model="form.confirmPassword" name="fanghu-password-confirmation" type="password" autocomplete="new-password" data-form-type="other" data-lpignore="true" data-1p-ignore :placeholder="mode==='forgot'?'再次输入新密码':'再次输入密码'" show-password /></el-form-item>
      <el-form-item v-if="mode==='login'"><el-input v-model.trim="form.captcha" maxlength="6" autocomplete="off" placeholder="图形验证码"><template #append><img v-if="image" :src="image" alt="验证码，点击刷新" title="点击刷新验证码" @click="refreshCaptcha" /></template></el-input></el-form-item>
      <el-form-item v-else>
        <div class="sms-code-row">
          <el-input v-model.trim="form.smsCode" name="fanghu-sms-code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="6位短信验证码" />
          <el-button native-type="button" :loading="smsSending" :disabled="smsCountdown > 0" @click="sendSmsCode">{{ smsCountdown > 0 ? `${smsCountdown}秒后重发` : '发送验证码' }}</el-button>
        </div>
      </el-form-item>
      <p v-if="message" class="login-message" role="status">{{ message }}</p><p v-if="error" class="login-error" role="alert">{{ error }}</p>
      <el-button type="primary" native-type="submit" :loading="submitting">{{ mode === 'login' ? '登录' : mode === 'register' ? '创建账号' : '重置密码' }}</el-button>
    </el-form>
  </section>
</template>
