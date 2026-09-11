<script setup>
import { reactive, ref, onMounted } from 'vue'
import { api } from '../api'
import '../demand.css'

const form = reactive({ title: '', description: '', categoryId: null, budgetLevel: '待沟通' })
const profile = reactive({ organization: '', contactName: '', contactPhone: '' })
const result = ref(''); const error = ref(''); const submitting = ref(false); const profileReady = ref(false)
const equipmentTree = ref([])
const taxonomyLoading = ref(false)
const taxonomyError = ref('')
async function loadProfile() {
  const response = await api('/api/me/profile')
  if (!response.ok) return
  const body = await response.text()
  if (!body) return
  const data = JSON.parse(body)
  if (data) { Object.assign(profile, data); profileReady.value = Boolean(data.contactName) }
}
async function saveProfile() {
  const response = await api('/api/me/profile', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(profile) })
  if (!response.ok) throw new Error('联系人档案保存失败')
  profileReady.value = true
}
async function submit() {
  if (!form.categoryId) { error.value = '请选择装备分类后再提交'; return }
  submitting.value = true; error.value = ''; result.value = ''
  try {
    if (!profileReady.value) await saveProfile()
    const payload = { ...form, categoryId: Number(form.categoryId) }
    const response = await api('/api/demands', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
    if (!response.ok) throw new Error('需求未能提交，请稍后重试')
    result.value = '需求已提交，系统正在生成候选对接；可在客户工作台确认联系意向。'
    Object.assign(form, { title: '', description: '', categoryId: null, budgetLevel: '待沟通' })
  } catch (e) { error.value = e.message } finally { submitting.value = false }
}
async function loadEquipmentTree() {
  taxonomyLoading.value = true
  taxonomyError.value = ''
  try {
    const response = await api('/api/categories/taxonomy/tree?type=EQUIPMENT')
    if (!response.ok) throw new Error('装备分类加载失败')
    equipmentTree.value = await response.json()
    if (!equipmentTree.value.length) taxonomyError.value = '暂无可用装备分类，请联系平台运营人员'
  } catch (e) {
    taxonomyError.value = e.message || '装备分类加载失败，请刷新页面重试'
  } finally {
    taxonomyLoading.value = false
  }
}
onMounted(() => Promise.all([loadProfile(), loadEquipmentTree()]))
</script>

<template>
  <section class="form-page"><header class="page-heading"><div><h1>提交采购需求</h1><p>需求会自动关联当前账号，匹配结果和线索进展可在平台后台查看。</p></div></header>
    <form class="entry-form" @submit.prevent="submit">
      <div class="form-row"><label>联系人<input v-model.trim="profile.contactName" required maxlength="80"></label><label>联系电话<input v-model.trim="profile.contactPhone" maxlength="30"></label></div>
      <label>所属组织<input v-model.trim="profile.organization" maxlength="200"></label>
      <label>需求标题<input v-model.trim="form.title" maxlength="200" required></label>
      <label>需求描述<textarea v-model.trim="form.description" rows="7" required></textarea></label>
      <div class="form-row"><label>装备分类<el-tree-select v-model="form.categoryId" :data="equipmentTree" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" check-strictly clearable :loading="taxonomyLoading" :disabled="taxonomyLoading || !equipmentTree.length" :render-after-expand="false" placeholder="请选择装备分类" loading-text="正在加载装备分类" no-data-text="暂无装备分类" aria-label="装备分类" /><small v-if="taxonomyError" class="field-error">{{ taxonomyError }}</small></label><label>预算级别<select v-model="form.budgetLevel"><option>待沟通</option><option>低</option><option>中</option><option>高</option></select></label></div>
      <p class="form-note">客户档案仅用于平台对接，不会在公开页面展示。</p><p v-if="result" class="form-result">{{ result }} <RouterLink to="/customer/workbench">前往客户工作台</RouterLink></p><p v-if="error" class="form-error">{{ error }}</p>
      <button class="primary-button" :disabled="submitting">{{ submitting ? '正在提交' : '提交并等待匹配' }}</button>
    </form>
  </section>
</template>
