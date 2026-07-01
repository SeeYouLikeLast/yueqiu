# 后端 HMDP 分层结构与 API 转发

本文档记录当前项目后端包结构、各目录职责，以及前端 `/api` 在开发和生产环境中的转发规则。后续调整包结构、接口前缀、Nginx 配置时，需要同步维护本文档。

## 1. 当前后端目录结构

```text
backend/
└─ src/main/java/com/hm/badminton/
   ├─ controller/
   ├─ service/
   │  └─ impl/
   ├─ mapper/
   ├─ entity/
   ├─ dto/
   ├─ vo/
   ├─ config/
   ├─ mq/
   ├─ common/
   ├─ utils/
   ├─ interceptor/
   └─ constants/
```

## 2. 各目录放什么

| 目录              | 放什么                                                                                 | 当前示例                                                        |
| --------------- | ----------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| `controller/`   | HTTP 接口层，只处理请求参数、调用 service、返回统一响应，不写复杂业务逻辑                                         | `AuthController`、`SportController`、`VenueProductController` |
| `service/`      | Service 接口层。仿 HMDP / MyBatis-Plus 项目中通常放 `IUserService`、`IVoucherOrderService` 这类接口 | `IAuthService`、`IEquipmentService`、`IVenueProductService`   |
| `service/impl/` | Service 实现层，写业务逻辑、事务、Redis 操作、数据库操作编排                                               | `AuthService`、`VenueProductService`、`SeckillService`        |
| `mapper/`       | 数据访问层。MyBatis / MyBatis-Plus 的 Mapper 接口放这里，复杂查询优先沉到 Mapper                         | `EquipmentOrderMapper`、`VenueOrderMapper`                   |
| `entity/`       | 数据实体或领域数据对象，通常和数据库表、外部 POI、订单等业务数据对应                                                | `Product`、`VenueOrder`、`SportType`、`FileMetadata`           |
| `dto/`          | Data Transfer Object，请求参数、登录用户摘要、服务间传递对象                                            | `LoginUser`                                                 |
| `vo/`           | View Object，专门返回给前端展示的对象。复杂页面推荐使用 VO，不直接返回 Entity                                   | 当前保留 `package-info.java`                                    |
| `config/`       | Spring 配置、第三方客户端配置、属性配置                                                             | `WebConfig`、`MinioConfig`、`AmapProperties`                  |
| `mq/`           | 消息队列消费者、异步消息入口；生产消息仍由对应业务 Service 编排                                                | `SeckillOrderConsumer`                                      |
| `common/`       | 通用响应、异常、分页、全局异常处理                                                                   | `ApiResponse`、`BusinessException`、`PageResult`              |
| `utils/`        | 工具类、上下文工具、ID 生成器                                                                    | `UserContext`、`IdGenerator`                                 |
| `interceptor/`  | Spring MVC 拦截器、登录态刷新、权限校验                                                           | `RefreshTokenInterceptor`、`LoginInterceptor`                |
| `constants/`    | Redis key 前缀、缓存 TTL、业务常量、状态码常量                                                      | `RedisConstants`                                            |

## 3. 当前与 HMDP 的差异

当前项目已经调整为 HMDP 风格分层目录，并已引入 `MyBatis-Plus + Mapper + Service 接口`。

HMDP 课程项目常见结构是：

```text
controller -> service interface -> service.impl -> mapper -> entity
```

当前项目中的 Controller 已改为依赖 `service/` 下的接口，`service/impl/` 下的类负责实现接口：

```text
controller -> IxxxService -> service.impl.XxxService -> mapper/entity
```

已经落地的 MyBatis / MyBatis-Plus 改动：

```text
pom.xml -> mybatis-plus-spring-boot3-starter
HmBadmintonApplication -> @MapperScan("com.hm.badminton.mapper")
mapper/EquipmentOrderMapper -> 装备订单查询
mapper/VenueOrderMapper -> 场所订单查询
mapper/UserMapper -> 用户账号表 MyBatis-Plus 访问
mapper/FollowMapper -> 关注关系 MyBatis-Plus 访问
mapper/BlogMapper -> 社区博客 MyBatis-Plus 访问
mapper/EquipmentMapper -> 装备商品、购物车、装备订单与库存写入
mapper/VenueProductMapper -> 场所售卖项目、库存、场所订单
mapper/VenueMapper -> 本地场馆、评价、收藏
mapper/SeckillMapper -> 秒杀活动、秒杀订单、库存扣减
mapper/SocialMapper -> 球友资料、约球活动、成员关系
mapper/FileMetadataMapper -> MinIO 文件元数据
mapper/AdminMapper -> 后台统计
mq/SeckillOrderConsumer -> RocketMQ 秒杀订单消费者，异步落库
```

当前业务侧已移除旧的模板式 JDBC 访问，下单、扣库存、场所售卖、秒杀、球友、文件元数据和后台统计链路统一通过 MyBatis-Plus Service 或 Mapper 访问数据库。秒杀采用 `Redis Lua + Redisson + RocketMQ + MySQL 唯一索引`，库存预扣和消息发送由 `SeckillService` 编排，订单最终落库由 `SeckillOrderConsumer` 消费后调用 `SeckillOrderMessageService` 完成。

登录态采用 HMDP 风格双拦截器：

```text
RefreshTokenInterceptor -> 拦截所有路径，有 token 就解析 Redis 登录态并刷新 TTL
LoginInterceptor        -> 拦截需要登录的路径，没有登录态就返回 401
```

登录 token Redis TTL 为 120 分钟，访问接口时自动续期。

## 4. `/api` 转发规则

现在采用 HMDP / Nginx 常见方式：

```text
浏览器请求：/api/sports
Vite 或 Nginx：剥离 /api
后端实际收到：/sports
```

也就是说：

- 前端代码继续请求 `/api/xxx`。
- 后端 Controller 不再写 `/api` 前缀。
- 开发环境由 Vite proxy 剥离 `/api`。
- 生产环境由 Nginx 剥离 `/api`。

## 5. 开发环境 Vite 配置

`frontend/vite.config.ts`：

```ts
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8088',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
```

示例：

```text
前端 fetch('/api/sports')
Vite 转发 http://localhost:8088/sports
后端 @RequestMapping("/sports")
```

## 6. 生产环境 Nginx 配置

前端构建后部署到 Nginx，接口仍请求 `/api/xxx`：

```nginx
server {
    listen 80;
    server_name localhost;

    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        rewrite ^/api/?(.*)$ /$1 break;
        proxy_pass http://host.docker.internal:8088;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

如果 Nginx 不是跑在 Docker 容器里，而是和 Spring Boot 后端直接部署在同一台机器上，可以把 `proxy_pass` 改成 `http://127.0.0.1:8088`。

示例：

```text
浏览器：GET /api/places/nearby
Nginx rewrite 后：GET /places/nearby
Spring Boot：@RequestMapping("/places") + @GetMapping("/nearby")
```

## 7. 直接调后端时的注意事项

现在如果绕过 Vite/Nginx，直接访问 Spring Boot 端口，需要去掉 `/api`：

```text
正确：http://localhost:8088/sports
错误：http://localhost:8088/api/sports
```

通过前端开发服务器访问时仍然带 `/api`：

```text
正确：http://localhost:5173/api/sports
```

因为 Vite 会把 `/api/sports` 转发成后端 `/sports`。
