# 约个球 AI 助手 Agent 实施规划

> 最后更新：2026-07-19
> 本文同时记录当前已经落地的代码、运行链路、部署要求和后续演进，避免把“规划中”误写成“已完成”。
> 当前代码逐步执行逻辑、候选数量、推荐规则和已知不足详见 [约个球 AI 助手当前业务逻辑](约个球AI助手当前业务逻辑.md)。

## 1. 业务目标与边界

AI 助手帮助用户完成四类决策：

1. 根据位置、球类、日期、时间和距离筛选附近真实场所。
2. 查询平台数据库中真实可售的场馆项目和库存时段。
3. 查找时间、水平、人数合适且仍可加入的约球活动。
4. 根据球类、预算和水平推荐真实可购买装备。

模型只负责理解需求、选择候选和解释原因，不直接执行购买、支付、加购或加入活动。所有写操作仍由前端展示确认面板，再调用原有业务接口。

前端入口包括：

- 底部导航“助手”。
- 各主要页面右下角悬浮“AI 助手”。
- 会话历史、新会话、快捷追问、球类多选和横向业务卡片。

## 2. 当前完成度

| 能力           | 状态  | 当前实现                              |
| ------------ | --- | --------------------------------- |
| DashScope 接入 | 已完成 | Spring AI Alibaba + `qwen-plus`   |
| 真实业务工具       | 已完成 | 场所、场馆项目、约球活动、装备工具                 |
| 结构化会话记忆      | 已完成 | 运动、日期、时段、预算、距离、水平、意图              |
| 真实库存时段筛选     | 已完成 | 完全匹配优先，不存在时回退真实单场一小时              |
| 工具并行查询       | 已完成 | StateGraph 并行分支 + 有界线程池           |
| 短事务拆分        | 已完成 | 问题和答案分别短事务持久化，远程调用在事务外            |
| 防模型编造        | 已完成 | 模型按 `cardId` 选卡并给逐卡理由，后端验证后生成事实回答 |
| SSE 进度输出     | 已完成 | 理解需求、查询工具、生成建议、最终结果事件             |
| 会话历史与限流      | 已完成 | MySQL 历史、Redis 结构化短期记忆、用户/IP 限流   |
| 安全动作闭环       | 已完成 | 场所、团购、装备、活动卡片跳转及二次确认              |
| Graph 多节点编排  | 已完成 | StateGraph 编排 9 个节点及 3 个并行工具分支    |
| 演示活动日期维护     | 已完成 | 启动和每日 00:05 将 `DEMO_*` 活动对齐到当天     |
| RAG 语义检索     | 未完成 | 尚未接入博客、评价、心得向量检索                  |

## 3. 技术栈

| 层       | 技术                                                           |
| ------- | ------------------------------------------------------------ |
| 模型      | DashScope `qwen-plus`                                        |
| AI 框架   | Spring AI Alibaba DashScope + `spring-ai-alibaba-graph-core` |
| 编排      | `StateGraph` + 有界 `ThreadPoolTaskExecutor`                   |
| HTTP 流  | Spring MVC `SseEmitter` + 前端 `fetch` ReadableStream          |
| 持久化     | MySQL + MyBatis-Plus                                         |
| 短期记忆/限流 | Redis                                                        |
| 场所来源    | 高德附近 POI                                                     |
| 业务事实    | 本地 `venue_inventory`、活动和装备表                                  |
| 前端      | Vue 3 + TypeScript                                           |
| 生产代理    | Nginx，SSE 路径关闭代理缓冲                                           |

模型配置：

```text
AI_DASHSCOPE_ENABLED=true
AI_DASHSCOPE_API_KEY=<只保存在服务器 app.env>
AI_MODEL=qwen-plus
```

没有开启 `AI_DASHSCOPE_ENABLED` 时接口返回 503。已经开启但模型临时失败时，系统使用经过后端事实校验的本地排序结果兜底。

## 4. 当前完整链路

