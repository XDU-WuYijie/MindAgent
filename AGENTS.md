# AGENTS

## 文档约定

- 项目进展文档：[项目进展.md](/Users/xiaotouming/JavaProjects/MindAgent/项目进展.md)
- 项目功能设计文档：[项目功能设计.md](/Users/xiaotouming/JavaProjects/MindAgent/项目功能设计.md)

## 维护规则

- 完成功能开发、功能调整、目录重构、运行方式调整后，必须同步更新：
  - [项目进展.md](/Users/xiaotouming/JavaProjects/MindAgent/项目进展.md)
  - [项目功能设计.md](/Users/xiaotouming/JavaProjects/MindAgent/项目功能设计.md)
- 如果数据库结构发生变化，必须同步更新：
  - [init_schema.sql](/Users/xiaotouming/JavaProjects/MindAgent/backend/src/main/resources/db/init_schema.sql)
- 如果端口、Docker 启动方式、目录结构发生变化，必须同步更新：
  - [README.md](/Users/xiaotouming/JavaProjects/MindAgent/README.md)
  - [docs/README.md](/Users/xiaotouming/JavaProjects/MindAgent/docs/README.md)

## 当前项目默认约定

- 本地依赖通过根目录 `docker-compose.yml` 启动
- MySQL、Redis、Chroma 数据目录位于 `docker/` 下
- 后端本地知识库目录位于 `backend/storage/knowledge`
- 仓库导入缓存目录位于 `backend/storage/repo-cache`
- 数据库首次初始化脚本位于 `backend/src/main/resources/db/init_schema.sql`
- `backend/src/main/resources/db/migration/` 用于存放手工维护的增量 SQL
