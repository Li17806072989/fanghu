<script setup>
import { reactive, ref } from 'vue'
import { api } from '../api'

const form = reactive({ name: '', creditCode: '', intro: '' })
const submitting = ref(false)
const result = ref('')
const error = ref('')
async function submit() {
  submitting.value = true; error.value = ''; result.value = ''
  try {
    const response = await api('/api/suppliers/apply', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form) })
    if (!response.ok) throw new Error('提交失败，请核对信息后再试')
    result.value = '申请已提交，平台将对企业资料进行核验。'
    form.name = ''; form.creditCode = ''; form.intro = ''
  } catch (e) { error.value = e.message } finally { submitting.value = false }
}
</script>

<template>
  <section class="form-page"><header class="page-heading"><div><h1>供应商入驻</h1><p>提交企业基础资料，审核通过后可进入方案上传工作台。</p></div></header>
    <form class="entry-form" @submit.prevent="submit"><label>企业名称<input v-model.trim="form.name" required maxlength="200" autocomplete="organization"></label><label>统一社会信用代码<input v-model.trim="form.creditCode" maxlength="40"></label><label>企业与能力简介<textarea v-model.trim="form.intro" required maxlength="2000" rows="7"></textarea></label><p class="form-note">资料用于入驻核验；解决方案附件会在上传后进行安全扫描并按授权控制下载。</p><p v-if="result" class="form-result">{{ result }}</p><p v-if="error" class="form-error">{{ error }}</p><button class="primary-button" :disabled="submitting">{{ submitting ? '正在提交' : '提交入驻申请' }}</button></form>
  </section>
</template>
