<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Picture, Refresh } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import '../topic-detail.css'

const route = useRoute()
const loading = ref(true)
const error = ref('')
const topic = ref(null)
const statusLabels = { DRAFT: '草稿', PENDING: '待审核', APPROVED: '已发布', REJECTED: '已驳回', OFFLINE: '已下线' }
const statusTypes = { APPROVED: 'success', PENDING: 'warning', REJECTED: 'danger', OFFLINE: 'info', DRAFT: 'info' }
const displayDate = computed(() => topic.value?.publishedAt || topic.value?.createdAt)

function formatDate(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '时间待补充'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api(`/api/topics/${route.params.id}`)
    const payload = await response.json()
    if (!response.ok) throw new Error(payload.message || '专题不存在或尚未发布')
    topic.value = payload
    document.title = `${payload.title} - 平台`
  } catch (e) {
    error.value = e.message || '专题加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="topic-detail-shell">
    <RouterLink class="topic-back" to="/topics"><el-icon><ArrowLeft /></el-icon>返回专题中心</RouterLink>

    <div v-if="loading" class="topic-detail-loading"><el-skeleton :rows="8" animated /></div>
    <section v-else-if="error" class="topic-detail-error">
      <h1>专题暂时无法打开</h1>
      <p>{{ error }}</p>
      <el-button type="primary" :icon="Refresh" @click="load">重新加载</el-button>
    </section>
    <article v-else-if="topic" class="topic-document">
      <figure class="topic-hero-media">
        <img v-if="topic.coverUrl" :src="topic.coverUrl" :alt="`${topic.title}专题封面`">
        <div v-else class="topic-hero-empty"><el-icon><Picture /></el-icon><span>专题封面待补充</span></div>
      </figure>
      <header class="topic-document-header">
        <div class="topic-status"><span>专题</span><el-tag :type="statusTypes[topic.status] || 'info'" effect="plain">{{ statusLabels[topic.status] || topic.status }}</el-tag></div>
        <h1>{{ topic.title }}</h1>
        <p>{{ topic.summary || '专题摘要待补充' }}</p>
        <div v-if="topic.categories?.length" class="topic-tags"><el-tag v-for="category in topic.categories" :key="category" effect="plain">{{ category }}</el-tag></div>
      </header>
      <section class="topic-overview">
        <h2>专题概览</h2>
        <p>{{ topic.summary || '该专题的详细内容正在整理中。' }}</p>
        <div v-if="topic.detailBlocks?.length" class="detail-content-blocks">
          <template v-for="(block, index) in topic.detailBlocks" :key="index">
            <p v-if="block.type === 'TEXT'" class="detail-content-text">
              <a v-if="block.linkUrl" :href="block.linkUrl" target="_blank" rel="noopener noreferrer">{{ block.text }}</a>
              <span v-else>{{ block.text }}</span>
            </p>
            <figure v-else-if="block.type === 'IMAGE'" class="detail-content-image">
              <a v-if="block.linkUrl" :href="block.linkUrl" target="_blank" rel="noopener noreferrer" :aria-label="block.caption || `打开第${index + 1}张详情图片的链接`"><img :src="block.imageUrl" :alt="block.caption || `${topic.title}详情图片`" loading="lazy"></a>
              <img v-else :src="block.imageUrl" :alt="block.caption || `${topic.title}详情图片`" loading="lazy">
              <figcaption v-if="block.caption">{{ block.caption }}</figcaption>
            </figure>
          </template>
        </div>
      </section>
      <dl class="topic-provenance">
        <div><dt>信息来源</dt><dd><a v-if="topic.sourceUrl" :href="topic.sourceUrl" target="_blank" rel="noopener noreferrer">{{ topic.sourceDesc || '查看原始来源' }}</a><span v-else>{{ topic.sourceDesc || '来源待补充' }}</span></dd></div>
        <div><dt>发布时间</dt><dd>{{ formatDate(displayDate) }}</dd></div>
        <div><dt>最近更新</dt><dd>{{ formatDate(topic.updatedAt) }}</dd></div>
      </dl>
    </article>
  </main>
</template>

<style scoped>
.topic-provenance a {
  color: #28627a;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
}

.topic-provenance a:hover {
  color: #c5532d;
}
</style>
