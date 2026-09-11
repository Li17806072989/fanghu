# 文件安全链路

## 推荐部署方式

安装 Docker Desktop 后，在项目根目录执行：

```powershell
docker compose up -d mysql redis minio minio-init clamav
docker compose ps
```

MinIO API 使用 `http://localhost:9000`，管理控制台使用
`http://localhost:9001`。`minio-init` 会创建 `fanghu-private` 桶并明确关闭匿名访问。
ClamAV 首次启动需要下载病毒库，健康检查变为 `healthy` 后才能上传文件。

## 安全行为

- 上传文件先写入临时目录并计算 SHA-256，再通过 ClamAV `INSTREAM` 扫描。
- ClamAV 未启动、超时、返回异常或发现病毒时，文件不会进入 MinIO。
- MinIO 桶保持私有，应用只签发默认 15 分钟有效的下载 URL。
- 管理员可下载全部文件；供应商只能下载自己企业的文件；客户只有在方案审核通过且供需双方确认对接后才能下载。
- 成功签发和拒绝下载都会写入 `fh_file_download_log`，记录用户、角色、IP、User-Agent 和过期时间。

## 联调检查

正常文本文件应上传成功。EICAR 测试文件应返回“文件包含恶意内容，已拒绝上传”，且 MinIO 中不存在对应对象：

```text
X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*
```

不要在生产环境关闭 `CLAMAV_ENABLED`。扫描关闭时，后端会拒绝保存新文件。
