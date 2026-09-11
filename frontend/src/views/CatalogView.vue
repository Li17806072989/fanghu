<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink as RouterLinkComponent, useRoute, useRouter } from 'vue-router'
import { Filter, Picture, Refresh, Search } from '@element-plus/icons-vue'
import { api } from '../api'
import '../catalog.css'

const props = defineProps({ kind: { type: String, required: true } })
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const treeLoading = ref(false)
const error = ref('')
const result = ref({ records: [], total: 0, page: 1, size: 12 })
const page = ref(1)
const filters = reactive({ keyword: '', status: 'APPROVED', dates: [], TOPIC: null, EQUIPMENT: null, SCENE: null, TECH: null })
const trees = reactive({ TOPIC: [], EQUIPMENT: [], SCENE: [], TECH: [] })

const isAdmin = computed(() => {
  try { return JSON.parse(localStorage.getItem('fanghu-roles') || '[]').includes('admin') } catch { return false }
})
const config = computed(() => ({
  all: { title: '平台统一检索', description: '在专题、解决方案和资讯中按分类与发布时间交叉筛选。', type: 'ALL', dimensions: ['TOPIC', 'EQUIPMENT', 'SCENE', 'TECH'] },
  topics: { title: '专题中心', description: '按专题体系和装备方向整理可核验信息。', type: 'TOPIC', dimensions: ['TOPIC', 'EQUIPMENT', 'SCENE', 'TECH'] },
  solutions: { title: '解决方案库', description: '检索已经平台审核发布的供应商解决方案。', type: 'SOLUTION', dimensions: ['EQUIPMENT', 'SCENE', 'TECH'] },
  articles: { title: '行业资讯', description: '按来源、分类和发布时间检索行业信息。', type: 'ARTICLE', dimensions: ['TOPIC', 'EQUIPMENT', 'SCENE', 'TECH'] }
}[props.kind]))
const dimensionLabels = { TOPIC: '专题分类', EQUIPMENT: '装备分类', SCENE: '应用场景', TECH: '技术标签' }
const typeLabels = { TOPIC: '专题', SOLUTION: '解决方案', ARTICLE: '资讯' }
const statusLabels = { DRAFT: '草稿', PENDING: '待审核', APPROVED: '已发布', REJECTED: '已驳回', OFFLINE: '已下线' }
const statusTypes = { APPROVED: 'success', PENDING: 'warning', REJECTED: 'danger', OFFLINE: 'info', DRAFT: 'info' }
const activeFilterCount = computed(() => config.value.dimensions.filter(type => filters[type]).length + (filters.dates?.length ? 1 : 0))

function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }).slice(0, 16) : '时间待补充' }
function detailPath(item) { return item.contentType === 'TOPIC' ? `/topics/${item.id}` : `/articles/${item.id}` }
function opensSource(item) { return Boolean(item.sourceUrl) }
function linkLabel(item) { return opensSource(item) ? `${item.title}（打开来源网站）` : `查看${typeLabels[item.contentType]}：${item.title}` }
function linkProps(item) {
  return opensSource(item)
    ? { href: item.sourceUrl, target: '_blank', rel: 'noopener noreferrer' }
    : { to: detailPath(item) }
}
function queryParams() {
  const params = new URLSearchParams({ contentType: config.value.type, page: String(page.value), size: '12', status: filters.status })
  if (filters.keyword.trim()) params.set('keyword', filters.keyword.trim())
  for (const type of config.value.dimensions) if (filters[type]) params.append('taxonomyIds', filters[type])
  if (filters.dates?.[0]) params.set('publishedFrom', filters.dates[0])
  if (filters.dates?.[1]) params.set('publishedTo', filters.dates[1])
  return params
}
async function loadTrees() {
  treeLoading.value = true
  const responses = await Promise.all(Object.keys(trees).map(async type => {
    const response = await api(`/api/categories/taxonomy/tree?type=${type}`)
    return [type, response.ok ? await response.json() : []]
  }))
  for (const [type, nodes] of responses) trees[type] = nodes
  treeLoading.value = false
}
async function search({ updateUrl = true } = {}) {
  loading.value = true; error.value = ''
  try {
    const response = await api(`/api/search?${queryParams()}`)
    const payload = await response.json()
    if (!response.ok) throw new Error(payload.message || '暂时无法完成检索')
    result.value = payload
    if (updateUrl) {
      const query = filters.keyword.trim() ? { q: filters.keyword.trim() } : {}
      router.replace({ query })
    }
  } catch (e) { error.value = e.message || '检索失败，请稍后重试' } finally { loading.value = false }
}
function submitSearch() { page.value = 1; search() }
function resetFilters() {
  Object.assign(filters, { keyword: '', status: 'APPROVED', dates: [], TOPIC: null, EQUIPMENT: null, SCENE: null, TECH: null })
  page.value = 1; search()
}
function changePage(value) { page.value = value; search({ updateUrl: false }) }
function initializeFromRoute() { filters.keyword = typeof route.query.q === 'string' ? route.query.q.slice(0, 100) : ''; page.value = 1 }

