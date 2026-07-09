# 约个球 AI 助手 Agent 实施规划

## 1. 功能定位

本项目计划新增一个面向手机端的 `约个球 AI 助手`。它不是普通闲聊机器人，而是一个可以调用平台业务能力的 Agent，用来帮助用户完成：

- 选择附近合适的运动场所
- 查询当前是否有可加入的约球活动
- 推荐可购买的装备或秒杀装备
- 根据用户位置、城市、运动类型、预算、时间、水平等条件做综合推荐
- 返回可点击的业务卡片，例如查看场所、加入活动、加入购物车、查看装备

核心目标是：用户只需要用自然语言表达需求，AI 助手自动调用后端工具查询真实业务数据，再把结果组织成适合手机端展示的推荐结果。

## 2. 技术选型

### 2.1 当前项目基础

项目现有技术栈：

- 后端：Spring Boot 3.5.7、Java 21
- 数据访问：MyBatis-Plus、MySQL
- 缓存与高并发：Redis、Redisson、Lua
- 异步秒杀订单：RocketMQ
- 文件存储：MinIO
- 地理位置与真实场所：高德 API
- 前端：Vue 3、Vite、TypeScript、lucide-vue-next

AI 助手应尽量复用现有业务能力，不重新绕开业务层直接访问数据库。

### 2.2 Agent 框架

采用 `Spring AI Alibaba`。

推荐原因：

- 与 Spring Boot 项目天然适配
- 支持 DashScope / 通义千问 / 百炼生态
- 支持 ChatClient、Tool Calling、Agent Framework、Graph、MCP 等能力
- 后续可以平滑演进为多步骤 Agent 或工作流 Agent

第一阶段建议使用：

- `spring-ai-alibaba-starter-dashscope`
- `ChatClient`
- Tool Calling

第二阶段再考虑：

- Agent Framework / ReactAgent
- Graph 工作流
- RAG 检索博客、评价、装备心得

### 2.3 模型选择

推荐模型：

- 开发阶段：`qwen-plus`
- 复杂推理或演示阶段：`qwen-max`
- 成本敏感阶段：可切换到更便宜的兼容模型

模型调用通过配置项控制，避免写死在代码中。

## 3. Maven 依赖规划

由于当前项目是 Spring Boot 3.5.7，不建议直接使用依赖 Spring Boot 4 的 Spring AI Alibaba 2.x 版本。

建议先尝试：

```xml
<properties>
    <spring-ai-alibaba.version>1.1.0.0</spring-ai-alibaba.version>
</properties>

<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    <version>${spring-ai-alibaba.version}</version>
</dependency>
```

如果与 Spring Boot 3.5.7 存在兼容问题，则降级到 Spring AI Alibaba `1.0.0.2` 文档线。

注意：

- 不建议第一版引入过多 Agent 依赖
- 先跑通 ChatClient + Tool Calling
- 再逐步引入 Agent Framework / Graph

## 4. 配置设计

在 `backend/src/main/resources/application.yml` 中新增：

```yaml
spring:
  ai:
    model:
      chat: dashscope
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY:}
      chat:
        options:
          model: ${AI_MODEL:qwen-plus}
          temperature: 0.3
          max-tokens: 1200

hm:
  agent:
    enabled: true
    max-history-messages: 10
    max-tool-calls: 6
    memory-ttl-minutes: 60
    rate-limit-per-minute: 10
```

本地启动前设置环境变量：

```powershell
$env:AI_DASHSCOPE_ENABLED="true"
$env:AI_DASHSCOPE_API_KEY="你的百炼或 DashScope API Key"
$env:AI_MODEL="qwen-plus"
```

如果希望写入启动脚本，可以在 `scripts/start-dev.ps1` 中读取 `.env.local`，但不要把真实 Key 提交到 Git。

当前代码默认：

- `AI_DASHSCOPE_ENABLED=false`
- 未配置 Key 时，AI 助手仍可使用本地业务工具模式
- 配置 `AI_DASHSCOPE_ENABLED=true` 且设置 `AI_DASHSCOPE_API_KEY` 后，才会真正调用 DashScope 模型

开发时推荐先用本地工具模式验证页面和业务卡片，再打开真实模型调用。

