# 约个球

面向羽毛球、乒乓球、足球、篮球、网球、排球等球类运动的移动端生活服务项目。项目参考 HMDP 的分层、Redis 缓存、登录拦截、秒杀、关注与 Feed 流模式，结合高德地图、MinIO、RocketMQ 和 Spring AI Alibaba，提供“找场所、买团购、买装备、约球、看社区、问 AI”的完整演示闭环。

> 当前为学习与演示项目：支付为模拟支付；高德场所为真实 POI 搜索，团购、库存、订单、评价和社区内容由平台本地数据维护。

**当前状态（2026-08-03）**：最终演示版已部署到 ECS。后端 50 个自动化测试通过，前端类型检查和生产构建通过，核心公开接口线上可访问。该结论表示“功能演示可交付”，不等同于具备商业生产系统的支付合规、高可用、灾备和容量承诺。上线边界与验收证据见 [最终版评审与验收清单](docs/最终版评审与验收清单.md)。

## 功能概览

### 移动端业务

- **首页与定位**：浏览器高精度定位优先，接入高德周边 POI、逆地理编码；附近结果不足时后端自动扩大检索半径。
- **场所与团购**：真实场所详情、评分与评价；本地场馆商品支持畅打套餐、单场时段、私教体验、库存、加购和模拟支付。
- **装备商城**：按运动与分类筛选，商品详情、购物车数量调整、普通购买、装备订单。
- **秒杀**：场馆商品和装备分别维护秒杀活动，支持秒杀下单与订单查询。
- **同城约球**：附近球友、发起活动、加入活动；活动按运动、时间、人数、水平、场所筛选，并区分自己发起、他人发起、自己加入。
- **球友社区**：发布/删除动态、图片上传、商品或团购关联、点赞、关注、关注流、用户主页。
- **个人中心**：邮箱登录/注册、密码登录、资料编辑、头像上传、订单与购物车入口。
- **AI 助手**：底部导航和全局悬浮入口；支持会话历史、短期上下文、场所/团购/装备/活动推荐、真实业务卡片和“购买团购后发起约球”的二次确认。

### 核心技术能力

- **HMDP 风格认证**：邮件验证码登录/自动注册、密码登录、Redis Token、Token 刷新拦截器、登录拦截器。
- **唯一性与防穿透**：邮箱、用户名等使用布隆过滤器预判，MySQL 唯一索引最终兜底。
- **缓存治理**：缓存空值防穿透、TTL 随机值防雪崩、热点秒杀活动逻辑过期防击穿；高德逆地理编码短缓存。
- **高并发秒杀**：Redis Lua 原子校验与预扣、Redisson 分布式锁、一人一单、数据库唯一索引、RocketMQ 异步落库；MQ 未部署或投递失败时自动降级为同步事务落库。
- **文件存储**：MySQL `file_metadata` 保存元数据，MinIO 保存文件本体；上传校验类型和大小，删除校验所有者。
- **Feed 流**：普通用户采用推模式写入关注者收件箱；大 V 使用拉模式，降低写扩散成本。
- **AI Grounding**：后端工具先查询真实候选数据并结构化排序，模型只负责解释与推荐，避免凭空编造商品或场所。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.5、Spring Validation、Spring Mail、Lombok |
| 数据访问 | MyBatis-Plus、MySQL 8.4 |
| 缓存与并发 | Redis 7、Spring Data Redis、Redisson、Lua |
| 消息队列 | RocketMQ 5 |
| 文件存储 | MinIO |
| AI | Spring AI Alibaba、DashScope / 通义千问 |
| 地图 | 高德 Web 服务 API、JS API 2.0 浏览器定位 |
| 前端 | Vue 3、TypeScript、Vite、lucide-vue-next |
| 部署 | Docker Compose、Nginx、systemd |

## 架构

```text
Vue Mobile Web
  └─ /api
       ├─ Vite Proxy（开发）
       └─ Nginx（生产）
            └─ Spring Boot :8088
                 ├─ MySQL       事实数据、订单、用户、博客、文件元数据
                 ├─ Redis       Token、验证码、缓存、秒杀库存、Feed、限流
                 ├─ RocketMQ    秒杀订单异步消息
                 ├─ MinIO       头像、博客图片、演示素材
                 ├─ 高德 API    场所、逆地理编码
                 └─ DashScope   AI 对话与推荐解释
```

## 目录结构