onMounted(async () => { initializeFromRoute(); await Promise.all([loadTrees(), search({ updateUrl: false })]) })
watch(() => props.kind, async () => { initializeFromRoute(); await search({ updateUrl: false }) })
watch(() => route.query.q, async value => {
  const next = typeof value === 'string' ? value.slice(0, 100) : ''
  if (next !== filters.keyword) { filters.keyword = next; page.value = 1; await search({ updateUrl: false }) }
})
</script>

<template>
  <section class="catalog-shell search-catalog">
    <header class="page-heading search-heading">
      <div><h1>{{ config.title }}</h1><p>{{ config.description }}</p></div>
      <span class="result-total">{{ loading ? '正在检索' : `找到 ${result.total} 条记录` }}</span>
    </header>

    <form class="search-command" @submit.prevent="submitSearch">
      <el-input v-model="filters.keyword" :prefix-icon="Search" maxlength="100" clearable placeholder="搜索标题、摘要、正文、来源或标签" aria-label="搜索关键词" />
      <el-button type="primary" :icon="Search" native-type="submit" :loading="loading">检索</el-button>
    </form>

    <div class="search-layout">
      <aside class="filter-panel" v-loading="treeLoading">
        <div class="filter-heading"><div><el-icon><Filter /></el-icon><h2>筛选条件</h2></div><span v-if="activeFilterCount">已选 {{ activeFilterCount }} 项</span></div>
        <label v-for="type in config.dimensions" :key="type" class="filter-field">
          <span>{{ dimensionLabels[type] }}</span>
          <el-tree-select v-model="filters[type]" :data="trees[type]" :props="{ label: 'name', children: 'children' }" node-key="id" value-key="id" check-strictly clearable filterable :render-after-expand="false" placeholder="全部" />
        </label>
        <label class="filter-field">
          <span>内容状态</span>
          <el-select v-model="filters.status" :disabled="!isAdmin">
            <el-option label="已发布" value="APPROVED" />
            <template v-if="isAdmin"><el-option label="待审核" value="PENDING" /><el-option label="草稿" value="DRAFT" /><el-option label="已驳回" value="REJECTED" /><el-option label="已下线" value="OFFLINE" /></template>
          </el-select>
        </label>
        <label class="filter-field">
          <span>发布时间</span>
          <el-date-picker v-model="filters.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" range-separator="至" />
        </label>
        <div class="filter-actions"><el-button type="primary" :loading="loading" @click="submitSearch">应用筛选</el-button><el-button :icon="Refresh" @click="resetFilters">重置</el-button></div>
      </aside>

      <main class="search-results" aria-live="polite">
        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false"><template #default><el-button link type="primary" @click="search()">重新加载</el-button></template></el-alert>
        <div v-else-if="loading" class="search-loading"><el-skeleton v-for="index in 4" :key="index" :rows="3" animated /></div>
        <div v-else-if="result.records.length" class="catalog-list">
          <article v-for="item in result.records" :key="`${item.contentType}-${item.id}`" class="catalog-item" :class="{ 'catalog-item--with-media': ['TOPIC', 'ARTICLE'].includes(item.contentType) }">
            <figure v-if="['TOPIC', 'ARTICLE'].includes(item.contentType)" class="catalog-media">
              <component
                :is="opensSource(item) ? 'a' : RouterLinkComponent"
                v-bind="linkProps(item)"
                :aria-label="linkLabel(item)"
              >
                <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.contentType === 'TOPIC' ? `${item.title}专题封面` : `${item.title}相关资讯图片`" loading="lazy">
                <span v-else class="catalog-media-empty"><el-icon><Picture /></el-icon><span>{{ item.contentType === 'TOPIC' ? '专题封面待补充' : '资讯图片待补充' }}</span></span>
              </component>
            </figure>
            <div class="catalog-main">
              <div class="record-classification"><span>{{ typeLabels[item.contentType] }}</span><el-tag :type="statusTypes[item.status] || 'info'" size="small" effect="plain">{{ statusLabels[item.status] || item.status }}</el-tag></div>
              <h2>
                <component
                  :is="opensSource(item) ? 'a' : RouterLinkComponent"
                  v-if="['TOPIC', 'ARTICLE'].includes(item.contentType)"
                  v-bind="linkProps(item)"
                  :aria-label="linkLabel(item)"
                >{{ item.title }}</component>
                <span v-else>{{ item.title }}</span>
              </h2>
              <p>{{ item.summary || '摘要待补充' }}</p>
              <div v-if="item.categories.length" class="record-tags"><el-tag v-for="category in item.categories" :key="category" size="small" effect="plain">{{ category }}</el-tag></div>
            </div>
            <dl class="record-meta"><div><dt>来源</dt><dd>{{ item.source }}</dd></div><div><dt>{{ item.contentType === 'SOLUTION' ? '更新' : '发布' }}</dt><dd>{{ formatDate(item.publishedAt) }}</dd></div></dl>
          </article>
          <el-pagination v-if="result.total > result.size" class="catalog-pagination" :current-page="page" :page-size="result.size" :total="result.total" layout="total, prev, pager, next" @current-change="changePage" />
        </div>
        <div v-else class="search-empty"><h2>没有符合条件的内容</h2><p>可以减少分类条件、扩大发布时间范围，或更换关键词后重新检索。</p><el-button @click="resetFilters">清除筛选条件</el-button></div>
      </main>
    </div>
  </section>
