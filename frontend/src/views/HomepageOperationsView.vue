<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import {
  Delete,
  Edit,
  Plus,
  Refresh,
  UploadFilled,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { api, hasPermission, notifyHomepageContactChange } from "../api";
import "../homepage-operations.css";

const canWrite = computed(() => hasPermission("platform:write"));
const loading = ref(false);
const saving = ref(false);
const error = ref("");
const activeTab = ref("ads");
const dialogVisible = ref(false);
const editingId = ref(null);
const deletingId = ref(null);
const adImageFile = ref(null);
const adImagePreview = ref("");
const data = ref({ ads: [], topics: [], articles: [], contact: null });
const contact = reactive({
  hotline: "",
  email: "",
  address: "",
  serviceHours: "",
});
const adForm = reactive({
  slotCode: "",
  title: "",
  imageUrl: "",
  targetUrl: "",
  startAt: "",
  endAt: "",
  status: "DRAFT",
  sortNo: 0,
});
const statusLabels = { DRAFT: "草稿", ACTIVE: "投放中", OFFLINE: "已下线" };
const statusTypes = { ACTIVE: "success", DRAFT: "warning", OFFLINE: "info" };

function formatDate(value) {
  return value
    ? new Date(value).toLocaleString("zh-CN", { hour12: false })
    : "不限";
}
async function responseMessage(response, fallback) {
  try {
    return (await response.json()).message || fallback;
  } catch {
    return fallback;
  }
}
async function load() {
  if (!canWrite.value) return;
  loading.value = true;
  error.value = "";
  try {
    const response = await api("/api/admin/homepage");
    if (!response.ok)
      throw new Error(await responseMessage(response, "首页运营数据加载失败"));
    data.value = await response.json();
    Object.assign(contact, {
      hotline: "",
      email: "",
      address: "",
      serviceHours: "",
      ...(data.value.contact || {}),
    });
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
function createAd() {
  error.value = "";
  editingId.value = null;
  Object.assign(adForm, {
    slotCode: "",
    title: "",
    imageUrl: "",
    targetUrl: "",
    startAt: "",
    endAt: "",
    status: "DRAFT",
    sortNo: 0,
  });
  resetAdImage();
  dialogVisible.value = true;
}
function editAd(row) {
  error.value = "";
  editingId.value = row.id;
  Object.assign(adForm, {
    slotCode: row.slotCode,
    title: row.title,
    imageUrl: row.imageUrl || "",
    targetUrl: row.targetUrl || "",
    startAt: row.startAt || "",
    endAt: row.endAt || "",
    status: row.status,
    sortNo: row.sortNo || 0,
  });
  resetAdImage(row.imageUrl || "");
  dialogVisible.value = true;
}
function resetAdImage(imageUrl = "") {
  if (adImagePreview.value.startsWith("blob:"))
    URL.revokeObjectURL(adImagePreview.value);
  adImageFile.value = null;
  adImagePreview.value = imageUrl;
}
function chooseAdImage(uploadFile) {
  const file = uploadFile.raw;
  if (!file) return;
  if (!["image/jpeg", "image/png", "image/gif"].includes(file.type)) {
    error.value = "广告图片仅支持 JPG、PNG 或 GIF 格式";
    return;
  }
  if (file.size > 8 * 1024 * 1024) {
    error.value = "广告图片不能超过 8MB";
    return;
  }
  error.value = "";
  resetAdImage();
  adImageFile.value = file;
  adImagePreview.value = URL.createObjectURL(file);
}
async function saveAd() {
  if (!adForm.slotCode.trim() || !adForm.title.trim()) {
    error.value = "请填写广告位编码和标题";
    return;
  }
  if (!adForm.imageUrl && !adImageFile.value) {
    error.value = "请选择广告图片";
    return;
  }
  saving.value = true;
  error.value = "";
  let createdId = null;
  try {
    let adId = editingId.value;
    if (!adId) {
      const createResponse = await api("/api/admin/homepage/ads", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...adForm, imageUrl: null, status: "DRAFT" }),
      });
      if (!createResponse.ok)
        throw new Error(
          await responseMessage(createResponse, "广告草稿创建失败"),
        );
      const created = await createResponse.json();
      adId = created.id;
      createdId = adId;
    }
    if (adImageFile.value) {
      const formData = new FormData();
      formData.append("file", adImageFile.value);
      const uploadResponse = await api(
        `/api/storage/upload?ownerType=AD&ownerId=${adId}`,
        { method: "POST", body: formData },
      );
      if (!uploadResponse.ok)
        throw new Error(
          await responseMessage(uploadResponse, "广告图片上传失败"),
        );
      const asset = await uploadResponse.json();
      adForm.imageUrl = `/api/storage/public/${asset.id}`;
    }
    const response = await api(`/api/admin/homepage/ads/${adId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(adForm),
    });
    if (!response.ok)
      throw new Error(await responseMessage(response, "广告位保存失败"));
    createdId = null;
    dialogVisible.value = false;
    ElMessage.success("广告位已保存");
    await load();
  } catch (e) {
    if (createdId) {
      try {
        await api(`/api/admin/homepage/ads/${createdId}`, { method: "DELETE" });
      } catch {
        // The original upload error is more useful to the operator.
      }
    }
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}
async function deleteAd(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除广告“${row.title}”吗？删除后无法恢复。`,
      "删除广告",
      {
        confirmButtonText: "删除",
        cancelButtonText: "取消",
        type: "warning",
        confirmButtonClass: "el-button--danger",
      },
    );
  } catch {
    return;
  }
  deletingId.value = row.id;
  error.value = "";
  try {
    const response = await api(`/api/admin/homepage/ads/${row.id}`, {
      method: "DELETE",
    });
    if (!response.ok)
      throw new Error(await responseMessage(response, "广告删除失败"));
    ElMessage.success("广告已删除");
    await load();
  } catch (e) {
    error.value = e.message;
  } finally {
    deletingId.value = null;
  }
}
async function toggleTopic(row) {
  const response = await api(
    `/api/admin/homepage/topics/${row.id}/recommended?value=${row.isRecommended}`,
    { method: "PUT" },
  );
  if (!response.ok) {
    row.isRecommended = !row.isRecommended;
    error.value = await responseMessage(response, "推荐状态更新失败");
  } else
    ElMessage.success(row.isRecommended ? "已加入首页推荐" : "已取消首页推荐");
}
async function toggleArticle(row) {
  const response = await api(
    `/api/admin/homepage/articles/${row.id}/hot?value=${row.isHot}`,
    { method: "PUT" },
  );
  if (!response.ok) {
    row.isHot = !row.isHot;
    error.value = await responseMessage(response, "热点状态更新失败");
  } else ElMessage.success(row.isHot ? "已加入热点排行" : "已移出热点排行");
}
async function saveContact() {
  saving.value = true;
  error.value = "";
  try {
    const response = await api("/api/admin/homepage/contact", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(contact),
    });
    if (!response.ok)
      throw new Error(await responseMessage(response, "联系方式保存失败"));
    data.value.contact = await response.json();
    notifyHomepageContactChange(data.value.contact);
    ElMessage.success("平台联系方式已保存，页面底部联系方式已同步更新");
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}
onMounted(load);
onBeforeUnmount(() => resetAdImage());
</script>

