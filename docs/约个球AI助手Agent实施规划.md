# 约个球 AI 助手 Agent 实施规划

> 最后更新：2026-07-25
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
| 结构化会话记忆      | 已完成 | 城市、运动、日期、时段、预算区间、距离、水平、偏好和意图       |
| 双通道需求理解      | 已完成 | 快捷按钮发送受控 Command；自由文本规则优先、模型按需补全 JSON |
| 真实库存时段筛选     | 已完成 | 完全匹配优先，不存在时回退真实单场一小时              |
| 工具并行查询       | 已完成 | StateGraph 并行分支 + 有界线程池           |
| 短事务拆分        | 已完成 | 问题和答案分别短事务持久化，远程调用在事务外            |
| 防模型编造        | 已完成 | 模型按 `cardId` 选卡并给逐卡理由，后端验证后生成事实回答 |
| SSE 进度输出     | 已完成 | 理解需求、查询工具、生成建议、最终结果事件             |
| 会话历史与限流      | 已完成 | 登录历史落 MySQL；游客历史落 Redis，均可查看、切换和删除 |
| 安全动作闭环       | 已完成 | 场所、团购、装备、活动卡片跳转及二次确认              |
| Graph 多节点编排  | 已完成 | StateGraph 编排 11 个节点及 3 个并行工具分支   |
| 演示活动日期维护     | 已完成 | 启动和每日 00:05 将 `DEMO_*` 活动对齐到今天/明天滚动窗口 |
| 统一推荐评分       | 已完成 | 按可用性、预算、时段、距离、水平、偏好、口碑等生成可解释分数   |
| RAG 混合检索     | 已完成 | 实体约束的博客、场馆评价和装备心得；向量失败时词法降级       |

## 3. 技术栈

| 层       | 技术                                                           |
| ------- | ------------------------------------------------------------ |
| 模型      | DashScope `qwen-plus`                                        |
| AI 框架   | Spring AI Alibaba DashScope + `spring-ai-alibaba-graph-core` |
| 编排      | `StateGraph` + 有界 `ThreadPoolTaskExecutor`                   |
| HTTP 流  | Spring MVC `SseEmitter` + 前端 `fetch` ReadableStream          |
| 持久化     | MySQL + MyBatis-Plus                                         |
| 游客历史/短期记忆/限流 | Redis                                                   |
| RAG     | MySQL 原文 + Redis 向量缓存 + DashScope Embedding（可选）            |
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
  -> 快捷按钮：校验 AgentCommand 后直接合并
     或自由文本：规则识别 -> 复杂/低置信度时模型提取 JSON -> 后端校验合并
  -> StateGraph dispatchTools
  -> 并行执行 queryPlacesAndProducts、queryActivities、queryEquipment
       场所分支内部先查高德场所，再查对应真实 inventory
  -> 给每张候选卡片生成稳定 cardId
  -> enrichKnowledge：按实体检索评价、博客和装备心得
  -> scoreCandidates：统一计算可解释推荐分
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
| 快捷命令协议        | `dto/agent/AgentCommand.java`、`AgentCommandType.java`                          |
| 模型需求提取        | `service/agent/impl/AgentRequirementModelExtractor.java`                       |
| 短事务持久化         | `service/agent/impl/AgentPersistenceService.java`                              |
| 游客匿名身份         | `service/agent/impl/AgentAnonymousSessionResolver.java`                        |
| 统一推荐评分         | `service/agent/impl/AgentRecommendationScorer.java`                            |
| RAG 混合检索        | `service/agent/impl/AgentRagService.java`                                      |
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
city                 目标城市
targetDate           目标日期
startTime/endTime    目标开始和结束时间
durationMinutes      期望时长
minBudget/maxBudget  最低/最高预算
maxDistanceMeters    最远距离
level                不限/初级/中级/高级
equipmentKeyword     鞋/球拍/手胶/护具/球包/球袜等归一化类别
preferenceTags       停车/淋浴/近地铁/灯光好/新手友好等偏好
avoidTags            用户明确排除的条件
sortPreference       BALANCED/PRICE/DISTANCE/RATING/TIME/VALUE
availabilityRequired 是否要求当前可用
refundableRequired   是否要求支持退款
fieldSources         每个字段来自请求、规则、资料或追问
intents              PLACE/ACTIVITY/EQUIPMENT
lastSelectedCardIds  上轮最终采用的候选
```

合并规则：

- 快捷按钮携带 `AgentCommand`，后端按命令枚举选择业务意图，不解析按钮显示文案。
- 自由文本先走快速规则；意图缺失、长复合条件或模糊指代时才调用模型提取 JSON patch。
- 模型提取字段必须经过意图/球类/水平/排序白名单、预算/距离范围和日期时间校验。
- 模型补全优先级低于明确的请求字段和规则，模型失败或低置信度时直接使用规则降级。
- 本轮明确提出的条件覆盖旧条件。
- 本轮没提到的条件沿用当前会话记忆。
- “不限球类/不限预算/不限距离/不限水平”会主动清空对应限制。
- “今晚、明天、7月16日、19:00-21:00、50 元以内、至少 200 元、3km、新手”等表达由后端解析。
- “扩大附近范围”会在上一轮距离上有界扩大，最高 50km。
- 距离优先、价格最低、评分最高、时间优先和性价比会转换为明确排序偏好。
- “鞋子/球鞋/训练鞋”等口语会归一为可命中数据库的装备关键词，并跨轮保留装备意图和品类。
- 缺少预算时先发澄清问题；用户已给预算但严格范围内无结果时，后端自动查询同球类、同品类中价格最接近且有库存的真实商品，并一次性说明原预算、需增加金额和商品现价，不要求用户再次选择预算。
- 自动放宽后仍无同类商品时才建议更换装备品类；装备查询永远不会因“扩大范围”切换成场馆团购。
- 过期日期会在读取时清理，避免第二天继续推荐昨天时段。

存储位置：

```text
登录用户：MySQL agent_conversation.requirements_json
游客：Redis agent:anonymous:conversation:{anonymousId}:{conversationId}
快速读取：Redis agent:conversation:requirement:{conversationId}
```

登录用户可在缓存失效后从 MySQL 恢复；游客结构化需求跟随匿名 Redis 会话 TTL。数据库升级脚本为：

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
    requirement-model-extraction-enabled: true
    requirement-model-min-length: 24
    requirement-model-min-confidence: 0.55
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
  agent:anonymous:conversations:{anonymousId}      游客会话索引（ZSet）
  agent:anonymous:conversation:{anonymousId}:{id}  游客会话元数据
  agent:anonymous:messages:{anonymousId}:{id}      游客消息列表
  agent:rag:embedding:{contentHash}                 RAG 向量缓存
  agent:rate:user:{userId}                         登录用户限流
  agent:rate:ip:{ip}                               游客限流
```

