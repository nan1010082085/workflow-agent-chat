# Workflow Agent Chat

独立的通用 Chat 产品：消费 Schema Platform AI 中已发布的 Agent Workflow，提供会话、Agent 选择、流式运行结果与 HITL 恢复能力。

## 产品边界

- `schema-platform/ai/app`：Workflow Builder、调试、评测、监控，以及服务 Editor/Flow 的领域 Copilot，保持不变。
- 本项目：Workflow Agent 的消费端，不承载 Workflow 编辑器，不复用 `ai/app` 的 Chat 页面和会话状态。
- `schema-platform/server`：现阶段作为 Workflow Runtime，通过 Open API 提供 Agent 执行能力。

## 技术栈

- Backend: Java 17+ (当前基线 Java 17), Spring Boot 3.4, Spring Web, Spring Data JPA, MySQL 8, Flyway
- Frontend: Vue 3, TypeScript, Vite, Pinia, Vue Router
- Runtime integration: REST first; SSE adapter in the chat backend

## 文档

- [PRD](docs/PRD.md)
- [技术架构与 API](docs/ARCHITECTURE.md)
- [UI/UX 设计](docs/UIUX.md)
- [开发计划](docs/DEVELOPMENT_PLAN.md)
- [落地任务清单](docs/TASKS.md)

## 运行（骨架）

```bash
cd backend && ./mvnw spring-boot:run
cd frontend && pnpm install && pnpm dev
```

启动前端后，可通过 `VITE_API_BASE_URL` 配置 Chat Backend 地址。Runtime 地址、API Key、数据库等配置见 `backend/src/main/resources/application.yml`。