<template>
  <section class="homepage-operations" v-loading="loading">
    <header class="operations-heading">
      <div>
        <h1>首页内容运营</h1>
        <p>
          管理广告排期、专题推荐、热点资讯和公开联系方式；首页只展示已发布内容。
        </p>
      </div>
      <div>
        <RouterLink class="el-button" to="/admin/content">内容管理</RouterLink
        ><RouterLink class="el-button" to="/admin">返回审核后台</RouterLink
        ><el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </header>
    <el-alert
      v-if="error"
      class="operations-alert"
      :title="error"
      type="error"
      show-icon
      closable
      @close="error = ''"
    />
    <div v-if="!canWrite" class="operations-denied">
      <h2>没有内容运营权限</h2>
      <p>当前账号缺少 platform:write 权限。</p>
    </div>
    <el-tabs v-else v-model="activeTab" class="operations-tabs">
      <el-tab-pane label="广告位" name="ads">
        <section class="operations-section">
          <div class="operations-section-heading">
            <div>
              <h2>广告位与排期</h2>
              <p>仅“投放中”且处于有效时间范围内的广告会出现在首页。</p>
            </div>
            <el-button type="primary" :icon="Plus" @click="createAd"
              >新增广告位</el-button
            >
          </div>
          <el-table :data="data.ads" empty-text="暂无广告位"
            ><el-table-column
              prop="title"
              label="广告标题"
              min-width="220"
            /><el-table-column
              prop="slotCode"
              label="位置编码"
              min-width="190"
            /><el-table-column label="状态" width="100"
              ><template #default="{ row }"
                ><el-tag :type="statusTypes[row.status]" effect="plain">{{
                  statusLabels[row.status] || row.status
                }}</el-tag></template
              ></el-table-column
            ><el-table-column label="投放时间" min-width="280"
              ><template #default="{ row }"
                >{{ formatDate(row.startAt) }} 至
                {{ formatDate(row.endAt) }}</template
              ></el-table-column
            ><el-table-column
              prop="sortNo"
              label="排序"
              width="80"
            /><el-table-column label="操作" width="150" fixed="right"
              ><template #default="{ row }"
                ><el-button
                  link
                  type="primary"
                  :icon="Edit"
                  @click="editAd(row)"
                  >编辑</el-button
                ><el-button
                  link
                  type="danger"
                  :icon="Delete"
                  :loading="deletingId === row.id"
                  @click="deleteAd(row)"
                  >删除</el-button
                ></template
              ></el-table-column
            ></el-table
          >
        </section>
      </el-tab-pane>
      <el-tab-pane label="推荐与热点" name="features">
        <div class="feature-columns">
          <section class="operations-section">
            <div class="operations-section-heading">
              <div>
                <h2>推荐专题</h2>
                <p>最多取最近发布的 6 条推荐专题。</p>
              </div>
            </div>
            <el-table :data="data.topics" empty-text="暂无已发布专题"
              ><el-table-column
                prop="title"
                label="专题"
                min-width="220" /><el-table-column label="首页推荐" width="110"
                ><template #default="{ row }"
                  ><el-switch
                    v-model="row.isRecommended"
                    @change="toggleTopic(row)" /></template></el-table-column
            ></el-table>
          </section>
          <section class="operations-section">
            <div class="operations-section-heading">
              <div>
                <h2>热点资讯</h2>
                <p>首页按发布时间展示最多 8 条热点资讯。</p>
              </div>
            </div>
            <el-table :data="data.articles" empty-text="暂无已发布资讯"
              ><el-table-column
                prop="title"
                label="资讯"
                min-width="220" /><el-table-column label="热点" width="90"
                ><template #default="{ row }"
                  ><el-switch
                    v-model="row.isHot"
                    @change="toggleArticle(row)" /></template></el-table-column
            ></el-table>
          </section>
        </div>
      </el-tab-pane>
      <el-tab-pane label="联系方式" name="contact">
        <section class="operations-section contact-form">
          <div class="operations-section-heading">
            <div>
              <h2>公开联系方式</h2>
              <p>空字段不会在首页展示，请只填写确认可公开的信息。</p>
            </div>
          </div>
          <el-form label-position="top" @submit.prevent="saveContact"
            ><div class="contact-grid">
              <el-form-item label="联系电话"
                ><el-input
                  v-model="contact.hotline"
                  maxlength="40" /></el-form-item
              ><el-form-item label="联系邮箱"
                ><el-input
                  v-model="contact.email"
                  maxlength="120" /></el-form-item
              ><el-form-item label="联系地址"
                ><el-input
                  v-model="contact.address"
                  maxlength="255" /></el-form-item
              ><el-form-item label="服务时间"
                ><el-input v-model="contact.serviceHours" maxlength="120"
              /></el-form-item>
            </div>
            <el-button type="primary" :loading="saving" @click="saveContact"
              >保存联系方式</el-button
            ></el-form
          >
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑广告位' : '新增广告位'"
      width="min(680px, 94vw)"
      @closed="resetAdImage()"
    >
      <el-alert
        v-if="error"
        class="ad-dialog-alert"
        :title="error"
        type="error"
        show-icon
        :closable="false"
      />
      <el-form label-position="top">
        <div class="ad-form-grid">
          <el-form-item label="广告位编码" required>
            <el-input
              v-model="adForm.slotCode"
              maxlength="60"
              placeholder="例如 HOME_PRIMARY"
            />
          </el-form-item>
          <el-form-item label="广告标题" required>
            <el-input v-model="adForm.title" maxlength="120" />
          </el-form-item>
        </div>
        <el-form-item label="广告图片" required>
          <el-upload
            class="ad-image-upload"
            accept="image/jpeg,image/png,image/gif"
            :auto-upload="false"
            :show-file-list="false"
            :on-change="chooseAdImage"
          >
            <div class="ad-image-picker">
              <img
                v-if="adImagePreview"
                :src="adImagePreview"
                :alt="`${adForm.title || '广告'}图片预览`"
              />
              <div v-else class="ad-image-placeholder">
                <el-icon><UploadFilled /></el-icon>
                <strong>选择广告图片</strong>
              </div>
              <span class="ad-image-action">
                <el-icon><UploadFilled /></el-icon>
                {{ adImagePreview ? "更换图片" : "浏览图片" }}
              </span>
            </div>
          </el-upload>
          <p class="ad-image-help">
            支持 JPG、PNG、GIF，最大 8MB，推荐使用 4:1 横幅图片。
          </p>
        </el-form-item>
        <el-form-item label="链接跳转地址">
          <el-input
            v-model="adForm.targetUrl"
            maxlength="500"
            placeholder="例如 /supplier/apply 或 https://example.com"
          />
        </el-form-item>
        <div class="ad-form-grid">
          <el-form-item label="开始时间"
            ><el-date-picker
              v-model="adForm.startAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="不填则立即开始"
              style="width: 100%" /></el-form-item
          ><el-form-item label="结束时间"
            ><el-date-picker
              v-model="adForm.endAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="不填则长期有效"
              style="width: 100%" /></el-form-item
          ><el-form-item label="状态"
            ><el-select v-model="adForm.status" style="width: 100%"
              ><el-option label="草稿" value="DRAFT" /><el-option
                label="投放中"
                value="ACTIVE" /><el-option
                label="已下线"
                value="OFFLINE" /></el-select></el-form-item
          ><el-form-item label="排序"
            ><el-input-number v-model="adForm.sortNo" :min="0" :max="9999"
          /></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false"
          >取消</el-button
        >
        <el-button type="primary" :loading="saving" @click="saveAd"
          >保存广告位</el-button
        >
      </template>
    </el-dialog>
  </section>
</template>
