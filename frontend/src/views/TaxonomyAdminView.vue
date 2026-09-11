<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api, hasPermission } from '../api'
import '../taxonomy-admin.css'

const types = [{ label: '专题分类', value: 'TOPIC' }, { label: '装备分类', value: 'EQUIPMENT' }, { label: '应用场景', value: 'SCENE' }, { label: '技术标签', value: 'TECH' }]
const activeType = ref('TOPIC')
const nodes = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({ parentId: 0, taxonomyType: 'TOPIC', code: '', name: '', description: '', sortNo: 0, enabled: true })
const canWrite = computed(() => hasPermission('platform:write'))
const tree = computed(() => buildTree(nodes.value))
const parentOptions = computed(() => [{ id: 0, name: '顶级分类', children: [] }, ...tree.value])

function buildTree(source) {
  const map = new Map(source.map(item => [item.id, { ...item, children: [] }]))
  const roots = []
  for (const item of map.values()) {
    if (item.parentId && map.has(item.parentId)) map.get(item.parentId).children.push(item)
    else roots.push(item)
  }
  const sort = list => { list.sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0) || a.id - b.id); list.forEach(item => sort(item.children)) }
  sort(roots); return roots
}
async function responseMessage(response, fallback) { try { return (await response.json()).message || fallback } catch { return fallback } }
async function load() {
  loading.value = true; error.value = ''
  try {
    const response = await api(`/api/categories/taxonomy?type=${activeType.value}&enabledOnly=false`)
    if (!response.ok) throw new Error(await responseMessage(response, '分类数据加载失败'))
    nodes.value = await response.json()
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
function openCreate(parentId = 0) {
  editingId.value = null
  Object.assign(form, { parentId, taxonomyType: activeType.value, code: '', name: '', description: '', sortNo: 0, enabled: true })
  dialogVisible.value = true
}
function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, { parentId: row.parentId, taxonomyType: row.taxonomyType, code: row.code, name: row.name, description: row.description || '', sortNo: row.sortNo || 0, enabled: row.enabled })
  dialogVisible.value = true
}
async function save() {
  if (!form.name.trim() || !form.code.trim()) { error.value = '分类名称和编码不能为空'; return }
  saving.value = true; error.value = ''
  try {
    const endpoint = editingId.value ? `/api/categories/taxonomy/${editingId.value}` : '/api/categories/taxonomy'
    const response = await api(endpoint, { method: editingId.value ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form) })
    if (!response.ok) throw new Error(await responseMessage(response, '分类保存失败'))
    dialogVisible.value = false; ElMessage.success('分类已保存'); await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}
async function changeType(value) { activeType.value = value; await load() }
onMounted(load)
</script>

<template>
  <section class="taxonomy-admin" v-loading="loading">
    <header class="taxonomy-header"><div><h1>分类与标签管理</h1><p>维护专题、装备、应用场景和技术标签的树形结构；已使用节点建议停用而非删除。</p></div><div><RouterLink class="el-button" to="/admin">返回审核后台</RouterLink><el-button :icon="Refresh" @click="load">刷新</el-button><el-button type="primary" :icon="Plus" :disabled="!canWrite" @click="openCreate()">新增顶级分类</el-button></div></header>
    <el-alert v-if="error" class="taxonomy-alert" :title="error" type="error" show-icon closable @close="error=''" />
    <el-segmented :model-value="activeType" :options="types" @change="changeType" />
    <section class="taxonomy-table"><div class="table-caption"><span>{{ types.find(item => item.value === activeType)?.label }}</span><small>共 {{ nodes.length }} 个节点</small></div><el-table :data="tree" row-key="id" default-expand-all :tree-props="{ children: 'children' }" empty-text="当前分类树没有节点"><el-table-column prop="name" label="分类名称" min-width="230" /><el-table-column prop="code" label="分类编码" min-width="190" /><el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip /><el-table-column prop="sortNo" label="排序" width="90" /><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="175" fixed="right"><template #default="{ row }"><el-button :icon="Plus" link :disabled="!canWrite" @click="openCreate(row.id)">新增下级</el-button><el-button :icon="Edit" link type="primary" :disabled="!canWrite" @click="openEdit(row)">编辑</el-button></template></el-table-column></el-table></section>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分类节点' : '新增分类节点'" width="min(600px, 94vw)"><el-form label-position="top"><el-form-item label="上级分类"><el-tree-select v-model="form.parentId" :data="parentOptions" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" check-strictly :render-after-expand="false" style="width:100%" /></el-form-item><div class="taxonomy-form-row"><el-form-item label="分类名称" required><el-input v-model="form.name" maxlength="80" /></el-form-item><el-form-item label="分类编码" required><el-input v-model="form.code" maxlength="60" placeholder="英文、数字或连字符" /></el-form-item></div><el-form-item label="分类说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="255" show-word-limit /></el-form-item><div class="taxonomy-form-row"><el-form-item label="排序值"><el-input-number v-model="form.sortNo" :min="0" :max="9999" /></el-form-item><el-form-item label="启用状态"><el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item></div></el-form><template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存分类</el-button></template></el-dialog>
  </section>
</template>