## 5. 后端目录结构

建议新增以下结构：

```text
backend/src/main/java/com/hm/badminton/
├─ controller/agent/
│  └─ AgentController.java
├─ service/agent/
│  ├─ IAgentService.java
│  ├─ impl/
│  │  └─ AgentServiceImpl.java
│  └─ tools/
│     ├─ PlaceAgentTool.java
│     ├─ VenueProductAgentTool.java
│     ├─ ActivityAgentTool.java
│     ├─ EquipmentAgentTool.java
│     └─ UserPreferenceAgentTool.java
├─ dto/agent/
│  ├─ AgentChatRequest.java
│  ├─ AgentChatResponse.java
│  ├─ AgentCard.java
│  ├─ AgentAction.java
│  └─ AgentToolResult.java
├─ entity/
│  ├─ AgentConversation.java
│  └─ AgentMessage.java
├─ mapper/agent/
│  ├─ AgentConversationMapper.java
│  └─ AgentMessageMapper.java
├─ config/
│  ├─ AgentProperties.java
│  └─ AgentConfig.java
└─ constants/
   └─ AgentConstants.java
```

目录职责：

- `controller/agent`：对外提供 AI 助手接口
- `service/agent`：编排模型调用、上下文、工具调用结果
- `service/agent/tools`：封装可被模型调用的业务工具
- `dto/agent`：前后端交互结构
- `entity`：聊天会话、消息记录实体
- `mapper/agent`：MyBatis-Plus 数据访问
- `config`：模型客户端、Agent 配置
- `constants`：Redis Key、角色、卡片类型、动作类型

## 6. 接口设计

第一阶段接口：

```http
POST   /agent/chat
GET    /agent/conversations
GET    /agent/conversations/{id}/messages
DELETE /agent/conversations/{id}
```

后续支持流式输出：

```http
POST /agent/chat/stream
```

### 6.1 聊天请求

```json
{
  "conversationId": 1,
  "message": "今晚西安附近有没有能打羽毛球的场？最好 50 元以内",
  "sportCode": "badminton",
  "city": "西安市",
  "lng": 108.94,
  "lat": 34.34
}
```

说明：

- `conversationId` 可以为空，为空时创建新会话
- `sportCode` 可以为空，Agent 可根据用户语义判断
- `city/lng/lat` 优先使用前端定位，也可以从登录用户资料兜底

### 6.2 聊天响应

```json
{
  "conversationId": 1,
  "answer": "我帮你筛了附近适合今晚打羽毛球的场所，优先选择距离近、可购买、价格低的。",
  "cards": [
    {
      "type": "place",
      "title": "创汇乒乓馆",
      "subtitle": "距离 1.2km，今晚可订",
      "coverUrl": "/api/files/31/download",
      "price": "¥39 起",
      "tags": ["羽毛球", "可核销", "本地演示"],
      "action": {
        "type": "open_place",
        "id": "amap_xxx",
        "requireConfirm": false
      }
    }
  ],
  "quickReplies": [
    "只看 50 元以内",
    "帮我找能加入的约球",
    "推荐新手球拍"
  ]
}
```

## 7. Agent 工具设计

大模型不能直接访问数据库，只能调用后端定义好的白名单工具。

### 7.1 场所查询工具

工具名：

```text
searchNearbyPlaces
```

参数：

```json
{
  "sportCode": "badminton",
  "city": "西安市",
  "lng": 108.94,
  "lat": 34.34,
  "radius": 5000,
  "keyword": "羽毛球"
}
```

作用：

- 查询高德附近真实场所
- 复用当前 `PlaceController` 和 `AmapPlaceService`
- 返回距离、名称、地址、封面、营业信息等

### 7.2 场馆商品工具

工具名：

```text
searchVenueProducts
```

参数：

```json
{
  "sportCode": "badminton",
  "placeRank": 1,
  "productType": "TIME_PACKAGE",
  "maxPrice": 50
}
```

作用：

- 查询场馆团购、单场时段、私教课
- 复用当前 `VenueItemService`
- 返回可购买项目卡片

### 7.3 约球活动工具

工具名：

```text
searchJoinableActivities
```

参数：

