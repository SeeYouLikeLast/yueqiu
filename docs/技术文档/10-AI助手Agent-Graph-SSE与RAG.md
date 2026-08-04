# AI 助手 Agent、Graph、SSE 与 RAG

## 1. 功能

AI 助手围绕四类业务回答：

- 附近场所和可购买场馆项目。
- 可加入的约球活动。
- 真实可购买装备。
- 预约规则、博客/评价/装备心得等软知识。

原则：价格、库存、时段、人数由真实业务工具提供，大模型只负责需求补全和解释，不允许自由编造商品。

## 2. HTTP 接口

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/api/agent/status` | AI 开关状态 |
| POST | `/api/agent/chat` | 同步完整回答 |
| POST | `/api/agent/chat/stream` | SSE 阶段事件 + 最终结果 |
| GET | `/api/agent/conversations` | 登录或匿名会话历史 |
| GET | `/api/agent/conversations/{id}/messages` | 会话消息 |
| DELETE | `/api/agent/conversations/{id}` | 删除当前身份拥有的会话 |

## 3. 意图识别架构

```text
快捷按钮 -> AgentCommand 结构化指令 -> 直接执行
自由文本 -> 规则快速提取
          -> 复杂/低置信度时调模型输出 JSON
          -> 后端校验并合并会话需求
```

`AgentRequirement` 保存运动、日期、时间、预算、距离、水平和查询目标，不再只把历史文本用分号拼接。

独立 Command（例如“查预约规则”）会清理不相关的时间、距离和团购约束，避免误用上一轮记忆。

## 4. Graph 工作流

`AgentGraphWorkflow` 使用确定性节点编排：

1. **prepare**：限流、会话归属、读取结构化记忆。
2. **extract**：规则/Command/模型提取需求。
3. **plan**：根据意图选择需要的工具，不是每轮调所有接口。
4. **tools**：并行查场所、场馆商品、活动和装备。
5. **rank**：后端结构化打分、扩大范围和候选裁剪。
6. **rag**：按需检索博客、场馆评价和装备心得。
7. **answer**：将已验证卡片交给 DashScope 生成推荐解释。
8. **validate**：模型返回 `selectedCardIds + explanation`，后端丢弃不存在 id。
9. **persist**：保存问题、回答、卡片和最新需求。

## 5. 业务工具

| 工具 | 数据来源 | 硬事实 |
| --- | --- | --- |
| `PlaceAgentTool` | 高德 API | 场所、距离、地址、营业信息 |
| `VenueProductAgentTool` | MySQL | 场馆商品、价格、日期、时段、库存 |
| `ActivityAgentTool` | MySQL | 可加入活动和剩余人数 |
| `EquipmentAgentTool` | MySQL | 装备、价格、库存、适用水平 |
| `BookingRuleAgentTool` | 规则配置/业务 | 预约和退改规则 |
| `UserPreferenceAgentTool` | 用户资料/会话 | 运动和水平偏好 |

## 6. 时段和扩圈规则

- 晚间需求只选与目标时段完全匹配的真实 inventory。
- 没有套餐时才回退到真实“单场 1 小时”项目，不建议用上午券协商晚上使用。
- 附近场所数量不足时后端自动扩大半径，并在回答中告知用户。
- 装备不受地理距离影响，“扩大附近范围”不会被转成场馆商品查询。

## 7. 推荐排序

后端先按可配置指标打分，模型再解释：

- 场所：距离、可预约、价格、评分/评价数、设施和用户偏好。
- 装备：预算、运动、水平、评分/销量、库存和活动价。
- 约球：时间、水平、距离、剩余人数、发起人可信度和费用。

## 8. SSE 流式交互

`chat/stream` 使用 `SseEmitter(120s)`。后台在独立执行器中运行 Graph，前端依次收到“理解需求”、“查询场所”、“正在排序”和最终 `result`，避免长时间空白等待。当前为阶段流，不是逐 token 文本流。

## 9. 会话和限流

- 登录用户：MySQL `agent_conversation/agent_message` 持久化。
- 游客：通过匿名 Cookie 隔离，Redis 保存 7 天历史，支持查看和删除。
- 结构化需求：`agent:conversation:requirement:*`，默认 60 分钟。
- 限流：登录 userId 和 client IP 双维度 Redis 计数，默认每分钟 10 次。

## 10. RAG

RAG 检索博客训练心得、场馆评价和装备体验。原文在 MySQL，Embedding 结果缓存到 Redis；Embedding 不可用时回退关键词检索。

RAG 只提供软知识，不替代价格、库存、时段和活动人数查询。

## 11. 降级与当前限制

- DashScope 不可用时可使用本地结构化摘要，真实卡片仍可展示。
- 工具超时会以空候选降级，需持续监控高德、MySQL 和模型的耗时/失败率。
- Graph 是 Java 中的确定性工作流，并非允许模型无限自主调用。
- 所有购买、加购和加入活动都由前端二次确认后调真实写接口。

## 12. 代码入口

`AgentController`、`AgentServiceImpl`、`AgentGraphWorkflow`、`AgentRequirementService`、`AgentRequirementModelExtractor`、`AgentRecommendationScorer`、`AgentRagService`、`service/agent/tools/*`。
