# 发布到 GitHub

本目录就是可独立构建的源码仓库。不要把原交接 ZIP、旧成品目录、个人游戏目录或本地构建缓存整个拖进仓库。`dist/`、`build/`、`.cache/`、`release/` 都已被忽略；真正发布的二进制由工作流从标签源码构建。

仓库名称使用 `Hoshino_Fuji_Launcher`，显示名称为 **Hoshino Fuji Launcher**。将本源码目录里的内容直接提交到仓库根目录；不要在仓库里再套一层 `project/`，也不要只上传 ZIP。包含 `.github/`、`.gitignore` 和 `.gitattributes` 等隐藏文件。

## 首次上传

1. 在 GitHub 新建空仓库，例如 `Hoshino_Fuji_Launcher`，默认分支使用 `main`。本项目已有完整 LICENSE 和 README，不需要另建模板。
2. 在解压后的源码目录运行 `python3 tools/build.py --test` 和 `python3 tools/check_release.py`。
3. 初始化 Git，检查待提交文件，再关联自己新建的仓库地址：

```sh
git init -b main
git add .
git diff --cached --stat
git diff --cached --check
git commit -m "Initial HFML open-source release"
# 用 GitHub 新仓库页面给出的地址设置 origin，然后推送 main。
```

发布前再次确认美术署名为 `hoshinofuji`、代码 MIT、原创素材 CC BY 4.0，这意味着允许别人按许可复制、修改以及商用。只授权你有权授予的部分；Minecraft 相关标志和图案仍受其权利人规则约束。

## 仓库设置

这些设置属于 GitHub 服务器配置，不能靠 ZIP 中的文件自动生效，仓库所有者需要在网页中启用：

- 账户启用通行密钥或双重验证，保管恢复码；不要共用账户或 PAT。
- 为 `main` 设置 Ruleset / 分支保护，要求 PR、评审和 Build and verify 的三个系统检查通过；禁止强推和删除。单人维护也应对外部贡献逐项审查。
- 创建名为 `release` 的 Environment，设置 required reviewers（建议禁止 self review），限制部署来源为受保护的 `v*` 标签。如果未配置，工作流里的 `environment: release` 本身不会提供人工批准保护。环境审批的可用范围以 GitHub 账户和仓库方案为准。
- 为 `v*` 标签设置 Ruleset，限制创建者，禁止更新和删除，避免同名版本被替换。
- Actions 的仓库默认权限设为 read-only，禁用 Actions 创建/批准 PR 的权限。外部贡献工作流保留人工审核；不要改成 `pull_request_target` 后执行外部 PR 代码。
- Security 页启用 Private vulnerability reporting、Dependabot alerts 和可用的 Secret scanning / Push protection。具体功能是否可用取决于仓库和方案。Dependabot 的 Actions 更新 PR 仍需要人工评审。
- 不需要配置任何长期发布密钥、微软密码或账户令牌。本工作流仅使用 GitHub 自动生成的短期 token。若以后增加签名，私钥不得入库。

重点评审 `.github/workflows/`、`tools/dependencies.lock.json`、构建脚本、下载域名列表、文件读写和外部进程调用的变更。别人 fork 你的项目并加入恶意代码，不会自动更改你的官方仓库；避免把陌生人的构建产物当作官方版本转发。

## 构建、校验和发布

普通 push / PR 在 macOS、Windows、Linux x64 runner 上编译并运行无窗口测试；Linux 用于构建，产物桌面支持范围仍是 Mac / Windows。CI 不上传二进制。每周以及依赖变更时查询 OSV；网络查询失败也会让检查失败，避免误报为“无公告”。

确认 `main` 检查通过、同步更新 Launcher.java 的 VERSION 和 CHANGELOG 后，为所选提交创建 `v0.2.0` 这类标签并推送。Release 工作流在受保护环境批准后进行：

1. 查询依赖公告，校验所有依赖哈希，编译，运行安全回归和发布扫描。
2. 打包完整 HFML 源码、依赖对应源码、运行文件和许可证，校验清单中所有文件。
3. 为 JAR、源码 ZIP、发行 ZIP 生成 GitHub 构建来源证明。
4. 建立 **Draft release**。维护者核对标签、Actions 日志和测试结果后，在网页手动发布草稿。

发布页应保留 `HFML-release.zip`、`HFML-source.zip`、`HFML.jar` 和 `SHA256SUMS.txt`。完整发行 ZIP 内含 OpenJFX 和 Gson 对应源码及第三方许可，不应在重新分发时删去这些文件。不要上传未审查的本地 JAR 替换自动构建产物。

来源核验使用 GitHub CLI，命令中的 OWNER/REPO 替换成实际仓库：

```sh
gh attestation verify HFML.jar --repo OWNER/REPO
```

也可以核验 ZIP 的构建证明。哈希只能发现文件变化；证明表明构建关联哪个仓库和提交。需要继续确认该仓库和提交可信，二者都不能代替代码审查或杀毒检测。

## 依赖维护与可复现性

精确版本、URL、文件大小、SHA-256 和源码 revision 均在 `tools/dependencies.lock.json`。更新时从官方上游重新核验版本和许可，更新对应源码项与 THIRD_PARTY_NOTICES，重新运行测试及公告查询，不要静默接受内容变化。每个二进制变体单独锁定。

构建禁用 Java annotation processors，固定 Java release 为 21，ZIP 条目排序及时间戳固定，不打包构建机器路径、时间和私人环境文件。同一 JDK 与工具版本、相同源码和依赖下可以比较 JAR 哈希；不同 JDK 编译器补丁或压缩库可能影响字节，不能据此承诺所有系统逐字节相同。Actions 日志和构建证明记录实际环境。
