<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import '../upload.css'

const form = ref({ title: '', categoryId: null, sceneTaxonomyIds: [], techTaxonomyIds: [], summary: '', content: '' })
const attachments = ref([])
const message = ref('')
const error = ref('')
const submitting = ref(false)
const equipmentTree = ref([])
const sceneTree = ref([])
const techTree = ref([])
const taxonomyNames = computed(() => {
  const result = {}
  const visit = nodes => nodes.forEach(node => { result[node.id] = node.name; visit(node.children || []) })
  visit(sceneTree.value); visit(techTree.value)
  return result
})

function selectFile(upload) { attachments.value = [upload.file] }
async function responseMessage(response, fallback) { try { return (await response.json()).message || fallback } catch { return fallback } }
async function loadTaxonomies() {
  const types = ['EQUIPMENT', 'SCENE', 'TECH']
  const responses = await Promise.all(types.map(type => api(`/api/categories/taxonomy/tree?type=${type}`)))
  equipmentTree.value = responses[0].ok ? await responses[0].json() : []
  sceneTree.value = responses[1].ok ? await responses[1].json() : []
  techTree.value = responses[2].ok ? await responses[2].json() : []
}
async function submit() {
  if (!form.value.title.trim() || !form.value.categoryId) { error.value = '请填写方案标题并选择装备分类'; return }
  submitting.value = true; message.value = ''; error.value = ''
  try {
    const taxonomyIds = [...form.value.sceneTaxonomyIds, ...form.value.techTaxonomyIds]
    const sceneTags = form.value.sceneTaxonomyIds.map(id => taxonomyNames.value[id]).filter(Boolean).join(',')
    const response = await api('/api/solutions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...form.value, sceneTags, taxonomyIds }) })
    if (!response.ok) throw new Error(await responseMessage(response, '方案提交失败，请先完成供应商入驻'))
    const solution = await response.json()
    for (const file of attachments.value) {
      const data = new FormData(); data.append('file', file)
      const upload = await api(`/api/storage/upload?ownerType=SOLUTION&ownerId=${solution.id}`, { method: 'POST', body: data })
      if (!upload.ok) throw new Error(await responseMessage(upload, '方案已保存，但附件安全扫描或上传失败'))
    }
    message.value = '方案和附件已提交，等待审核'
  } catch (e) { error.value = e.message || '提交失败，请稍后重试' } finally { submitting.value = false }
}
onMounted(loadTaxonomies)
</script>

<template>
  <section class="upload-page">
    <RouterLink class="back-link" to="/workbench">返回供应商工作台</RouterLink>
    <h1>上传企业解决方案</h1>
    <el-alert v-if="error" :title="error" type="error" show-icon closable @close="error=''" />
    <el-alert v-if="message" :title="message" type="success" show-icon closable @close="message=''" />
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="方案标题" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
      <el-form-item label="装备分类" required><el-tree-select v-model="form.categoryId" :data="equipmentTree" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" check-strictly filterable :render-after-expand="false" placeholder="选择装备分类" style="width:100%" /></el-form-item>
      <div class="taxonomy-row">
        <el-form-item label="应用场景"><el-tree-select v-model="form.sceneTaxonomyIds" :data="sceneTree" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" multiple check-strictly filterable collapse-tags :render-after-expand="false" placeholder="选择应用场景" style="width:100%" /></el-form-item>
        <el-form-item label="技术标签"><el-tree-select v-model="form.techTaxonomyIds" :data="techTree" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" multiple check-strictly filterable collapse-tags :render-after-expand="false" placeholder="选择技术标签" style="width:100%" /></el-form-item>
      </div>
      <el-form-item label="摘要"><el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      <el-form-item label="方案正文"><el-input v-model="form.content" type="textarea" :rows="8" /></el-form-item>
      <el-form-item label="资料附件"><el-upload drag :auto-upload="false" :limit="1" :on-change="selectFile"><div>拖拽或选择文件，提交时自动扫描</div></el-upload></el-form-item>
      <el-button type="primary" native-type="submit" :loading="submitting">提交审核</el-button>
    </el-form>
  </section>
</template>
