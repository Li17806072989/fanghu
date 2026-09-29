# 生产部署

生产唯一业务后端是独立的 `backend` 服务（容器内端口 8081），前端由根目录 Compose 中的 `frontend` Nginx 服务提供（端口 80），并通过同源代理访问 API；数据库迁移由 backend 内置 Flyway 管理。

发布前执行：

```powershell
.\script\release.ps1
```

`release.ps1` 会构建前后端并启动根目录 Compose 中的 `backend`、`frontend` 和基础设施服务。上线后访问服务器 80 端口。

根目录 `pom.xml`、`ruoyi-admin` 及 `script/docker` 中的 RuoYi 编排仅保留作源码参考和兼容用途，不得用于生产发布。

生产环境必须在 `.env` 中显式配置数据库、Redis、MinIO、短信和安全相关变量；不要使用开发默认值。