```text
用户发送问题
  -> POST /api/agent/chat/stream
  -> 登录用户按 userId、游客按 IP 限流
  -> 短事务 A：创建/复用会话，保存用户问题
  -> Redis/MySQL 读取 AgentRequirement
  -> 合并本轮结构化需求
  -> StateGraph dispatchTools
  -> 并行执行 queryPlacesAndProducts、queryActivities、queryEquipment
       场所分支内部先查高德场所，再查对应真实 inventory
  -> 给每张候选卡片生成稳定 cardId
  -> 模型返回 selectedCardIds + recommendationReasons
  -> 后端校验 cardId 必须属于本轮候选
  -> 逐卡理由只绑定已验证 ID；后端根据卡片生成事实回答
  -> 短事务 B：保存答案、卡片和结构化需求
  -> Redis 更新短期结构化记忆
  -> SSE 返回 done，前端渲染答案与业务卡片
```

对应代码：

| 责任             | 文件                                                                             |
| -------------- | ------------------------------------------------------------------------------ |
| HTTP 与 SSE     | `controller/agent/AgentController.java`                                        |
| Graph 定义和执行    | `service/agent/graph/AgentGraphWorkflow.java`                                  |
| Graph 状态与运行上下文 | `service/agent/graph/AgentGraphState.java`、`AgentGraphRunContext.java`         |
| Graph 节点业务实现   | `service/agent/impl/AgentServiceImpl.java`                                     |
| 结构化需求          | `service/agent/impl/AgentRequirementService.java`                              |
| 短事务持久化         | `service/agent/impl/AgentPersistenceService.java`                              |
| 并发线程池          | `config/AgentAsyncConfig.java`                                                 |
| 场所工具           | `service/agent/tools/PlaceAgentTool.java`                                      |
| 场馆项目工具         | `service/agent/tools/VenueProductAgentTool.java`                               |
| 活动工具           | `service/agent/tools/ActivityAgentTool.java`                                   |
| 装备工具           | `service/agent/tools/EquipmentAgentTool.java`                                  |
| 真实时段查询         | `mapper/trade/VenueItemMapper.java`、`service/trade/impl/VenueItemService.java` |
| 前端 SSE 解析      | `frontend/src/api/client.ts`                                                   |
| 前端聊天交互         | `frontend/src/App.vue`                                                         |

## 5. 结构化会话记忆

旧实现把最近消息用分号拼起来，容易丢失约束，也会让模型误解。本轮已改为 `AgentRequirement`：

```text
sportCodes           运动类型，可多选
targetDate           目标日期
startTime/endTime    目标开始和结束时间
maxBudget            最高预算
maxDistanceMeters    最远距离
level                不限/初级/中级/高级
equipmentKeyword     鞋/球拍/手胶/护具/球包/球袜等归一化类别
intents              PLACE/ACTIVITY/EQUIPMENT
lastSelectedCardIds  上轮最终采用的候选
```

合并规则：

- 本轮明确提出的条件覆盖旧条件。
- 本轮没提到的条件沿用当前会话记忆。
- “不限球类/不限预算/不限距离/不限水平”会主动清空对应限制。
- “今晚、明天、7月16日、19:00-21:00、50 元以内、3km、新手”等表达由后端解析。
- “鞋子/球鞋/训练鞋”等口语会归一为可命中数据库的装备关键词，并跨轮保留装备意图和品类。
- 缺少预算时先发澄清问题；装备无结果时只调整预算或品类，不把“扩大范围”误解释为场馆团购。
- 过期日期会在读取时清理，避免第二天继续推荐昨天时段。

存储位置：

```text
MySQL agent_conversation.requirements_json
Redis agent:conversation:requirement:{conversationId}
```

Redis 用于短期快速读取，MySQL 用于缓存失效后的恢复。数据库升级脚本为：

```text
backend/src/main/resources/db/migration-20260716-agent-structured-memory.sql
```

## 6. 场馆项目与时段规则

场所来自高德，团购和库存来自平台数据库，二者不能混为同一数据源。

用户给出日期和开始时间时：

1. 查询 `venue_inventory` 中日期、开始时间、结束时间完全一致且仍有可售量的项目。
2. 同时应用球类、场所顺序和最高预算条件。
3. 完全匹配不存在时，只回退到数据库中真实存在的 `COURT_SLOT` 单场一小时项目。
4. 回退项目必须位于用户要求的时间窗口内。
5. 仍不存在就明确无结果，不允许把上午券描述成晚间可用，也不允许虚构“可协商”。

