# Agent 高级知识学习指南：HMDP、Graph、RAG、SSE 与 GSSC

本文是 [10-AI 助手 Agent、Graph、SSE 与 RAG](10-AI助手Agent-Graph-SSE与RAG.md) 的完整学习版。第 10 篇用于快速了解功能，本篇用于从源码理解项目为什么这样设计、一次请求如何执行、各模块如何协作，以及面试时如何准确表达。

本文只描述当前项目真实存在的能力。项目没有实现的向量数据库、MCP、多 Agent、强化学习、逐 token 输出等能力，会明确标注为“未实现”或“后续方向”。

## 1. 初学者阅读路线

建议按以下顺序阅读：

1. 先读第 2～4 节，理解 HMDP 基础、Agent 定位和完整请求链路。
2. 再读第 5～7 节，理解记忆、上下文工程和 GSSC。
3. 阅读第 8～10 节，理解 Graph、业务工具、防幻觉和 RAG。
4. 阅读第 11 节，沿着 Vue、Nginx、Spring MVC 理解 SSE。
5. 最后阅读第 12～15 节，理解稳定性、能力边界、面试问答和练习题。

推荐一边阅读本文，一边在 IDE 中按照链接打开源码。不要先背术语，先追踪一条真实请求的数据如何变化。

## 2. 术语表

| 术语 | 在本项目中的含义 |
| --- | --- |
| Agent | 能理解需求、维护状态、调用业务工具并生成可执行结果的 AI 助手 |
| Graph | 使用 Spring AI Alibaba Graph 编排固定节点和并行分支的工作流 |
| Command | 前端快捷按钮发送的结构化指令，绕过不必要的自然语言猜测 |
| Requirement | 从本轮输入、历史状态和用户资料合并出的结构化需求 |
| Tool | 查询场所、场馆库存、约球活动、装备和预约规则的业务能力 |
| Hard Fact | 价格、库存、时段、距离、活动人数、订单状态等实时事实 |
| Soft Knowledge | 环境、服务、训练感受、装备体验等主观知识 |
| RAG | 检索 Soft Knowledge，再用证据增强推荐解释 |
| Embedding | 将文本转换为向量，用余弦相似度衡量语义接近程度 |
| Candidate | 业务工具查出的真实候选卡片，模型只能在其中选择 |
| GSSC | Gather、Select、Structure、Compress 四阶段上下文构建流水线 |
| SSE | 服务端通过一个长连接向浏览器持续推送阶段事件 |
| 降级 | 模型、Embedding 或某个工具失败后，使用确定性逻辑继续返回结果 |

## 3. 从 HMDP 到业务 Agent

### 3.1 HMDP 提供了什么基础

这个项目不是从空白开始搭建 Agent，而是在 HMDP 本地生活业务基础上继续扩展。HMDP 中已经存在的工程思想包括：

- MySQL 保存用户、博客、订单和商品等最终业务数据。
- Redis 保存登录态、热点缓存、Feed、限流状态和短期会话。
- `ThreadLocal` 在一次请求内传递当前用户。
- 缓存穿透、逻辑过期、分布式锁等高并发保护。
- 博客、关注、点赞和 Feed 形成可供 RAG 使用的社区内容。

对应源码入口：

