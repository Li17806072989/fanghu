<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template><section class="placeholder"><p class="eyebrow">MVP 页面骨架</p><h1>{{ route.meta.title || '平台模块' }}</h1><p>页面路由和信息架构已建立，下一步接入 RuoYi-Vue-Plus 权限、API 与 MySQL 数据。</p></section></template>
