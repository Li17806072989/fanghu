<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'

const route = useRoute()
const solution = ref(null)
const error = ref('')
const loading = ref(true)
const detailBlocks = computed(() => {
  try {
    const parsed = solution.value?.detailContent ? JSON.parse(solution.value.detailContent) : []
    return Array.isArray(parsed) ? parsed : []
  } catch { return [] }
})
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '时间待补充' }
async function load() {
  loading.value = true
  try {
    const response = await api(`/api/solutions/${route.params.id}`)
    const body = await response.json()
    if (!response.ok) throw new Error(body.message || '解决方案不存在或尚未发布')
    solution.value = body
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section class="solution-detail" v-loading="loading">
    <RouterLink class="back-link" to="/solutions">返回解决方案库</RouterLink>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <template v-if="solution">
      <header class="solution-detail-heading"><div class="solution-detail-title"><span class="eyebrow">解决方案</span><h1>{{ solution.title }}</h1><p>{{ solution.summary || '暂无摘要' }}</p></div><div class="solution-detail-cover" v-if="solution.imageUrl"><img :src="solution.imageUrl" :alt="`${solution.title}解决方案封面`"></div><el-tag type="success" effect="plain">已发布</el-tag></header>
      <dl class="solution-meta"><div><dt>装备分类</dt><dd>{{ solution.categoryId || '待补充' }}</dd></div><div><dt>应用场景与技术标签</dt><dd>{{ solution.sceneTags || '待补充' }}</dd></div><div><dt>更新时间</dt><dd>{{ formatDate(solution.updatedAt || solution.createdAt) }}</dd></div></dl>
      <article class="solution-content"><h2>方案说明</h2><p>{{ solution.content || '暂无正文内容' }}</p></article>
      <div v-if="detailBlocks.length" class="detail-content-blocks">
        <template v-for="(block, index) in detailBlocks" :key="index">
          <p v-if="block.type === 'TEXT'" class="detail-content-text"><a v-if="block.linkUrl" :href="block.linkUrl" target="_blank" rel="noopener noreferrer">{{ block.text }}</a><span v-else>{{ block.text }}</span></p>
          <figure v-else-if="block.type === 'IMAGE'" class="detail-content-image"><a v-if="block.linkUrl" :href="block.linkUrl" target="_blank" rel="noopener noreferrer"><img :src="block.imageUrl" :alt="block.caption || `${solution.title}详情图片`"></a><img v-else :src="block.imageUrl" :alt="block.caption || `${solution.title}详情图片`"><figcaption v-if="block.caption">{{ block.caption }}</figcaption></figure>
        </template>
      </div>
    </template>
  </section>
</template>

<style scoped>
.solution-detail{max-width:980px;margin:0 auto;padding:40px 24px 72px}.back-link{color:#28627a;text-underline-offset:3px}.solution-detail-heading{display:flex;justify-content:space-between;gap:24px;align-items:flex-start;margin:28px 0 24px;padding-bottom:24px;border-bottom:1px solid #ced5cf}.solution-detail-title{flex:1}.solution-detail-cover{width:260px;flex:none}.solution-detail-cover img{display:block;width:100%;aspect-ratio:16/10;object-fit:cover;border:1px solid #ced5cf}.eyebrow{font-size:13px;color:#28627a}.solution-detail h1{margin:8px 0 12px;font-size:34px;line-height:1.25}.solution-detail-heading p{margin:0;color:#52675e;line-height:1.7}.solution-meta{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin:0 0 28px}.solution-meta div{padding:16px;background:#f7f8f5;border:1px solid #dfe5e1}.solution-meta dt{font-size:12px;color:#60746a;margin-bottom:8px}.solution-meta dd{margin:0;line-height:1.5}.solution-content{border-top:1px solid #ced5cf;padding-top:24px;line-height:1.8;white-space:pre-wrap}.solution-content h2{font-size:20px;margin:0 0 14px}.solution-content p{margin:0}@media(max-width:680px){.solution-detail-heading{display:block}.solution-detail-cover{width:100%;margin-top:18px}.solution-detail-heading .el-tag{margin-top:16px}.solution-meta{grid-template-columns:1fr}.solution-detail h1{font-size:28px}}
.detail-content-blocks{display:grid;gap:24px;margin-top:30px;padding-top:30px;border-top:1px solid #dce2dd}.detail-content-text{line-height:1.8;white-space:pre-wrap}.detail-content-text a{color:#28627a}.detail-content-image{margin:0;max-width:760px}.detail-content-image img{display:block;width:100%;height:auto;max-height:620px;object-fit:contain;background:#eef1ed;border:1px solid #ced5cf}.detail-content-image figcaption{margin-top:8px;color:#60746a;font-size:13px}
</style>
