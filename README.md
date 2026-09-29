# Fanghu Platform

面向客户与供应商的特种装备专题、资讯和解决方案对接平台。

## 项目结构

- `backend`：Spring Boot API 服务，使用 Flyway 管理业务数据库迁移。
- `frontend`：Vue 3 门户，生产构建后由 Nginx 提供静态文件。
- `docker-compose.yml`：生产依赖和前后端服务编排。
- `script`：构建、发布和健康检查脚本。
- `PRODUCTION.md`：生产部署、环境变量和回滚说明。

RuoYi 源码目录用于兼容和迁移参考，不是当前独立服务的启动入口。

## 本地开发

要求 JDK 17+、Node.js 20+ 和 Docker Desktop。

```powershell
cd backend
..\mvnw.cmd spring-boot:run

cd ..\frontend
npm ci
npm run dev
```

开发服务默认地址：前端 `http://localhost:5173`，后端 `http://localhost:8081`。

## 验证

```powershell
cd backend
..\mvnw.cmd test

cd ..\frontend
npm run build
npm run test:e2e
```

生产部署请按 [PRODUCTION.md](PRODUCTION.md) 执行，不要直接使用开发服务器对外提供服务。
