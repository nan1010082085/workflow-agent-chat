# Workflow Agent Chat

面向业务任务的对话工作台：选择助手（或基础模型），用自然语言完成审核、摘要、问答、生成等工作；处理中状态、需要确认时的操作、会话历史都在同一界面完成。

本仓库是**消费端产品**：不负责助手编排、工作流编辑或模型基础设施，只提供会话、目录、消息与执行状态的产品层体验。

## 功能概览

- 模型优先对话，也可切换已发布的任务助手
- 会话列表、本地目录整理、自动标题
- 处理中 / 需要确认 / 失败重试等状态呈现
- 消息 Markdown 与代码块展示
- Docker Compose 一键部署（前端 + 后端 + MySQL）

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router |
| 后端 | Java 17、Spring Boot 3.4、Spring Data JPA、Flyway |
| 数据 | MySQL 8 |
| 部署 | Docker Compose、Nginx 静态托管 |

## 文档

| 文档 | 说明 |
|---|---|
| [PRD](docs/PRD.md) | 产品定义与 MVP 范围 |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | 拓扑、数据模型与 API |
| [UIUX](docs/UIUX.md) | 交互与文案约定 |
| [DEVELOPMENT_PLAN](docs/DEVELOPMENT_PLAN.md) | 开发阶段 |
| [TASKS](docs/TASKS.md) | 落地任务清单 |
| [RUNTIME_ISSUES](docs/RUNTIME_ISSUES.md) | 与外部 Runtime 契约待确认项（内部对接用） |

## 本地开发

```bash
# 后端（需本机 MySQL，库名见 application.yml）
cd backend && ./mvnw spring-boot:run

# 前端
cd frontend && pnpm install && pnpm dev
```

可选环境变量：

- 前端：`VITE_API_BASE_URL`、`VITE_TENANT_ID`、`VITE_USER_ID`
- 后端：见 `backend/src/main/resources/application.yml`（数据库、Runtime 地址、凭证均通过环境变量注入）

无可用 Runtime 时，可设置 `RUNTIME_MOCK_ENABLED=true` 使用内置 Mock，便于联调 UI。

## 部署

```bash
cp .env.example .env
# 至少修改 MYSQL_PASSWORD、MYSQL_ROOT_PASSWORD，并配置 RUNTIME_BASE_URL（及所需凭证）
docker compose up -d --build
```

默认入口：`http://<host>:5301`（前端 Nginx 将 `/api` 反代到后端）。健康检查：`GET /health`。

生产建议：入口 HTTPS、收紧 `CHAT_CORS_ORIGINS`、MySQL 仅走 Compose 内网、密钥只放在服务器 `.env`（勿提交仓库）。

## 安全提示

- **不要把真实 `.env`、密钥、截图或 Playwright 日志提交到仓库。**
- 当前身份依赖请求头 `X-Tenant-Id` / `X-User-Id`（开发态有默认值），**公网部署前必须由网关注入可信身份或接入 JWT**，否则会话数据可被伪造头访问。
- Runtime / 模型网关凭证仅配置在后端，前端不持有。

## 仓库可见性

若文档中的对接细节或部署拓扑不适合对外公开，请将 GitHub 仓库设为 **Private**，或仅公开本 README 与必要的产品说明。