返回给前端的卡片携带真实 `productId`、`inventoryId`、日期、时段和价格；库存数量不直接暴露给普通页面。

## 7. 防编造设计

每张候选卡片有后端生成的 `cardId`，例如：

```text
place:B0...
venue:12:301
activity:8001
equipment:21
```

模型收到的是经过裁剪的人类可读事实和不透明 `cardId`，要求只返回：

```json
{
  "selectedCardIds": ["venue:12:301"],
  "recommendationReasons": {
    "venue:12:301": "晚间时段完全匹配，价格也在预算内"
  },
  "explanation": "优先考虑时段、距离和预算匹配"
}
```

后端随后执行四层保护：

1. 丢弃不属于本轮候选集合的 ID。
2. 模型结果为空或 JSON 不合法时，使用确定性排序兜底。
3. 逐卡理由只能绑定到已验证 ID，重复或过长理由会被丢弃。
4. 最终名称、价格、距离、日期和时段由后端从已验证卡片渲染，不直接展示模型自由生成的数字。

因此模型不能凭空创造商品，也不能把一个球类的内容链接到另一个球类商品。

## 8. 并行查询与事务边界

场所、活动和装备之间没有数据依赖，由 `StateGraph` 的三个分支使用有界线程池并行执行；场馆项目需要真实场所顺序，因此放在场所分支内串行查询。

```text
                 -> 场所 -> 场馆项目
结构化需求 ------> 活动
                 -> 装备
```

`AgentServiceImpl.chat()` 不再使用大事务：

- `beginTurn()`：短事务，只保存问题。
- 高德、业务工具、DashScope：全部在事务外执行，不占用数据库连接。
- `completeTurn()`：短事务，原子保存答案、卡片和需求 JSON。

每一步记录 `requestId` 和 `elapsedMs`，可在日志中定位是高德、数据库、模型还是持久化较慢。

场所分支还包含一次请求内的有界扩圈：结果少于 2 个时按 `8km、15km、30km、50km` 逐级查询，得到足够场所后立即停止。扩圈只属于 `PLACE` 查询，不会作用于平台全局装备。

可调配置：

```yaml
hm:
  agent:
    tool-timeout-seconds: 5
    tool-threads: 6
```

## 9. SSE 协议与前端行为

接口：

```text
POST /api/agent/chat/stream
Content-Type: application/json
Accept: text/event-stream
```

事件：

| 事件 | 内容 | 前端用途 |
| --- | --- | --- |
| `stage` | `code`、`message` | 更新“正在理解/查询/生成建议” |
| `conversation` | `conversationId` | 尽早绑定当前会话 |
| `cards` | 本轮候选卡片 | 为后续渐进卡片预留 |
| `done` | 完整 `AgentChatResponse` | 渲染最终答案、卡片和快捷追问 |
| `error` | `code`、`message` | 结束思考态并显示错误 |

前端使用 `fetch + response.body.getReader()`，因为浏览器原生 `EventSource` 只能 GET，无法发送聊天 JSON。生产 Nginx 对此路径配置：

```nginx
proxy_buffering off;
proxy_cache off;
proxy_read_timeout 120s;
add_header X-Accel-Buffering no always;
```

当前 SSE 已能逐步展示处理阶段，最终答案仍在模型选择完成后一次渲染。后续若需要逐字输出，需把“流式自然语言生成”和“结构化候选选择”拆成两个模型步骤，不能直接流式解析半截 JSON。

## 10. 数据表与 Redis Key

```text
MySQL
  agent_conversation
    id / user_id / title / requirements_json / status / created_at / updated_at
  agent_message
    conversation_id / role / content / cards_json / tool_name / tool_result_json

Redis
  agent:conversation:requirement:{conversationId}  结构化短期记忆
  agent:rate:user:{userId}                         登录用户限流
  agent:rate:ip:{ip}                               游客限流
```

登录用户可以查看、切换和删除自己的历史会话。游客首页不显示默认聊天记录；游客仍可临时提问，但不能进入历史会话管理。

## 11. 部署与升级

已有数据库必须先执行迁移：