```json
{
  "sportCode": "badminton",
  "city": "西安市",
  "startTime": "2026-07-09T19:00:00",
  "endTime": "2026-07-09T21:00:00"
}
```

作用：

- 查询可加入活动
- 过滤已满员活动
- 过滤自己创建或自己已加入的活动
- 复用当前 `SocialService`

### 7.4 装备推荐工具

工具名：

```text
searchEquipment
```

参数：

```json
{
  "sportCode": "badminton",
  "categoryId": 1,
  "keyword": "新手球拍",
  "maxPrice": 300
}
```

作用：

- 查询普通装备商品
- 支持运动类型、分类、预算、关键词
- 返回装备卡片

### 7.5 秒杀装备工具

工具名：

```text
searchSeckillEquipment
```

参数：

```json
{
  "sportCode": "badminton"
}
```

作用：

- 查询当前可抢购装备
- 只返回展示信息
- 不直接执行抢购

### 7.6 用户偏好工具

工具名：

```text
getCurrentUserPreference
```

作用：

- 获取当前登录用户的城市、水平、常打运动、可约时间、经纬度
- 用于个性化推荐

## 8. 写操作安全策略

Agent 不允许自动替用户完成敏感操作。

禁止模型直接执行：

- 加入活动
- 加入购物车
- 下单
- 支付
- 抢购

正确流程：

1. Agent 推荐结果
2. 前端展示按钮
3. 用户点击确认
4. 前端调用原有业务接口

示例：

```json
{
  "type": "activity",
  "title": "今晚 19:00 羽毛球约局",
  "action": {
    "type": "join_activity",
    "id": 12,
    "requireConfirm": true
  }
}
```

这样可以避免大模型误操作，也能保持原业务接口的权限校验、库存校验和事务逻辑。

## 9. 数据库设计

新增会话表：

```sql
create table agent_conversation (
    id bigint primary key,
    user_id bigint not null,
    title varchar(128) not null,
    status tinyint not null default 1,
    created_at datetime not null,
    updated_at datetime not null,
    index idx_agent_conversation_user (user_id, updated_at)
);
```

新增消息表：

```sql
create table agent_message (
    id bigint primary key,
    conversation_id bigint not null,
    user_id bigint not null,
    role varchar(32) not null,
    content text,
    cards_json json,
    tool_name varchar(64),
    tool_result_json json,
    created_at datetime not null,
    index idx_agent_message_conversation (conversation_id, created_at)
);
```

字段说明：

- `role=user`：用户消息
- `role=assistant`：AI 回复
- `role=tool`：工具调用结果
- `cards_json`：前端业务卡片
- `tool_result_json`：工具原始结果，便于排查 Agent 推荐依据

## 10. Redis 设计

Redis 只保存短期上下文和限流信息：

```text
agent:memory:{userId}:{conversationId}
agent:rate:user:{userId}
agent:rate:ip:{ip}
```

建议：

- 会话短期上下文 TTL：60 分钟
- 用户限流：每分钟 10 次
- IP 限流：每分钟 20 次
- 历史上下文最多带 10 条消息

Redis Key 后续应统一写入：

```text
backend/src/main/java/com/hm/badminton/constants/RedisConstants.java
```

## 11. Prompt 设计

系统提示词建议：

```text
你是“约个球”平台的 AI 助手。
你的任务是帮助用户选择运动场所、查找可加入的约球活动、推荐可购买装备。
你不能编造场所、价格、库存、活动人数。
涉及场所、活动、装备时，必须优先调用工具查询真实业务数据。
下单、加入活动、加入购物车、抢购、支付必须由用户点击确认，不能自动替用户完成。
回答要简洁，适合手机端展示。
如果用户条件不完整，优先根据用户资料和当前位置推断；仍无法判断时，再向用户追问一个最关键的问题。
```

## 12. 前端设计

### 12.1 入口

推荐两种方案：

方案一：底部导航新增 `助手`

```text
首页 / 社区 / 约球 / 装备 / 我的 / 助手
```

方案二：首页右下角悬浮 AI 按钮

```text
AI 助手
```

第一版推荐使用底部导航，入口稳定，展示更完整。

### 12.2 聊天页面

页面结构：

