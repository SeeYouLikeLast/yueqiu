# 约个球生产部署

该目录只服务于阿里云 ECS 等 Linux 服务器部署；`../docker-compose.yml` 继续保留给 Windows + WSL 本地开发。

## 部署结构

```text
Internet -> Nginx :80/:443 -> Vue dist + /api -> Spring Boot :8088 (127.0.0.1)
                                                -> Docker: MySQL / Redis / RocketMQ / MinIO
```

除 Nginx 的 `80/443` 外，所有服务端口均绑定为 `127.0.0.1`，不会被公网直接访问。MinIO 文件通过 Nginx 的 `/objects/` 输出，管理端 `9001` 仅可通过 SSH 隧道访问。

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
sudo cp /opt/hm-badminton/deploy/production/nginx/hm-badminton.conf /etc/nginx/conf.d/hm-badminton.conf
# 编辑 server_name 为你的真实域名
sudo nginx -t && sudo systemctl reload nginx
```

域名解析到 ECS 公网 IP 后，再使用 Certbot 申请 HTTPS 证书。中国大陆 ECS 对外提供网站服务前需完成域名 ICP 备案；香港节点适合先做无备案演示。

## 运维命令

```bash
sudo journalctl -u hm-badminton -f
sudo systemctl restart hm-badminton
cd /opt/hm-badminton/deploy/production && docker compose logs -f minio
ssh -L 9001:127.0.0.1:9001 root@你的ECS公网IP
```
