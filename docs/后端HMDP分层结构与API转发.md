# 后端 HMDP 分层结构与 API 转发

本文档描述当前代码结构和 `/api` 转发规则。接口清单见 [项目功能与接口总览.md](项目功能与接口总览.md)。

## 1. 分层原则

项目采用仿 HMDP 的分层：

```text
Controller -> Service 接口 -> ServiceImpl -> Mapper / Entity -> MySQL
                         -> Redis / MinIO / RocketMQ / 高德 / DashScope
```

| 类型 | 作用 | 约束 |
| --- | --- | --- |
| Entity | 数据表或领域实体 | 不直接作为复杂页面返回模型 |
| DTO | HTTP 请求和层间输入 | Controller 的请求对象放这里 |
| VO | 面向前端的展示结构 | 统一屏蔽库存、内部 id、数据库字段等不应暴露的信息 |
| Mapper | MyBatis-Plus 数据访问 | 简单 CRUD 使用 `BaseMapper`，复杂 SQL 使用注解或 XML Mapper |
| Service | 业务接口 | Controller 只依赖接口 |
| ServiceImpl | 事务、缓存、远程调用与业务编排 | `@Transactional` 放在这一层，不包裹长时间第三方调用 |

## 2. 当前目录

```text
backend/src/main/java/com/hm/badminton/
├─ controller/
│  ├─ agent/      AI 助手
│  ├─ auth/       注册、登录、资料、定位
│  ├─ catalog/    球类、分类、统一商品查询
│  ├─ community/  博客、关注
│  ├─ file/       文件上传与元数据
│  ├─ place/      高德场所、评价
│  ├─ social/     球友、约球
│  └─ trade/      购物车、支付、订单、秒杀
├─ service/       与 controller 同业务域分包，接口 + impl
├─ mapper/        agent/auth/community/file/place/social/trade
├─ entity/        MyBatis-Plus 实体
├─ dto/           auth/community/social/trade/agent 等请求与传输对象
├─ vo/            EquipmentVO、VenueItemVO、OrderCardVO 等展示对象
├─ config/        Web、CORS、MinIO、高德、Agent 属性配置
├─ interceptor/   RefreshTokenInterceptor、LoginInterceptor
├─ constants/     RedisConstants、AgentConstants、TradeType 等
├─ mq/            秒杀订单 Producer/Consumer 入口
├─ common/        ApiResponse、PageResult、BusinessException、全局异常处理
├─ utils/         CacheClient、UserContext、LocationContextResolver、IdGenerator 等
└─ task/          定时清理等后台任务
```

### 关键职责

- `controller`：校验输入、获取登录用户、调用 Service、返回 `ApiResponse`；不拼接复杂 SQL 或处理事务。
- `service/*/impl`：统一处理业务规则、缓存失效、事务边界及多资源编排。
- `mapper`：全部数据库读写使用 MyBatis-Plus/Mapper；项目已移除业务链路中的 `JdbcTemplate`。
- `mq`：只处理消息收发和幂等消费；秒杀业务规则仍由 `ISeckillService` 编排。
- `utils/LocationContextResolver`：按 `X-Location-City`、登录态城市、默认西安的优先级解析城市，避免中文城市重复进入 URL。

## 3. HMDP 风格的登录链路

```text
RefreshTokenInterceptor（所有路径）
  -> 解析 Authorization: Bearer token
  -> 从 Redis Hash 读取必要登录摘要
  -> 写入 ThreadLocal UserContext 并刷新 TTL

LoginInterceptor（仅受保护路径）
  -> UserContext 无用户时直接返回 401
```

受保护路径包括：个人资料和定位、关注流、购物车、支付、订单、关注、秒杀下单、编辑球友资料、加入活动、文件上传/删除等。

`UserContext.requireUserId()` 仍保留在 Service/Controller 入口：它是防御式校验，避免未来新增路由或测试绕过拦截器后产生空用户写入。

## 4. MyBatis-Plus 使用方式

```java
public interface UserMapper extends BaseMapper<UserAccount> {
}

UserAccount user = userMapper.selectById(userId);
List<UserAccount> users = userMapper.selectList(
    new LambdaQueryWrapper<UserAccount>()
        .eq(UserAccount::getStatus, 1)
        .orderByDesc(UserAccount::getCreatedAt));
```

- 单表 CRUD：`BaseMapper + LambdaQueryWrapper/LambdaUpdateWrapper`。
- 多表聚合、库存条件扣减、Feed、场所顺序映射：Mapper 中显式 SQL，便于保证性能和可读性。
- 所有实体统一用 Lombok `@Data`；请求对象和复杂页面返回优先 DTO/VO，不在 Controller 内定义内部请求类。

## 5. `/api` 转发

前端始终调用 `/api/...`，后端 Controller 不写 `/api` 前缀。

```text
浏览器 /api/sports
  -> 开发：Vite rewrite 成 http://localhost:8088/sports
  -> 生产：Nginx rewrite 成 http://127.0.0.1:8088/sports
```

### 开发环境

`frontend/vite.config.ts`：

```ts
proxy: {
  '/api': {
    target: 'http://localhost:8088',
    changeOrigin: true,
    rewrite: (path) => path.replace(/^\/api/, '')
  },
  '/objects': {
    target: 'http://localhost:9000',
    changeOrigin: true,
    rewrite: (path) => path.replace(/^\/objects/, '')
  }
}
```

### 生产环境

Nginx 负责 TLS、SPA 回退、`/api/` 代理和 `/objects/` MinIO 直连：

```text
HTTPS Browser
  -> Nginx /              Vue dist
  -> Nginx /api/          Spring Boot 127.0.0.1:8088
  -> Nginx /objects/      MinIO 127.0.0.1:9000
```

代理会带上 `X-Real-IP`、`X-Forwarded-For`、`X-Forwarded-Proto`、`X-Forwarded-Host` 和 `X-Forwarded-Port`。Spring 的 `forward-headers-strategy=framework` 用于正确识别原始 HTTPS，避免同源 POST 被误判为 CORS 请求。

生产 CORS 白名单由环境变量 `CORS_ALLOWED_ORIGINS` 配置；不要在 Controller 里零散添加 `@CrossOrigin`。

## 6. 位置与 URL 约定

- 精确附近搜索必须传 `lng`、`lat`，它们决定高德搜索中心，不能为了缩短 URL 删除。
- 城市是上下文，不再作为查询参数传递；前端把 URI 编码后的城市放入 `X-Location-City`。
- 评价接口示例：`GET /api/venues/reviews/badminton/1`，无 `city` 查询参数。
- 自定义请求头能缩短 URL，不能隐藏信息；浏览器开发者工具仍可查看请求头。

## 7. 修改规则

1. 新接口先放入对应业务域 Controller，再定义 DTO、Service 接口、ServiceImpl 和 Mapper。
2. 写操作必须明确登录校验、事务边界、缓存失效和幂等规则。
3. 新 Redis Key 必须加入 `RedisConstants` 与 `数据存储分工-MySQL-Redis-MinIO.md`。
4. 改动 `/api`、Nginx、端口或 CORS 时同步更新本文件和生产部署文档。
