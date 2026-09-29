<script setup>
import { reactive, ref } from 'vue'
import { api, clearSession } from '../api'

const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const loading = ref(false)
const message = ref('')
const error = ref('')

async function submit() {
  message.value = ''; error.value = ''
  if (form.newPassword.length < 8) { error.value = '新密码至少需要8位'; return }
  if (form.newPassword !== form.confirmPassword) { error.value = '两次输入的新密码不一致'; return }
  loading.value = true
  try {
    const response = await api('/system/user/profile/updatePwd', {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    })
    const body = await response.json()
    if (body.code !== 200) throw new Error(body.msg || '密码修改失败')
    clearSession()
    window.location.replace('/login')
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
</script>

<template>
  <section class="login-page">
    <h1>账号安全</h1>
    <p class="login-intro">修改密码后需要重新登录。</p>
    <el-form class="login-form" autocomplete="off" @submit.prevent="submit">
      <el-form-item label="当前密码"><el-input v-model="form.oldPassword" type="password" show-password /></el-form-item>
      <el-form-item label="新密码"><el-input v-model="form.newPassword" type="password" show-password /></el-form-item>
      <el-form-item label="确认新密码"><el-input v-model="form.confirmPassword" type="password" show-password /></el-form-item>
      <p v-if="message" class="login-message">{{ message }}</p><p v-if="error" class="login-error">{{ error }}</p>
      <el-button type="primary" native-type="submit" :loading="loading">修改密码</el-button>
    </el-form>
  </section>
</template>

<style scoped>@import '../login.css';</style>
