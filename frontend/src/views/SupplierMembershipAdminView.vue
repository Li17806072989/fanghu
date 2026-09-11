<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { CircleCheck, Close, Present, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, hasPermission } from '../api'
import '../supplier-membership-admin.css'

const canWrite = computed(() => hasPermission('platform:write'))
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const page = ref(1)
const result = ref({ records: [], total: 0, current: 1, size: 20 })
const suppliers = ref([])
const filter = reactive({ keyword: '', status: '' })
const inviteVisible = ref(false)
const activateVisible = ref(false)
const inviteForm = reactive({ supplierId: null, durationMonths: 12, planName: '邀请会员', note: '' })
const activateForm = reactive({ id: null, supplierName: '', planName: '付费投放服务', feeAmount: null, startsAt: '', endsAt: '', note: '' })

const statusLabels = { PENDING: '待确认收款', ACTIVE: '生效中', EXPIRED: '已到期', CANCELLED: '已停用' }
const statusTypes = { PENDING: 'warning', ACTIVE: 'success', EXPIRED: 'info', CANCELLED: 'danger' }
const sourceLabels = { INVITED: '邀请赠送', PAID: '付费投放' }
const paymentLabels = { NOT_REQUIRED: '无需付款', PENDING: '待付款', PAID: '已收款', REFUNDED: '已退款' }

