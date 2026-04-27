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
  - `user / user123`

## 前端页面入口

- `frontend/login.html`：统一登录入口，登录后按角色自动跳转
- `frontend/admin.html`：管理员知识库管理页，未登录或非管理员会被拦截
- `frontend/index.html`：用户业务展示页，未登录会跳转登录页
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
  - 主聊天模型默认走 Qwen OpenAI 兼容云端接口（`https://dashscope.aliyuncs.com/compatible-mode` + `/v1/chat/completions`）
  - Spring AI 聊天模型默认也走 Qwen 云端模型，向量模型默认改为 `text-embedding-3-small`
- 启动前请按实际使用场景设置：
  - `QWEN_API_KEY`
  - 或分别设置 `SPRING_AI_OPENAI_API_KEY`、`SPRING_AI_OPENAI_EMBEDDING_MODEL`、`VLLM_API_KEY`

## 文档入口

- [AGENTS.md](/Users/xiaotouming/JavaProjects/MindAgent/AGENTS.md)
- [项目进展.md](/Users/xiaotouming/JavaProjects/MindAgent/项目进展.md)
- [项目功能设计.md](/Users/xiaotouming/JavaProjects/MindAgent/项目功能设计.md)
- [docs/README.md](/Users/xiaotouming/JavaProjects/MindAgent/docs/README.md)
