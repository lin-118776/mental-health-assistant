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

## 本地环境

- Java 17，设置好 `JAVA_HOME`。
- Node.js 22.12 或以上版本、npm。当前 Vite 的版本约束也允许 Node 20.19 系列。
- MySQL 8，MySQL 命令行客户端；数据库和后端时间统一为北京时间。
- Maven 可使用后端自带的 Wrapper，首次执行需要网络下载 Maven 和依赖。

以下命令以 Windows PowerShell 为例，起始目录为项目根目录。

## 1. 初始化新数据库

在项目根目录打开 MySQL 客户端，输入实际的数据库管理员密码：

```powershell
mysql --default-character-set=utf8mb4 -u root -p
```

在 MySQL 客户端中执行：

```sql
SOURCE backend/sql/schema.sql;
SOURCE backend/sql/seed-categories.sql;
USE mental_health_assistant;
SHOW TABLES;
```

`schema.sql` 建立 `mental_health_assistant` 数据库及以下七张表：

| 表 | 用途 |
| --- | --- |
| user | 用户、角色和账号状态 |
| emotion_diary | 用户情绪日志 |
| knowledge_category | 知识分类 |
| knowledge_article | 知识文章 |
| sys_file_info | 上传文件信息 |
| chat_session | 咨询会话及最后一次情绪分析 |
| chat_message | 用户和 AI 消息 |

`seed-categories.sql` 只补充缺失的基础分类，不覆盖已有分类内容，不创建用户或默认密码。文章内容由管理员自行添加。

这两个脚本可以重复执行，但 `CREATE TABLE IF NOT EXISTS` **不会升级或修复已有表结构**。已有数据的环境应先备份并单独核对字段差异，不要将本步骤当作迁移脚本。原有 `backend/sql/chat.sql` 仅包含聊天表，新环境使用完整的 `schema.sql` 即可。

### 已有数据库：邮箱唯一索引

新建数据库已包含 `uk_user_email`，注册和个人资料共用同一邮箱唯一性约束，未填写邮箱保存为 `NULL`。旧环境需人工核对，以下命令不会由程序自动执行。

先备份，并检查已有索引、重复的非空邮箱和空字符串：

```sql
SHOW INDEX FROM `user` WHERE Key_name = 'uk_user_email';
SELECT email, COUNT(*) AS account_count FROM `user`
WHERE email IS NOT NULL AND TRIM(email) <> '' GROUP BY email HAVING COUNT(*) > 1;
SELECT id, username, email FROM `user` WHERE email IS NOT NULL AND TRIM(email) = '';
```

如存在重复的非空邮箱，先人工确认账号归属并修正，不能直接合并或删除用户。确认 `email` 列与新建脚本一样允许 `NULL`、且尚无唯一索引后，才执行：

```sql
UPDATE `user` SET email = NULL WHERE email IS NOT NULL AND TRIM(email) = '';
ALTER TABLE `user` ADD UNIQUE KEY `uk_user_email` (`email`);
```

此操作只规范空邮箱并增加唯一约束，不重置密码或变更角色。未完成旧库索引升级时，应用层重复检查不能替代数据库对并发注册的约束。

## 2. 启动后端

数据库账号需要对该库具有查询、插入、更新、删除权限。打开一个 PowerShell 窗口，将下列占位值替换为自己的配置；这些标准 Spring 环境变量会覆盖 `application.yml`：

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/mental_health_assistant?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'
$env:SPRING_DATASOURCE_USERNAME = '你的数据库账号'
$env:SPRING_DATASOURCE_PASSWORD = '你的数据库密码'
$env:SPRING_AI_OPENAI_API_KEY = '你的硅基流动API密钥'
$env:JWT_SECRET = '替换为随机生成且至少32字节的固定密钥'
$env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Duser.timezone=Asia/Shanghai".Trim()
cd backend
.\mvnw.cmd spring-boot:run
```

后端默认端口是 `8080`。环境变量只在当前 PowerShell 及其子进程中生效，换窗口或重启服务时要重新提供配置。JWT 密钥应保持一致；更换密钥会使已有登录凭证失效。请不要把真实密码和密钥写入提交。

Linux/macOS 使用相应的 `export` 语法设置同名变量，再执行 `./mvnw spring-boot:run`。AI 对话和情绪分析需要有效的 API 密钥及网络连接。

## 3. 启动前端

另开一个终端，在项目根目录执行：

```powershell
cd frontend
npm install
npm run dev
```

打开终端显示的地址，默认是 `http://localhost:5173`。现有 Vite 配置把 `/api` 和 `/uploads/` 请求代理到 `http://localhost:8080`，因此本地开发时后端应使用 `8080` 端口。

