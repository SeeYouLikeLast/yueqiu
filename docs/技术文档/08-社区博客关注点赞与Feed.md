# 社区博客、关注、点赞与 Feed

## 1. 功能

- 推荐/球类/关注动态查询。
- 动态详情、用户主页动态、发布和删除。
- 关联场所团购或装备商品。
- 关注/取消关注和点赞/取消点赞。
- 普通博主推模式、大 V 拉模式和滚动分页。
- 软删除后 30 天归档、90 天后分批清理。

## 2. HTTP 接口

| 方法 | 路径 | 登录 | 作用 |
| --- | --- | --- | --- |
| GET | `/api/blogs` | 可选 | `channel/sport/keyword/page/size` 动态列表 |
| GET | `/api/blogs/{id}` | 可选 | 动态详情 |
| GET | `/api/blogs/of/user/{userId}` | 可选 | 某用户动态 |
| GET | `/api/blogs/of/follow` | 是 | `lastId/offset` 关注 Feed 滚动分页 |
| POST | `/api/blogs` | 是 | 发动态 |
| DELETE | `/api/blogs/{id}` | 是 | 删除自己的动态 |
| PUT | `/api/blogs/{id}/like` | 是 | 点赞/取消点赞 |
| PUT | `/api/follows/{id}/{follow}` | 是 | 关注/取消关注 |

## 3. 发布链路

1. 校验标题、正文、运动类型、图片和关联对象。
2. 上传图片已由文件服务完成，博客保存图片 URL/ID。
3. 写入 `blogs`并清理列表、详情和 RAG 相关缓存。
4. 普通博主将 blogId + 时间戳推送到粉丝 `feed:{userId}` ZSet。
5. 大 V 不向大量粉丝全量推送，关注流查询时从数据库拉取再合并。

## 4. 关注 Feed 混合模式

`BlogService.followFeed(maxTime, offset, currentUser)` 的核心步骤：

1. 从当前用户 ZSet 读普通博主推送的 blogId。
2. 查询当前用户关注的大 V，按时间从 MySQL 拉取动态。
3. 合并两路数据，按时间排序并以 blogId 去重。
4. 批量查用户，避免每条动态单独查作者的 N+1 问题。
5. 返回 `minTime + offset`作为下一页游标，处理同一毫秒多条动态。
6. Redis Feed 丢失时使用数据库回源，可用性不完全依赖缓存。

## 5. 点赞

- `blog:liked:{blogId}` ZSet 成员为 userId，score 为点赞时间。
- Redis 判断用户当前状态，MySQL 更新点赞计数。
- 事务完成后清理 `blog:{id}` 详情缓存。
- 更严格的商业系统应增加点赞关系持久化或可重建日志。

## 6. 删除和归档

1. 删除时必须同时匹配 `blogId + userId + status=1`。
2. 逻辑删除设置 `status=0` 和删除时间，避免请求链路上大量物理删除。
3. 定时任务每批 500/1000 条将超过 30 天的数据复制到 `blog_archive`。
4. 超过 90 天的归档删除数据再分批物理清理，批次间暂停，减轻锁和 IO 峰值。

## 7. 数据与代码入口

- MySQL：`blogs`、`blog_archive`、`follows`。
- Redis：`feed:*`、`follows:*`、`blog:liked:*`、`blog:*`、`blog:outbox:*`。
- Service：`BlogService`、`FollowService`、`BlogMaintenanceService`。
- Controller：`BlogController`、`FollowController`。
