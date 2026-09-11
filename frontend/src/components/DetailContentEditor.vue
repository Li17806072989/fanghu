<script setup>
import { onUnmounted, ref } from 'vue'
import { ArrowDown, ArrowUp, Delete, Document, Picture, Plus, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const blocks = ref([])
const files = ref({})
const previews = ref({})
let nextKey = 1

function key() { return `detail-${Date.now()}-${nextKey++}` }
function message(response, fallback) { return response.json().then(body => body.message || fallback).catch(() => fallback) }
function clearPreview(blockKey) {
  const preview = previews.value[blockKey]
  if (preview?.startsWith('blob:')) URL.revokeObjectURL(preview)
  delete previews.value[blockKey]
}
function clearAllPreviews() {
  Object.keys(previews.value).forEach(clearPreview)
  files.value = {}
}
function reset(serialized = '') {
  clearAllPreviews()
  let parsed = []
  try { parsed = serialized ? JSON.parse(serialized) : [] } catch { parsed = [] }
  blocks.value = Array.isArray(parsed)
    ? parsed.filter(block => ['TEXT', 'IMAGE'].includes(block?.type)).map(block => ({
        key: key(), type: block.type, text: block.text || '', imageUrl: block.imageUrl || '',
        caption: block.caption || '', linkUrl: block.linkUrl || ''
      }))
    : []
}
function add(type) {
  blocks.value.push({ key: key(), type, text: '', imageUrl: '', caption: '', linkUrl: '' })
}
function remove(index) {
  clearPreview(blocks.value[index].key)
  delete files.value[blocks.value[index].key]
  blocks.value.splice(index, 1)
}
function move(index, direction) {
  const target = index + direction
  if (target < 0 || target >= blocks.value.length) return
  const [block] = blocks.value.splice(index, 1)
  blocks.value.splice(target, 0, block)
}
function chooseImage(block, event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/gif'].includes(file.type)) { ElMessage.error('详情图片仅支持 JPG、PNG 或 GIF'); event.target.value = ''; return }
  if (file.size > 8 * 1024 * 1024) { ElMessage.error('详情图片不能超过 8MB'); event.target.value = ''; return }
  clearPreview(block.key)
  files.value[block.key] = file
  previews.value[block.key] = URL.createObjectURL(file)
  event.target.value = ''
}
function validHttpUrl(value) {
  if (!value?.trim()) return true
  try { return ['http:', 'https:'].includes(new URL(value.trim()).protocol) } catch { return false }
}
function validate() {
  if (blocks.value.length > 60) return '站内详情最多添加60个内容块'
  for (let index = 0; index < blocks.value.length; index += 1) {
    const block = blocks.value[index]
    if (block.type === 'TEXT' && !block.text.trim()) return `第${index + 1}项文字内容不能为空`
    if (block.type === 'IMAGE' && !block.imageUrl && !files.value[block.key]) return `第${index + 1}项请选择图片`
    if (!validHttpUrl(block.linkUrl)) return `第${index + 1}项跳转链接必须是完整的 HTTP(S) 地址`
  }
  return ''
}
function serialize({ excludePendingImages = false } = {}) {
  const content = blocks.value
    .filter(block => !(excludePendingImages && block.type === 'IMAGE' && !block.imageUrl))
    .map(block => block.type === 'TEXT'
      ? { type: 'TEXT', text: block.text.trim(), imageUrl: null, caption: null, linkUrl: block.linkUrl.trim() || null }
      : { type: 'IMAGE', text: null, imageUrl: block.imageUrl || null, caption: block.caption.trim() || null, linkUrl: block.linkUrl.trim() || null })
  return content.length ? JSON.stringify(content) : null
}
async function upload(ownerType, ownerId) {
  let uploaded = false
  for (const block of blocks.value) {
    const file = files.value[block.key]
    if (!file) continue
    const data = new FormData()
    data.append('file', file)
    const response = await api(`/api/storage/upload?ownerType=${ownerType}&ownerId=${ownerId}`, { method: 'POST', body: data })
    if (!response.ok) throw new Error(await message(response, '详情图片上传失败'))
    const asset = await response.json()
    block.imageUrl = `/api/storage/public/${asset.id}`
    delete files.value[block.key]
    clearPreview(block.key)
    uploaded = true
  }
  return uploaded
}

defineExpose({ reset, serialize, upload, validate })
onUnmounted(clearAllPreviews)
</script>

<template>
  <section class="detail-editor" aria-labelledby="detail-editor-title">
    <header>
      <div><h3 id="detail-editor-title">站内详情内容</h3><p>内容按下方顺序展示，文字和图片都可以设置跳转链接。</p></div>
      <div class="detail-add-actions">
        <el-button :icon="Document" @click="add('TEXT')">添加文字</el-button>
        <el-button :icon="Picture" @click="add('IMAGE')">添加图片</el-button>
      </div>
    </header>

    <el-form v-if="blocks.length" class="detail-blocks" label-position="top">
      <article v-for="(block, index) in blocks" :key="block.key" class="detail-block">
        <div class="detail-block-toolbar">
          <strong>{{ index + 1 }}. {{ block.type === 'TEXT' ? '文字段落' : '详情图片' }}</strong>
          <div>
            <el-tooltip content="上移" placement="top"><el-button :icon="ArrowUp" circle :disabled="index === 0" :aria-label="`上移第${index + 1}项`" @click="move(index, -1)" /></el-tooltip>
            <el-tooltip content="下移" placement="top"><el-button :icon="ArrowDown" circle :disabled="index === blocks.length - 1" :aria-label="`下移第${index + 1}项`" @click="move(index, 1)" /></el-tooltip>
            <el-tooltip content="删除" placement="top"><el-button :icon="Delete" circle type="danger" plain :aria-label="`删除第${index + 1}项`" @click="remove(index)" /></el-tooltip>
          </div>
        </div>

        <template v-if="block.type === 'TEXT'">
          <el-form-item label="文字内容" required><el-input v-model="block.text" type="textarea" :rows="4" maxlength="5000" show-word-limit placeholder="输入站内详情页展示的文字说明" /></el-form-item>
          <el-form-item label="文字跳转链接"><el-input v-model="block.linkUrl" maxlength="500" placeholder="选填，填写后点击这段文字会打开链接" /></el-form-item>
        </template>
        <template v-else>
          <div class="detail-image-row">
            <img v-if="previews[block.key] || block.imageUrl" :src="previews[block.key] || block.imageUrl" :alt="block.caption || '详情图片预览'">
            <label class="detail-image-picker">
              <input type="file" accept="image/jpeg,image/png,image/gif" @change="event => chooseImage(block, event)">
              <el-icon><UploadFilled /></el-icon><span>{{ files[block.key]?.name || (block.imageUrl ? '更换图片' : '选择图片，最大 8MB') }}</span>
            </label>
          </div>
          <div class="detail-image-fields">
            <el-form-item label="图片说明"><el-input v-model="block.caption" maxlength="300" placeholder="选填，显示在图片下方" /></el-form-item>
            <el-form-item label="图片跳转链接"><el-input v-model="block.linkUrl" maxlength="500" placeholder="选填，填写后点击图片会打开链接" /></el-form-item>
          </div>
        </template>
      </article>
    </el-form>
    <div v-else class="detail-editor-empty"><el-icon><Plus /></el-icon><span>尚未添加站内详情内容</span></div>
  </section>
</template>

<style scoped>
.detail-editor{margin-top:8px;padding-top:22px;border-top:1px solid #ced5cf}.detail-editor>header{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;margin-bottom:16px}.detail-editor h3{margin:0 0 5px;font-size:18px}.detail-editor header p{margin:0;color:#60746a;font-size:13px;line-height:1.55}.detail-add-actions{display:flex;flex:none;gap:8px}.detail-add-actions .el-button,.detail-block-toolbar .el-button{margin:0}.detail-blocks{display:grid;gap:14px}.detail-block{padding:16px;background:#f7f8f5;border:1px solid #ced5cf;border-radius:6px}.detail-block-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:14px}.detail-block-toolbar strong{font-size:14px}.detail-block-toolbar>div{display:flex;gap:6px}.detail-block-toolbar .el-button{width:32px;height:32px}.detail-image-row{display:grid;grid-template-columns:170px minmax(0,1fr);gap:14px;align-items:center;margin-bottom:14px}.detail-image-row img{display:block;width:170px;aspect-ratio:16/10;object-fit:cover;border:1px solid #ced5cf}.detail-image-picker{position:relative;display:flex;align-items:center;justify-content:center;gap:8px;min-height:88px;padding:14px;border:1px dashed #9eaaa2;color:#344b43;cursor:pointer;text-align:center}.detail-image-picker:hover{border-color:#c5532d;color:#a94020}.detail-image-picker input{position:absolute;width:1px;height:1px;overflow:hidden;opacity:0}.detail-image-picker span{overflow-wrap:anywhere}.detail-image-fields{display:grid;grid-template-columns:1fr 1fr;gap:0 14px}.detail-editor-empty{display:flex;align-items:center;justify-content:center;gap:8px;min-height:84px;border:1px dashed #aeb9b2;color:#60746a;font-size:13px}@media(max-width:620px){.detail-editor>header{display:grid}.detail-add-actions{display:grid;grid-template-columns:1fr 1fr}.detail-image-row,.detail-image-fields{grid-template-columns:1fr}.detail-image-row img{width:100%}.detail-block{padding:14px}}
</style>
