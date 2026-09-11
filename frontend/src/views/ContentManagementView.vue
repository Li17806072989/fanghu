<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { Delete, DocumentChecked, Edit, Plus, Refresh, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, hasPermission } from '../api'
import DetailContentEditor from '../components/DetailContentEditor.vue'
import '../content-management.css'

const canWrite = computed(() => hasPermission('platform:write'))
const activeType = ref('topics')
const loading = ref(false)
const saving = ref(false)
const deletingId = ref(null)
const error = ref('')
const drawerVisible = ref(false)
const editingId = ref(null)
const coverFile = ref(null)
const coverPreview = ref('')
const detailEditor = ref(null)
const page = ref(1)
const result = ref({ records: [], total: 0, current: 1, size: 20 })
const filter = reactive({ keyword: '', status: '' })
const trees = reactive({ TOPIC: [], EQUIPMENT: [], SCENE: [], TECH: [] })
const articleCategories = ref([])
const topicForm = reactive({ title: '', summary: '', coverUrl: '', sourceDesc: '', sourceUrl: '', categoryId: null, topicIds: [], sceneIds: [], techIds: [], status: 'DRAFT' })
const articleForm = reactive({ title: '', summary: '', imageUrl: '', content: '', sourceName: '', sourceUrl: '', categoryId: null, publishedAt: '', topicIds: [], equipmentIds: [], sceneIds: [], techIds: [], status: 'DRAFT' })
const statuses = { DRAFT: '草稿', PENDING: '待审核', APPROVED: '已发布', REJECTED: '已驳回', OFFLINE: '已下线' }
const statusTypes = { DRAFT: 'info', PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', OFFLINE: 'info' }
const drawerTitle = computed(() => `${editingId.value ? '编辑' : '新增'}${activeType.value === 'topics' ? '专题' : '资讯'}`)

function nowValue() {
  const date = new Date(Date.now() - new Date().getTimezoneOffset() * 60000)
  return date.toISOString().slice(0, 19)
}
function flatten(nodes) { return nodes.flatMap(node => [node, ...flatten(node.children || [])]) }
function idSet(type) { return new Set(flatten(trees[type]).map(node => node.id)) }
function idsFor(type, ids = []) { const valid = idSet(type); return ids.filter(id => valid.has(id)) }
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '未设置' }
async function responseMessage(response, fallback) { try { return (await response.json()).message || fallback } catch { return fallback } }

async function loadDependencies() {
  const types = ['TOPIC', 'EQUIPMENT', 'SCENE', 'TECH']
  const responses = await Promise.all([...types.map(type => api(`/api/categories/taxonomy/tree?type=${type}`)), api('/api/categories/articles')])
  for (let index = 0; index < types.length; index += 1) trees[types[index]] = responses[index].ok ? await responses[index].json() : []
  articleCategories.value = responses[4].ok ? await responses[4].json() : []
}

