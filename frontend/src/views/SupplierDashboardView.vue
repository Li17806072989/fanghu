<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { CircleCheck, CircleClose, DataAnalysis, Download, Edit, Files, OfficeBuilding, Phone, Plus, Refresh, Upload } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { api } from '../api'
import DetailContentEditor from '../components/DetailContentEditor.vue'
import '../supplier-workbench.css'

const loading = ref(true)
const saving = ref(false)
const downloadingId = ref(null)
const activeTab = ref('overview')
const error = ref('')
const message = ref('')
const data = ref({ supplier: null, solutions: [], attachments: [], leads: [], summary: {}, trend: [], membership: null, serviceEffect: null })
const categories = ref([])
const equipmentTree = ref([])
const sceneTree = ref([])
const techTree = ref([])
const detailEditor = ref(null)
const coverFile = ref(null)
const coverPreview = ref('')
const profile = reactive({ name: '', creditCode: '', intro: '' })
const editVisible = ref(false)
const attachmentVisible = ref(false)
const feedbackVisible = ref(false)
const selectedFile = ref(null)
const editForm = reactive({ id: null, title: '', categoryId: null, sceneTaxonomyIds: [], techTaxonomyIds: [], summary: '', content: '', imageUrl: '', detailContent: '' })
const attachmentForm = reactive({ solutionId: null })
const feedbackForm = reactive({ id: null, demandTitle: '', status: 'CONTACTED', remark: '', nextActionAt: '' })

const solutionMap = computed(() => Object.fromEntries(data.value.solutions.map(item => [item.id, item.title])))
const approvedSolutions = computed(() => data.value.solutions.filter(item => item.auditStatus === 'APPROVED').length)
const serviceActive = computed(() => data.value.membership?.status === 'ACTIVE')
const paidServiceActive = computed(() => serviceActive.value && data.value.membership?.sourceType === 'PAID')
const paidServicePending = computed(() => data.value.membership?.sourceType === 'PAID' && data.value.membership?.status === 'PENDING')
const canApplyPaid = computed(() => data.value.supplier?.verificationStatus === 'APPROVED' && (!data.value.membership || ['EXPIRED', 'CANCELLED'].includes(data.value.membership.status)))
const effectData = computed(() => data.value.serviceEffect || data.value.summary || {})
const qualificationRate = computed(() => percentage(effectData.value.qualifiedLeads, effectData.value.inquiries))
const wonFeedbackRate = computed(() => percentage(effectData.value.wonLeads, effectData.value.inquiries))
const solutionApprovalRate = computed(() => percentage(approvedSolutions.value, data.value.solutions.length))
const statusLabels = { DRAFT: '草稿', PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', OFFLINE: '已下线', NEW: '新线索', MATCHED: '已匹配', CONTACTED: '已联系', INTENT: '有意向', WON: '已成交', LOST: '已关闭' }
const statusTypes = { APPROVED: 'success', WON: 'success', PENDING: 'warning', MATCHED: 'primary', CONTACTED: 'primary', INTENT: 'warning', REJECTED: 'danger', LOST: 'info', OFFLINE: 'info' }

function statusLabel(value) { return statusLabels[value] || value || '未知' }
function statusType(value) { return statusTypes[value] || 'info' }
function membershipStatusLabel(value) { return ({ PENDING: '待确认收款', ACTIVE: '生效中', EXPIRED: '已到期', CANCELLED: '已停用' })[value] || '未开通' }
function membershipSourceLabel(value) { return ({ INVITED: '平台邀请赠送', PAID: '企业付费投放' })[value] || '基础入驻' }
function percentage(part, total) {
  const numerator = Number(part || 0)
  const denominator = Number(total || 0)
  return denominator > 0 ? `${(numerator / denominator * 100).toFixed(1)}%` : '—'
}
function fileName(key) { return key?.split('/').pop()?.replace(/^[0-9a-f-]{36}-/, '') || '附件' }
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '未设置' }
function categoryName(id) { return categories.value.find(item => item.id === id)?.name || `分类 ${id}` }
function flattenTree(nodes) { return nodes.flatMap(node => [node, ...flattenTree(node.children || [])]) }

