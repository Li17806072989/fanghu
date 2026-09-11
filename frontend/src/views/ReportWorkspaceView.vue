<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { Document, Refresh, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import '../ai-reports.css'

const loading = ref(false)
const creating = ref(false)
const error = ref('')
const sources = ref({ topics: [], articles: [], solutions: [] })
const jobs = ref([])
const form = reactive({ title: '', topicIds: [], articleIds: [], solutionIds: [] })
const requestVisible = ref(false)
const requestReason = ref('')
const selectedJob = ref(null)
const contentVisible = ref(false)
const fullReport = ref(null)
let timer

const selectedCount = computed(() => form.topicIds.length + form.articleIds.length + form.solutionIds.length)
const statusLabels = { QUEUED: '排队中', RUNNING: '生成中', COMPLETED: '已完成', FAILED: '生成失败' }
const statusTypes = { QUEUED: 'info', RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger' }
const accessLabels = { NOT_REQUESTED: '未申请', PENDING: '待审核', GRANTED: '已授权', DENIED: '已拒绝' }
const accessTypes = { NOT_REQUESTED: 'info', PENDING: 'warning', GRANTED: 'success', DENIED: 'danger' }

function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
function generatorLabel(value) { return value?.startsWith('AI:') ? `模型生成 · ${value.slice(3)}` : value === 'STRUCTURED' ? '结构化汇编' : '等待生成' }
async function message(response, fallback) { try { return (await response.json()).message || fallback } catch { return fallback } }
async function load({ quiet = false } = {}) {
  if (!quiet) loading.value = true
  try {
    const [sourceResponse, jobResponse] = await Promise.all([api('/api/ai/reports/sources'), api('/api/ai/reports')])
    if (!sourceResponse.ok || !jobResponse.ok) throw new Error('汇报工作台数据加载失败')
    sources.value = await sourceResponse.json(); jobs.value = await jobResponse.json()
  } catch (e) { if (!quiet) error.value = e.message } finally { if (!quiet) loading.value = false }
}
async function createReport() {
  if (!form.title.trim()) { error.value = '请填写汇报标题'; return }
  if (!selectedCount.value) { error.value = '请至少选择一条专题、资讯或解决方案'; return }
  creating.value = true; error.value = ''
  try {
    const response = await api('/api/ai/reports', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form) })
    if (!response.ok) throw new Error(await message(response, '汇报任务创建失败'))
    Object.assign(form, { title: '', topicIds: [], articleIds: [], solutionIds: [] })
    ElMessage.success('异步汇报任务已创建'); await load({ quiet: true })
  } catch (e) { error.value = e.message } finally { creating.value = false }
}
function openRequest(row) { selectedJob.value = row; requestReason.value = ''; requestVisible.value = true }
async function requestAccess() {
  const response = await api(`/api/ai/reports/${selectedJob.value.id}/access-request`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason: requestReason.value }) })
  if (!response.ok) { error.value = await message(response, '全文申请提交失败'); return }
  requestVisible.value = false; ElMessage.success('全文申请已提交'); await load({ quiet: true })
}
async function viewContent(row) {
  const response = await api(`/api/ai/reports/${row.id}/content`)
  if (!response.ok) { error.value = await message(response, '暂时无法读取全文'); return }
  fullReport.value = await response.json(); contentVisible.value = true
}
onMounted(async () => { await load(); timer = setInterval(() => { if (jobs.value.some(job => ['QUEUED', 'RUNNING'].includes(job.status))) load({ quiet: true }) }, 3000) })
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <section class="report-workspace" v-loading="loading">
    <header class="report-heading"><div><h1>AI 汇报工作台</h1><p>从已审核专题、资讯和解决方案中选择资料，先生成标题与目录，全文需按权限申请。</p></div><el-button :icon="Refresh" @click="load()">刷新</el-button></header>
    <el-alert v-if="error" class="report-alert" :title="error" type="error" show-icon closable @close="error=''" />

    <section class="report-builder">
      <div class="builder-heading"><div><h2>新建汇报任务</h2><p>已选择 {{ selectedCount }} 条资料，单次最多 30 条。</p></div><el-icon><Document /></el-icon></div>
      <el-form label-position="top" @submit.prevent="createReport">
        <el-form-item label="汇报标题" required><el-input v-model="form.title" maxlength="200" placeholder="例如：无人系统应用与解决方案资料汇编" /></el-form-item>
        <div class="source-grid">
          <el-form-item label="专题"><el-select v-model="form.topicIds" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择已发布专题" :max-collapse-tags="2"><el-option v-for="item in sources.topics" :key="item.id" :label="item.title" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="资讯"><el-select v-model="form.articleIds" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择已发布资讯" :max-collapse-tags="2"><el-option v-for="item in sources.articles" :key="item.id" :label="item.title" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="解决方案"><el-select v-model="form.solutionIds" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择已发布方案" :max-collapse-tags="2"><el-option v-for="item in sources.solutions" :key="item.id" :label="item.title" :value="item.id" /></el-select></el-form-item>
        </div>
        <el-button type="primary" native-type="submit" :loading="creating" :disabled="selectedCount > 30">生成标题与目录</el-button>
      </el-form>
    </section>

    <section class="report-list-section">
      <div class="list-heading"><div><h2>我的汇报任务</h2><p>目录可以直接查看，任务列表不会返回全文正文。</p></div><span>{{ jobs.length }} 个任务</span></div>
      <div v-if="jobs.length" class="report-list">
        <article v-for="job in jobs" :key="job.id" class="report-job">
          <div class="job-main"><div class="job-status"><el-tag :type="statusTypes[job.status]" effect="plain">{{ statusLabels[job.status] || job.status }}</el-tag><el-tag :type="accessTypes[job.accessStatus]" effect="plain">全文{{ accessLabels[job.accessStatus] || job.accessStatus }}</el-tag></div><h3>{{ job.title }}</h3><p>专题 {{ job.topicCount }} · 资讯 {{ job.articleCount }} · 方案 {{ job.solutionCount }} · {{ generatorLabel(job.generator) }}</p><time>{{ formatDate(job.createdAt) }}</time></div>
          <div class="job-toc"><strong>目录</strong><ol><li v-for="item in job.toc" :key="item">{{ item }}</li></ol></div>
          <div class="job-actions"><el-button v-if="job.status==='COMPLETED' && ['NOT_REQUESTED','DENIED'].includes(job.accessStatus)" @click="openRequest(job)">申请全文</el-button><el-button v-if="job.status==='COMPLETED' && job.accessStatus==='GRANTED'" type="primary" :icon="View" @click="viewContent(job)">查看全文</el-button><span v-if="job.accessStatus==='PENDING'">等待运营审核</span><span v-if="job.status==='FAILED'">请重新创建任务</span></div>
        </article>
      </div>
      <div v-else class="report-empty">尚未创建汇报任务。</div>
    </section>

    <el-dialog v-model="requestVisible" title="申请查看全文" width="min(520px, 92vw)"><p class="dialog-note">{{ selectedJob?.title }}</p><el-input v-model="requestReason" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="填写全文用途或业务说明（选填）" /><template #footer><el-button @click="requestVisible=false">取消</el-button><el-button type="primary" @click="requestAccess">提交申请</el-button></template></el-dialog>
    <el-dialog v-model="contentVisible" :title="fullReport?.title || '汇报全文'" width="min(900px, 94vw)" class="report-content-dialog"><div class="content-meta">{{ generatorLabel(fullReport?.generator) }} · 有效期至 {{ formatDate(fullReport?.expiresAt) }}</div><pre>{{ fullReport?.content }}</pre></el-dialog>
  </section>
</template>