function localValue(date) {
  const adjusted = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return adjusted.toISOString().slice(0, 19)
}
function resetActivationDates() {
  const start = new Date()
  const end = new Date(start)
  end.setFullYear(end.getFullYear() + 1)
  activateForm.startsAt = localValue(start)
  activateForm.endsAt = localValue(end)
}
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '待确认' }
function formatAmount(value) { return value == null ? '待确认' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
async function responseMessage(response, fallback) { try { return (await response.json()).message || fallback } catch { return fallback } }

async function load() {
  if (!canWrite.value) return
  loading.value = true; error.value = ''
  try {
    const params = new URLSearchParams({ page: String(page.value), size: '20' })
    if (filter.keyword.trim()) params.set('keyword', filter.keyword.trim())
    if (filter.status) params.set('status', filter.status)
    const response = await api(`/api/admin/supplier-memberships?${params}`)
    if (!response.ok) throw new Error(await responseMessage(response, '会员服务记录加载失败'))
    result.value = await response.json()
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
async function loadSuppliers() {
  const response = await api('/api/admin/supplier-memberships/suppliers')
  suppliers.value = response.ok ? await response.json() : []
}
function applyFilter() { page.value = 1; load() }
function resetFilter() { Object.assign(filter, { keyword: '', status: '' }); applyFilter() }
function openInvite() {
  Object.assign(inviteForm, { supplierId: null, durationMonths: 12, planName: '邀请会员', note: '' })
  inviteVisible.value = true
}
async function saveInvite() {
  if (!inviteForm.supplierId) { error.value = '请选择受邀企业'; return }
  saving.value = true; error.value = ''
  try {
    const response = await api('/api/admin/supplier-memberships/invite', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(inviteForm) })
    if (!response.ok) throw new Error(await responseMessage(response, '邀请会员赠送失败'))
    inviteVisible.value = false; ElMessage.success(`已赠送 ${inviteForm.durationMonths} 个月邀请会员`); await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}
function openActivate(row) {
  Object.assign(activateForm, { id: row.id, supplierName: row.supplierName, planName: row.planName || '付费投放服务', feeAmount: null, note: row.note || '' })
  resetActivationDates()
  activateVisible.value = true
}
async function activatePaid() {
  if (!activateForm.feeAmount || !activateForm.startsAt || !activateForm.endsAt) { error.value = '请填写实际到账金额和服务周期'; return }
  saving.value = true; error.value = ''
  try {
    const payload = { planName: activateForm.planName, feeAmount: activateForm.feeAmount, startsAt: activateForm.startsAt, endsAt: activateForm.endsAt, note: activateForm.note }
    const response = await api(`/api/admin/supplier-memberships/${activateForm.id}/activate`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
    if (!response.ok) throw new Error(await responseMessage(response, '付费服务开通失败'))
    activateVisible.value = false; ElMessage.success('已确认收款并开通投放服务'); await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}
async function cancelService(row) {
  try {
    await ElMessageBox.confirm(`确定停用“${row.supplierName}”的${row.planName}吗？停用后将立即取消投放权限。`, '停用会员服务', { type: 'warning', confirmButtonText: '确认停用', cancelButtonText: '取消' })
    const response = await api(`/api/admin/supplier-memberships/${row.id}/cancel`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: '{}' })
    if (!response.ok) throw new Error(await responseMessage(response, '服务停用失败'))
    ElMessage.success('会员服务已停用'); await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') error.value = e.message || '服务停用失败' }
}

onMounted(async () => { if (canWrite.value) await Promise.all([load(), loadSuppliers()]) })
</script>

<template>
  <section class="membership-admin">
    <header class="membership-heading">
      <div><h1>会员与投放管理</h1><p>向优质企业赠送邀请会员，或在确认实际收款后开通付费投放。</p></div>
      <div><RouterLink class="el-button" to="/admin">返回审核后台</RouterLink><el-button :icon="Refresh" @click="load">刷新</el-button><el-button v-if="canWrite" type="primary" :icon="Present" @click="openInvite">赠送邀请会员</el-button></div>
    </header>
    <el-alert v-if="error" class="membership-alert" :title="error" type="error" show-icon closable @close="error=''" />
    <section v-if="!canWrite" class="membership-denied"><h2>没有会员管理权限</h2><p>当前账号缺少 platform:write 权限。</p></section>
    <template v-else>
      <form class="membership-filter" @submit.prevent="applyFilter">
        <el-select v-model="filter.status" placeholder="全部状态"><el-option label="全部状态" value="" /><el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" /></el-select>
        <el-input v-model="filter.keyword" :prefix-icon="Search" clearable maxlength="100" placeholder="检索企业名称" />
        <el-button type="primary" native-type="submit">查询</el-button><el-button @click="resetFilter">重置</el-button>
      </form>
      <section class="membership-table" v-loading="loading">
        <div class="membership-caption"><strong>会员服务记录</strong><span>共 {{ result.total }} 条</span></div>
        <el-table :data="result.records" empty-text="暂无会员或付费投放记录">
          <el-table-column prop="supplierName" label="企业" min-width="190" show-overflow-tooltip />
          <el-table-column label="获取方式" width="110"><template #default="{ row }"><el-tag :type="row.sourceType === 'INVITED' ? 'success' : 'primary'" effect="plain">{{ sourceLabels[row.sourceType] || row.sourceType }}</el-tag></template></el-table-column>
          <el-table-column prop="planName" label="服务方案" min-width="150" show-overflow-tooltip />
          <el-table-column label="金额 / 付款" width="140"><template #default="{ row }"><strong class="membership-amount">{{ formatAmount(row.feeAmount) }}</strong><span class="membership-secondary">{{ paymentLabels[row.paymentStatus] || row.paymentStatus }}</span></template></el-table-column>
          <el-table-column label="服务周期" min-width="230"><template #default="{ row }">{{ formatDate(row.startsAt) }}<span class="membership-period">至 {{ formatDate(row.endsAt) }}</span></template></el-table-column>
          <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="statusTypes[row.status]" effect="plain">{{ statusLabels[row.status] || row.status }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="190" fixed="right"><template #default="{ row }"><el-button v-if="row.sourceType === 'PAID' && row.status === 'PENDING'" :icon="CircleCheck" link type="success" @click="openActivate(row)">确认收款并开通</el-button><el-button v-if="['PENDING','ACTIVE'].includes(row.status)" :icon="Close" link type="danger" @click="cancelService(row)">停用</el-button></template></el-table-column>
        </el-table>
        <div class="membership-pagination"><el-pagination v-model:current-page="page" :page-size="20" :total="result.total" layout="total, prev, pager, next" @current-change="load" /></div>
      </section>
    </template>

    <el-dialog v-model="inviteVisible" title="赠送邀请会员" width="min(580px, 94vw)">
      <el-alert title="邀请会员无需付款，赠送后立即获得投放权限。默认有效期为 12 个月。" type="success" :closable="false" show-icon />
      <el-form label-position="top" class="membership-dialog-form"><el-form-item label="受邀企业" required><el-select v-model="inviteForm.supplierId" filterable placeholder="选择已通过资质审核的企业" style="width:100%"><el-option v-for="item in suppliers" :key="item.id" :label="item.name" :value="item.id"><span>{{ item.name }}</span><small v-if="item.creditCode" class="supplier-code">{{ item.creditCode }}</small></el-option></el-select></el-form-item><div class="membership-form-grid"><el-form-item label="服务名称"><el-input v-model="inviteForm.planName" maxlength="120" /></el-form-item><el-form-item label="赠送月数"><el-input-number v-model="inviteForm.durationMonths" :min="1" :max="60" style="width:100%" /></el-form-item></div><el-form-item label="内部备注"><el-input v-model="inviteForm.note" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="inviteVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveInvite">确认赠送</el-button></template>
    </el-dialog>

    <el-dialog v-model="activateVisible" title="确认收款并开通" width="min(620px, 94vw)">
      <p class="membership-dialog-copy">{{ activateForm.supplierName }}提交了付费投放申请。仅在实际款项到账后执行开通。</p>
      <el-form label-position="top"><div class="membership-form-grid"><el-form-item label="服务名称" required><el-input v-model="activateForm.planName" maxlength="120" /></el-form-item><el-form-item label="实际到账金额" required><el-input-number v-model="activateForm.feeAmount" :min="0.01" :precision="2" :step="100" style="width:100%" /></el-form-item></div><div class="membership-form-grid"><el-form-item label="开始时间" required><el-date-picker v-model="activateForm.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item><el-form-item label="结束时间" required><el-date-picker v-model="activateForm.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item></div><el-form-item label="收款或合同备注"><el-input v-model="activateForm.note" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="activateVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="activatePaid">确认已收款并开通</el-button></template>
    </el-dialog>
  </section>
</template>
