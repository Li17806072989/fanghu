# 核心数据库实体（MVP）

数据库：MySQL 8.0，字符集 `utf8mb4`。所有业务表建议包含 `id`、`created_at`、`updated_at`、`created_by`、`updated_by`、`deleted` 字段。

## 实体关系

```text
sys_user 1---1 supplier_company
sys_user 1---N customer_profile
supplier_company 1---N solution
topic_category 1---N topic
taxonomy_node 1---N taxonomy_node
taxonomy_node N---N topic/solution/article (content_taxonomy)
topic N---N solution (topic_solution)
article_category 1---N article
customer_profile 1---N demand
demand 1---N lead
supplier_company 1---N lead
solution 1---N file_asset
file_asset 1---N file_download_log
supplier_company 1---N supplier_metric
```

## 业务表

| 表 | 关键字段 | 用途 |
|---|---|---|
| `fh_customer_profile` | `user_id`, `organization`, `contact_name`, `contact_phone`, `verification_status` | 客户身份与联系方式 |
| `fh_supplier_company` | `owner_user_id`, `name`, `credit_code`, `intro`, `verification_status`, `service_status` | 供应商企业档案及入驻审核 |
| `fh_topic_category` | `parent_id`, `name`, `sort_no`, `enabled` | 专题多级分类 |
| `fh_taxonomy_node` | `parent_id`, `taxonomy_type`, `code`, `name`, `sort_no`, `enabled` | 专题、装备、应用场景、技术标签的统一树形分类节点 |
| `fh_content_taxonomy` | `content_type`, `content_id`, `taxonomy_id`, `primary_flag` | 专题、解决方案、资讯与多个分类节点的关联 |
| `fh_topic` | `category_id`, `title`, `summary`, `cover_url`, `source_desc`, `status`, `is_recommended`, `published_at` | 专题条目与首页推荐状态 |
| `fh_solution` | `supplier_id`, `title`, `category_id`, `scene_tags`, `summary`, `content`, `audit_status`, `visibility` | 供应商解决方案 |
| `fh_topic_solution` | `topic_id`, `solution_id`, `sort_no` | 专题与方案关联 |
| `fh_article_category` | `name`, `sort_no`, `enabled` | 资讯分类 |
| `fh_article` | `category_id`, `title`, `summary`, `content`, `source_url`, `source_name`, `status`, `is_hot`, `published_at` | 军事资讯 |
| `fh_demand` | `customer_id`, `title`, `description`, `category_id`, `budget_level`, `status` | 客户需求 |
| `fh_lead` | `demand_id`, `customer_id`, `supplier_id`, `status`, `remark`, `next_action_at` | 对接线索闭环 |
| `fh_file_asset` | `owner_type`, `owner_id`, `object_key`, `sha256`, `visibility`, `scan_status`, `download_count` | 私有文件、版本和安全扫描 |
| `fh_file_download_log` | `file_id`, `user_id`, `user_role`, `ip_address`, `user_agent`, `result`, `expires_at` | 限时下载签发和拒绝访问审计 |
| `fh_ad_slot` | `slot_code`, `title`, `image_url`, `target_url`, `start_at`, `end_at`, `status`, `sort_no` | 首页广告位、投放状态与有效期 |
| `fh_platform_contact` | `hotline`, `email`, `address`, `service_hours` | 首页公开联系方式，空字段不展示 |
| `fh_supplier_metric` | `supplier_id`, `metric_date`, `impressions`, `views`, `inquiries`, `downloads`, `qualified_leads`, `won_leads` | 供应商效果反馈 |
| `fh_ai_job` | `requester_id`, `title`, `source_ids`, `status`, `toc_json`, `access_status`, `generator`, `expires_at` | AI 汇报异步任务、目录与全文授权状态 |
| `fh_ai_report_content` | `job_id`, `content`, `generator` | 与任务摘要隔离保存的汇报全文，仅通过授权接口读取 |

## 状态约定

- 审核：`DRAFT`、`PENDING`、`APPROVED`、`REJECTED`、`OFFLINE`
- 线索：`NEW`、`MATCHED`、`CONTACTED`、`INTENT`、`WON`、`LOST`
- 文件扫描：`PENDING`、`PASSED`、`BLOCKED`
- 文件可见性：`PRIVATE`、`AUTHORIZED`、`PUBLIC_PREVIEW`

## 分类与搜索约定

- 分类类型：`TOPIC`（专题）、`EQUIPMENT`（装备）、`SCENE`（应用场景）、`TECH`（技术标签）。
- `fh_taxonomy_node.parent_id = 0` 表示顶级节点，内容选择父节点时搜索会包含全部下级节点。
- 多个分类维度之间采用交集匹配；同一分类节点包含自身及其全部下级节点。
- 公开检索仅返回 `APPROVED` 内容；管理员可额外筛选草稿、待审、驳回和下线状态。
- 专题、资讯按 `published_at` 筛选，解决方案按 `updated_at` 筛选。

## 首页内容运营约定

- 首页广告仅展示状态为 `ACTIVE` 且当前时间在投放有效期内的记录。
- 推荐专题必须同时满足 `status = APPROVED` 与 `is_recommended = 1`，最多展示最近发布的 6 条。
- 热点资讯必须同时满足 `status = APPROVED` 与 `is_hot = 1`，最多展示最近发布的 8 条。
- 联系方式由运营人员维护；未配置字段不会在公开首页展示。
- 首页不根据测试数据生成或展示成交额、客户数量、对接数量等平台业绩。

## AI 汇报权限约定

- 任务只能引用已审核发布的专题、资讯和解决方案，单次最多选择 30 条资料。
- 普通用户只能查看自己的任务标题、状态和目录，任务列表不查询或返回全文字段。
- 全文保存在独立表中；申请经运营人员授权后，任务创建者可在有效期内读取。
- 具有管理员角色和 `platform:audit` 权限的运营人员可审核全文申请并直接核验全文。
- 配置 `AI_API_ENDPOINT` 与 `AI_API_KEY` 后调用兼容的模型接口；未配置或调用失败时生成明确标注的结构化汇编草稿。
