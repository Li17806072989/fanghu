<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api, hasPermission } from '../api'
import '../ai-reports.css'

const canAudit = computed(() => hasPermission('platform:audit'))
const loading = ref(false)
const error = ref('')
const jobs = ref([])
const accessStatus = ref('PENDING')
const selected = ref(null)
const opinion = ref('')
const decisionVisible = ref(false)
const contentVisible = ref(false)
const fullReport = ref(null)
const statusLabels = { QUEUED: '排队中', RUNNING: '生成中', COMPLETED: '已完成', FAILED: '生成失败' }
const accessLabels = { NOT_REQUESTED: '未申请', PENDING: '待审核', GRANTED: '已授权', DENIED: '已拒绝' }

function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
async function responseMessage(response, fallback) { try { return (await response.json()).message || fallback } catch { return fallback } }
async function load() {
  if (!canAudit.value) return
  loading.value = true; error.value = ''
  try {
    const query = accessStatus.value ? `?accessStatus=${accessStatus.value}` : ''
    const response = await api(`/api/admin/ai-reports${query}`)
    if (!response.ok) throw new Error(await responseMessage(response, '汇报授权列表加载失败'))
    jobs.value = await response.json()
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
function openDecision(row) { selected.value = row; opinion.value = ''; decisionVisible.value = true }
async function decide(decision) {
  const response = await api(`/api/admin/ai-reports/${selected.value.id}/access`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ decision, opinion: opinion.value }) })
  if (!response.ok) { error.value = await responseMessage(response, '授权处理失败'); return }
  decisionVisible.value = false; ElMessage.success(decision === 'GRANTED' ? '已授权全文访问' : '已拒绝全文申请'); await load()
}
async function viewContent(row) {
  const response = await api(`/api/ai/reports/${row.id}/content`)
  if (!response.ok) { error.value = await responseMessage(response, '全文读取失败'); return }
  fullReport.value = await response.json(); contentVisible.value = true
}
onMounted(load)
</script>

<template>
  <section class="report-workspace report-admin" v-loading="loading">
    <header class="report-heading"><div><h1>汇报全文授权</h1><p>审核客户的全文获取申请；目录和资料数量可先行核验。</p></div><div><RouterLink class="el-button" to="/admin">返回审核后台</RouterLink><el-button :icon="Refresh" @click="load">刷新</el-button></div></header>
    <el-alert v-if="error" class="report-alert" :title="error" type="error" show-icon closable @close="error=''" />
    <div v-if="!canAudit" class="report-empty">当前账号没有 platform:audit 权限。</div>
    <section v-else class="report-list-section admin-report-list"><div class="list-heading"><div><h2>授权申请</h2><p>只有已生成完成且未过期的任务可以授权。</p></div><el-select v-model="accessStatus" style="width:150px" @change="load"><el-option label="待审核" value="PENDING" /><el-option label="已授权" value="GRANTED" /><el-option label="已拒绝" value="DENIED" /><el-option label="全部任务" value="" /></el-select></div>
      <el-table :data="jobs" empty-text="当前没有相关汇报任务"><el-table-column prop="title" label="汇报标题" min-width="250" /><el-table-column prop="requesterId" label="申请人编号" width="110" /><el-table-column label="资料范围" min-width="180"><template #default="{ row }">专题 {{ row.topicCount }} / 资讯 {{ row.articleCount }} / 方案 {{ row.solutionCount }}</template></el-table-column><el-table-column label="任务状态" width="100"><template #default="{ row }">{{ statusLabels[row.status] || row.status }}</template></el-table-column><el-table-column label="授权状态" width="100"><template #default="{ row }">{{ accessLabels[row.accessStatus] || row.accessStatus }}</template></el-table-column><el-table-column label="创建时间" width="180"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column><el-table-column label="操作" width="180" fixed="right"><template #default="{ row }"><el-button v-if="row.status==='COMPLETED'" link :icon="View" @click="viewContent(row)">核验全文</el-button><el-button v-if="row.accessStatus==='PENDING'" link type="primary" @click="openDecision(row)">审核</el-button></template></el-table-column></el-table>
    </section>
    <el-dialog v-model="decisionVisible" title="全文授权审核" width="min(620px, 92vw)"><h3 class="decision-title">{{ selected?.title }}</h3><ol class="decision-toc"><li v-for="item in selected?.toc || []" :key="item">{{ item }}</li></ol><el-input v-model="opinion" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="填写授权说明或拒绝原因" /><template #footer><el-button type="danger" plain @click="decide('DENIED')">拒绝</el-button><el-button type="primary" @click="decide('GRANTED')">授权全文</el-button></template></el-dialog>
    <el-dialog v-model="contentVisible" :title="fullReport?.title || '汇报全文核验'" width="min(900px, 94vw)" class="report-content-dialog"><pre>{{ fullReport?.content }}</pre></el-dialog>
  </section>
</template>
