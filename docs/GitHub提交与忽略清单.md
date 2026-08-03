# GitHub 提交与忽略清单

## 1. 应该提交

- `backend/src/`、`frontend/src/`：前后端源码。
- `backend/pom.xml`、`frontend/package.json`、`frontend/package-lock.json`：构建和依赖描述。
- `backend/src/main/resources/db/`：表结构、迁移和可公开的演示数据。
- `deploy/production/`：生产环境部署脚本、Nginx 配置和 `.env.example`。
- `scripts/`：启停、数据初始化和 JMeter 测试脚本。
- `docs/`中的正式项目文档、架构、关键业务、AI 助手和运维文档。
- `.env.example`：只保留变量名和安全示例，帮助新环境完成配置。

## 2. docs 中只留本机

| 路径 | 原因 |
| --- | --- |
| `docs/.obsidian/` | Obsidian 窗口、插件和工作区状态，与项目无关，且频繁变化 |
| `docs/1问题.md` | 个人待办和早期问题草稿，不是最终项目说明 |
| `docs/2原理.md` | 个人学习大纲，内容尚未形成正式文档 |
| `docs/初期环境配置--ai操作/羽动羽毛球点评项目规划.md` | 旧项目名和早期规划，已被当前 README 和功能文档取代 |

`docs/3注释.md`是复杂业务的代码阅读索引，应继续提交。WSL/Docker 和项目启停文档仍被 README 引用，也应保留。

## 3. 其他不应提交的文件

| 类型 | 示例 | 原因 |
| --- | --- | --- |
| 真实配置 | `.env`、`deploy/.env`、`deploy/production/.env` | 可能包含数据库密码、邮箱授权码、JWT 和 API Key |
| 构建产物 | `backend/target/`、`frontend/dist/`、`frontend/node_modules/` | 可由源码和依赖文件重新生成 |
| 运行日志 | `logs/`、`*.log` | 体积持续增长，可能带请求或环境信息 |
| 压测产物 | `reports/`、`*.jtl` | 属于一次性结果；应提交 `.jmx` 脚本和结论文档 |
| 运行数据 | `data/`、`uploads/` | 数据库、MinIO 或上传文件应留在本机/服务器 |
| 发布压缩包 | `*.tgz` | 可重新打包，不应让 Git 保存多份二进制副本 |
| IDE 状态 | `.idea/`、`.vscode/`、`.obsidian/` | 个人工具状态，易产生无意义差异 |

JMeter 的 `.jmx` 需要提交。当前 CSV 仅使用可重建的演示账号；如果以后改成真实账号或固定 token，应改为 `.example.csv` 并忽略真实文件。

## 4. 正确操作

`.gitignore` 只对“尚未跟踪”的文件生效。文件如果已经提交过，需要从 Git 索引移除：

```powershell
git rm -r --cached <路径>
git add .gitignore
git status
git commit -m "chore: remove local and generated files"
git push
```

`--cached` 只移除 Git 跟踪，不删除本机文件。提交前可以使用下列命令确认忽略来源：

```powershell
git check-ignore -v <文件路径>
git diff --cached --stat
git status --short
```