文件保存在后端启动工作目录的 `uploads/` 中，后端通过 `/uploads/` 提供访问。前台封面和后台预览默认使用同源路径，由 Vite 开发代理或正式部署的反向代理转发，不再拼接固定服务器地址。已有完整 HTTP(S) 图片地址保持原样。正式部署需同时代理 `/api/` 和 `/uploads/`，见下方示例。

文章封面上传仅限管理员。前后端统一允许 JPG/JPEG/PNG，单文件不超过 5 MiB；后端会验证真实图片内容，宽高不超过 8192 像素且总像素不超过 2000 万。有效图片原样保存，不重新压缩。Servlet 总请求上限为 6 MiB，为 multipart 字段和头部留出空间；代理层也需使用相应请求上限。

## 4. 创建第一个管理员

1. 在前端注册账号，需要用户名、有效手机号和密码。首次注册默认是普通用户。
2. 使用数据库管理员账号，在 MySQL 客户端中执行下面的 SQL，将用户名替换为刚注册的实际值：

```sql
USE mental_health_assistant;
UPDATE `user` SET user_type = 2
WHERE username = '替换为刚注册的用户名' AND user_type = 1;
SELECT id, username, user_type, status FROM `user`
WHERE username = '替换为刚注册的用户名';
```

3. 退出后重新登录，管理员会进入后台；普通用户登录后进入前台。
4. 后台可以创建知识文章、查看咨询记录和管理普通用户状态。

这里没有初始管理员账号或通用密码。用户管理页面不提供角色提升功能，首次授权由数据库管理员完成。

## 构建与部署

后端在 `backend` 目录执行：

```powershell
.\mvnw.cmd -DskipTests package
java -Duser.timezone=Asia/Shanghai -jar target/qi-springboot-0.0.1-SNAPSHOT.jar
```

前端在 `frontend` 目录执行 `npm run build`，静态产物位于 `frontend/dist/`。运行 JAR 时仍需提供前文的环境变量，并固定启动工作目录，以便持续使用同一份 `uploads/` 文件。

若使用 Nginx，可参考以下路由配置；域名、证书和实际静态目录需按部署环境设置。下面仅展示 HTTP 路由示例，不会自动部署或修改服务器：

```nginx
server {
    listen 80;
    server_name localhost;
    client_max_body_size 6m;
    root /srv/mental-health-assistant/frontend/dist;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_buffering off;
        proxy_read_timeout 300s;
    }
    location /uploads/ {
        proxy_pass http://127.0.0.1:8080;
    }
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

`/api/` 保留原路径；关闭代理缓冲以支持 SSE 流式回复。`try_files` 用于支持 Vue Router 页面直接访问和刷新。

## 验证

不连接真实数据库的后端专项测试，在 `backend` 目录执行：

```powershell
.\mvnw.cmd '-Dtest=*Tests,!QiSpringbootApplicationTests' test
```

其中数据库初始化测试在 H2 的 MySQL 模式执行建表和分类数据，并检查七个实体的字段映射；测试会移除数据库切换与 MySQL 表选项，因此不能替代真实 MySQL 验证。`QiSpringbootApplicationTests` 是完整应用上下文测试，需要另行准备实际运行配置。

前端权限测试在 `frontend` 目录执行：

```powershell
node --test tests/authGuard.test.js tests/authRequest.test.js tests/knowledgePublication.test.js tests/fileUrl.test.js tests/registration.test.js tests/coverUpload.test.js tests/consultationState.test.js tests/knowledgeSearch.test.js tests/adminDiaryDetail.test.js tests/personalDiaryState.test.js
npm run build
```

启动后应手动核对：普通用户注册登录、日志新增编辑删除与个人趋势、管理员分页和禁用用户、文章发布阅读，以及实际 AI 流式回复和情绪分析。自动测试通过不代表已完成真实 MySQL、浏览器或 AI 平台联调。
