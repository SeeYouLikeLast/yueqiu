# 文件上传与 MinIO 对象存储

## 1. 存储分工

- MySQL `file_metadata`：文件名、MIME、大小、bucket、object key、业务类型、业务 id、所有者和状态。
- MinIO：图片和附件的二进制本体。
- Nginx：公开对象 `/objects/...` 的同源代理和长缓存。

数据库不保存大二进制字段，MinIO 也不承担业务归属查询。

## 2. HTTP 接口

| 方法 | 路径 | 登录 | 作用 |
| --- | --- | --- | --- |
| POST | `/api/files/upload` | 是 | multipart 上传，支持 `bizType/bizId` |
| GET | `/api/files/{id}` | 可选 | 元数据详情 |
| GET | `/api/files` | 可选 | 按业务和所有者查询 |
| GET | `/api/files/{id}/download` | 可选 | Java 下载兼容链路 |
| GET | `/api/files/{id}/presigned-url` | 可选 | MinIO 临时签名 URL |
| DELETE | `/api/files/{id}` | 是 | 所有者删除 |

## 3. 上传链路

1. Controller 强制登录，取得 ownerUserId。
2. Service 校验空文件、大小、MIME 和允许的后缀。
3. 使用服务端生成的对象名，不直接信任客户端文件名作为路径。
4. 先上传 MinIO，再写 `file_metadata`。
5. 数据库写入失败时 catch 中尝试删除已上传对象，减少孤儿文件。
6. 返回可持久引用的文件 id 和公开路径。

## 4. 删除链路

1. 查元数据。
2. 比较 `owner_user_id` 与当前用户，防止用户通过猜 id 删除他人文件。
3. 删除 MinIO 对象。
4. 更新/删除 MySQL 元数据。

MySQL `@Transactional` 不能自动回滚 MinIO。当前通过补偿删除降低不一致；更完整的方案是对象状态机 + 定时孤儿扫描。

## 5. 图片展示策略

- 真实用户上传的头像/动态图片使用 MinIO。
- 演示商品和社区占位图由前端绘制，不为每条演示数据存大量 MinIO 图片。
- 真实高德场所图使用高德返回的照片。
- 公开图片使用 `/objects/hm-badminton/...`，由 Nginx 直达 MinIO，不经过 Java 转发。
- 列表 `<img loading="lazy">` 延迟加载屏幕外图片。

## 6. 安全和部署

- MinIO 9000/9001 只绑定 `127.0.0.1`，不向公网开放。
- access key/secret key 只位于服务器环境文件。
- Nginx 限制 `client_max_body_size`，Spring 再限制 multipart 大小。
- 敏感文件不应通过公开 `/objects` 路径输出，应使用授权下载或短期签名 URL。

## 7. 代码入口

`FileController`、`FileStorageService`、`FileMetadataMapper`、`MinioConfig`、`MinioProperties`、Nginx `/objects/` location。