```bash
docker exec -i hm-badminton-mysql mysql \
  -uroot -p"$MYSQL_ROOT_PASSWORD" hm_badminton \
  < backend/src/main/resources/db/migration-20260716-agent-structured-memory.sql
```

重新构建前后端并替换 Nginx 配置后执行：

```bash
nginx -t
systemctl reload nginx
```

若 Nginx 未关闭 SSE 缓冲，接口虽然返回 200，但多个进度事件可能在请求结束时一起出现。

## 12. 已落地：Spring AI Alibaba Graph

当前已经引入 `spring-ai-alibaba-graph-core`，由 `AgentGraphWorkflow` 编译并复用一个 `StateGraph`。实际节点为：

```text
beginTurn
  -> understandRequirement
  -> dispatchTools
  -> [queryPlacesAndProducts | queryActivities | queryEquipment]
  -> mergeCandidates
  -> selectCandidates
  -> persistAnswer
```

三个工具节点在 Graph 中并行汇合。`RunnableConfig.context()` 保存 SSE Consumer、登录用户等单次运行对象；Graph state 保存分支卡片和节点结果。会话长期记忆仍由 MySQL/Redis 管理，因此当前没有注册内存 Checkpointer，避免单例 Graph 持续积累已完成请求状态。

当前 Graph 是安全、确定性的业务工作流，不是模型任意规划工具的自治 Agent。购买、支付、加购和加入活动仍不进入 Graph，必须由前端二次确认。

## 13. 后续阶段：RAG

RAG 只解决主观知识问题：

- 博客训练心得。
- 场馆环境和服务评价。
- 装备使用体验。

建议流程：内容清洗 -> 分段 -> 向量化 -> 带城市/球类/实体 ID 的混合检索 -> 返回来源。

RAG 不能替代实时工具。价格、库存、可售时段、活动人数和订单状态永远从 MySQL/Redis 业务链路查询。

## 14. 当前不足与继续优化

1. 结构化需求目前是规则解析，复杂口语和农历/周期性时间仍需专门需求提取节点。
2. 场所排序已有距离、预算、时段等事实，但还没有统一、可配置、可解释的评分引擎。
3. SSE 已推送阶段，尚未逐 token 推送自然语言。
4. 工具超时后使用空结果降级，需要增加指标监控和告警。
5. 游客会话使用 `userId=0` 持久化但不开放历史管理，后续可增加匿名会话过期清理任务。
6. Graph 已完成确定性工作流编排，但尚未接入动态模型路由、执行恢复和 RAG，不能宣称为完全自治 Agent。

## 15. 演示约球活动时间策略

为保证约球页和 AI 活动推荐每天都有当天数据，轻量演示数据采用以下策略：

1. `data.sql` 原始插入的 24 场 `DEMO_*` 活动统一为当天 `19:00-21:00`。
2. `ActivityMaintenanceTask` 在应用启动和每天北京时间 `00:05` 调用维护服务。
3. `SocialMapper.alignDemoActivitiesToCurrentDay()` 只修改 `place_id` 以 `DEMO_` 开头的记录，并保留活动原有时分秒、人数、水平和费用。
4. 用户真实发起的活动保持原始日期，不参与滚动；结束后按正常业务规则转为“已结束”。
5. AI 活动工具继续校验城市、运动、水平、人数和结束时间，不会因为演示日期刷新而绕过可加入条件。
6. 活动筛选采用日期一致、时段重叠和水平门槛兼容规则；“不限水平”会取消等级限制，而不是作为 SQL 等值条件。
7. 无结果快捷追问必须包含可解析的新条件。目前使用“查看今天19:00可加入的局”和“不限水平查看今天可加入的局”，避免旧的结构化条件被无意继承。

## 16. 安全规则

- DashScope API Key 只存在服务器环境变量，不进入 Git、前端、数据库或日志。
- 模型没有数据库写权限、支付权限和 MinIO 管理权限。
- 模型输出不能直接转成业务命令，只能选择后端提供的候选 ID。
- 所有购买、支付、加购、加入活动继续经过登录、库存/人数校验、事务和幂等控制。
- 日志不得记录密码、验证码、Token 或完整个人隐私信息。
