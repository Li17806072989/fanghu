<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink as RouterLinkComponent, useRouter } from 'vue-router'
import { ArrowRight, OfficeBuilding, Search } from '@element-plus/icons-vue'
import { api, hasRole } from '../api'
import '../home.css'
import '../home-cover.css'

const keyword = ref('')
const loading = ref(true)
const homepage = ref({ ads: [], recommendedTopics: [], hotArticles: [], contact: null })
const router = useRouter()
const isCustomer = hasRole('customer') && !hasRole('admin')
const showSupplierEntry = !isCustomer
const visibleAds = computed(() => isCustomer
  ? homepage.value.ads.filter(ad => ad.targetUrl !== '/supplier/apply')
  : homepage.value.ads)

function search() {
  const q = keyword.value.trim()
  router.push({ path: '/search', query: q ? { q } : {} })
}
function date(value) { return value ? new Date(value).toLocaleDateString('zh-CN') : '时间待补充' }
function isExternalLink(value) { return /^https?:\/\//i.test(value || '') }
function contentLinkProps(sourceUrl, detailPath) {
  return sourceUrl
    ? { href: sourceUrl, target: '_blank', rel: 'noopener noreferrer' }
    : { to: detailPath }
}
async function load() {
  try {
    const response = await api('/api/homepage')
    if (response.ok) homepage.value = await response.json()
  } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section class="hero home-hero">
    <div class="home-hero-inner">
      <RouterLink class="hero-logo" to="/" aria-label="平台首页">
        <strong>平台</strong>
        <span>特种装备信息服务</span>
      </RouterLink>
      <div class="hero-copy">
        <h1>快速找到可核验的装备方案</h1>
        <p class="lead">将专题、资讯、企业能力与项目对接放在同一个可追溯入口。</p>
      </div>
      <form class="search hero-search" role="search" @submit.prevent="search">
        <label class="sr-only" for="home-search">平台统一检索</label>
        <input id="home-search" v-model="keyword" maxlength="100" placeholder="搜索装备、技术或企业">
        <button class="search-action" type="submit"><el-icon><Search /></el-icon><span>开始检索</span></button>
      </form>
    </div>
  </section>

  <main class="home-main" :aria-busy="loading">
    <section v-if="visibleAds.length" class="home-ad-band" aria-label="广告推荐">
      <component
        :is="ad.targetUrl ? 'a' : 'div'"
        v-for="ad in visibleAds"
        :key="ad.id"
        :href="ad.targetUrl || undefined"
        :target="isExternalLink(ad.targetUrl) ? '_blank' : undefined"
        :rel="isExternalLink(ad.targetUrl) ? 'noopener noreferrer' : undefined"
        class="home-ad"
        :class="{ 'has-image': ad.imageUrl, 'is-link': ad.targetUrl }"
        :aria-label="ad.targetUrl ? `${ad.title}，查看广告详情` : ad.title"
      >
        <img v-if="ad.imageUrl" :src="ad.imageUrl" :alt="ad.title" loading="lazy">
        <span v-else class="home-ad-copy"><small>广告</small><strong>{{ ad.title }}</strong><el-icon><ArrowRight /></el-icon></span>
      </component>
    </section>

    <section class="home-editorial">
      <div class="recommended-topics">
      <header class="section-title"><h2>推荐专题</h2><RouterLink to="/topics">查看全部专题 <el-icon><ArrowRight /></el-icon></RouterLink></header>
      <div v-if="homepage.recommendedTopics.length" class="topic-grid">
        <article v-for="topic in homepage.recommendedTopics" :key="topic.id">
          <component
            :is="topic.sourceUrl ? 'a' : RouterLinkComponent"
            v-if="topic.coverUrl"
            class="topic-cover-link"
            v-bind="contentLinkProps(topic.sourceUrl, `/topics/${topic.id}`)"
            :aria-label="topic.sourceUrl ? `${topic.title}（打开来源网站）` : `查看专题：${topic.title}`"
          >
            <img class="topic-cover" :src="topic.coverUrl" :alt="`${topic.title}专题封面`" loading="lazy">
          </component>
          <h3>
            <a
              v-if="topic.sourceUrl"
              :href="topic.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
              :aria-label="`${topic.title}（打开来源网站）`"
            >{{ topic.title }}</a>
            <RouterLink v-else :to="`/topics/${topic.id}`">{{ topic.title }}</RouterLink>
          </h3>
          <p>{{ topic.summary || '专题摘要待补充' }}</p>
          <div><span>{{ topic.sourceDesc || '来源信息待补充' }}</span><time>{{ date(topic.publishedAt) }}</time></div>
        </article>
      </div>
      <div v-else class="home-empty">暂无已发布的推荐专题，运营人员可在后台选择。</div>
      </div>

      <aside class="hot-ranking">
      <header class="section-title"><h2>热点资讯排行</h2><RouterLink to="/articles">更多资讯</RouterLink></header>
      <ol v-if="homepage.hotArticles.length">
        <li v-for="(article, index) in homepage.hotArticles" :key="article.id">
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <div>
            <h3>
              <component
                :is="article.sourceUrl ? 'a' : RouterLinkComponent"
                v-bind="contentLinkProps(article.sourceUrl, `/articles/${article.id}`)"
                :aria-label="article.sourceUrl ? `${article.title}（打开来源网站）` : `查看资讯：${article.title}`"
              >{{ article.title }}</component>
            </h3>
            <p>{{ article.sourceName || '来源待补充' }} · {{ date(article.publishedAt) }}</p>
          </div>
        </li>
      </ol>
      <div v-else class="home-empty compact">暂无已发布的热点资讯。</div>
      </aside>
    </section>
  </main>

  <section v-if="showSupplierEntry" class="home-service-band supplier-only">
    <div class="supplier-entry">
      <el-icon><OfficeBuilding /></el-icon>
      <div><h2>入驻并发布企业解决方案</h2><p>提交企业资料，审核通过后维护方案、私有附件与线索反馈。</p></div>
      <RouterLink class="primary-button" to="/supplier/apply">申请入驻 <el-icon><ArrowRight /></el-icon></RouterLink>
    </div>
  </section>

  <section class="workflow">
    <article><h2>专题与资讯</h2><p>按装备、场景和可信来源组织碎片化信息。</p><div class="workflow-actions"><RouterLink to="/topics">浏览专题</RouterLink><RouterLink to="/articles">阅读资讯</RouterLink></div></article>
    <article><h2>供需对接</h2><p>客户提交需求后，平台可依据分类与能力资料生成匹配线索。</p><div class="workflow-actions"><RouterLink to="/demand/new">提交需求</RouterLink><RouterLink to="/solutions">查看方案</RouterLink></div></article>
    <article><h2>资料安全</h2><p>方案附件采用私有存储、安全扫描和限时授权下载。</p><div v-if="showSupplierEntry" class="workflow-actions"><RouterLink to="/workbench">供应商工作台</RouterLink></div></article>
  </section>
</template>

<style scoped>
.topic-cover-link {
  display: block;
  margin-bottom: 15px;
  overflow: hidden;
}

.topic-cover-link .topic-cover {
  margin-bottom: 0;
  transition: transform 0.24s ease-out, filter 0.24s ease-out;
}

.topic-cover-link:hover .topic-cover {
  transform: scale(1.025);
  filter: brightness(0.92);
}

.topic-cover-link:focus-visible {
  outline: 3px solid #c5532d;
  outline-offset: 3px;
}

.topic-grid h3 a {
  color: #14231f;
  text-decoration: none;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
  overflow-wrap: anywhere;
}

.topic-grid h3 a:hover {
  color: #28627a;
  text-decoration-line: underline;
}

.topic-grid h3 a:focus-visible {
  outline: 2px solid #c5532d;
  outline-offset: 3px;
}

.hot-ranking h3 a {
  text-decoration: none;
}

.hot-ranking h3 a:hover {
  text-decoration-line: underline;
}
</style>