- [RefreshTokenInterceptor.java](../../backend/src/main/java/com/hm/badminton/interceptor/RefreshTokenInterceptor.java#L25)：从 Redis 恢复登录状态。
- [UserContext.java](../../backend/src/main/java/com/hm/badminton/utils/UserContext.java#L16)：通过 `ThreadLocal` 保存当前用户。
- [CacheClient.java](../../backend/src/main/java/com/hm/badminton/utils/CacheClient.java#L46)：缓存空值解决穿透；第 76 行附近实现逻辑过期。
- [BlogService.java](../../backend/src/main/java/com/hm/badminton/service/community/impl/BlogService.java#L110)：社区 Feed；博客内容后来成为 Agent 的软知识来源之一。

### 3.2 Agent 继承了什么，又增加了什么

普通 HMDP 接口通常是一条确定链路：

```text
Controller -> Service -> Redis/MySQL -> DTO -> 前端
```

Agent 在此基础上增加了需求理解、状态合并、工具编排、知识检索、候选评分和自然语言解释：

```text
自然语言或 Command
  -> 合并结构化需求
  -> Graph 路由和并行查询
  -> 真实业务工具返回候选
  -> RAG 补充软知识
  -> 后端确定性评分
  -> 模型选择候选 ID 并生成解释
  -> 后端校验
  -> SSE 返回过程和结果
```

因此它不是“把聊天记录交给模型回答”的普通聊天框，而是一个**受到业务数据和后端规则约束的业务 Agent**。

### 3.3 为什么不让模型直接查询数据库

模型擅长理解口语和生成解释，但不应成为价格、库存和订单状态的事实来源。直接让模型决定业务结果会带来：

- 编造数据库中不存在的场馆或装备。
- 把过期时段描述为可预约。
- 忽略库存、预算、距离或人数限制。
- 同一个问题多次生成不一致结果。
- 生成结果无法直接映射到前端操作。

所以项目把职责拆成：

```text
模型：理解复杂表达、在候选中选择、生成差异化解释
工具：查询价格、库存、时段、距离、人数等硬事实
RAG：补充评价、体验、环境等软知识
后端：合并状态、评分、校验和降级
```

## 4. 一次 Agent 请求的完整代码链路

以用户发送“帮我找一个适合新手、环境比较好，而且今晚有空场的羽毛球馆”为例。

### 4.1 前端发起流式请求

前端调用 `streamApi`，使用 `fetch` 发送 POST JSON，并读取响应流：

- [client.ts](../../frontend/src/api/client.ts#L61)：建立请求。
- [client.ts](../../frontend/src/api/client.ts#L88)：读取 `ReadableStream`。
- [client.ts](../../frontend/src/api/client.ts#L106)：解析 `event:` 和 `data:`。
- [App.vue](../../frontend/src/App.vue#L3052)：消费阶段事件并更新聊天界面。

### 4.2 Controller 建立 SSE 连接

[AgentController.java](../../backend/src/main/java/com/hm/badminton/controller/agent/AgentController.java#L58) 暴露 `/agent/chat/stream`，接收：

- 用户消息。
- 可选的结构化 Command。
- 会话 ID。
- 当前城市、经纬度等上下文。
- 登录令牌或匿名会话标识。

Controller 自身不执行 Agent 逻辑，只把请求、客户端 IP 和匿名 ID 交给 `AgentServiceImpl.chatStream(...)`。

### 4.3 Service 启动虚拟线程

[AgentServiceImpl.java](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L186) 创建超时为 120 秒的 `SseEmitter`，并在虚拟线程中运行 Graph。

这里必须先捕获当前登录用户。原因是 `UserContext` 使用 `ThreadLocal`，新线程不会自动继承原请求线程的数据。捕获后再显式放入运行上下文，能够避免异步线程中用户丢失或串号。

### 4.4 Graph 的十个处理阶段

核心实现位于 [AgentGraphWorkflow.java](../../backend/src/main/java/com/hm/badminton/service/agent/graph/AgentGraphWorkflow.java#L78)：

```text
START
  -> beginTurn
  -> understandRequirement
  -> dispatchTools
       -> queryPlacesAndProducts ─┐
       -> queryActivities ────────┼-> mergeCandidates
       -> queryEquipment ─────────┘
  -> enrichKnowledge
  -> scoreCandidates
  -> selectCandidates
  -> persistAnswer
  -> END
```

各阶段作用如下：

1. `beginTurn`：限流、创建或恢复会话、短事务保存用户问题。
2. `understandRequirement`：加载上一轮结构化需求并合并本轮输入。
3. `dispatchTools`：根据 Command 和意图决定需要执行哪些业务分支。
4. `queryPlacesAndProducts`：查询真实场所，再查询场馆可售项目和时段。
5. `queryActivities`：查询未满员、可加入的约球活动。
6. `queryEquipment`：查询普通或秒杀装备。
7. `mergeCandidates`：合并、去重并限制候选规模。
8. `enrichKnowledge`：通过 RAG 为候选补充博客、评价和心得。
9. `scoreCandidates`：后端根据事实和偏好统一评分排序。
10. `selectCandidates` 与 `persistAnswer`：模型选择候选 ID，后端校验并保存回答。

对应 Service 方法：

- [beginTurn](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L224)
- [understandRequirement](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L245)
- [dispatchTools](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L267)
- [queryPlacesAndProducts](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L293)
- [queryActivities](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L320)
- [queryEquipment](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L347)
- [mergeCandidates](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L401)
- [enrichKnowledge](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L421)
- [scoreCandidates](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L441)
- [selectCandidates](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L455)
- [persistAnswer](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L483)

### 4.5 为什么保存问题和保存答案使用两个短事务

高德、数据库工具、Embedding 和大模型调用都可能较慢。如果从用户提问开始一直持有一个数据库事务，会让连接和锁占用时间过长。

当前设计先用短事务保存问题，外部调用结束后再用第二个短事务保存答案。这样即使模型调用耗时几秒，也不会长期占用数据库事务资源。

## 5. 意图识别与结构化需求

### 5.1 当前推荐架构

```text
快捷按钮
  -> 结构化 Command，直接执行

自由文本
  -> 基础规则快速识别
  -> 复杂或低置信度时调用模型提取 JSON
  -> 后端校验并合并会话状态
  -> Graph 查询真实业务数据
  -> 模型生成解释
```

Command 适合“查看预约规则”“按距离重新筛选”“不限预算装备”等明确动作。它不应该再次让模型猜意图，也不应错误继承与本动作无关的时间、距离或商品类型。

自由文本先使用规则，是因为“今晚”“500 元以内”“羽毛球”“附近”等高频表达可以快速、稳定解析。规则置信度不足时，才由 [AgentRequirementModelExtractor.java](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRequirementModelExtractor.java#L74) 提取 JSON。

### 5.2 结构化需求保存什么

[AgentRequirement.java](../../backend/src/main/java/com/hm/badminton/dto/agent/AgentRequirement.java#L15) 保存的不是自然语言摘要，而是后续工具可以直接使用的字段，例如：

- 运动类型。
- 日期、开始和结束时间。
- 预算上限。
- 距离上限。
- 用户水平。
- 当前意图和目标类型。
- 偏好标签与避雷标签。
- 是否允许扩大范围。

[AgentRequirementService.load](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRequirementService.java#L130) 读取上一轮状态，[merge](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRequirementService.java#L154) 合并本轮变化。

### 5.3 为什么不能只拼接聊天历史

原始对话中会混有已经失效的信息。例如用户先说“今晚打球”，下一轮点击“查看预约规则”，如果只把历史全部拼给模型，规则查询可能错误继承“今晚 + 羽毛球馆推荐”。

结构化状态允许按字段更新、清空和隔离：

```text
上一轮：intent=FIND_VENUE, sport=badminton, time=tonight
本轮：command=VIEW_BOOKING_RULES
结果：直接查询规则，不携带 time、distance、groupBuy 等筛选条件
```

这也是项目从“能回答”升级为“能够稳定执行业务”的关键一步。

## 6. 记忆系统与上下文工程

### 6.1 项目中的四类上下文

项目没有照搬某个通用 Memory 框架，而是按业务生命周期拆分存储。

| 类型 | 项目实现 | 生命周期 | 用途 |
| --- | --- | --- | --- |
| 请求工作记忆 | `AgentGraphRunContext` | 单次 Graph 执行 | 节点之间传递用户、需求、候选、耗时和结果 |
| 结构化短期记忆 | Redis `agent:conversation:requirement:{id}` | 默认约 60 分钟 | 记住球类、时间、预算、距离、水平和偏好 |
| 会话长期记录 | MySQL `agent_conversation`、`agent_message` | 持久化 | 登录用户的历史会话管理和消息恢复 |
| 匿名历史 | HttpOnly Cookie + Redis List/String/ZSet | 默认 7 天 TTL | 游客继续会话、查看和删除历史 |
| 外部知识记忆 | 博客、场馆评价、装备心得 | 跟随业务数据 | RAG 检索软知识 |

持久化入口位于：

- [AgentPersistenceService.java](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentPersistenceService.java#L72)：开始一轮会话。
- [AgentPersistenceService.java](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentPersistenceService.java#L111)：完成并保存回答。
- [AgentPersistenceService.java](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentPersistenceService.java#L182)：读取会话列表。
- [AgentPersistenceService.java](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentPersistenceService.java#L227)：读取消息。

Redis Key 集中定义在 [RedisConstants.java](../../backend/src/main/java/com/hm/badminton/constants/RedisConstants.java#L37)。

### 6.2 长期记忆究竟保存在哪里

需要区分“历史记录”和“用户语义记忆”：

- 登录用户的历史对话长期保存在 MySQL。
- 本轮到下一轮需要复用的结构化条件保存在 Redis，并设置 TTL。
- 游客不写入 `userId=0` 的永久 MySQL 历史，而是使用匿名 Cookie 关联 Redis 数据，同时开放历史查看和删除。
- 博客和评价属于业务知识库，不是某个用户的私人长期记忆。

当前项目**没有**自动执行“工作记忆 -> 情景记忆 -> 语义记忆”的总结沉淀，也没有 Neo4j 知识图谱和多模态感知记忆。若面试被问到，应该如实说明。

### 6.3 上下文的推荐优先级

当不同来源发生冲突时，推荐按以下顺序处理：

```text
System 与安全规则
  > 当前结构化 Command
  > 实时工具返回的硬事实
  > 当前用户输入
  > 结构化会话状态
  > 用户资料和长期偏好
  > RAG 软知识
  > 未结构化的原始历史
```

例如评价说“价格便宜”，但实时工具返回当前价格为 89 元，回答中必须使用 89 元；RAG 只能说明体验，不能覆盖实时价格。

## 7. 项目中的分布式 GSSC 流水线

### 7.1 GSSC 是什么

通用 `ContextBuilder` 常把上下文工程拆成：

```text
Gather -> Select -> Structure -> Compress
收集      选择       结构化       压缩
```

当前项目没有一个名字叫 `ContextBuilder` 的集中类，但四个阶段真实存在，并分布在需求服务、持久化服务、Graph、业务工具、RAG、评分器和模型适配代码中。因此更准确的说法是：

> 约个球 Agent 实现了一条由多个业务组件共同完成的“分布式 GSSC 流水线”。

### 7.2 Gather：收集上下文

收集阶段的输入包括：

1. 本轮用户问题和结构化 Command。
2. 当前城市、经纬度和客户端上下文。
3. 登录用户的城市、水平和偏好。
4. Redis 中上一轮结构化需求。
5. MySQL 或匿名 Redis 中的会话和消息。
6. 高德附近场所结果。
7. 场馆商品、可售日期和时段。
8. 可加入的约球活动。
9. 普通装备和秒杀装备。
10. 社区博客、装备心得和场馆评价。

对应组件：

```text
AgentRequirementService   -> 上一轮需求 + 本轮输入 + 用户资料
AgentPersistenceService   -> 会话、消息和匿名历史
PlaceAgentTool            -> 真实附近场所
VenueProductAgentTool     -> 场馆商品、库存和时段
ActivityAgentTool         -> 约球活动和人数
EquipmentAgentTool        -> 装备、价格和库存
BookingRuleAgentTool      -> 独立预约规则
AgentRagService           -> 博客、评价和装备心得
```

这里与“把最近五轮聊天记录全部拼进去”最大的区别，是每类上下文都有明确来源、生命周期和可信等级。

### 7.3 Select：选择真正相关的内容

Gather 并不意味着所有内容都送给模型。Select 阶段通过以下规则减少无关信息：

- Command 优先于自由文本猜测。
- 高频明确表达先用规则解析，低置信度才调用模型提取 JSON。
- `dispatchTools` 只启用与当前意图相关的查询分支。
- 场所、活动和装备都先做城市、球类、时间、预算等业务过滤。
- 合并后候选卡片限制为最多 24 个，避免模型输入无限增长。
- 只有出现“环境、服务、适合新手、体验、灯光、地板”等主观需求时才启用 RAG。
- RAG 使用关键词与语义混合评分，并按候选卡片保留 TopK 证据。
- `AgentRecommendationScorer` 再按照硬条件、偏好和知识证据统一排序。

Select 的目的不只是节省 token，更重要的是阻止无关历史污染当前业务动作。

### 7.4 Structure：组织为模型可用的结构

项目没有把上下文组织成长篇自然语言，而是使用明确对象：

- `AgentRequirement`：用户真正想要什么。
- `AgentGraphRunContext`：本次 Graph 执行状态。
- `AgentContext`：城市、时间偏好、结构化需求和候选卡片。
- `AgentCard`：统一表示场所、团购、活动、装备或规则。
- `AgentRagEvidence`：绑定到候选卡片的软知识证据。

最终送给模型的 JSON 主要包含：

```json
{
  "userMessage": "帮我找适合新手且今晚有空场的羽毛球馆",
  "requirement": {
    "sport": "羽毛球",
    "date": "今天",
    "time": "今晚",
    "level": "新手"
  },
  "city": "西安市",
  "cards": [
    {
      "cardId": "venue-product:123",
      "title": "羽毛球晚间黄金单场 1 小时",
      "price": 58,
      "availableTime": "19:00-20:00",
      "distanceKm": 4.6,
      "ragEvidence": ["用户评价认为灯光均匀，对新手友好"]
    }
  ],
  "userPreference": {
    "preferenceTags": ["适合新手", "环境好"],
    "avoidTags": []
  }
}
```

模型被要求只返回：

```json
{
  "selectedCardIds": ["venue-product:123"],
  "recommendationReasons": {
    "venue-product:123": "今晚 19:00 有库存，价格符合预算，评价中多次提到灯光均匀。"
  },
  "explanation": "优先选择时段匹配且更适合新手的场馆项目。"
}
```

这种结构让后端可以校验 ID，也让每张卡片获得不同推荐理由。

### 7.5 Compress：压缩而不丢失关键约束

项目当前没有精确的 tokenizer 和全局 `max_tokens` 预算器，但已经通过业务裁剪完成实用的压缩：

- 用结构化需求替代重复聊天历史。
- 合并候选并限制最多 24 张卡片。
- 每张卡片的 RAG 证据默认保留 Top 3，最大不超过 5。
- 证据摘要截取约 110 个字符。
- [cardsForModel](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L1095) 只保留模型决策需要的字段。
- 模型最多选择 4 个结果，后端再扩展必要的场所关联卡片。
- 价格、库存、时段和 ID 等硬事实优先保留，装饰性字段优先删除。

所以这里的 Compress 不是简单截断字符串，而是“先理解业务重要性，再裁剪字段和数量”。

### 7.6 与通用 ContextBuilder 框架的异同

| 通用框架 | 约个球项目 |
| --- | --- |
| PDF、Word、音频等文件离线入库 | 直接使用 MySQL 中博客、评价、装备心得和实时业务表 |
| MarkItDown 转换和通用 Chunk | 当前数据较短，以一篇博客或一条评价为文档，没有通用文件切块 |
| Qdrant 向量库 | Redis 只缓存文本向量，Java 内存中计算余弦相似度 |
| Working/Episodic/Semantic/Perceptual 四类 Memory | 单次运行上下文、Redis 结构化需求、MySQL/Redis 会话历史和业务知识 |
| `ContextPacket[]` 统一评分 | 目前由多个服务分散选择和裁剪 |
| 通用相关性 + 新鲜度 | 业务硬约束、推荐评分、实体绑定和 RAG 相似度共同决定 |
| 自动记忆巩固 | 当前未实现 |

两者的共同点是都在做 Gather、Select、Structure、Compress；不同点是本项目更强调实时业务约束和可执行卡片，而通用框架更强调多源文档与通用记忆。

### 7.7 后续可演进为集中式 ContextBuilder

可新增统一的数据包：

```java
public record ContextPacket(
        String source,
        String type,
        Object content,
        double relevance,
        Instant timestamp,
        int priority,
        int estimatedTokens,
        boolean hardFact,
        String entityId
) {}
```

再由统一组件执行：

```java
class AgentContextBuilder {
    List<ContextPacket> gather(...);
    List<ContextPacket> select(...);
    ModelContext structure(...);
    ModelContext compress(...);
}
```

升级时应优先增加：

1. 全局 token 预算和每类上下文配额。
2. 来源优先级与冲突检测。
3. 新鲜度和实体一致性评分。
4. Prompt 版本、上下文快照和可回放日志。
5. 统计每个上下文包是否真正影响最终选择。

## 8. Spring AI Alibaba Graph 编排

### 8.1 为什么使用 Graph

如果所有逻辑都写在一个 Service 方法中，代码会变成大量条件分支，很难回答：当前执行到哪里、哪个工具超时、哪些查询能够并行、失败后如何降级。

Graph 将流程拆成职责清晰的节点，并显式声明边：

- 节点便于单独计时、测试和替换。
- 场所、活动、装备三个分支可以并行。
- RAG 只能发生在候选合并以后。
- 模型选择只能发生在后端评分以后。
- SSE 可以在每个节点切换时推送进度。

节点注册和边定义位于 [AgentGraphWorkflow.java](../../backend/src/main/java/com/hm/badminton/service/agent/graph/AgentGraphWorkflow.java#L86) 与 [第 144 行](../../backend/src/main/java/com/hm/badminton/service/agent/graph/AgentGraphWorkflow.java#L144)。

### 8.2 哪些步骤真正并行

`dispatchTools` 后的三个分支可以同时执行：

```text
场所与场馆商品分支
约球活动分支
装备分支
```

但场所分支内部“查场所 -> 查该场所可售商品”存在数据依赖，不能盲目并行。Graph 的价值不是让所有步骤都并行，而是明确哪些步骤独立、哪些步骤有先后依赖。

### 8.3 这是不是多 Agent

不是。当前是**单 Agent 的确定性 Graph 工作流**：多个节点共享同一个运行上下文，业务工具也不是拥有独立目标、记忆和规划能力的 Agent。

如果未来拆成场所 Agent、活动 Agent、装备 Agent，并让它们独立规划、协商和汇总，才更接近多 Agent 系统。

## 9. 业务工具、硬事实与防幻觉

### 9.1 工具列表

| 工具 | 作用 | 事实来源 |
| --- | --- | --- |
| [PlaceAgentTool](../../backend/src/main/java/com/hm/badminton/service/agent/tools/PlaceAgentTool.java#L26) | 查询真实附近运动场所 | 高德/场所业务链路 |
| [VenueProductAgentTool](../../backend/src/main/java/com/hm/badminton/service/agent/tools/VenueProductAgentTool.java#L29) | 查询场馆商品、库存和时段 | MySQL/Redis |
| [ActivityAgentTool](../../backend/src/main/java/com/hm/badminton/service/agent/tools/ActivityAgentTool.java#L31) | 查询可加入活动 | MySQL |
| [EquipmentAgentTool](../../backend/src/main/java/com/hm/badminton/service/agent/tools/EquipmentAgentTool.java#L31) | 查询普通和秒杀装备 | MySQL/Redis |
| [BookingRuleAgentTool](../../backend/src/main/java/com/hm/badminton/service/agent/tools/BookingRuleAgentTool.java#L16) | 独立查询预约规则 | 平台规则配置 |
| [UserPreferenceAgentTool](../../backend/src/main/java/com/hm/badminton/service/agent/tools/UserPreferenceAgentTool.java#L20) | 查询用户城市与偏好 | 用户资料 |

这些方法通过 Spring AI 的 `@Tool` 描述能力，但当前主要由后端 Graph 确定性调度，并不是完全放任模型自由决定任意工具调用。

### 9.2 硬事实与软知识的边界

```text
硬事实，由实时工具提供：
价格、库存、可售日期、可售时段、距离、活动人数、订单状态

软知识，由 RAG 提供：
适合新手吗、环境怎么样、服务如何、灯光和地板如何、装备手感如何
```

用户问“适合新手、环境好，而且今晚有空场的羽毛球馆”时：

- RAG 回答“适合新手、环境和用户评价如何”。
- 场所与库存工具回答“今晚有没有空场、价格和库存是多少、能否预订”。

一句话概括：**RAG 提供软知识，工具提供硬事实。**

### 9.3 防幻觉的五道约束

1. 业务工具先查出真实候选。
2. [cardsForModel](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L1095) 只发送白名单字段。
3. 模型只能返回 `selectedCardIds`，不能凭空创建商品。
4. [validateSelection](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L1118) 检查 ID 是否属于真实候选，并限制数量。
5. 模型失败、返回空结果或非法 ID 时，使用后端确定性排序生成本地回答。

这使模型成为“候选选择器和解释器”，而不是业务事实数据库。

## 10. RAG 的类型、完整流程与代码

### 10.1 项目属于哪一种 RAG

当前实现最准确的定义是：

> **候选约束的轻量模块化混合 RAG。**

它具有高级 RAG 和模块化 RAG 的部分特征：

- 使用 Embedding 语义检索，不只匹配关键词。
- 组合关键词与语义相似度。
- RAG 是 Graph 中可启停的独立节点。
- 文档证据与具体候选实体绑定。
- 检索结果经过 TopK、后端评分和模型选择。
- 模型选择结果再次由后端校验。

它不是完整的现代模块化 RAG，因为当前没有通用离线文档清洗、Chunk、独立向量数据库、BM25、多查询扩展、HyDE、Cross-Encoder 重排、自我反思和系统化检索评测。

### 10.2 为什么不是朴素 RAG

朴素 RAG 通常是：

```text
用户问题 -> 关键词找几篇文档 -> 全文拼进 Prompt -> 模型回答
```

本项目是候选优先：

```text
业务工具先查真实候选
  -> RAG 只检索与候选有关的软知识
  -> 证据绑定到 cardId
  -> 后端评分
  -> 模型只能选择真实 cardId
  -> 后端再次校验
```

即使 RAG 找到无关内容，也不能生成一个数据库中不存在的商品或覆盖实时库存。

### 10.3 完整十二步流程

#### 第 1 步：判断是否需要 RAG

[AgentRagService.enrich](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRagService.java#L78) 会检查：

- RAG 是否启用。
- 候选卡片是否为空。
- 问题中是否包含环境、服务、新手、体验、灯光、地板、稳定、舒适等主观词。
- 用户是否存在偏好或避雷标签。

只问“现在价格多少”时没有必要调用 Embedding；问“适不适合新手”时才需要软知识。

#### 第 2 步：先获得真实业务候选

Graph 先通过场所、活动和装备工具获得 `AgentCard`。RAG 不负责发现可售库存，只负责为已经存在的卡片补充知识。

#### 第 3 步：收集知识文档

[collectDocuments](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRagService.java#L135) 收集：

- 状态正常的社区博客和装备心得，按点赞数和创建时间排序。
- 对应场所的用户评价。
- 博客的 `relatedType`、`relatedId` 与装备或场馆商品建立实体关系。

内部使用：

```java
RagDocument(
    sourceType,
    sourceId,
    title,
    searchText,
    targetCardIds
)
```

`targetCardIds` 很关键，它让证据能够绑定到具体候选，而不是成为全局模糊描述。

#### 第 4 步：构建检索 Query

检索 Query 不只包含本轮问题，还会加入 `preferenceTags` 和 `avoidTags`。例如：

```text
用户问题：推荐一个适合新手的羽毛球馆
偏好：灯光好、交通方便
避雷：地板滑

检索 Query：推荐一个适合新手的羽毛球馆 灯光好 交通方便 地板滑
```

正向和负向标签都进入检索，后续评分再区分偏好命中和避雷命中。

#### 第 5 步：关键词相似度

[lexicalSimilarity](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRagService.java#L272) 会统一小写、移除标点并生成中文二元词片段，再根据两个集合交集计算相似度：

```text
lexical = intersection / sqrt(leftTokenCount * rightTokenCount)
```

这属于轻量关键词匹配，不是 BM25 或 TF-IDF。它对“灯光”“新手”“地板”等字面相同表达有效。

#### 第 6 步：生成和缓存 Embedding

[embeddings](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRagService.java#L194) 通过 Spring AI `EmbeddingModel` 调用 DashScope Embedding：

1. 对 Query 和文档文本计算 SHA-256，形成稳定 Key。
2. 先读取 Redis `agent:rag:embedding:{hash}`。
3. 只批量请求缓存缺失的文本向量。
4. 将 `float[]` 序列化为 JSON 写回 Redis。
5. 默认 TTL 为 7 天。

配置入口位于 [application.yml](../../backend/src/main/resources/application.yml#L109)，Key 定义位于 [RedisConstants.java](../../backend/src/main/java/com/hm/badminton/constants/RedisConstants.java#L41)。

Redis 在这里的作用是**缓存 Embedding 结果，减少重复 Embedding API 调用和响应时间**，不是 Redis Vector Search，也不是完整向量数据库。

#### 第 7 步：计算语义相似度

[cosine](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRagService.java#L297) 在 Java 内存中计算 Query 向量与文档向量的余弦相似度。

语义检索的体现是：即使用户说“适合刚开始学的人”，文档写的是“新手友好、容错高”，字面不完全相同，Embedding 向量仍可能较接近。

#### 第 8 步：混合检索评分

Embedding 可用时：

```text
retrievalScore = 0.30 * lexical + 0.70 * semantic
```

如果文档明确绑定当前卡片，再增加实体绑定奖励，最终分数上限为 1。Embedding 不可用时退化为纯关键词相似度。

这样既保留精确词命中，又能召回语义相近表达。

#### 第 9 步：按卡片保留 TopK 证据

证据不是全局取 TopK，而是按每张候选卡片聚合并保留 TopK。默认值为 3，代码限制最大为 5，见 [AgentRagService.java](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRagService.java#L109)。

每张卡片会得到：

- `ragEvidence`。
- 证据数量。
- 高亮摘要。
- 来源类型。
- `knowledgeScore`。

#### 第 10 步：进入统一推荐评分

[AgentRecommendationScorer.java](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRecommendationScorer.java#L43) 不只看 RAG，还统一考虑：

- 运动类型匹配。
- 时段与库存可用性。
- 预算匹配。
- 距离。
- 用户水平。
- 偏好命中和避雷项。
- 卡片类型权重。
- RAG `knowledgeScore`。

知识证据只占一部分权重，不能让一条好评覆盖“无库存”这样的硬条件。

#### 第 11 步：将证据安全地交给模型

`cardsForModel` 只发送模型决策所需字段，并在系统提示中明确：

- 软知识只能用于推荐解释。
- 不得把评价推断为实时价格、库存或时段。
- 每个已选卡片必须生成不同的推荐理由。
- 只能返回候选列表中的 `cardId`。

#### 第 12 步：校验并降级

模型返回 `selectedCardIds` 后，后端通过 `validateSelection` 校验。模型不可用、响应为空、JSON 解析失败或 ID 非法时，直接使用后端排序和本地模板生成可信回答。

### 10.4 RAG 的目标是增强推荐还是降低耗时

两者都有，但主次不同：

- **关键词 + 语义混合检索的首要目标是增强推荐质量和语义召回。**
- **Redis 缓存文本向量的目标是减少重复 Embedding 调用、成本和响应耗时。**

简历可写为：

> 基于场馆评价、社区博客和装备心得构建关键词与语义混合检索，通过 DashScope Embedding 提升语义召回，并使用 Redis 缓存向量以降低重复调用耗时。

### 10.5 当前 RAG 的不足与升级方向

| 当前不足 | 建议升级 | 影响 |
| --- | --- | --- |
| 每次运行收集业务文档 | 增加离线清洗、增量索引 | 查询更快，但需要索引一致性管理 |
| 没有通用 Chunk | 按标题、段落和实体切块 | 长博客召回更准，但文档数量增加 |
| 轻量词片段相似度 | 引入 BM25 | 关键词排序更可靠，但部署复杂度上升 |
| Java 内存余弦计算 | 使用 Redis Search/Qdrant/Elasticsearch 向量索引 | 数据规模可扩展，但增加中间件和运维成本 |
| 固定 0.3/0.7 权重 | 基于离线评测调参 | 效果更可解释，但需要标注数据集 |
| 无 Cross-Encoder | 对召回结果二次重排 | 精度提高，但推理耗时和成本上升 |
| 无查询重写、多查询、HyDE | 对复杂问题生成检索变体 | 召回提高，但模型调用增加 |
| 无检索评测 | 建立 Recall@K、MRR、NDCG 和证据命中率 | 可量化迭代，但需要人工构建标准答案 |

## 11. SSE 通信如何建立

### 11.1 为什么使用 SSE

一次 Agent 请求会经历需求理解、多个工具查询、RAG、评分和模型调用。如果只使用普通 HTTP，用户会在几秒内看不到任何变化。

SSE 允许服务端在同一个 HTTP 响应上持续推送：

```text
正在理解需求
正在查询附近场所
正在查询可售时段
正在补充评价
正在生成建议
最终结果
```

它适合服务端单向推送；用户发送消息仍使用普通 POST。

### 11.2 为什么前端用 fetch，而不是 EventSource

原生 `EventSource` 更适合 GET，难以携带当前项目需要的 JSON 请求体和自定义认证头。项目使用：

```text
fetch POST + application/json + Accept: text/event-stream
```

然后手动读取 `response.body.getReader()`，通过 `TextDecoder` 解码，并按空行切分 SSE 事件。

### 11.3 完整网络链路

```text
Vue 页面
  -> HTTPS POST /api/agent/chat/stream
  -> ECS Nginx
  -> HTTP /agent/chat/stream
  -> Spring MVC AgentController
  -> SseEmitter
  -> 虚拟线程运行 Graph
  -> 多次 emitter.send(...)
  -> Nginx 不缓冲，立即转发
  -> 浏览器 ReadableStream 逐段解析
```

生产 Nginx 配置位于 [hm-badminton.conf](../../deploy/production/nginx/hm-badminton.conf#L78)，关键项包括：

```nginx
proxy_http_version 1.1;
proxy_set_header Connection "";
proxy_buffering off;
proxy_cache off;
proxy_read_timeout 120s;
proxy_send_timeout 120s;
add_header X-Accel-Buffering no;
```

如果未关闭 Nginx 缓冲，后端虽然逐次 `send`，浏览器仍可能等到缓冲区积满后一次性收到，看起来就像没有流式效果。

### 11.4 后端如何发送事件

[sendEvent](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L714) 通过同步保护调用：

```java
emitter.send(SseEmitter.event().name(event).data(data));
```

线上数据格式类似：

```text
event: stage
data: {"name":"queryPlaces","message":"正在查询附近场所"}

event: done
data: {"conversationId":12,"answer":"...","cards":[...]}

```

当前事件类型：

- `conversation`：会话已建立。
- `stage`：Graph 当前执行阶段。
- `cards`：可用于提前返回候选卡片。
- `done`：最终回答和卡片。
- `error`：业务或系统错误。

前端当前主要处理 `conversation`、`stage`、`done` 和 `error`；`cards` 尚未形成完整的增量渲染体验。

### 11.5 连接如何结束

- 正常完成：发送 `done`，然后 `emitter.complete()`。
- 执行异常：发送 `error`，然后完成连接。
- 浏览器离开页面：发送可能抛出异常，后端停止继续推送，但已经开始的持久化仍应安全完成。
- 超过 120 秒：`SseEmitter` 或代理超时关闭连接。

### 11.6 当前 SSE 的真实边界

当前实现是**阶段流式**，不是大模型逐 token 流式。用户能看到“执行到哪里”，最终自然语言回答仍在 `done` 中整体返回。

后续可以增加：

- 模型 token 增量事件。
- `cards` 事件的前端即时渲染。
- `AbortController`，页面离开时取消后端任务。
- 心跳事件，避免长时间无数据被代理关闭。
- 事件 ID、重连和 `Last-Event-ID`。
- 断连感知和任务取消，避免无效模型调用继续消耗资源。

## 12. 降级、超时、并发与安全

### 12.1 三层降级

```text
Embedding 失败 -> 使用关键词检索
模型失败      -> 使用后端排序 + 本地模板回答
工具超时      -> 该分支返回空候选，其他分支继续
```

降级的目标不是让所有异常都静默，而是在非关键依赖失败时仍返回不编造的结果，并同时记录日志和指标。

### 12.2 超时和并发

当前工具执行有独立线程池和超时配置，相关配置见 [application.yml](../../backend/src/main/resources/application.yml#L109)。场所、活动、装备可以并行，默认工具超时约 5 秒、线程数约 6。

需要监控：

- 每个工具的耗时和超时率。
- 空结果率。
- 模型失败率。
- Embedding 失败率。
- 确定性降级率。
- SSE 完成率与客户端断连率。

这些指标能区分慢在高德、数据库、Redis、Embedding 还是回答模型，而不是只看到“AI 很慢”。

### 12.3 熔断和告警

当某个外部依赖连续超时，可以短时间停止请求该依赖并走降级，避免线程不断堆积。告警应包含：

- 依赖名称。
- 请求量、P95/P99 耗时和超时率。
- 熔断状态和持续时间。
- 使用了哪种降级结果。
- 关联 `requestId`，便于追踪完整 Graph。

### 12.4 安全边界

- Cookie 中的匿名 ID 只用于定位匿名会话，不信任前端传入的用户 ID。
- 登录身份从后端认证上下文获取。
- Tool 输入仍需校验城市、距离、预算和分页上限。
- 模型输出必须按 JSON 协议解析并验证候选 ID。
- RAG 文本属于不可信数据，不能覆盖系统规则或触发任意工具。
- 日志避免记录完整令牌、Cookie 和敏感用户信息。

## 13. 对照 HelloAgents 第三部分高级知识扩展

参考章节：

- [第八章：记忆与检索](https://github.com/datawhalechina/hello-agents/blob/main/docs/chapter8/%E7%AC%AC%E5%85%AB%E7%AB%A0%20%E8%AE%B0%E5%BF%86%E4%B8%8E%E6%A3%80%E7%B4%A2.md)
- [第九章：上下文工程](https://github.com/datawhalechina/hello-agents/blob/main/docs/chapter9/Chapter9-Context-Engineering.md)
- [第十章：智能体通信协议](https://github.com/datawhalechina/hello-agents/blob/main/docs/chapter10/%E7%AC%AC%E5%8D%81%E7%AB%A0%20%E6%99%BA%E8%83%BD%E4%BD%93%E9%80%9A%E4%BF%A1%E5%8D%8F%E8%AE%AE.md)
- [第十一章：Agentic RL](https://github.com/datawhalechina/hello-agents/blob/main/docs/chapter11/%E7%AC%AC%E5%8D%81%E4%B8%80%E7%AB%A0%20Agentic-RL.md)
- [第十二章：智能体性能评估](https://github.com/datawhalechina/hello-agents/blob/main/docs/chapter12/%E7%AC%AC%E5%8D%81%E4%BA%8C%E7%AB%A0%20%E6%99%BA%E8%83%BD%E4%BD%93%E6%80%A7%E8%83%BD%E8%AF%84%E4%BC%B0.md)

### 13.1 第八章：记忆与检索

项目已实现：

- 单轮工作记忆 `AgentGraphRunContext`。
- Redis 结构化短期需求。
- MySQL 登录会话历史。
- Redis 匿名会话历史。
- 博客、评价和装备心得检索。

项目未实现：

- 自动记忆重要性判断。
- 工作记忆向情景记忆、语义记忆的自动巩固。
- 知识图谱关系记忆。
- 图像、音频等感知记忆。

### 13.2 第九章：上下文工程

这是当前项目实现最完整的部分之一：

- Command、规则和模型 JSON 提取共同完成需求理解。
- 原始历史被压缩为结构化 Requirement。
- 工具按意图选择，不把所有数据交给模型。
- 候选、证据和模型字段都有数量与白名单限制。
- 硬事实和软知识设置不同可信等级。
- GSSC 虽然分布在多个类中，但逻辑完整存在。

### 13.3 第十章：通信协议

项目已使用：

- Spring AI `@Tool` 形式描述内部工具。
- HTTP JSON 作为前端请求协议。
- SSE 作为服务端进度协议。

项目未使用：

- MCP 工具协议。
- A2A、ANP 等 Agent 间通信协议。
- 多 Agent 协商和任务转交。

所以面试中不应把 `@Tool` 直接描述成 MCP。

### 13.4 第十一章：Agentic RL

当前项目没有强化学习。`AgentRecommendationScorer` 是人工规则和权重构成的确定性评分器，不存在奖励模型、轨迹采样、策略更新或在线学习。

如果未来引入 RL，可将用户点击、下单、加入活动、取消和负反馈构造成奖励信号，但必须解决偏差、探索风险和线上安全问题。

### 13.5 第十二章：性能评估

项目当前具备的可观测基础：

- Graph 节点计时。
- 工具超时日志。
- 模型和 Embedding 降级日志。
- 最终候选数量和 RAG 证据数量。

尚缺少：

- 固定测试问题集和标准候选。
- 意图识别准确率、字段抽取 F1。
- RAG Recall@K、MRR、NDCG。
- 推荐命中率和非法 cardId 率。
- LLM-as-Judge 与人工一致性校验。
- 端到端 P50/P95/P99 延迟和成本报表。

## 14. 当前缺陷与升级路线

### 14.1 上下文工程

当前 GSSC 分散在多个服务中，调试时难以还原“最终给模型看了什么”。建议先增加统一上下文快照和 token 预算，再决定是否抽象 `AgentContextBuilder`。

好处是可追踪、可回放、便于控制成本；坏处是增加对象转换和日志存储量，且抽象过早可能让简单业务变复杂。

### 14.2 RAG

当前方案适合小规模业务数据，但文档量增长后，逐批收集和 Java 内存余弦计算会变慢。可逐步升级为离线索引、BM25 + 向量数据库 + 重排器。

好处是召回规模和精度提高；坏处是索引更新、数据一致性、部署和评测成本显著增加。

### 14.3 Graph

当前 Graph 是确定性工作流，优点是稳定可控，缺点是复杂新意图仍需写节点和路由。后续可增加条件边、节点级重试、执行恢复和 Prompt/Graph 版本记录，但不应为了“更像 Agent”而取消必要的业务约束。

### 14.4 SSE

当前只能展示阶段，不能逐 token 输出。若改为模型原生流式，需要同时解决 token 事件协议、断连取消、答案持久化和前端消息合并，不能只替换一个接口。

### 14.5 评测

最优先建立三组数据集：

1. 结构化需求抽取集：球类、日期、时段、预算、距离、水平、Command。
2. 工具与推荐集：给定数据库快照，期待调用分支和候选 ID。
3. RAG 证据集：问题、相关实体、标准证据和应避免的错误事实。

评测建立后，权重、Prompt 和模型升级才有客观依据。

## 15. 面试追问与一分钟回答

### 15.1 这个 Agent 和普通聊天机器人有什么区别

**回答：**这个项目里的 AI 不是直接把问题交给大模型回答，而是一个受业务约束的 Agent。请求进入 `AgentController` 后，由 `AgentGraphWorkflow` 按“需求理解、工具分发、并行查询、RAG、评分、模型选择、持久化”执行。场馆、活动和装备都由真实业务工具查询，模型只能从 `AgentCard` 候选中返回 `selectedCardIds`，后端还会通过 `validateSelection` 校验。因此模型负责理解和解释，价格、库存、时段等事实仍由 MySQL、Redis和高德链路提供。这样既保留自然语言交互，又避免模型编造商品或可售时段。

### 15.2 为什么不把全部聊天历史直接交给模型

**回答：**全部拼接历史会带来 token 增长和状态污染，例如上一轮查今晚场馆，下一轮点击“预约规则”，旧的时间和场馆条件可能被错误继承。项目用 `AgentRequirementService` 把球类、日期、时间、预算、距离、水平和偏好结构化保存在 Redis。本轮先识别 Command，再按字段合并或清空状态，只有低置信度口语才调用模型提取 JSON。这样历史不再是一段不可控文本，而是工具可以直接使用、能够覆盖和验证的业务状态。

### 15.3 项目的短期记忆和长期记忆保存在哪里

**回答：**单次请求的工作记忆保存在 `AgentGraphRunContext`，只在 Graph 执行期间存在；跨轮的结构化需求保存在 Redis，例如球类、预算和时间，默认有约一小时 TTL。登录用户的会话和消息长期保存在 MySQL 的 `agent_conversation`、`agent_message`；游客由 HttpOnly 匿名 Cookie 关联 Redis 会话，保留约七天并支持历史管理。博客和评价属于 RAG 外部知识，不等于用户记忆。项目暂时没有自动把历史总结成用户语义画像，这属于后续能力。

### 15.4 如何防止大模型幻觉

**回答：**项目采用候选约束。Graph 先调用 `PlaceAgentTool`、`VenueProductAgentTool`、`ActivityAgentTool` 和 `EquipmentAgentTool` 查出真实候选，`cardsForModel` 只把白名单字段交给模型。模型协议要求只返回 `selectedCardIds` 和每个 ID 的推荐理由，`validateSelection` 再检查 ID 是否确实存在于候选中，并限制最多选择四个。如果模型超时、JSON 错误或返回非法 ID，就退回 `AgentRecommendationScorer` 的确定性排序和本地模板回答。因此模型不能创造价格、库存、商品或活动。

### 15.5 项目中的 RAG 是怎么实现的

**回答：**这是候选约束的轻量混合 RAG。`AgentRagService` 先从博客、装备心得和场馆评价构建 `RagDocument`，并用 `targetCardIds` 绑定真实候选。检索同时计算中文词片段相似度和 DashScope Embedding 余弦相似度，权重大约是 0.3 和 0.7，每张卡片保留 TopK 证据。Embedding 通过文本 SHA-256 Key 缓存在 Redis，减少重复 API 调用。证据进入统一评分和模型解释，但不能覆盖价格、库存和时段。Embedding 失败时自动退化为关键词检索。

### 15.6 为什么使用 Graph，而不是一个大 Service 方法

**回答：**Agent 请求包含外部场所、活动、装备、RAG 和模型等多个阶段。Graph 把这些阶段定义成节点和边，让场所、活动、装备三个独立分支可以并行，也保证 RAG 必须在候选合并后执行、模型必须在评分后选择。每个节点都可以记录阶段和耗时，通过 SSE 告诉前端当前进度。这样比一个充满 `if/else` 的方法更容易定位超时、增加新工具和设计降级，同时仍保持确定性业务流程。

### 15.7 SSE 是如何建立的

**回答：**前端使用 `fetch` POST JSON 到 `/api/agent/chat/stream`，因为原生 `EventSource` 不方便携带请求体和认证头。Nginx 关闭 `proxy_buffering` 和缓存，把连接转发给 Spring MVC。Controller 返回超时 120 秒的 `SseEmitter`，Service 在虚拟线程中运行 Graph，并在节点切换时发送 `stage`，完成时发送 `done`。前端通过 `ReadableStream.getReader()` 和 `TextDecoder` 按 SSE 的空行协议解析。目前是阶段级流式，不是模型逐 token 流式。

### 15.8 这是不是多 Agent，是否使用了强化学习

**回答：**都没有。当前是一个 Agent 的确定性 Graph，多个节点共享 `AgentGraphRunContext`，工具只是业务函数，没有独立目标、记忆和协商协议。推荐排序由 `AgentRecommendationScorer` 的人工权重完成，不存在奖励模型、策略训练或在线更新，所以也不是 Agentic RL。这样设计是因为当前业务更重视价格、库存和订单准确性，确定性流程更容易控制和验证。未来数据足够时可以研究多 Agent 或学习排序，但不能把规则评分包装成强化学习。

### 15.9 你会如何评估这个 Agent

**回答：**我会分层评估，而不是只看回答是否流畅。第一层测意图和字段抽取准确率，例如球类、预算、时间和 Command；第二层固定数据库快照，检查调用了正确工具、候选 ID 和库存是否正确；第三层用 Recall@K、MRR 或 NDCG 测 RAG 是否召回正确评价；第四层检查模型是否只选合法 cardId、推荐理由是否引用真实证据；最后统计端到端 P50/P95、工具超时率、模型失败率和降级率。这样才能判断问题发生在需求理解、检索、业务工具还是生成阶段。

### 15.10 如何解释项目的 GSSC 上下文工程

**回答：**项目没有单独的 `ContextBuilder` 类，但实现了一条分布式 GSSC。Gather 由需求服务、会话服务、场所和商品工具、RAG 收集当前问题、历史状态、真实候选和软知识；Select 根据 Command、意图、业务过滤、TopK 和推荐评分选择相关内容；Structure 使用 `AgentRequirement`、`AgentCard` 和 JSON 协议组织上下文；Compress 用结构化记忆替代原始历史，把候选限制为 24、证据限制为 Top3，并通过 `cardsForModel` 只保留白名单字段。它的核心不是单纯缩短 Prompt，而是让不同来源具有明确优先级并保护硬事实。

## 16. 推荐源码阅读顺序

1. [AgentController.java](../../backend/src/main/java/com/hm/badminton/controller/agent/AgentController.java#L58)：确认 HTTP 和 SSE 入口。
2. [AgentServiceImpl.chatStream](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L186)：理解异步执行和事件发送。
3. [AgentGraphWorkflow.java](../../backend/src/main/java/com/hm/badminton/service/agent/graph/AgentGraphWorkflow.java#L78)：先画出节点图。
4. [AgentRequirementService.java](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRequirementService.java#L130)：理解结构化需求如何跨轮合并。
5. [AgentServiceImpl.dispatchTools](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L267)：理解意图如何映射到工具分支。
6. `service/agent/tools` 目录：确认每类硬事实来自哪里。
7. [AgentRagService.java](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRagService.java#L78)：追踪文档收集、缓存、混合检索和 TopK。
8. [AgentRecommendationScorer.java](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentRecommendationScorer.java#L43)：理解候选为什么这样排序。
9. [cardsForModel](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L1095) 和 [validateSelection](../../backend/src/main/java/com/hm/badminton/service/agent/impl/AgentServiceImpl.java#L1118)：理解防幻觉闭环。
10. [client.ts](../../frontend/src/api/client.ts#L61) 与 [Nginx 配置](../../deploy/production/nginx/hm-badminton.conf#L78)：从浏览器反向追踪 SSE。

## 17. 练习题

### 17.1 入门练习

1. 用户输入“明天晚上 8 点，200 元以内，离我 5 公里找羽毛球馆”，列出 `AgentRequirement` 应得到的字段。
2. 用户下一轮点击“查看预约规则”，说明哪些字段应保留、哪些字段不应参与本轮查询。
3. 沿 Graph 记录该请求经过的节点，并指出三个并行分支。
4. 解释为什么场所查询与该场所商品查询不能完全并行。

### 17.2 RAG 练习

1. 构造“适合刚学羽毛球的人”和“新手友好”的例子，对比关键词与语义召回。
2. 修改一条博客内容，观察 SHA-256 Key 和 Redis Embedding 缓存如何变化。
3. 模拟 EmbeddingModel 异常，验证系统是否退化为 lexical 检索。
4. 构造与候选无关的高相似度博客，检查实体绑定能否阻止错误证据进入卡片。

### 17.3 稳定性练习

1. 将一个工具延迟超过 5 秒，观察该分支超时后其他分支是否仍能返回。
2. 让模型返回不存在的 `cardId`，验证 `validateSelection` 和本地降级。
3. 开启 Nginx 缓冲，再对比 SSE 阶段消息的到达时间。
4. 页面请求过程中主动离开，观察后端是否继续执行，并设计 `AbortController` 和取消信号。

### 17.4 架构练习

1. 设计 `ContextPacket` 的优先级和 token 配额，输出一次请求的上下文快照。
2. 为 `VIEW_BOOKING_RULES` 增加自动化回归测试，保证不继承场馆推荐条件。
3. 构建 30 条标准问题，分别评估需求抽取、工具路由、RAG 召回和非法 ID 率。
4. 说明什么时候应该引入向量数据库，什么时候 Redis 向量缓存已经足够。

## 18. 总结

约个球 Agent 的核心价值不是“接入了大模型”，而是完成了下面这条可信链路：

```text
Command/自然语言
  -> 规则优先、模型兜底的需求结构化
  -> Redis/MySQL 会话与结构化记忆
  -> Spring AI Alibaba Graph 编排
  -> 真实业务工具提供硬事实
  -> 混合 RAG 提供软知识
  -> 后端统一评分
  -> 模型只选择真实 cardId 并生成解释
  -> 后端校验和确定性降级
  -> SSE 返回阶段与结果
```

从高级 Agent 知识看，项目已经较完整地实践了记忆、上下文工程、工具使用、Graph 编排、混合 RAG、防幻觉和阶段流式交互；通信协议标准化、记忆巩固、完整模块化 RAG、Agentic RL 和系统化评测仍属于后续升级方向。