</template>

<style scoped>
.catalog-main h2 a {
  color: #14231f;
  text-decoration: none;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
  overflow-wrap: anywhere;
}

.catalog-main h2 a:hover {
  color: #28627a;
  text-decoration-line: underline;
}

.catalog-main h2 a:focus-visible {
  outline: 2px solid #c5532d;
  outline-offset: 3px;
}

.catalog-item--with-media {
  grid-template-columns: 220px minmax(0, 1fr) 170px;
  align-items: center;
}

.catalog-media {
  width: 100%;
  margin: 0;
}

.catalog-media > a {
  display: block;
  color: inherit;
}

.catalog-media img,
.catalog-media-empty {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  border: 1px solid #ced5cf;
}

.catalog-media img {
  object-fit: cover;
}

.catalog-media-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #eef1ed;
  color: #60746a;
  font-size: 12px;
}

.catalog-media-empty .el-icon {
  font-size: 24px;
}

.catalog-item--with-media .catalog-main,
.catalog-item--with-media .record-meta {
  align-self: center;
}

@media (max-width: 1000px) {
  .catalog-item--with-media {
    grid-template-columns: 170px minmax(0, 1fr) 140px;
    gap: 20px;
  }
}

@media (max-width: 760px) {
  .catalog-item--with-media {
    grid-template-columns: 140px minmax(0, 1fr);
    gap: 16px;
  }

  .catalog-item--with-media .record-meta {
    grid-column: 2;
  }
}

@media (max-width: 560px) {
  .catalog-item--with-media {
    grid-template-columns: 1fr;
  }

  .catalog-item--with-media .record-meta {
    grid-column: auto;
  }
}
</style>