async function load() {
  if (!canWrite.value) return
  loading.value = true; error.value = ''
  try {
    const params = new URLSearchParams({ page: String(page.value), size: '20' })
    if (filter.keyword.trim()) params.set('keyword', filter.keyword.trim())
    if (filter.status) params.set('status', filter.status)
    const response = await api(`/api/admin/content/${activeType.value}?${params}`)
    if (!response.ok) throw new Error(await responseMessage(response, '内容列表加载失败'))
    result.value = await response.json()
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function clearCoverPreview() {
  if (coverPreview.value.startsWith('blob:')) URL.revokeObjectURL(coverPreview.value)
  coverPreview.value = ''
}
function resetTopic() {
  Object.assign(topicForm, { title: '', summary: '', coverUrl: '', sourceDesc: '', sourceUrl: '', categoryId: null, topicIds: [], sceneIds: [], techIds: [], status: 'DRAFT' })
}
function resetArticle() {
  Object.assign(articleForm, { title: '', summary: '', imageUrl: '', content: '', sourceName: '', sourceUrl: '', categoryId: articleCategories.value[0]?.id || null, publishedAt: nowValue(), topicIds: [], equipmentIds: [], sceneIds: [], techIds: [], status: 'DRAFT' })
}
async function openCreate() {
  editingId.value = null; coverFile.value = null; clearCoverPreview()
  activeType.value === 'topics' ? resetTopic() : resetArticle()
  drawerVisible.value = true
  await nextTick()
  detailEditor.value?.reset()
}
async function openEdit(row) {
  error.value = ''
  try {
    const response = await api(`/api/admin/content/${activeType.value}/${row.id}`)
    if (!response.ok) throw new Error(await responseMessage(response, '内容详情加载失败'))
    const item = await response.json()
    editingId.value = item.id; coverFile.value = null; clearCoverPreview()
    if (activeType.value === 'topics') {
      Object.assign(topicForm, { title: item.title || '', summary: item.summary || '', coverUrl: item.coverUrl || '', sourceDesc: item.sourceDesc || '', sourceUrl: item.sourceUrl || '', categoryId: item.categoryId,
        topicIds: idsFor('TOPIC', item.taxonomyIds), sceneIds: idsFor('SCENE', item.taxonomyIds), techIds: idsFor('TECH', item.taxonomyIds), status: item.status })
      coverPreview.value = item.coverUrl || ''
    } else {
      Object.assign(articleForm, { title: item.title || '', summary: item.summary || '', imageUrl: item.imageUrl || '', content: item.content || '', sourceName: item.sourceName || '', sourceUrl: item.sourceUrl || '', categoryId: item.categoryId,
        publishedAt: item.publishedAt || '', topicIds: idsFor('TOPIC', item.taxonomyIds), equipmentIds: idsFor('EQUIPMENT', item.taxonomyIds), sceneIds: idsFor('SCENE', item.taxonomyIds), techIds: idsFor('TECH', item.taxonomyIds), status: item.status })
      coverPreview.value = item.imageUrl || ''
    }
    drawerVisible.value = true
    await nextTick()
    detailEditor.value?.reset(item.detailContent)
  } catch (e) { error.value = e.message }
}

function chooseCover(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (file.size > 8 * 1024 * 1024) { error.value = `${activeType.value === 'topics' ? '专题封面' : '资讯图片'}不能超过 8MB`; event.target.value = ''; return }
  coverFile.value = file; clearCoverPreview(); coverPreview.value = URL.createObjectURL(file)
}
function topicPayload(excludePendingImages = false) {
  return { title: topicForm.title, summary: topicForm.summary, coverUrl: topicForm.coverUrl, sourceDesc: topicForm.sourceDesc, sourceUrl: topicForm.sourceUrl,
    categoryId: topicForm.categoryId, taxonomyIds: [...topicForm.topicIds, ...topicForm.sceneIds, ...topicForm.techIds],
    detailContent: detailEditor.value?.serialize({ excludePendingImages }) || null }
}
function articlePayload(excludePendingImages = false) {
  return { categoryId: articleForm.categoryId, title: articleForm.title, summary: articleForm.summary, imageUrl: articleForm.imageUrl, content: articleForm.content,
    sourceName: articleForm.sourceName, sourceUrl: articleForm.sourceUrl, publishedAt: articleForm.publishedAt,
    taxonomyIds: [...articleForm.topicIds, ...articleForm.equipmentIds, ...articleForm.sceneIds, ...articleForm.techIds],
    detailContent: detailEditor.value?.serialize({ excludePendingImages }) || null }
}
function validate(submit) {
  if (activeType.value === 'topics') {
    if (!topicForm.title.trim() || !topicForm.categoryId) return '请填写专题标题并选择装备分类'
    if (submit && !topicForm.sourceDesc.trim()) return '提交审核前请填写来源说明'
  } else {
    if (!articleForm.title.trim() || !articleForm.categoryId) return '请填写资讯标题并选择资讯分类'
    if (submit && ((!articleForm.imageUrl && !coverFile.value) || !articleForm.content.trim() || !articleForm.sourceName.trim() || !articleForm.publishedAt)) return '提交审核前请上传资讯图片，并填写正文、来源名称和发布时间'
  }
  return ''
}

async function save(submit) {
  const validation = validate(submit)
  if (validation) { error.value = validation; return }
  const detailValidation = detailEditor.value?.validate()
  if (detailValidation) { error.value = detailValidation; return }
  saving.value = true; error.value = ''
  try {
    const base = `/api/admin/content/${activeType.value}`
    const endpoint = editingId.value ? `${base}/${editingId.value}` : base
    let response = await api(endpoint, { method: editingId.value ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(activeType.value === 'topics' ? topicPayload(true) : articlePayload(true)) })
    if (!response.ok) throw new Error(await responseMessage(response, '草稿保存失败'))
    let saved = await response.json(); editingId.value = saved.id
    const isTopic = activeType.value === 'topics'
    const ownerType = isTopic ? 'TOPIC' : 'ARTICLE'
    let requiresFinalUpdate = false
    if (coverFile.value) {
      const imageLabel = isTopic ? '专题封面' : '资讯图片'
      const data = new FormData(); data.append('file', coverFile.value)
      const upload = await api(`/api/storage/upload?ownerType=${ownerType}&ownerId=${saved.id}`, { method: 'POST', body: data })
      if (!upload.ok) throw new Error(await responseMessage(upload, `草稿已保存，但${imageLabel}上传失败`))
      const asset = await upload.json()
      if (isTopic) topicForm.coverUrl = `/api/storage/public/${asset.id}`
      else articleForm.imageUrl = `/api/storage/public/${asset.id}`
      coverFile.value = null
      requiresFinalUpdate = true
    }
    if (await detailEditor.value?.upload(ownerType, saved.id)) requiresFinalUpdate = true
    if (requiresFinalUpdate) {
      response = await api(`${base}/${saved.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(isTopic ? topicPayload() : articlePayload()) })
      if (!response.ok) throw new Error(await responseMessage(response, '图片已上传，但详情内容关联失败'))
      saved = await response.json()
    }
    if (submit) {
      response = await api(`${base}/${saved.id}/submit`, { method: 'PATCH' })
      if (!response.ok) throw new Error(await responseMessage(response, '草稿已保存，但提交审核失败'))
    }
    ElMessage.success(submit ? '内容已保存并提交审核' : '草稿已保存')
    drawerVisible.value = false; clearCoverPreview(); await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function toggleFeature(row, value) {
  error.value = ''
  const isTopic = activeType.value === 'topics'
  const endpoint = isTopic ? `/api/admin/homepage/topics/${row.id}/recommended?value=${value}` : `/api/admin/homepage/articles/${row.id}/hot?value=${value}`
  const response = await api(endpoint, { method: 'PUT' })
  if (!response.ok) {
    if (isTopic) row.isRecommended = !value; else row.hot = !value
    error.value = await responseMessage(response, isTopic ? '首页推荐状态更新失败' : '热点状态更新失败')
    return
  }
  ElMessage.success(value ? (isTopic ? '已加入首页推荐' : '已加入首页热点') : (isTopic ? '已取消首页推荐' : '已移出首页热点'))
}
async function deleteContent(row) {
  const contentName = activeType.value === 'topics' ? '专题' : '资讯'
  try {
    const onlineWarning = ['APPROVED', 'PENDING'].includes(row.status)
      ? '该内容当前已发布或正在审核，删除后会立即从公开页面或审核队列移除。'
      : ''
    await ElMessageBox.confirm(
      `确定删除${contentName}“${row.title}”吗？${onlineWarning}删除后无法恢复，分类关联和相关文件将被清理，操作记录会保留。`,
      `删除${contentName}`,
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
    deletingId.value = row.id
    const response = await api(`/api/admin/content/${activeType.value}/${row.id}`, { method: 'DELETE' })
    if (!response.ok) throw new Error(await responseMessage(response, `${contentName}删除失败`))
    if (result.value.records.length === 1 && page.value > 1) page.value -= 1
    ElMessage.success(`${contentName}已删除`)
    await load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    error.value = e.message || `${contentName}删除失败`
  } finally { deletingId.value = null }
}
function applyFilter() { page.value = 1; load() }
function resetFilter() { Object.assign(filter, { keyword: '', status: '' }); applyFilter() }
async function changeType() { page.value = 1; Object.assign(filter, { keyword: '', status: '' }); await load() }
onMounted(async () => { if (canWrite.value) { await loadDependencies(); await load() } })
onUnmounted(clearCoverPreview)
</script>

<template>
  <section class="content-management">
    <header class="content-heading">
      <div><h1>内容管理</h1><p>维护专题与资讯，保存草稿并提交审核；已发布内容可直接配置首页推荐。</p></div>
      <div><RouterLink class="el-button" to="/admin">返回审核后台</RouterLink><el-button :icon="Refresh" @click="load">刷新</el-button><el-button v-if="canWrite" type="primary" :icon="Plus" @click="openCreate">新增{{ activeType === 'topics' ? '专题' : '资讯' }}</el-button></div>
    </header>
    <el-alert v-if="error" class="content-alert" :title="error" type="error" show-icon closable @close="error=''" />
    <section v-if="!canWrite" class="content-denied"><h2>没有内容管理权限</h2><p>当前账号缺少 platform:write 权限。</p></section>
    <template v-else>
      <el-tabs v-model="activeType" class="content-tabs" @tab-change="changeType"><el-tab-pane label="专题管理" name="topics" /><el-tab-pane label="资讯管理" name="articles" /></el-tabs>
      <form class="content-filter" @submit.prevent="applyFilter">
        <el-select v-model="filter.status" aria-label="内容状态" placeholder="全部状态"><el-option label="全部状态" value="" /><el-option v-for="(label, value) in statuses" :key="value" :label="label" :value="value" /></el-select>
        <el-input v-model="filter.keyword" clearable maxlength="100" :placeholder="activeType === 'topics' ? '检索标题或来源' : '检索标题或来源名称'" />
        <el-button type="primary" native-type="submit">查询</el-button><el-button @click="resetFilter">重置</el-button>
      </form>
      <section class="content-table" v-loading="loading">
        <div class="content-table-caption"><strong>{{ activeType === 'topics' ? '专题条目' : '资讯条目' }}</strong><span>共 {{ result.total }} 条</span></div>
        <el-table v-if="activeType === 'topics'" :data="result.records" empty-text="暂无专题内容">
          <el-table-column label="专题" min-width="260"><template #default="{ row }"><strong class="content-title">{{ row.title }}</strong><span class="content-source">{{ row.sourceDesc || '来源待补充' }}</span></template></el-table-column>
          <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="statusTypes[row.status]" effect="plain">{{ statuses[row.status] || row.status }}</el-tag></template></el-table-column>
          <el-table-column label="首页推荐" width="105"><template #default="{ row }"><el-switch v-model="row.isRecommended" :disabled="row.status !== 'APPROVED'" @change="value => toggleFeature(row, value)" /></template></el-table-column>
          <el-table-column label="发布时间" width="180"><template #default="{ row }">{{ formatDate(row.publishedAt) }}</template></el-table-column>
          <el-table-column label="更新时间" width="180"><template #default="{ row }">{{ formatDate(row.updatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="170" fixed="right"><template #default="{ row }"><el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button><el-button link type="danger" :icon="Delete" :loading="deletingId === row.id" :disabled="deletingId !== null && deletingId !== row.id" @click="deleteContent(row)">删除</el-button></template></el-table-column>
        </el-table>
        <el-table v-else :data="result.records" empty-text="暂无资讯内容">
          <el-table-column label="资讯" min-width="280"><template #default="{ row }"><strong class="content-title">{{ row.title }}</strong><span class="content-source">{{ row.sourceName || '来源待补充' }}</span></template></el-table-column>
          <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="statusTypes[row.status]" effect="plain">{{ statuses[row.status] || row.status }}</el-tag></template></el-table-column>
          <el-table-column label="首页热点" width="105"><template #default="{ row }"><el-switch v-model="row.hot" :disabled="row.status !== 'APPROVED'" @change="value => toggleFeature(row, value)" /></template></el-table-column>
          <el-table-column label="发布时间" width="180"><template #default="{ row }">{{ formatDate(row.publishedAt) }}</template></el-table-column>
          <el-table-column label="更新时间" width="180"><template #default="{ row }">{{ formatDate(row.updatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="170" fixed="right"><template #default="{ row }"><el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button><el-button link type="danger" :icon="Delete" :loading="deletingId === row.id" :disabled="deletingId !== null && deletingId !== row.id" @click="deleteContent(row)">删除</el-button></template></el-table-column>
        </el-table>
        <div class="content-pagination"><el-pagination v-model:current-page="page" :page-size="20" :total="result.total" layout="total, prev, pager, next" @current-change="load" /></div>
      </section>
    </template>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="min(760px, 100%)" class="content-editor" destroy-on-close @closed="clearCoverPreview">
      <el-alert v-if="(activeType === 'topics' ? topicForm.status : articleForm.status) === 'APPROVED'" title="编辑已发布内容并保存后，内容会转为草稿并暂时从公开页面下线。" type="warning" show-icon :closable="false" />
      <el-form v-if="activeType === 'topics'" label-position="top" @submit.prevent="save(false)">
        <el-form-item label="专题标题" required><el-input v-model="topicForm.title" maxlength="200" show-word-limit /></el-form-item>
        <div class="editor-grid"><el-form-item label="装备分类" required><el-tree-select v-model="topicForm.categoryId" :data="trees.EQUIPMENT" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" check-strictly filterable placeholder="选择装备主分类" /></el-form-item><el-form-item label="专题分类"><el-tree-select v-model="topicForm.topicIds" :data="trees.TOPIC" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" multiple check-strictly filterable collapse-tags placeholder="选择专题分类" /></el-form-item></div>
        <div class="editor-grid"><el-form-item label="应用场景"><el-tree-select v-model="topicForm.sceneIds" :data="trees.SCENE" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" multiple check-strictly filterable collapse-tags placeholder="选择应用场景" /></el-form-item><el-form-item label="技术标签"><el-tree-select v-model="topicForm.techIds" :data="trees.TECH" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" multiple check-strictly filterable collapse-tags placeholder="选择技术标签" /></el-form-item></div>
        <div class="editor-grid"><el-form-item label="来源说明" required><el-input v-model="topicForm.sourceDesc" maxlength="255" placeholder="例如：企业官网或公开报道" /></el-form-item><el-form-item label="来源链接"><el-input v-model="topicForm.sourceUrl" maxlength="500" placeholder="选填，填写完整的 https:// 地址" /></el-form-item></div>
        <el-form-item label="专题摘要"><el-input v-model="topicForm.summary" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="专题封面"><div class="cover-field"><img v-if="coverPreview" :src="coverPreview" alt="专题封面预览"><label class="cover-picker"><input type="file" accept="image/jpeg,image/png,image/gif" @change="chooseCover"><el-icon><UploadFilled /></el-icon><span>{{ coverFile ? coverFile.name : '选择 JPG、PNG 或 GIF，最大 8MB' }}</span></label></div></el-form-item>
      </el-form>
      <el-form v-else label-position="top" @submit.prevent="save(false)">
        <el-form-item label="资讯标题" required><el-input v-model="articleForm.title" maxlength="200" show-word-limit /></el-form-item>
        <div class="editor-grid"><el-form-item label="资讯分类" required><el-select v-model="articleForm.categoryId" filterable placeholder="选择资讯分类"><el-option v-for="item in articleCategories" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="发布时间" required><el-date-picker v-model="articleForm.publishedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择发布时间" /></el-form-item></div>
        <div class="editor-grid"><el-form-item label="来源名称" required><el-input v-model="articleForm.sourceName" maxlength="120" placeholder="发布机构或原始媒体" /></el-form-item><el-form-item label="来源链接"><el-input v-model="articleForm.sourceUrl" maxlength="500" placeholder="选填，填写完整的 https:// 地址" /></el-form-item></div>
        <el-form-item label="资讯摘要"><el-input v-model="articleForm.summary" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="资讯图片"><div class="cover-field"><img v-if="coverPreview" :src="coverPreview" alt="资讯图片预览"><label class="cover-picker"><input type="file" accept="image/jpeg,image/png,image/gif" @change="chooseCover"><el-icon><UploadFilled /></el-icon><span>{{ coverFile ? coverFile.name : '选择相关资讯图片，支持 JPG、PNG 或 GIF，最大 8MB' }}</span></label></div></el-form-item>
        <el-form-item label="资讯正文" required><el-input v-model="articleForm.content" type="textarea" :rows="11" maxlength="100000" show-word-limit /></el-form-item>
        <div class="editor-grid"><el-form-item label="专题分类"><el-tree-select v-model="articleForm.topicIds" :data="trees.TOPIC" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" multiple check-strictly filterable collapse-tags /></el-form-item><el-form-item label="装备分类"><el-tree-select v-model="articleForm.equipmentIds" :data="trees.EQUIPMENT" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" multiple check-strictly filterable collapse-tags /></el-form-item><el-form-item label="应用场景"><el-tree-select v-model="articleForm.sceneIds" :data="trees.SCENE" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" multiple check-strictly filterable collapse-tags /></el-form-item><el-form-item label="技术标签"><el-tree-select v-model="articleForm.techIds" :data="trees.TECH" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" multiple check-strictly filterable collapse-tags /></el-form-item></div>
      </el-form>
      <DetailContentEditor ref="detailEditor" />
      <template #footer><div class="editor-actions"><el-button @click="drawerVisible=false">取消</el-button><el-button :loading="saving" @click="save(false)">保存草稿</el-button><el-button type="primary" :icon="DocumentChecked" :loading="saving" @click="save(true)">保存并提交审核</el-button></div></template>
    </el-drawer>
  </section>
</template>
