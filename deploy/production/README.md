# 约个球生产部署

该目录只服务于阿里云 ECS 等 Linux 服务器部署；`../docker-compose.yml` 继续保留给 Windows + WSL 本地开发。

## 部署结构

```text
Internet -> Nginx :80/:443 -> Vue dist + /api -> Spring Boot :8088 (127.0.0.1)
                                                -> Docker: MySQL / Redis / MinIO
                                                -> RocketMQ（仅完整模式启用）
```

除 Nginx 的 `80/443` 外，所有服务端口均绑定为 `127.0.0.1`，不会被公网直接访问。MinIO 文件通过 Nginx 的 `/objects/` 输出，管理端 `9001` 仅可通过 SSH 隧道访问。

## 2GB 轻量演示模式

默认 `docker compose up -d` 不启动 RocketMQ，配合 `SPRING_PROFILES_ACTIVE=prod,lite` 可在 2GB ECS 展示定位、场所、团购、装备、购物车、约球、社区、AI 和 Redis 秒杀。

- 秒杀默认优先使用 MQ 异步落库；轻量模式因未部署 MQ 自动降级为同步落库。MQ 投递失败时也会使用相同订单号同步补偿，消费者可幂等忽略后续重复消息。
- MySQL、Redis、MinIO 已降低缓存和容器内存上限。
- 不要在 2GB ECS 上运行 `npm run build` 或长期保持 VS Code Remote；请在本机构建前端后上传 `dist`。
- 演示图片约 11MB，影响主要是磁盘和网络，不是本次 OOM 的核心原因。

### 可选的轻量 RocketMQ

服务器已配置至少 4GB Swap，且空闲内存足够时，可以启动受限资源的单节点 RocketMQ：

```bash
cd /opt/hm-badminton/deploy/production
docker compose --profile mq --env-file .env up -d rocketmq-namesrv rocketmq-broker
docker compose --profile mq --env-file .env run --rm rocketmq-init
```

该配置只创建 `hm-seckill-order`，读写队列各 1 个；NameServer/Broker 的容器上限分别为 128MB/256MB。该规格只适合低并发功能演示，确认容器稳定后，将 `/etc/hm-badminton/app.env` 的运行环境改为：

```text
SPRING_PROFILES_ACTIVE=prod,mq-lite
```

如果 RocketMQ 因资源不足停止，应用仍保留 MQ 发送失败后的同步落库补偿。停止 MQ 并恢复纯轻量模式：

```bash
docker compose --profile mq --env-file .env stop rocketmq-broker rocketmq-namesrv
sed -i 's/^SPRING_PROFILES_ACTIVE=.*/SPRING_PROFILES_ACTIVE=prod,lite/' /etc/hm-badminton/app.env
systemctl restart hm-badminton
```

升级到至少 4 核 8GB 后，需要完整异步链路时使用：

```bash
docker compose --profile mq --env-file .env up -d
```

并把 `/etc/hm-badminton/app.env` 中的 `SPRING_PROFILES_ACTIVE` 改回 `prod`，然后重启后端。

## 首次准备

1. 购买 Ubuntu 24.04 ECS，建议 4 核 8 GB、80 GB 云盘。
2. 安全组仅放行 `80/443` 到公网，`22` 仅放行自己的公网 IP；不要放行 `3306/3307/6379/9876/10909-10912/9000/9001/8088`。
3. 安装 Docker Compose、JDK 21、Maven、Node.js、Nginx、Git。
4. 将整个项目上传或克隆到 `/opt/hm-badminton`。

Alibaba Linux 3 / CentOS Stream 可先执行：

```bash
dnf install -y git docker java-21-openjdk-devel maven nodejs nginx rsync
systemctl enable --now docker nginx
docker compose version
```

若最后一条提示没有 Compose V2，请先安装 Docker Compose 插件后再继续。

## 启动基础设施

```bash
cd /opt/hm-badminton/deploy/production
cp .env.example .env
chmod 600 .env
# 编辑 .env，替换所有示例密码
docker compose --env-file .env up -d
docker compose ps
```

不要执行 `docker compose down -v`，它会删除 MySQL、Redis、MinIO、RocketMQ 的持久化卷。

## 首次初始化演示数据

仅新服务器、尚未产生真实业务数据时执行一次：

```bash
cd /opt/hm-badminton/deploy/production
bash initialize-demo-data.sh
bash upload-demo-assets.sh
```

第一个脚本会要求输入 `YES`，因为它会重建数据库表。后续部署、升级、重启都不要再运行这两个脚本。

## 打包并部署后端

```bash
cd /opt/hm-badminton/backend
mvn clean package -DskipTests
sudo useradd --system --home /opt/hm-badminton --shell /usr/sbin/nologin hmapp || true
sudo cp target/hm-badminton-backend-0.1.0.jar /opt/hm-badminton/backend/app.jar

sudo mkdir -p /etc/hm-badminton
sudo cp /opt/hm-badminton/deploy/production/app.env.example /etc/hm-badminton/app.env
sudo chmod 600 /etc/hm-badminton/app.env
# 编辑 /etc/hm-badminton/app.env，替换域名、密码和 API Key

sudo cp /opt/hm-badminton/deploy/production/systemd/hm-badminton.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now hm-badminton
sudo systemctl status hm-badminton
```