登录用户可以查看、切换和删除 MySQL 中自己的历史会话。游客首次进入不显示伪造欢迎消息；首次提问后，后端通过 `HttpOnly` 的 `hm_agent_guest` Cookie 标识浏览器，将历史放入 Redis。游客同样可以查看、切换和删除自己的历史，默认保留 7 天、最多 30 个会话、每个会话 100 条消息。Cookie 和 Redis key 共同校验归属，知道负数会话 ID 也无法读取其他游客的记录。

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
  -> enrichKnowledge
  -> scoreCandidates
  -> selectCandidates
  -> persistAnswer
```

三个工具节点在 Graph 中并行汇合。`RunnableConfig.context()` 保存 SSE Consumer、登录用户等单次运行对象；Graph state 保存分支卡片和节点结果。会话长期记忆仍由 MySQL/Redis 管理，因此当前没有注册内存 Checkpointer，避免单例 Graph 持续积累已完成请求状态。

当前 Graph 是安全、确定性的业务工作流，不是模型任意规划工具的自治 Agent。购买、支付、加购和加入活动仍不进入 Graph，必须由前端二次确认。

## 13. 已落地：轻量混合 RAG

RAG 只解决主观知识问题：

- 博客训练心得。
- 场馆环境和服务评价。
- 装备使用体验。

当前实现采用适合轻量演示数据和 2GB ECS 的方案，不新增 Milvus/Elasticsearch：

1. MySQL 继续保存博客、场馆评价和装备心得原文。
2. 先按城市、球类和实体 ID 约束候选，避免把上海足球评价绑定到西安羽毛球馆。
3. 装备博客只绑定对应 `equipmentId`；场馆商品博客只绑定对应商品和同一真实场所；评价只绑定当前 `city + sport + placeRank` 对应场所。
4. 可用 DashScope `EmbeddingModel` 计算语义相似度，向量按内容 SHA-256 缓存在 Redis。
5. Embedding 未配置、超时或失败时，自动使用中文关键词/二元词法相似度，不影响主业务返回。
6. 每条证据保留 `sourceType/sourceId/title/excerpt/relevance`，模型只能引用这些可追溯软知识。
7. RAG 结果写入卡片 `ragEvidence/knowledgeHighlights/knowledgeSources/knowledgeScore`，随后进入统一评分节点。

RAG 不能替代实时工具。价格、库存、可售时段、活动人数和订单状态永远从 MySQL/Redis 业务链路查询。
**RAG 提供“软知识”，工具提供“硬事实”。**
eg:用户问：

```
帮我找一个适合新手、环境比较好，而且今晚有空场的羽毛球馆。
```

这个问题包含两类信息。

**RAG 负责：**

```
适合新手吗？
环境怎么样？
用户评价如何？
灯光和地板如何？
```

**实时工具负责：**

```
今晚是否有空场？
当前价格是多少？
还有几个场地？
是否能够预订？
```

## 14. 当前不足与继续优化

1. 双通道需求理解已落地，但农历、周期性计划、缺少坐标的“离公司近”等表达仍需专门时间/地点解析器或澄清节点。
2. 统一评分已经可解释，但权重目前写在 Java 中；下一步可配置化并用点击、购买和加入活动反馈离线评估。
3. 当前 RAG 按完整博客/评价检索，数据扩大后应增加内容清洗、分段、增量索引和召回指标。
4. SSE 已推送处理阶段，尚未逐 token 推送自然语言。
5. 工具超时后使用空结果降级，尚需 Micrometer 指标、熔断和告警来统计超时率、空结果率、模型失败率和降级率。
6. 游客历史依赖浏览器 Cookie；清除 Cookie、更换浏览器或 Redis TTL 到期后无法恢复，也不会自动合并到登录账号。
7. Graph 是确定性业务工作流，模型提取器只补全结构化需求，尚未接入动态模型路由和执行恢复，不能宣称为完全自治 Agent。

## 15. 演示约球活动时间策略

为保证约球页和 AI 活动推荐既有近期数据、又不会推荐已经开始的场次，轻量演示数据采用以下策略：

1. `data.sql` 原始插入 48 场 `DEMO_*` 活动：4 城市 × 6 球类 × 今天/明天，统一为 `19:00-21:00`。
2. `ActivityMaintenanceTask` 在应用启动和每天北京时间 `00:05` 调用维护服务。
3. `SocialMapper.alignDemoActivitiesToRollingWindow()` 将 `_D0` 对齐到今天、`_D1` 对齐到明天，并保留活动原有时分秒、人数、水平和费用。
4. 用户真实发起的活动保持原始日期，不参与滚动；结束后按正常业务规则转为“已结束”。
5. 对外可加入列表要求 `start_time > 当前时间`；场次开始后不再推荐。自己发起和自己加入列表仍按 `end_time > 当前时间` 展示进行中活动。
6. 活动筛选采用日期一致、时段重叠和水平门槛兼容规则；“不限水平”会取消等级限制，而不是作为 SQL 等值条件。
7. 无结果快捷追问必须包含可解析的新条件：19:00 前推荐今天，19:00 后自动改为明天，避免旧条件被无意继承。

## 16. 安全规则

- DashScope API Key 只存在服务器环境变量，不进入 Git、前端、数据库或日志。
- 模型没有数据库写权限、支付权限和 MinIO 管理权限。
- 模型输出不能直接转成业务命令，只能选择后端提供的候选 ID。
- 所有购买、支付、加购、加入活动继续经过登录、库存/人数校验、事务和幂等控制。
- 日志不得记录密码、验证码、Token 或完整个人隐私信息。

## 17. 最终版验收状态

- Graph、结构化需求、真实工具卡片、实体约束 RAG、游客/登录会话历史和 SSE 阶段进度已经落地。
- 自动化测试覆盖需求解析、澄清、Graph、RAG、评分、匿名会话、装备查询和订单号等关键单元；2026-08-03 后端共 50 个测试通过。
- 当前仍属于受控业务 Agent：Graph 节点和写操作由后端规则控制，模型不能直接支付、扣库存或写数据库。
- DashScope、高德或 RAG 不可用时允许降级，但尚未形成统一指标与告警；这属于上线前 P1 可观测性任务。
- 商业化前需完成模型费用预算、提示词版本管理、召回评估、敏感内容治理和人工反馈闭环。

项目整体风险和交付口径见 [最终版评审与验收清单](最终版评审与验收清单.md)。