async function downloadAttachment(row) {
  if (downloadingId.value) return
  downloadingId.value = row.id; error.value = ''; message.value = ''
  try {
    const response = await api(`/api/storage/${row.id}/download`)
    const payload = await response.json()
    if (!response.ok) throw new Error(payload.message || '无法获取下载链接')
    window.open(payload.url, '_blank', 'noopener,noreferrer')
    message.value = `已签发限时下载链接，有效期 ${Math.round(payload.expiresIn / 60)} 分钟`
    row.downloadCount = (row.downloadCount || 0) + 1
  } catch (e) {
    error.value = e.message || '下载失败，请稍后重试'
  } finally {
    downloadingId.value = null
  }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const [overviewResponse, equipmentResponse, sceneResponse, techResponse] = await Promise.all([
      api('/api/workbench/supplier'), api('/api/categories/taxonomy/tree?type=EQUIPMENT'),
      api('/api/categories/taxonomy/tree?type=SCENE'), api('/api/categories/taxonomy/tree?type=TECH')
    ])
    if (!overviewResponse.ok) throw new Error('工作台数据加载失败，请重新登录后再试')
    data.value = await overviewResponse.json()
    equipmentTree.value = equipmentResponse.ok ? await equipmentResponse.json() : []
    sceneTree.value = sceneResponse.ok ? await sceneResponse.json() : []
    techTree.value = techResponse.ok ? await techResponse.json() : []
    categories.value = flattenTree(equipmentTree.value)
    if (data.value.supplier) Object.assign(profile, data.value.supplier)
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

async function saveProfile() {
  saving.value = true; message.value = ''; error.value = ''
  try {
    const response = await api('/api/workbench/supplier/profile', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(profile) })
    if (!response.ok) throw new Error((await response.json()).message || '企业资料保存失败')
    data.value.supplier = await response.json(); message.value = '企业资料已保存'
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

function editSolution(row) {
  const sceneIds = new Set(flattenTree(sceneTree.value).map(item => item.id))
  const techIds = new Set(flattenTree(techTree.value).map(item => item.id))
  Object.assign(editForm, { id: row.id, title: row.title, categoryId: row.categoryId,
    sceneTaxonomyIds: (row.taxonomyIds || []).filter(id => sceneIds.has(id)),
    techTaxonomyIds: (row.taxonomyIds || []).filter(id => techIds.has(id)),
    summary: row.summary || '', content: row.content || '', imageUrl: row.imageUrl || '', detailContent: row.detailContent || '' })
  coverFile.value = null
  if (coverPreview.value?.startsWith('blob:')) URL.revokeObjectURL(coverPreview.value)
  coverPreview.value = row.imageUrl || ''
  editVisible.value = true
  nextTick(() => detailEditor.value?.reset(row.detailContent || ''))
}
function selectCover(upload) {
  if (!upload?.file) return
  if (!['image/jpeg', 'image/png', 'image/gif'].includes(upload.file.type)) { error.value = '封面仅支持 JPG、PNG 或 GIF'; return }
  if (upload.file.size > 8 * 1024 * 1024) { error.value = '封面图片不能超过 8MB'; return }
  coverFile.value = upload.file
  if (coverPreview.value?.startsWith('blob:')) URL.revokeObjectURL(coverPreview.value)
  coverPreview.value = URL.createObjectURL(upload.file)
}
async function saveSolution() {
  if (!editForm.title || !editForm.categoryId) { error.value = '请填写方案标题和装备分类'; return }
  const detailError = detailEditor.value?.validate()
  if (detailError) { error.value = detailError; return }
  saving.value = true; error.value = ''; message.value = ''
  try {
    const sceneNames = flattenTree(sceneTree.value).filter(item => editForm.sceneTaxonomyIds.includes(item.id)).map(item => item.name)
    const taxonomyIds = [...editForm.sceneTaxonomyIds, ...editForm.techTaxonomyIds]
    const payload = { ...editForm, detailContent: detailEditor.value?.serialize({ excludePendingImages: true }), sceneTags: sceneNames.join(','), taxonomyIds }
    let response = await api(`/api/solutions/${editForm.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
    if (!response.ok) throw new Error((await response.json()).message || '方案保存失败')
    const solution = await response.json()
    if (coverFile.value) {
      const data = new FormData(); data.append('file', coverFile.value)
      const upload = await api(`/api/storage/upload?ownerType=SOLUTION_IMAGE&ownerId=${editForm.id}`, { method: 'POST', body: data })
      if (!upload.ok) throw new Error('方案已保存，但封面上传失败，请重新编辑后继续上传')
      const asset = await upload.json(); editForm.imageUrl = `/api/storage/public/${asset.id}`
    }
    await detailEditor.value?.upload('SOLUTION_DETAIL', editForm.id)
    response = await api(`/api/solutions/${editForm.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...solution, ...editForm, detailContent: detailEditor.value?.serialize() || null, taxonomyIds, sceneTags: sceneNames.join(',') }) })
    if (!response.ok) throw new Error('方案已保存，但详情内容保存失败，请重新编辑后继续上传')
    editVisible.value = false; message.value = '方案已更新并重新进入审核队列'; await load()
  } catch (e) { error.value = e.message || '方案保存失败' } finally { saving.value = false }
}

