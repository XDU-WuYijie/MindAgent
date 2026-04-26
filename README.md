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

## 环境变量说明

- 所有大模型 `api-key` 已移除明文默认值
- 默认策略：
  - 主聊天模型默认走 Qwen OpenAI 兼容云端接口（`https://dashscope.aliyuncs.com/compatible-mode` + `/v1/chat/completions`）
  - Spring AI 侧模型默认也走 Qwen 云端模型，便于先跑通聊天与后续向量能力
- 启动前请按实际使用场景设置：
  - `QWEN_API_KEY`
  - 或分别设置 `SPRING_AI_OPENAI_API_KEY`、`VLLM_API_KEY`

## 文档入口

- [AGENTS.md](/Users/xiaotouming/JavaProjects/MindAgent/AGENTS.md)
- [项目进展.md](/Users/xiaotouming/JavaProjects/MindAgent/项目进展.md)
- [项目功能设计.md](/Users/xiaotouming/JavaProjects/MindAgent/项目功能设计.md)
- [docs/README.md](/Users/xiaotouming/JavaProjects/MindAgent/docs/README.md)
