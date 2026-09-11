# fanghu-platform

面向客户与供应商的特种装备专题、资讯和解决方案对接平台。

## 当前阶段

本仓库是 RuoYi-Vue-Plus 兼容的业务骨架，已包含：

- 核心数据库实体设计：见 `docs/database-core.md`
- MVP 页面与路由清单：见 `docs/mvp-pages.md`
- Spring Boot 后端启动骨架：`backend`
- Vue 3 + Vite 前端门户骨架：`frontend`

由于当前环境无法访问 GitHub，上游 RuoYi-Vue-Plus 源码尚未拉取。接入时建议将上游源码放入同一仓库，再把本文档定义的业务模块迁移到其 `ruoyi-modules` 和 `ruoyi-ui` 目录。

## 本地启动

后端要求 JDK 17+、Maven 3.9+；前端要求 Node.js 20+。

```powershell
cd backend
mvn spring-boot:run

cd ..\frontend
npm install
npm run dev
```

默认 API 地址为 `http://localhost:8081`，前端地址为 `http://localhost:5173`。

## 后续接入 RuoYi-Vue-Plus

1. 拉取 `https://github.com/dromara/RuoYi-Vue-Plus` 对应稳定分支。
2. 保留其认证、权限、日志和代码生成模块。
3. 将 `backend` 中的业务包迁移至 RuoYi 模块结构。
4. 将 `frontend` 页面迁移至 RuoYi 的 Vue3 管理端，同时保留本骨架的门户路由。
5. 根据 `docs/database-core.md` 生成迁移脚本，所有表增加租户/审计字段时遵循上游规范。