```text
顶部：约个球助手
中间：聊天记录
卡片：场所卡、活动卡、装备卡
底部：输入框 + 发送按钮
快捷问题：
  - 今晚附近能打球吗
  - 帮我找能加入的局
  - 推荐新手装备
  - 50 元以内的场地
```

### 12.3 卡片动作

Agent 返回的卡片不直接执行业务，而是交给前端路由或原接口：

- `open_place`：打开场所详情
- `open_equipment`：打开装备详情或装备列表并高亮
- `open_activity`：打开活动详情
- `add_cart`：弹确认后加入购物车
- `join_activity`：弹确认后加入活动

## 13. 实施阶段

### 第一阶段：基础聊天跑通

目标：

- 添加 Spring AI Alibaba 依赖
- 配置 DashScope Key
- 新增 `AgentController`
- 新增 `AgentService`
- 前端新增 AI 助手页面
- 能完成普通聊天

不做复杂工具，不做下单动作。

### 第二阶段：接入工具调用

目标：

- 接入场所查询工具
- 接入场馆商品工具
- 接入约球活动工具
- 接入装备推荐工具
- 返回业务卡片

用户可以问：

```text
今晚附近有没有能打羽毛球的地方？
```

Agent 能返回真实场所和可购买项目。

### 第三阶段：会话记忆和限流

目标：

- MySQL 保存会话和消息
- Redis 保存短期上下文
- 用户和 IP 限流
- 登录用户个性化推荐

### 第四阶段：安全动作闭环

目标：

- 支持推荐后点击加入购物车
- 支持推荐后点击加入活动
- 支持推荐后跳转场所或装备
- 所有写操作都由前端二次确认

### 第五阶段：Agent Framework / Graph

目标：

- 支持多步骤规划
- 支持先分析需求，再查工具，再排序推荐
- 例如：

```text
用户：今晚 7 点我想在西安附近打羽毛球，预算 50 左右，有没有场地或者能加入的局？

Agent：
1. 识别运动类型：羽毛球
2. 识别时间：今晚 19:00
3. 识别预算：50 元
4. 查询附近场所
5. 查询可购买场馆商品
6. 查询可加入约球活动
7. 综合排序返回
```

### 第六阶段：RAG 检索增强

目标：

- 检索博客内容
- 检索场馆评价
- 检索装备心得
- 回答更主观的问题：

```text
这个球拍适合新手吗？
这个场馆环境怎么样？
周末这个地方人多吗？
```

此阶段再考虑引入向量数据库。

## 14. 推荐最终效果

用户输入：

```text
今晚 7 点我想在西安附近打羽毛球，预算 50 左右，有没有场地或者能加入的局？
```

Agent 返回：

```text
我建议你优先看这 2 个选择：

1. 创汇羽毛球馆
距离 1.4km，今晚可订，单人畅打 ¥39。

2. 西安运动公园羽毛球局
今晚 19:00-21:00，目前 3/4 人，可以加入。

如果你还缺球拍，我也找到了 2 个适合新手的羽毛球拍。
```

下方展示：

- 场所卡片
- 团购卡片
- 活动卡片
- 装备卡片

用户可以直接点击：

- 查看场所
- 加入活动
- 加入购物车
- 查看装备

## 15. 注意事项

- AI 不能编造业务数据，必须通过工具查询
- AI 不直接下单、不直接支付、不直接抢购
- 工具返回字段要精简，避免把无关数据暴露给模型
- 对模型输出要做结构化解析，前端尽量依赖 `cards`，不要从自然语言里解析业务动作
- Key 必须走环境变量，不提交到 Git
- 第一版不要急着做 RAG，先把结构化工具调用做好

## 16. 参考资料

- Spring AI Alibaba GitHub：https://github.com/alibaba/spring-ai-alibaba
- Spring AI Alibaba 快速开始：https://java2ai.com/en/docs/1.0.0.2/tutorials/starters-and-quick-guide/
- Spring AI Alibaba DashScope 文档：https://java2ai.com/integration/chatmodels/dashScope
- Maven Central spring-ai-alibaba-starter-dashscope：https://central.sonatype.com/artifact/com.alibaba.cloud.ai/spring-ai-alibaba-starter-dashscope
