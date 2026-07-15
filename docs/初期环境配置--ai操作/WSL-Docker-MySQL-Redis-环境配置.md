# WSL Docker MySQL Redis MinIO 环境配置

本文档记录本项目推荐的 Windows + WSL 环境配置流程，用 WSL 中的 Docker 运行 MySQL、Redis 和 MinIO，Windows 侧运行 Java 后端与 Vue 前端。

最终端口约定：

```text
MySQL: localhost:3307
Redis: localhost:6379
MinIO API: localhost:9000
MinIO Console: localhost:9001
后端: http://localhost:8088
前端: http://localhost:5173
```

## 1. 安装 WSL

在 Windows 管理员 PowerShell 中执行：

```powershell
wsl --install -d Ubuntu-24.04
```

安装完成后按提示重启电脑。重启后打开 Ubuntu，创建 Linux 用户和密码。

检查 WSL 状态：

```powershell
wsl -l -v
```

建议使用 WSL2。如果不是 WSL2，可以执行：

```powershell
wsl --set-version Ubuntu-24.04 2
```

## 2. 开启 Systemd

在 Ubuntu 中执行：

```bash
sudo tee /etc/wsl.conf <<'EOF'
[boot]
systemd=true
EOF
```

然后回到 Windows PowerShell 执行：

```powershell
wsl --shutdown
```

重新打开 Ubuntu。

## 3. 更新 Ubuntu

在 Ubuntu 中执行：

```bash
sudo apt update
sudo apt upgrade -y
```

## 4. 安装 Docker

本项目使用 Ubuntu 仓库自带的 Docker 包，避免 Docker 官方源在部分网络下出现 GPG 公钥下载失败、`NO_PUBKEY`、仓库未签名等问题。

如果之前添加过 Docker 官方源，先清理：

```bash
sudo rm -f /etc/apt/sources.list.d/docker.list
sudo rm -f /etc/apt/keyrings/docker.asc
sudo rm -f /etc/apt/keyrings/docker.gpg
```

安装 Docker：

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-v2
```

启动 Docker：

```bash
sudo systemctl enable --now docker
```

把当前用户加入 docker 用户组：

```bash
sudo usermod -aG docker $USER
newgrp docker
```

验证安装：

```bash
docker --version
docker compose version
docker run hello-world
```

如果 `docker-compose-v2` 找不到，可以改装旧版 Compose：

```bash
sudo apt install -y docker-compose
docker-compose version
```

后续命令中如果没有 `docker compose`，就把它替换成 `docker-compose`。

## 5. 启动 MySQL、Redis 和 MinIO

注意：下面的 `/mnt/d/...` 路径和 `docker compose` 命令要在 Ubuntu / WSL 终端中执行，不是在 Windows PowerShell 中执行。

进入项目部署目录：

```bash
cd /mnt/d/Program/java/hm/hm-badminton/deploy
```

启动容器：

```bash
docker compose up -d
```

查看容器：

```bash
docker ps
```

预期能看到：

```text
hm-badminton-mysql
hm-badminton-redis
hm-badminton-minio
```

当前项目的 Compose 配置通过 `deploy/.env` 控制镜像和账号：

```text
MYSQL_IMAGE=docker.m.daocloud.io/library/mysql:8.4
REDIS_IMAGE=docker.m.daocloud.io/library/redis:7.4
MINIO_IMAGE=quay.io/minio/minio:latest
MINIO_CLIENT_IMAGE=quay.io/minio/mc:latest
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin123
MINIO_BUCKET=hm-badminton
```

MySQL 使用 `3307` 是为了避开 Windows 本机可能已有的 MySQL `3306`。

MinIO 控制台：

```text
http://localhost:9001
用户名: minioadmin
密码: minioadmin123
```

`minio-init` 容器会自动创建 bucket：

```text
hm-badminton
```

如果你坚持在 Windows PowerShell 里启动 WSL 内的 Docker，可以加 `wsl` 前缀：

```powershell
wsl -e bash -lc "cd /mnt/d/Program/java/hm/hm-badminton/deploy && docker compose up -d"
wsl -e bash -lc "docker ps"
```

## 6. 检查服务

检查 Redis：

```bash
docker exec -it hm-badminton-redis redis-cli ping
```

预期输出：

```text
PONG
```

检查 MySQL：

```bash
docker exec -it hm-badminton-mysql mysql -uroot -p123456 -e "show databases;"
```

预期能看到：

```text
hm_badminton
```

检查 MinIO：

```bash
docker logs hm-badminton-minio --tail=50
docker logs hm-badminton-minio-init --tail=50
```

也可以打开浏览器：

```text
http://localhost:9001
```

登录后确认存在 bucket：

```text
hm-badminton
```

## 7. 后端配置

项目后端配置文件：

```text
D:\Program\java\hm\hm-badminton\backend\src\main\resources\application.yml
```

当前已配置为：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/hm_badminton?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 123456
  data:
    redis:
      host: localhost
      port: 6379

hm:
  minio:
    endpoint: http://localhost:9000
    access-key: minioadmin
    secret-key: minioadmin123
    bucket: hm-badminton
```