```text
hm-badminton/
├─ backend/                         # Spring Boot 后端
│  └─ src/main/java/com/hm/badminton/
│     ├─ controller/                # agent、auth、catalog、community、file、place、social、trade
│     ├─ service/                   # Service 接口与 impl 实现
│     ├─ mapper/                    # MyBatis-Plus Mapper
│     ├─ entity/ dto/ vo/           # 实体、请求 DTO、响应 VO
│     ├─ config/ constants/         # 配置与 Redis 等常量
│     ├─ interceptor/ utils/        # 登录拦截、Token 刷新、上下文、工具
│     ├─ mq/                        # RocketMQ 消费者与消息模型
│     └─ task/                      # 博客归档与清理定时任务
├─ frontend/                        # Vue 移动端单页应用
├─ deploy/                          # Windows + WSL 本地中间件编排
│  └─ production/                   # ECS 生产部署包
├─ seed-assets/                     # 演示图片素材
├─ scripts/                         # 启停、轻量数据、接口性能测试脚本
├─ docs/                            # 业务、接口、缓存、AI 说明
├─ start-dev.bat                    # Windows 一键启动开发环境
└─ stop-dev.bat                     # Windows 一键关闭前后端
```

## 本地开发

### 前置条件

- Windows + WSL2 Ubuntu
- WSL 中 Docker 与 Docker Compose
- JDK 21、Maven、Node.js 18+

### 一键启动

在项目根目录的 Windows PowerShell 中运行：

```powershell
.\start-dev.bat
```

该脚本会：

1. 在 WSL Docker 中启动 MySQL、Redis、RocketMQ、MinIO。
2. 后台启动 Spring Boot `8088` 与 Vite `5173`。
3. 在每次启动前清空 `logs/backend.log`、`logs/frontend.log`。

访问地址：

```text
前端：http://localhost:5173
后端：http://localhost:8088
MinIO Console：http://localhost:9001
```

可选参数：

```powershell
.\scripts\start-dev.ps1 -SkipDocker
.\scripts\start-dev.ps1 -SkipBackend
.\scripts\start-dev.ps1 -SkipFrontend
.\scripts\start-dev.ps1 -OpenWindows
```

关闭前端与后端：

```powershell
.\stop-dev.bat
```

关闭 WSL 中间件：

```bash
cd /mnt/d/Program/java/hm/hm-badminton/deploy
docker compose down
```

## 配置

后端主配置：[application.yml](backend/src/main/resources/application.yml)。不要将真实密码或 API Key 写进 Git；开发和生产均优先使用环境变量。

| 环境变量 | 用途 |
| --- | --- |
| `AMAP_KEY` | 高德 Web 服务 API Key |
| `VITE_AMAP_JS_KEY` | 前端构建使用的高德 JS Key；配置域名白名单，不与 Web 服务 Key 共用 |
| `AI_DASHSCOPE_ENABLED` | 是否启用 DashScope，例如 `true` |
| `AI_DASHSCOPE_API_KEY` | DashScope API Key |
| `AI_MODEL` | 模型名，例如 `qwen-plus` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | QQ 邮箱与 SMTP 授权码 |
| `MYSQL_URL` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 数据库连接配置 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 连接配置 |
| `MINIO_ENDPOINT` | Java 上传文件时使用的 MinIO 内部地址；公开图片统一由 Nginx 的 `/objects/` 路径直连 MinIO |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO 访问凭据 |
| `JWT_SECRET` | 生产环境 JWT 密钥 |

PowerShell 示例：

```powershell
$env:AMAP_KEY="你的高德Key"
$env:VITE_AMAP_JS_KEY="你的高德JSKey"
$env:AI_DASHSCOPE_ENABLED="true"
$env:AI_DASHSCOPE_API_KEY="你的DashScopeKey"
$env:AI_MODEL="qwen-plus"
$env:MAIL_USERNAME="你的QQ邮箱"
$env:MAIL_PASSWORD="你的QQ邮箱SMTP授权码"
.\start-dev.bat
```

## 数据与初始化

- 开发环境的 `schema.sql`、`data.sql` 会在启动时重建轻量演示数据，适合调试，不应保存真实业务数据。
- 生产环境使用 [application-prod.yml](backend/src/main/resources/application-prod.yml)，已关闭自动 SQL 初始化，服务重启不会清表。
- 演示图片位于 `seed-assets/generated`，由脚本上传到 MinIO；MySQL 只保存对象元数据和访问地址。
- 本地数据量、字段差异和绑定规则见 [轻量演示数据方案](docs/低优先级/轻量演示数据方案.md)。

