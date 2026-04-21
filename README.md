# MindBridge

MindBridge is a Spring Boot + WebFlux backend for mental-health chat workflows:

- JWT auth (`accessToken + refreshToken`)
- intent routing (`CHAT / CONSULT / RISK`)
- RAG retrieval (local/chroma)
- MCP-like dispatch (excel/email mock integration)
- SSE chat streaming

## Quick Start (Backend)

1. Start backend:

```powershell
cd E:\Conch\MindBridge\backend
mvn spring-boot:run
```

2. Health check:

```powershell
curl http://localhost:8081/api/health
```

## Run With Public LLM API (No Lab Server Needed)

If your lab vLLM server is intranet-only, run backend in API mode:

```powershell
cd E:\Conch\MindBridge
powershell -ExecutionPolicy Bypass -File .\scripts\start-api-mode.ps1 `
  -ApiKey "YOUR_API_KEY" `
  -BaseUrl "https://api.openai.com" `
  -Model "gpt-4o-mini" `
  -ServerPort 8081
```

Notes:

- API mode uses Spring profile `api` and forces `mindbridge.llm.provider=spring-ai`.
- In API mode, backend ignores frontend-requested model name and uses `SPRING_AI_OPENAI_MODEL`.
- You can use any OpenAI-compatible endpoint by changing `-BaseUrl`.

## Web UI via Nginx Reverse Proxy

This repo provides a minimal frontend tester at `frontend/index.html`.

Nginx proxies:

- `http://localhost:8088/` -> frontend
- `http://localhost:8088/api/*` -> backend `http://localhost:8081/api/*`

### Start Nginx (Docker)

```powershell
cd E:\Conch\MindBridge\deploy
docker compose -f .\docker-compose.nginx.yml up -d
```

Open:

- `http://localhost:8088`
- `http://localhost:8088/lab.html` (admin lab page, admin login required)

### Pull Repository As Knowledge Source (Admin)

After admin login, call:

```powershell
curl -X POST "http://localhost:8081/api/kb/pull-repo" `
  -H "Authorization: Bearer <ADMIN_TOKEN>" `
  -H "Content-Type: application/json" `
  -d "{\"repoUrl\":\"https://github.com/spring-projects/spring-ai.git\",\"branch\":\"main\",\"subPath\":\"\"}"
```

Notes:

- Requires local `git` command available in PATH.
- Imports `.md/.txt` files from the repository into `mindbridge.rag.knowledge-dir`.
- Triggers automatic knowledge reload after import.

### Stop Nginx

```powershell
cd E:\Conch\MindBridge\deploy
docker compose -f .\docker-compose.nginx.yml down
```

## Optional: Use MySQL instead of H2

The backend defaults to H2 file DB. To switch to MySQL, set env vars before startup:

```powershell
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/mindbridge?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
$env:DB_DRIVER="com.mysql.cj.jdbc.Driver"
```

Then start backend:

```powershell
cd E:\Conch\MindBridge\backend
mvn spring-boot:run
```