后端启动时会自动执行：

```text
backend/src/main/resources/db/schema.sql
backend/src/main/resources/db/data.sql
```

第一次启动会自动建表并写入模拟数据。

文件存储采用：

```text
MySQL: 保存 file_metadata 元数据
MinIO: 保存文件本体
```

## 8. 启动后端和前端

在 Windows PowerShell 启动后端：

```powershell
cd D:\Program\java\hm\hm-badminton\backend
mvn spring-boot:run
```

启动前端：

```powershell
cd D:\Program\java\hm\hm-badminton\frontend
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

## 9. 文件上传测试

上传文件：

```powershell
curl.exe -X POST "http://localhost:8088/api/files/upload?bizType=venue&bizId=1" `
  -F "file=@D:\test\court.jpg"
```

查询元数据：

```text
GET http://localhost:8088/api/files/{id}
GET http://localhost:8088/api/files?bizType=venue&bizId=1
```

下载文件：

```text
GET http://localhost:8088/api/files/{id}/download
```

生成临时访问地址：

```text
GET http://localhost:8088/api/files/{id}/presigned-url
```

## 10. 常见问题

### Docker Hub 拉取超时

如果出现：

```text
failed to resolve reference "docker.io/library/redis:8"
i/o timeout
```

说明 Docker 正在直连 Docker Hub，网络超时。项目已经通过 `deploy/.env` 改成镜像代理地址。重新进入部署目录后执行：

```bash
cd /mnt/d/Program/java/hm/hm-badminton/deploy
docker compose down
docker compose pull
docker compose up -d
docker ps
```

如果还是失败，先单独测试拉取：

```bash
docker pull docker.m.daocloud.io/library/redis:7.4
docker pull docker.m.daocloud.io/library/mysql:8.4
docker pull quay.io/minio/minio:latest
docker pull quay.io/minio/mc:latest
```

### Docker 官方源 NO_PUBKEY

如果出现：

```text
NO_PUBKEY 7EA0A9C3F273FCD8
The repository 'https://download.docker.com/linux/ubuntu noble InRelease' is not signed.
```

说明 Docker 官方 GPG 公钥没有下载成功。按本文档第 4 节清理 Docker 官方源，然后使用：

```bash
sudo apt install -y docker.io docker-compose-v2
```

### docker 命令需要 sudo

执行：

```bash
sudo usermod -aG docker $USER
newgrp docker
```

如果仍然不行，退出 Ubuntu 后重新进入，或者在 Windows PowerShell 执行：

```powershell
wsl --shutdown
```

再打开 Ubuntu。

### systemctl 无法使用

如果执行：

```bash
sudo systemctl enable --now docker
```

提示 systemd 不可用，检查：

```bash
cat /etc/wsl.conf
```

确保包含：

```text
[boot]
systemd=true
```

然后在 Windows PowerShell 执行：

```powershell
wsl --shutdown
```

重新打开 Ubuntu。

### MySQL 端口冲突

Windows 本机如果已经有 MySQL 占用 `3306`，本项目使用：

```text
localhost:3307
```

不要把 Compose 改回 `3306:3306`，否则容易和 Windows 本机 MySQL 冲突。

### 重新初始化数据

当前后端配置：

```yaml
spring:
  sql:
    init:
      mode: always
```

每次后端启动都会重新执行 schema 和 data，适合开发学习。如果要保留数据，把它改成：

```yaml
spring:
  sql:
    init:
      mode: never
```
