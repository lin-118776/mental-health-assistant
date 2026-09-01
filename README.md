# 心理健康助手 (Mental Health Assistant)

基于 AI 大模型的心理健康助手平台，前后端分离架构。

## 项目结构

```
mental-health-assistant/
├── frontend/   # 前端：Vue 3 + Element Plus + ECharts
└── backend/    # 后端：Spring Boot 3 + MySQL + 硅基流动大模型 API
```

## 技术栈

- **前端**：Vue 3、Vite、Element Plus、Pinia、Vue Router、ECharts、wangEditor
- **后端**：Spring Boot、Spring AI（OpenAI 兼容协议）、MySQL、JWT

## 快速开始

### 后端

1. 创建 MySQL 数据库，执行 `backend/sql/` 下的建库脚本
2. 修改 `backend/src/main/resources/application.yml` 中的数据库连接和 API Key（推荐通过环境变量 `SILICONFLOW_API_KEY` 注入）
3. 启动：

```bash
cd backend
./mvnw spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173` 即可。