## API 概览

开发环境由 Vite 将 `/api/*` 转发到后端并去掉 `/api` 前缀；生产环境由 Nginx 完成同样的转发。下列为前端使用的 API 前缀：

| 模块 | 前缀 | 代表接口 |
| --- | --- | --- |
| 认证与用户 | `/api/auth` | 验证码、登录、当前用户、资料、定位、公开主页 |
| 运动与分类 | `/api/sports`、`/api/categories/{type}` | 运动目录、场所/装备分类 |
| 场所 | `/api/places`、`/api/venues` | 高德附近场所、逆地理编码、场所评价 |
| 商品 | `/api/items/{type}` | 场馆商品/装备列表、详情、场馆库存 |
| 购物与支付 | `/api/cart`、`/api/payments`、`/api/orders/{type}` | 加购、购物车结算、支付、订单 |
| 秒杀 | `/api/seckill/{type}` | 场所/装备秒杀查询与抢购 |
| 社区 | `/api/blogs`、`/api/follows` | 博客、点赞、关注、关注 Feed |
| 约球 | `/api/social` | 球友、资料、活动、加入活动、购买后发起活动 |
| 文件 | `/api/files` | 上传、列表、下载、预签名 URL、删除 |
| AI | `/api/agent` | 状态、会话、消息、聊天、删除会话 |

完整接口、参数和业务说明见 [项目功能与接口总览](docs/项目功能与接口总览.md)。

## 生产部署

生产部署文件统一位于 [deploy/production](deploy/production)：

- `docker-compose.yml`：MySQL、Redis、RocketMQ、MinIO，仅绑定 `127.0.0.1`。
- `.env.example`：Docker 密码和镜像模板。
- `app.env.example`：Spring Boot 生产环境变量模板。
- `nginx/`：静态前端、`/api` 与 `/objects` 反向代理。
- `systemd/`：后端守护服务。
- `initialize-demo-data.sh`：仅首次初始化演示数据库。
- `upload-demo-assets.sh`：仅首次上传 MinIO 演示图片。

2GB ECS 使用 `prod,lite` Profile，默认不启动 RocketMQ；完整异步消息链路见 [生产部署说明](deploy/production/README.md)。

详细步骤见 [生产部署说明](deploy/production/README.md)。部署到中国大陆 ECS 前，请准备域名与 ICP 备案；生产安全组仅开放 `80/443`，SSH `22` 仅允许自己的公网 IP，禁止开放 MySQL、Redis、RocketMQ、MinIO、后端端口。

## 相关文档

- [文档索引](docs/文档索引.md)
- [最终版评审与验收清单](docs/最终版评审与验收清单.md)
- [生产安全与密钥管理](docs/生产安全与密钥管理.md)
- [备份、恢复与监控](docs/备份恢复与监控.md)
- [新手代码阅读指南：从页面到数据库完整追踪](docs/新手代码阅读指南.md)
- [项目启动与关闭](docs/初期环境配置--ai操作/项目启动与关闭.md)
- [后端 HMDP 分层结构与 API 转发](docs/后端HMDP分层结构与API转发.md)
- [数据存储分工：MySQL、Redis、MinIO](docs/数据存储分工-MySQL-Redis-MinIO.md)
- [Redis 防穿透、雪崩、击穿](docs/关键业务/redis防穿透、雪崩、击穿（Get）.md)
- [秒杀业务逻辑与并发控制](<docs/关键业务/秒杀业务逻辑与并发控制(Post).md>)
- [AI 助手当前业务逻辑](docs/约个球AI助手当前业务逻辑.md)
- [AI 助手 Agent 实施规划](docs/约个球AI助手Agent实施规划.md)
- [接口性能测试与优化建议](docs/低优先级/接口性能测试与优化建议.md)

## 安全提醒

- `.env`、`app.env`、邮箱 SMTP 授权码、DashScope Key、JWT 密钥、生产数据库密码不得提交 Git。
- 高德 JS Key 应在高德控制台限制允许的 Web 域名；Web 服务 Key 不应暴露在前端代码中。
- 首次上传前优先使用 GitHub 私有仓库；若密钥曾进入提交历史，应立即轮换，而不是只依赖 `.gitignore`。
- 当前 MinIO Bucket 为公开读，适合头像和博客图片，不适合任何私密附件；商业化前应拆分公开与私有 Bucket。
