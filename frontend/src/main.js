import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import { landingPath, readRoles } from './api'
import './style.css'
import './layout-system.css'

const routes = [
  { path: '/', component: () => import('./views/HomeView.vue') },
  { path: '/search', component: () => import('./views/CatalogView.vue'), props: { kind: 'all' }, meta: { title: '平台检索' } },
  { path: '/topics', component: () => import('./views/CatalogView.vue'), props: { kind: 'topics' }, meta: { title: '专题中心' } },
  { path: '/topics/:id', component: () => import('./views/TopicDetailView.vue'), meta: { title: '专题详情' } },
  { path: '/solutions', component: () => import('./views/CatalogView.vue'), props: { kind: 'solutions' }, meta: { title: '解决方案库' } },
  { path: '/articles', component: () => import('./views/CatalogView.vue'), props: { kind: 'articles' }, meta: { title: '军事资讯' } },
  { path: '/articles/:id', component: () => import('./views/ArticleDetailView.vue'), meta: { title: '资讯详情' } },
  { path: '/supplier/apply', component: () => import('./views/SupplierApplyView.vue'), meta: { title: '供应商入驻', roles: ['supplier', 'admin'] } },
  { path: '/reports', component: () => import('./views/ReportWorkspaceView.vue'), meta: { title: 'AI 汇报工作台', roles: ['customer', 'supplier', 'admin'] } },
  { path: '/demand/new', component: () => import('./views/DemandView.vue'), meta: { title: '提交采购需求', roles: ['customer', 'admin'] } },
  { path: '/login', component: () => import('./views/LoginView.vue'), meta: { title: '登录 / 注册' } },
  { path: '/admin', component: () => import('./views/AdminView.vue'), meta: { title: '运营管理', roles: ['admin'] } },
  { path: '/admin/content', component: () => import('./views/ContentManagementView.vue'), meta: { title: '内容管理', roles: ['admin'] } },
  { path: '/admin/taxonomies', component: () => import('./views/TaxonomyAdminView.vue'), meta: { title: '分类与标签管理', roles: ['admin'] } },
  { path: '/admin/homepage', component: () => import('./views/HomepageOperationsView.vue'), meta: { title: '首页内容运营', roles: ['admin'] } },
  { path: '/admin/supplier-memberships', component: () => import('./views/SupplierMembershipAdminView.vue'), meta: { title: '会员与投放管理', roles: ['admin'] } },
  { path: '/admin/ai-reports', component: () => import('./views/AiReportAdminView.vue'), meta: { title: '汇报全文授权', roles: ['admin'] } },
  { path: '/operations/leads', component: () => import('./views/LeadOperationsView.vue'), meta: { title: '线索运营', roles: ['admin'] } },
  { path: '/workbench', component: () => import('./views/SupplierDashboardView.vue'), meta: { title: '供应商工作台', roles: ['supplier', 'admin'] } },
  { path: '/workbench/solutions', component: () => import('./views/SolutionUploadView.vue'), meta: { title: '方案上传', roles: ['supplier', 'admin'] } },
  { path: '/customer/workbench', component: () => import('./views/CustomerDashboardView.vue'), meta: { title: '客户工作台', roles: ['customer', 'admin'] } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]
const router=createRouter({ history: createWebHistory(), routes })
router.beforeEach((to)=>{const required=to.meta.roles;if(required?.length){if(!localStorage.getItem('fanghu-token'))return '/login';const roles=readRoles();if(!required.some(role=>roles.includes(role)))return landingPath(roles)}})
createApp(App).use(router).use(ElementPlus).mount('#app')
