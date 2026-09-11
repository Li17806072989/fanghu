<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { SwitchButton, UserFilled } from '@element-plus/icons-vue'
import { api, clearSession, landingPath, onAuthChange, onHomepageContactChange, readRoles, readUsername, refreshSession } from './api'

const route = useRoute()
const router = useRouter()
const authRevision = ref(0)
let stopAuthListener
let stopContactListener
const roles = computed(() => {
  authRevision.value
  route.fullPath
  return readRoles()
})
const loggedIn = computed(() => Boolean(localStorage.getItem('fanghu-token')) && roles.value.length > 0)
const isSupplier = computed(() => roles.value.includes('supplier'))
const isCustomer = computed(() => roles.value.includes('customer'))
const isAdmin = computed(() => roles.value.includes('admin'))
const footerContact = ref(null)
const currentYear = new Date().getFullYear()
const username = computed(() => {
  authRevision.value
  return readUsername()
})
const userHome = computed(() => landingPath(roles.value))
onMounted(() => {
  stopAuthListener = onAuthChange(() => { authRevision.value += 1 })
  stopContactListener = onHomepageContactChange(contact => { footerContact.value = contact })
  refreshSession()
  api('/api/homepage').then(async response => {
    if (response.ok) footerContact.value = (await response.json()).contact
  }).catch(() => {})
})
onUnmounted(() => {
  stopAuthListener?.()
  stopContactListener?.()
})
async function logout() {
  try {
    await api('/api/auth/logout', { method: 'POST' })
  } finally {
    clearSession()
    router.push('/')
  }
}
</script>
<template>
  <header class="topbar">
    <div class="topbar-inner">
      <RouterLink class="brand" to="/">平台 <small>特种装备 · 解决方案 · 对接</small></RouterLink>
      <nav>
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/topics">专题</RouterLink>
        <RouterLink to="/solutions">解决方案</RouterLink>
        <RouterLink to="/articles">资讯</RouterLink>
        <RouterLink v-if="loggedIn" to="/reports">AI 汇报</RouterLink>
        <RouterLink v-if="isSupplier || (!loggedIn && !isCustomer && !isAdmin)" to="/supplier/apply">供应商入驻</RouterLink>
        <RouterLink v-if="isCustomer" to="/customer/workbench">客户工作台</RouterLink>
        <RouterLink v-if="isSupplier" to="/workbench">供应商工作台</RouterLink>
        <RouterLink v-if="isAdmin" to="/admin">运营后台</RouterLink>
        <RouterLink v-if="!loggedIn" to="/login">登录 / 注册</RouterLink>
        <RouterLink v-else class="account-identity" :to="userHome" :aria-label="`当前用户：${username}`">
          <el-avatar :size="28"><el-icon><UserFilled /></el-icon></el-avatar>
          <span class="account-name">{{ username }}</span>
        </RouterLink>
        <button v-if="loggedIn" class="logout-button" type="button" @click="logout">
          <el-icon><SwitchButton /></el-icon>
          <span>退出登录</span>
        </button>
      </nav>
    </div>
  </header>
  <main><RouterView /></main>
  <footer class="site-footer">
    <div class="footer-inner">
      <div class="footer-links" role="navigation" aria-label="页脚导航">
        <RouterLink to="/">网站首页</RouterLink>
        <RouterLink to="/topics">专题中心</RouterLink>
        <RouterLink to="/solutions">解决方案库</RouterLink>
        <RouterLink to="/articles">军事资讯</RouterLink>
        <RouterLink v-if="loggedIn" to="/reports">AI 汇报</RouterLink>
        <RouterLink v-if="isSupplier || (!loggedIn && !isCustomer && !isAdmin)" to="/supplier/apply">供应商入驻</RouterLink>
        <RouterLink v-if="isCustomer" to="/customer/workbench">客户工作台</RouterLink>
        <RouterLink v-if="isSupplier" to="/workbench">供应商工作台</RouterLink>
      </div>
      <div v-if="footerContact && (footerContact.hotline || footerContact.email || footerContact.address || footerContact.serviceHours)" class="footer-contact">
        <span v-if="footerContact.hotline">业务咨询：{{ footerContact.hotline }}</span>
        <span v-if="footerContact.email">联系邮箱：{{ footerContact.email }}</span>
        <span v-if="footerContact.address">联系地址：{{ footerContact.address }}</span>
        <span v-if="footerContact.serviceHours">服务时间：{{ footerContact.serviceHours }}</span>
      </div>
      <p class="footer-copyright">© {{ currentYear }} 平台 · 专题、装备方案、行业资讯与供需对接</p>
    </div>
  </footer>
</template>
