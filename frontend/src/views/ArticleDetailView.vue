<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowLeft, Picture, Refresh } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import '../topic-detail.css'

const route = useRoute()
const loading = ref(true)
const error = ref('')
const article = ref(null)
const statusLabels = { DRAFT: '草稿', PENDING: '待审核', APPROVED: '已发布', REJECTED: '已驳回', OFFLINE: '已下线' }
const statusTypes = { APPROVED: 'success', PENDING: 'warning', REJECTED: 'danger', OFFLINE: 'info', DRAFT: 'info' }
const displayDate = computed(() => article.value?.publishedAt || article.value?.createdAt)

function formatDate(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '时间待补充'
}

async function load() {
  loading.value = true
  error.value = ''
  article.value = null
  try {
    const response = await api(`/api/articles/${route.params.id}`)
    const payload = await response.json()
    if (!response.ok) throw new Error(payload.message || '资讯不存在或尚未发布')
    article.value = payload
    document.title = `${payload.title} - 平台`
  } catch (e) {
    error.value = e.message || '资讯加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.id, (next, previous) => { if (next !== previous) load() })
</script>

<template>
  <main class="topic-detail-shell">
    <RouterLink class="topic-back" to="/articles"><el-icon><ArrowLeft /></el-icon>返回资讯中心</RouterLink>

    <div v-if="loading" class="topic-detail-loading"><el-skeleton :rows="8" animated /></div>
    <section v-else-if="error" class="topic-detail-error">
      <h1>资讯暂时无法打开</h1>
      <p>{{ error }}</p>
      <el-button type="primary" :icon="Refresh" @click="load">重新加载</el-button>
    </section>
    <article v-else-if="article" class="topic-document">
      <figure class="topic-hero-media">
        <img v-if="article.imageUrl" :src="article.imageUrl" :alt="`${article.title}相关资讯图片`">
        <div v-else class="topic-hero-empty"><el-icon><Picture /></el-icon><span>资讯图片待补充</span></div>
      </figure>
      <header class="topic-document-header">
        <div class="topic-status"><span>资讯</span><el-tag :type="statusTypes[article.status] || 'info'" effect="plain">{{ statusLabels[article.status] || article.status }}</el-tag></div>
        <h1>{{ article.title }}</h1>
        <p>{{ article.summary || '资讯摘要待补充' }}</p>
        <div v-if="article.categories?.length" class="topic-tags"><el-tag v-for="category in article.categories" :key="category" effect="plain">{{ category }}</el-tag></div>
      </header>
      <section class="topic-overview article-body">
        <h2>资讯正文</h2>
        <p>{{ article.content || '该资讯的正文内容正在整理中。' }}</p>
        <div v-if="article.detailBlocks?.length" class="detail-content-blocks">
          <template v-for="(block, index) in article.detailBlocks" :key="index">
            <p v-if="block.type === 'TEXT'" class="detail-content-text">
              <a v-if="block.linkUrl" :href="block.linkUrl" target="_blank" rel="noopener noreferrer">{{ block.text }}</a>
              <span v-else>{{ block.text }}</span>
            </p>
            <figure v-else-if="block.type === 'IMAGE'" class="detail-content-image">
              <a v-if="block.linkUrl" :href="block.linkUrl" target="_blank" rel="noopener noreferrer" :aria-label="block.caption || `打开第${index + 1}张详情图片的链接`"><img :src="block.imageUrl" :alt="block.caption || `${article.title}详情图片`" loading="lazy"></a>
              <img v-else :src="block.imageUrl" :alt="block.caption || `${article.title}详情图片`" loading="lazy">
              <figcaption v-if="block.caption">{{ block.caption }}</figcaption>
            </figure>
          </template>
        </div>
      </section>
      <dl class="topic-provenance">
        <div><dt>信息来源</dt><dd><a v-if="article.sourceUrl" :href="article.sourceUrl" target="_blank" rel="noopener noreferrer">{{ article.sourceName || '查看原始来源' }}</a><span v-else>{{ article.sourceName || '来源待补充' }}</span></dd></div>
        <div><dt>发布时间</dt><dd>{{ formatDate(displayDate) }}</dd></div>
        <div><dt>最近更新</dt><dd>{{ formatDate(article.updatedAt) }}</dd></div>
      </dl>
    </article>
  </main>
</template>

<style scoped>
.article-body p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.topic-provenance a {
  color: #28627a;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
}

.topic-provenance a:hover {
  color: #c5532d;
}
</style>