function openAttachment(row) { attachmentForm.solutionId = row.id; selectedFile.value = null; attachmentVisible.value = true }
function pickFile(event) { selectedFile.value = event.target.files?.[0] || null }
async function uploadAttachment() {
  if (!selectedFile.value) { error.value = '请选择需要上传的附件'; return }
  saving.value = true; error.value = ''
  const body = new FormData(); body.append('file', selectedFile.value)
  const response = await api(`/api/storage/upload?ownerType=SOLUTION&ownerId=${attachmentForm.solutionId}`, { method: 'POST', body })
  if (response.ok) { attachmentVisible.value = false; message.value = '附件已通过扫描并保存'; await load() }
  else error.value = (await response.json()).message || '附件上传失败'
  saving.value = false
}

function openFeedback(row) {
  Object.assign(feedbackForm, { id: row.id, demandTitle: row.demandTitle, status: ['CONTACTED', 'INTENT', 'WON', 'LOST'].includes(row.status) ? row.status : 'CONTACTED', remark: row.remark || '', nextActionAt: row.nextActionAt || '' })
  feedbackVisible.value = true
}
async function saveFeedback() {
  saving.value = true; error.value = ''
  const response = await api(`/api/workbench/supplier/leads/${feedbackForm.id}/feedback`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: feedbackForm.status, remark: feedbackForm.remark, nextActionAt: feedbackForm.nextActionAt || null }) })
  if (response.ok) { feedbackVisible.value = false; message.value = '线索反馈已更新'; await load() }
  else error.value = (await response.json()).message || '线索反馈保存失败'
  saving.value = false
}
async function confirmLead(row, accepted) {
  saving.value = true; error.value = ''; message.value = ''
  try {
    const response = await api(`/api/workbench/supplier/leads/${row.id}/confirmation`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ accepted }) })
    if (!response.ok) throw new Error((await response.json()).message || '对接意向确认失败')
    message.value = accepted ? '已确认对接，等待客户确认后开放联系方式' : '已标记为暂不对接'
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}
async function applyPaidService() {
  try {
    await ElMessageBox.confirm('提交后平台运营人员会联系确认投放方案、费用和服务周期。当前操作不会自动扣款。', '申请付费投放', { type: 'info', confirmButtonText: '提交申请', cancelButtonText: '取消' })
    saving.value = true; error.value = ''; message.value = ''
    const response = await api('/api/workbench/supplier/paid-service/apply', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' })
    if (!response.ok) throw new Error((await response.json()).message || '付费投放申请提交失败')
    message.value = '付费投放申请已提交，请等待平台确认方案和收款'
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') error.value = e.message || '付费投放申请提交失败'
  } finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <section class="supplier-workbench" v-loading="loading">
    <header class="workbench-heading">
      <div>
        <h1>供应商工作台</h1>
        <p v-if="data.supplier">{{ data.supplier.name }}的资料、方案和对接进展</p>
        <p v-else>完善企业入驻后管理解决方案和客户线索</p>
      </div>
      <div class="heading-actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <RouterLink v-if="data.supplier" class="el-button el-button--primary" to="/workbench/solutions"><el-icon><Plus /></el-icon><span>新建方案</span></RouterLink>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon closable @close="error=''" />
    <el-alert v-if="message" :title="message" type="success" show-icon closable @close="message=''" />

    <div v-if="!loading && !data.supplier" class="workbench-empty">
      <el-icon><OfficeBuilding /></el-icon>
      <h2>尚未建立企业档案</h2>
      <p>提交企业资料并等待资质审核，通过后即可发布方案和接收客户线索。</p>
      <RouterLink class="primary-button" to="/supplier/apply">前往供应商入驻</RouterLink>
    </div>

    <template v-else-if="data.supplier">
      <div class="supplier-identity">
        <div><strong>{{ data.supplier.name }}</strong><span>信用代码 {{ data.supplier.creditCode || '待补充' }}</span></div>
        <el-tag :type="statusType(data.supplier.verificationStatus)" effect="plain">资质{{ statusLabel(data.supplier.verificationStatus) }}</el-tag>
      </div>

      <div class="metric-strip" aria-label="近30日经营数据">
        <div><span>方案曝光</span><strong>{{ data.summary.impressions || 0 }}</strong><small>近 30 日</small></div>
        <div><span>附件下载</span><strong>{{ data.summary.downloads || 0 }}</strong><small>累计真实下载</small></div>
        <div><span>客户咨询</span><strong>{{ data.summary.inquiries || 0 }}</strong><small>匹配线索</small></div>
        <div><span>有效意向</span><strong>{{ data.summary.qualifiedLeads || 0 }}</strong><small>意向及成交</small></div>
        <div><span>已成交</span><strong>{{ data.summary.wonLeads || 0 }}</strong><small>供应商反馈</small></div>
      </div>

      <el-tabs v-model="activeTab" class="workbench-tabs">
        <el-tab-pane label="工作概览" name="overview">
          <div class="overview-grid">
            <section class="workbench-section">
              <div class="section-heading"><div><h2>最近方案</h2><p>审核状态决定公开目录中的可见性</p></div><el-button text @click="activeTab='solutions'">查看全部</el-button></div>
              <el-table :data="data.solutions.slice(0, 5)" empty-text="暂无方案">
                <el-table-column prop="title" label="方案名称" min-width="220" />
                <el-table-column label="审核状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.auditStatus)" effect="plain">{{ statusLabel(row.auditStatus) }}</el-tag></template></el-table-column>
                <el-table-column label="更新时间" width="180"><template #default="{ row }">{{ formatDate(row.updatedAt) }}</template></el-table-column>
              </el-table>
            </section>
            <section class="workbench-section lead-preview">
              <div class="section-heading"><div><h2>待跟进线索</h2><p>优先处理尚未确认意向的客户需求</p></div><el-button text @click="activeTab='leads'">进入线索</el-button></div>
              <div v-if="data.leads.length" class="lead-list"><button v-for="lead in data.leads.slice(0,4)" :key="lead.id" @click="activeTab='leads'"><span>{{ lead.demandTitle }}</span><el-tag :type="statusType(lead.status)" size="small" effect="plain">{{ statusLabel(lead.status) }}</el-tag></button></div>
              <el-empty v-else :image-size="56" description="暂无匹配线索" />
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane label="入驻与付费效果" name="effect">
          <section class="effect-section">
            <div class="effect-heading">
              <div><h2>入驻与付费效果反馈</h2><p>区分基础入驻自然效果与付费推广效果，所有数据均来自平台真实操作记录。</p></div>
              <el-tag :type="serviceActive ? 'success' : paidServicePending ? 'warning' : 'info'" effect="plain">{{ serviceActive ? `${membershipSourceLabel(data.membership.sourceType)}生效中` : membershipStatusLabel(data.membership?.status) }}</el-tag>
            </div>

            <dl class="onboarding-feedback">
              <div><dt>入驻申请</dt><dd>{{ formatDate(data.supplier.createdAt) }}</dd><span>资料已提交</span></div>
              <div><dt>资质审核</dt><dd>{{ statusLabel(data.supplier.verificationStatus) }}</dd><span>{{ data.supplier.verificationStatus === 'APPROVED' ? '企业资质已通过平台审核' : data.supplier.verificationStatus === 'REJECTED' ? '请按审核意见修改资料' : '平台运营人员正在审核' }}</span></div>
              <div><dt>方案上线</dt><dd>{{ approvedSolutions }} / {{ data.solutions.length }}</dd><span>已发布方案 / 全部方案</span></div>
              <div><dt>当前服务</dt><dd>{{ data.membership?.planName || '基础入驻服务' }}</dd><span>{{ serviceActive ? `${formatDate(data.membership.startsAt)} 至 ${formatDate(data.membership.endsAt)}` : paidServicePending ? '申请已提交，等待平台确认收款' : '暂未开通会员或付费投放' }}</span></div>
            </dl>

            <div v-if="!serviceActive" class="paid-effect-empty">
              <el-icon><DataAnalysis /></el-icon>
              <div><strong>{{ paidServicePending ? '付费投放申请等待确认' : '尚无可归因的会员服务周期' }}</strong><p>{{ paidServicePending ? '平台确认投放方案和实际收款后，将录入服务周期并开放投放权限。' : '下方展示基础入驻产生的自然效果；开通后将按服务起止时间单独统计。' }}</p><el-button v-if="canApplyPaid" type="primary" :loading="saving" @click="applyPaidService">申请付费投放</el-button></div>
            </div>

            <div class="effect-metric-heading"><div><h3>{{ serviceActive ? '本期会员服务效果' : '基础入驻效果' }}</h3><p>{{ data.serviceEffect ? `${formatDate(data.serviceEffect.startsAt)} 至 ${formatDate(data.serviceEffect.endsAt)}` : '当前统计口径与工作台经营数据一致' }}</p></div><el-button text @click="activeTab='leads'">查看线索反馈</el-button></div>
            <div class="effect-metrics" aria-label="供应商效果数据">
              <div><span>方案曝光</span><strong>{{ effectData.impressions || 0 }}</strong><small>{{ data.serviceEffect ? '服务期目录曝光' : '近 30 日目录曝光' }}</small></div>
              <div><span>内容浏览</span><strong>{{ effectData.views || 0 }}</strong><small>{{ data.serviceEffect ? '服务期内容访问' : '近 30 日内容访问' }}</small></div>
              <div><span>附件下载</span><strong>{{ effectData.downloads || 0 }}</strong><small>{{ data.serviceEffect ? '服务期限时下载' : '当前方案累计下载' }}</small></div>
              <div><span>客户咨询</span><strong>{{ effectData.inquiries || 0 }}</strong><small>{{ data.serviceEffect ? '服务期新增线索' : '累计匹配线索' }}</small></div>
              <div><span>有效意向</span><strong>{{ effectData.qualifiedLeads || 0 }}</strong><small>意向及成交线索</small></div>
              <div><span>成交反馈</span><strong>{{ effectData.wonLeads || 0 }}</strong><small>供应商已反馈成交</small></div>
            </div>

            <div class="effect-rates">
              <div><span>方案审核通过率</span><strong>{{ solutionApprovalRate }}</strong><small>{{ approvedSolutions }} 个方案已公开</small></div>
              <div><span>线索有效意向率</span><strong>{{ qualificationRate }}</strong><small>有效意向 / 客户咨询</small></div>
              <div><span>成交反馈率</span><strong>{{ wonFeedbackRate }}</strong><small>成交反馈 / 客户咨询</small></div>
              <div><span>服务获取方式</span><strong class="effect-source">{{ membershipSourceLabel(data.membership?.sourceType) }}</strong><small>{{ paidServiceActive ? '实际收款后开通' : data.membership?.sourceType === 'INVITED' ? '平台赠送免会员费' : '尚未开通服务' }}</small></div>
            </div>
            <p class="effect-footnote">说明：会员生效后按服务起止时间统计曝光、浏览、下载和新增线索；未开通时展示基础入驻数据。成交结果以供应商在线索反馈中填写的状态为准。</p>
          </section>
        </el-tab-pane>

        <el-tab-pane label="企业资料" name="profile">
          <section class="workbench-section profile-section">
            <div class="section-heading"><div><h2>企业基础资料</h2><p>修改基础资料不会改变当前资质审核状态</p></div></div>
            <el-form label-position="top" @submit.prevent="saveProfile">
              <div class="form-grid"><el-form-item label="企业名称" required><el-input v-model="profile.name" maxlength="200" /></el-form-item><el-form-item label="统一社会信用代码"><el-input v-model="profile.creditCode" maxlength="40" /></el-form-item></div>
              <el-form-item label="企业与能力简介"><el-input v-model="profile.intro" type="textarea" :rows="7" maxlength="2000" show-word-limit /></el-form-item>
              <el-button type="primary" :loading="saving" @click="saveProfile">保存企业资料</el-button>
            </el-form>
          </section>
        </el-tab-pane>

        <el-tab-pane :label="`方案管理 ${data.solutions.length}`" name="solutions">
          <section class="workbench-section">
            <div class="section-heading"><div><h2>解决方案</h2><p>编辑后将重新进入审核队列，已通过版本不再公开</p></div><RouterLink class="el-button el-button--primary" to="/workbench/solutions"><el-icon><Plus /></el-icon><span>新建方案</span></RouterLink></div>
            <el-table :data="data.solutions" empty-text="暂无解决方案">
              <el-table-column label="方案名称" min-width="230"><template #default="{ row }"><RouterLink v-if="row.auditStatus === 'APPROVED'" :to="`/solutions/${row.id}`" class="solution-link">{{ row.title }}</RouterLink><span v-else>{{ row.title }}</span></template></el-table-column>
              <el-table-column label="分类" width="150"><template #default="{ row }">{{ categoryName(row.categoryId) }}</template></el-table-column>
              <el-table-column label="审核状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.auditStatus)" effect="plain">{{ statusLabel(row.auditStatus) }}</el-tag></template></el-table-column>
              <el-table-column label="更新时间" width="180"><template #default="{ row }">{{ formatDate(row.updatedAt) }}</template></el-table-column>
              <el-table-column label="操作" width="185" fixed="right"><template #default="{ row }"><el-button :icon="Edit" link type="primary" @click="editSolution(row)">编辑</el-button><el-button :icon="Upload" link @click="openAttachment(row)">附件</el-button></template></el-table-column>
            </el-table>
          </section>
        </el-tab-pane>

        <el-tab-pane :label="`附件记录 ${data.attachments.length}`" name="attachments">
          <section class="workbench-section">
            <div class="section-heading"><div><h2>附件上传记录</h2><p>仅显示当前企业方案所属文件及安全扫描结果</p></div></div>
            <el-table :data="data.attachments" empty-text="暂无附件记录">
              <el-table-column label="文件" min-width="230"><template #default="{ row }"><span class="file-name"><el-icon><Files /></el-icon>{{ fileName(row.objectKey) }}</span></template></el-table-column>
              <el-table-column label="所属方案" min-width="190"><template #default="{ row }">{{ solutionMap[row.ownerId] || `方案 ${row.ownerId}` }}</template></el-table-column>
              <el-table-column label="扫描状态" width="110"><template #default="{ row }"><el-tag :type="row.scanStatus==='PASSED'?'success':row.scanStatus==='PENDING'?'warning':'danger'" effect="plain">{{ row.scanStatus==='PASSED'?'已通过':row.scanStatus==='PENDING'?'扫描中':'未通过' }}</el-tag></template></el-table-column>
              <el-table-column label="文件校验" width="130"><template #default="{ row }"><el-tooltip v-if="row.sha256" :content="row.sha256" placement="top"><code class="file-hash">{{ row.sha256.slice(0, 12) }}</code></el-tooltip><span v-else class="withheld-contact">待生成</span></template></el-table-column>
              <el-table-column prop="downloadCount" label="下载次数" width="100" />
              <el-table-column label="上传时间" width="180"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
              <el-table-column label="操作" width="110" fixed="right"><template #default="{ row }"><el-button :icon="Download" link type="primary" :loading="downloadingId===row.id" :disabled="row.scanStatus!=='PASSED'" @click="downloadAttachment(row)">限时下载</el-button></template></el-table-column>
            </el-table>
          </section>
        </el-tab-pane>

        <el-tab-pane :label="`客户线索 ${data.leads.length}`" name="leads">
          <section class="workbench-section">
            <div class="section-heading"><div><h2>客户需求与线索反馈</h2><p>双方确认后才开放联系人；更新联系结果和下一步计划时，运营人员可同步查看。</p></div></div>
            <el-table :data="data.leads" empty-text="暂无匹配线索">
              <el-table-column prop="demandTitle" label="客户需求" min-width="230" />
              <el-table-column label="匹配度" width="90"><template #default="{ row }">{{ row.matchScore == null ? '—' : `${row.matchScore}%` }}</template></el-table-column>
              <el-table-column label="客户意向" width="110"><template #default="{ row }"><el-tag :type="statusType(row.customerConfirmation === 'ACCEPTED' ? 'APPROVED' : row.customerConfirmation === 'DECLINED' ? 'LOST' : 'PENDING')" effect="plain">{{ row.customerConfirmation === 'ACCEPTED' ? '已确认' : row.customerConfirmation === 'DECLINED' ? '暂不对接' : '待确认' }}</el-tag></template></el-table-column>
              <el-table-column label="本方意向" width="110"><template #default="{ row }"><el-tag :type="statusType(row.supplierConfirmation === 'ACCEPTED' ? 'APPROVED' : row.supplierConfirmation === 'DECLINED' ? 'LOST' : 'PENDING')" effect="plain">{{ row.supplierConfirmation === 'ACCEPTED' ? '已确认' : row.supplierConfirmation === 'DECLINED' ? '暂不对接' : '待确认' }}</el-tag></template></el-table-column>
              <el-table-column label="进展" width="105"><template #default="{ row }"><el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
              <el-table-column label="客户联系人" min-width="160"><template #default="{ row }"><span v-if="row.contactName" class="contact-detail"><el-icon><Phone /></el-icon>{{ row.contactName }} {{ row.contactPhone || '电话待补充' }}</span><span v-else class="withheld-contact">双方确认后开放</span></template></el-table-column>
              <el-table-column prop="remark" label="最近反馈" min-width="220" show-overflow-tooltip />
              <el-table-column label="下次跟进" width="180"><template #default="{ row }">{{ formatDate(row.nextActionAt) }}</template></el-table-column>
              <el-table-column label="操作" width="178" fixed="right"><template #default="{ row }"><template v-if="row.status === 'MATCHED' && row.supplierConfirmation === 'PENDING'"><el-button :icon="CircleCheck" link type="success" :loading="saving" @click="confirmLead(row, true)">确认对接</el-button><el-button :icon="CircleClose" link type="danger" :loading="saving" @click="confirmLead(row, false)">暂不对接</el-button></template><el-button v-else-if="row.customerConfirmation === 'ACCEPTED' && row.supplierConfirmation === 'ACCEPTED' && ['CONTACTED','INTENT'].includes(row.status)" :icon="Edit" link type="primary" @click="openFeedback(row)">反馈</el-button><span v-else class="withheld-contact">{{ row.status === 'MATCHED' ? '等待双方确认' : '无需操作' }}</span></template></el-table-column>
            </el-table>
          </section>
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-drawer v-model="editVisible" title="编辑解决方案" size="min(760px, 100%)" class="content-editor" destroy-on-close>
      <el-form label-position="top"><el-form-item label="方案标题" required><el-input v-model="editForm.title" maxlength="200" /></el-form-item><el-form-item label="装备分类" required><el-tree-select v-model="editForm.categoryId" :data="equipmentTree" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" check-strictly filterable :render-after-expand="false" style="width:100%" /></el-form-item><div class="form-grid"><el-form-item label="应用场景"><el-tree-select v-model="editForm.sceneTaxonomyIds" :data="sceneTree" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" multiple check-strictly filterable collapse-tags :render-after-expand="false" style="width:100%" /></el-form-item><el-form-item label="技术标签"><el-tree-select v-model="editForm.techTaxonomyIds" :data="techTree" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" multiple check-strictly filterable collapse-tags :render-after-expand="false" style="width:100%" /></el-form-item></div><el-form-item label="摘要"><el-input v-model="editForm.summary" type="textarea" :rows="3" maxlength="500" /></el-form-item><el-form-item label="方案封面"><div class="cover-field"><div v-if="coverPreview" class="cover-preview"><img :src="coverPreview" alt="方案封面预览"></div><el-upload :auto-upload="false" :limit="1" accept="image/jpeg,image/png,image/gif" :on-change="selectCover"><el-button>选择或更换封面</el-button></el-upload></div></el-form-item><el-form-item label="方案正文"><el-input v-model="editForm.content" type="textarea" :rows="7" /></el-form-item></el-form>
      <DetailContentEditor ref="detailEditor" />
      <template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveSolution">保存并重新送审</el-button></template>
    </el-drawer>

    <el-dialog v-model="attachmentVisible" title="上传方案附件" width="min(520px, 92vw)">
      <p class="dialog-note">文件将进入私有存储，通过安全扫描后才可按业务权限下载。</p>
      <label class="file-picker"><input type="file" @change="pickFile"><el-icon><Upload /></el-icon><span>{{ selectedFile?.name || '选择本地文件' }}</span></label>
      <template #footer><el-button @click="attachmentVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="uploadAttachment">上传并扫描</el-button></template>
    </el-dialog>

    <el-dialog v-model="feedbackVisible" title="更新线索反馈" width="min(560px, 92vw)">
      <p class="dialog-note">{{ feedbackForm.demandTitle }}</p>
      <el-form label-position="top"><el-form-item label="当前进展"><el-segmented v-model="feedbackForm.status" :options="[{label:'已联系',value:'CONTACTED'},{label:'有意向',value:'INTENT'},{label:'已成交',value:'WON'},{label:'已关闭',value:'LOST'}]" /></el-form-item><el-form-item label="反馈说明"><el-input v-model="feedbackForm.remark" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item><el-form-item label="下次跟进时间"><el-date-picker v-model="feedbackForm.nextActionAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择时间" style="width:100%" /></el-form-item></el-form>
      <template #footer><el-button @click="feedbackVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveFeedback">保存反馈</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.contact-detail{display:inline-flex;align-items:center;gap:6px;color:#24735a;white-space:nowrap}.withheld-contact{font-size:12px;color:#60746a}.file-hash{font-size:12px;color:#344b43;font-variant-numeric:tabular-nums}.solution-link{color:#28627a;text-decoration:none}.solution-link:hover{text-decoration:underline}
</style>
