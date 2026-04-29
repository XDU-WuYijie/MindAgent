# MindAgent

MindAgent 是一个基于 Spring Boot + WebFlux 的智能体后端项目，当前包含后端接口、静态前端页面、Docker 本地依赖环境以及项目文档。

## 目录说明

- `backend/`：后端服务代码
- `frontend/`：静态前端页面
- `docker/`：nginx 配置及 MySQL / Redis / Chroma 数据目录
- `docs/`：归档资料与补充文档
- `backend/storage/`：后端本地运行时文件目录（知识库原文、仓库缓存）
- `docker-compose.yml`：项目本地依赖服务编排文件

## 快速启动

1. 启动依赖服务

```bash
cd /Users/xiaotouming/JavaProjects/MindAgent
docker compose -f docker-compose.yml up -d
```

2. 启动后端

```bash
cd /Users/xiaotouming/JavaProjects/MindAgent/backend
mvn spring-boot:run
```

3. 健康检查

```bash
curl http://127.0.0.1:8082/api/health
```

## 默认端口

- `8082`：MindAgent 后端
- `8088`：MindAgent 前端 nginx
- `3308`：MySQL
- `6380`：Redis
- `18001`：Chroma

## 默认本地账号

- MySQL：`root / 123456`
- Redis：密码 `123456`
- 应用默认用户：
  - `admin / admin123`
  - `teacher / teacher123`
  - `user / user123`

## 前端页面入口

- `frontend/login.html`：统一登录入口，登录后按角色自动跳转
- `frontend/admin.html`：管理员知识库管理页，未登录或非管理员会被拦截
- `frontend/index.html`：学生聊天与预约页，未登录会跳转登录页
- `frontend/teacher.html`：老师排班与预约管理页，未登录或非老师会被拦截
- `frontend/lab.html`：管理员联调页，未登录或非管理员会被拦截

## 知识库上传说明

- 文档上传已按知识库空间拆分为两组入口：
  - `上传校园心理健康知识`
  - `上传校园内部知识库`
- 校园心理健康知识空间已合并原来的权威科普资料内容
- 每个空间下再拆分为 `faq/` 和 `kb/` 两个目录，文件会落到对应的知识库目录中
- 目前本地目录约定为：
  - `backend/storage/knowledge/knowlegde_base_1/faq`
  - `backend/storage/knowledge/knowlegde_base_1/kb`
  - `backend/storage/knowledge/knowledge_base_3/faq`
  - `backend/storage/knowledge/knowledge_base_3/kb`
- 上传后会按 `knowledgeBaseKey + 文档名` 进行同名覆盖更新，先删除旧 chunk 再重新生成新的 chunk
- 支持的知识文件格式仍为 `.md` / `.txt`

## 环境变量说明

- 所有大模型 `api-key` 已移除明文默认值
- 默认策略：
  - 当前聊天、预约 Tool Calling、Embedding、Rerank 全部统一走阿里云百炼
  - 聊天与预约 Agent 默认使用 Qwen OpenAI-compatible（DashScope compatible-mode）
  - Embedding 默认模型为 `text-embedding-v4`
  - Rerank 默认模型为 `qwen3-rerank`
  - 已预留普通聊天切换到 Ollama 的配置位，但本次运行仍默认走百炼
- 启动前请按实际使用场景设置：
  - `QWEN_API_KEY`
  - 或分别设置 `SPRING_AI_OPENAI_API_KEY`、`AI_CHAT_PROVIDER`、`AI_CHAT_MODEL`、`AI_AGENT_PROVIDER`、`AI_AGENT_MODEL`

## 预约闭环

- 新角色：
  - `TEACHER`
- 老师端采用“空闲时间驱动排班”：
  - 老师录入 `startTime/endTime` 空闲区间
  - `POST /api/teacher/slots` 会自动按 1 小时切分生成排班明细
  - `startTime/endTime` 必须整点，且总时长必须是整数小时
  - 同一老师同一时间如果已有任意排班记录，则整次创建失败，不做部分成功
  - `BOOKED` 时段不能被覆盖、编辑、删除
- 老师端接口：
  - `GET/PUT /api/teacher/profile/me`
  - `GET/POST/PUT/DELETE /api/teacher/slots`
  - `PATCH /api/teacher/slots/{id}/open`
  - `PATCH /api/teacher/slots/{id}/close`
  - `GET /api/teacher/appointments`
  - `GET /api/teacher/appointments/{id}`
  - `PATCH /api/teacher/appointments/{id}/cancel`
  - `PATCH /api/teacher/appointments/{id}/complete`
- 学生预约接口：
  - `GET /api/appointment/slots/available`
  - `POST /api/appointment/create`
  - `GET /api/appointment/my`
  - `POST /api/appointment/{id}/cancel`
- 学生查看可预约信息：
  - 后端仍返回未来 `AVAILABLE` 时段
  - 前端和 Tool Calling 统一按“老师 -> 空闲时段”分组展示
  - 聊天中查询“哪些老师可以预约 / 现在谁有空 / 今天还能约谁”时，优先调用 `queryAvailableSlots`
- AI 路由：
  - `APPOINTMENT_PROCESS` 继续走现有 RAG，用于回答“怎么预约、状态是什么意思”
  - `APPOINTMENT_ACTION` 走 Spring AI 原生 Tool Calling，用于“帮我预约、查询我的预约、取消我的预约”
- Tool Calling 观测：
  - `/api/chat/stream` 新增 `tool_call_start`、`tool_call_result`、`final_reply` SSE 事件
  - 所有 AI 工具调用落表到 `ai_tool_call_log`

## 文档入口

- [AGENTS.md](/Users/xiaotouming/JavaProjects/MindAgent/AGENTS.md)
- [项目进展.md](/Users/xiaotouming/JavaProjects/MindAgent/项目进展.md)
- [项目功能设计.md](/Users/xiaotouming/JavaProjects/MindAgent/项目功能设计.md)
- [docs/README.md](/Users/xiaotouming/JavaProjects/MindAgent/docs/README.md)