生产 Profile 已关闭 `schema.sql` 与 `data.sql` 的自动执行，重启后端不会重置已有订单、用户或业务数据。

## 打包并部署前端

```bash
cd /opt/hm-badminton/frontend
npm ci
VITE_AMAP_JS_KEY=你的高德JSKey npm run build
sudo mkdir -p /var/www/hm-badminton
sudo rsync -a --delete dist/ /var/www/hm-badminton/
```

## 配置 Nginx 与 HTTPS

```bash
# 此配置包含 443 证书路径，请在证书已签发后执行；首次签发前保留现有 HTTP 的 Nginx 配置。
sudo cp /opt/hm-badminton/deploy/production/nginx/hm-badminton.conf /etc/nginx/conf.d/hm-badminton.conf
# 编辑 server_name 为你的真实域名
sudo nginx -t && sudo systemctl reload nginx
```

域名解析到 ECS 公网 IP 后，可以使用 Certbot 申请 HTTPS 证书。中国大陆 ECS 对外提供网站服务前需完成域名 ICP 备案；香港节点适合先做无备案演示。

没有域名时，也可以申请公网 IP 证书。Let’s Encrypt 的 IP 证书有效期约 6 天，必须搭配下面的自动续期服务。需要 Certbot 5.4+；Linux 发行版仓库里的旧版 Certbot 通常不支持此功能。

```bash
# 先保证安全组已放行 80、443，并将 Nginx 保持在 HTTP 配置状态。
sudo dnf install -y python3.11 python3.11-pip
sudo python3.11 -m venv /opt/hm-badminton/certbot
sudo /opt/hm-badminton/certbot/bin/pip install --upgrade pip 'certbot>=5.4,<6'

# app.env 中的 JAVA_TOOL_OPTIONS 含空格，不能直接 source；只读取邮件配置。
EMAIL=$(grep -m1 '^CERTBOT_EMAIL=' /etc/hm-badminton/app.env | cut -d= -f2-)
if [ -z "$EMAIL" ] || [ "$EMAIL" = "replace-with-your-notification-email" ]; then
  EMAIL=$(grep -m1 '^MAIL_USERNAME=' /etc/hm-badminton/app.env | cut -d= -f2-)
fi
test -n "$EMAIL" || { echo '请先在 /etc/hm-badminton/app.env 配置 CERTBOT_EMAIL 或 MAIL_USERNAME'; exit 1; }
sudo /opt/hm-badminton/certbot/bin/certbot certonly --webroot \
  --webroot-path /var/www/hm-badminton \
  --ip-address 你的ECS公网IP \
  --preferred-profile shortlived \
  --email "$EMAIL" --agree-tos --non-interactive

# 将 Nginx 配置中的占位主机名替换为公网 IP；证书目录会随之对应到该 IP。
PUBLIC_HOST=你的ECS公网IP
sudo sed "s/your-domain.example/$PUBLIC_HOST/g" \
  /opt/hm-badminton/deploy/production/nginx/hm-badminton.conf \
  | sudo tee /etc/nginx/conf.d/hm-badminton.conf > /dev/null
sudo nginx -t && sudo systemctl reload nginx

sudo cp /opt/hm-badminton/deploy/production/systemd/hm-badminton-certbot-renew.* /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now hm-badminton-certbot-renew.timer
```

浏览器精确位置只有在 HTTPS 下才能工作。HTTP 环境会自动退化为后端高德 IP 定位，再失败才使用默认城市西安。

## 运维命令

```bash
sudo journalctl -u hm-badminton -f
sudo systemctl restart hm-badminton
cd /opt/hm-badminton/deploy/production && docker compose logs -f minio
ssh -L 9001:127.0.0.1:9001 root@你的ECS公网IP
```

## 更新发布

日常发布只更新代码、JAR、前端静态文件和必要的增量 SQL，不重新初始化演示数据：

1. 本地执行后端 `mvn test` 和前端 `npm run build`。
2. 备份 ECS 的数据库、当前 `app.jar`、`/var/www/hm-badminton` 和 Nginx 配置。
3. 上传新 JAR，执行 `systemctl restart hm-badminton` 并检查日志。
4. 将新 `dist/` 同步到 `/var/www/hm-badminton/`。Vite 资源带哈希，`index.html` 已配置为不缓存。
5. 修改 Nginx 时先执行 `nginx -t`，通过后再 reload。
6. 验证首页、`/api/sports`、登录、社区、购物车、约球和 AI。

更新发布时禁止执行：

```text
initialize-demo-data.sh
docker compose down -v
```

## 回滚

每次发布前给 JAR、前端目录和 Nginx 配置创建带时间戳的备份。应用异常时先恢复上一版文件，再重启后端或 reload Nginx。涉及数据库结构时必须使用可逆的增量迁移，并在发布前验证备份可以恢复。

详细检查、密钥和灾备说明：

- [最终版评审与验收清单](../../docs/最终版评审与验收清单.md)
- [生产安全与密钥管理](../../docs/生产安全与密钥管理.md)
- [备份、恢复与监控](../../docs/备份恢复与监控.md)
