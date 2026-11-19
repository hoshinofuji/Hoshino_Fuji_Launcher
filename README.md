# Hoshino Fuji Launcher

**Hoshino Fuji Launcher** 是 Java / JavaFX 编写的 Minecraft Java Edition 社区启动器。窗口顶部显示 **Hoshino Fuji Launcher**；发行文件名保留为 `HFML.jar`，HFML 仅作为历史文件标识，不再展开为含 Minecraft 的产品全称。

首页沿用星野藤森林主题、分层挥手和眨眼动画；可以管理本地实例、下载支持的官方游戏版本、安装 Fabric / Forge / NeoForge，并通过 Modrinth 搜索模组、资源包与光影包。首页不展示启动和下载日志。

## 使用

需要 **JDK / Java 21 或更新版本**。支持 macOS Apple Silicon、macOS Intel、Windows x64；Windows ARM64 和 Linux 桌面运行尚未支持。Windows 安装 Java 时请选择添加到 PATH。发布压缩包中保留 `third-party-sources/`、许可证和校验文件，解压后运行 `Launch.command` 或 `Launch.bat`，也可以执行：

```sh
java -jar HFML.jar
```

macOS 如果启动脚本没有执行权限，先运行 `chmod +x Launch.command`。本项目没有 Apple 公证或 Windows 代码签名，请从你信任的项目发布页下载并核验来源，不要关闭系统安全保护。

启动器默认读取系统中的 `.minecraft` 安装目录，游戏文件通过官方渠道下载；独立实例保存在 JAR 旁的 `instances/`，可以用环境变量 `MC_LAUNCHER_INSTANCES` 指定新位置。升级前保留自己的存档。实例删除只尝试移入回收站，系统不支持时操作失败并保留文件。

目前采用本地玩家模式，**没有微软账户登录、购买验证或联机认证实现**。不收集账户密码，不支持需要微软会话认证的在线服务；请合法持有游戏并遵守其条款。较旧的启动参数和旧式 natives 版本会明确提示不支持。加载器及模组是可执行第三方代码，只安装你信任的项目。

## 从源码构建

需要 Python **3.10+**、JDK **21+**，并保证 `java` / `javac` 可用或设置 `JAVA_HOME`。构建本身只用 Python 标准库，无需 Maven、Gradle 或 Python 第三方包：

```sh
python3 tools/build.py --test
```

结果为 `dist/HFML.jar` 和 `dist/SHA256SUMS.txt`。第一次构建从 Maven Central 下载 JavaFX 21.0.13.1 和 Gson 2.15.0，后续每次都核对 `tools/dependencies.lock.json` 中的 SHA-256。缓存损坏会直接失败；修复缓存后重试，不要跳过校验。下载失败时检查网络、代理及 Python 的系统 CA 配置，不要禁用 TLS 证书验证。

```sh
# 生成可再分发包，包括本项目源码和匹配的依赖源码
python3 tools/build.py --test --release
# 依赖缓存完整后可以断网构建
python3 tools/build.py --test --release --offline
# 检查发行包中的全部文件
python3 tools/verify_checksums.py dist
```

`--release` 会下载约 95 MiB 的 OpenJFX 完整对应源码；请和二进制一起发布，保留第三方许可。Linux x64 可用于 CI 编译和无窗口测试，构建产物的桌面运行目标仍是上述 Mac / Windows。

项目结构：`src/main/java/` 包含全部 Java 源码；`src/main/resources/` 是运行素材；`src/test/java/` 是安全回归测试；`art/` 保留设计原图与历史加工脚本；`tools/` 是锁定依赖、构建和校验工具；`.github/workflows/` 提供跨系统构建及受控发布。

## 发布与安全

可选联网检查：`python3 tools/build.py --test --network-test`，会访问真实渠道并将小型模组下载为测试数据，不运行模组。

发布准备和 GitHub 设置见 [docs/PUBLISHING.md](docs/PUBLISHING.md)，安全修复范围、实际验证结果及剩余限制见 [docs/SECURITY-AUDIT.md](docs/SECURITY-AUDIT.md)。安全问题请遵循 [SECURITY.md](SECURITY.md)。提交源码前运行 `python3 tools/check_release.py`；不要提交自己的游戏目录、账户文件、日志、密钥或旧交接压缩包。

SHA-256 用于发现文件变化；GitHub 构建证明用于核对产物来源；这些都不能证明代码绝无恶意或漏洞。仓库所有者仍需保护账户并审查合并。

## 开发说明

本项目由 **hoshinofuji** 发起并主导设计，开发过程中使用 Claude、Codex 等 AI 工具辅助编写代码、调整界面及整理文档。项目已进行构建验证和部分安全测试，但仍可能存在错误与漏洞，欢迎提交 Issue 和 Pull Request。

## 许可和鸣谢

代码为 [MIT](LICENSE)；原创角色与原创素材为 [CC BY 4.0](LICENSE-ASSETS)，署名 **hoshinofuji**。角色和界面制作使用了 AI 辅助，许可限于作者有权授予的部分。JavaFX、Gson 及 Minecraft 相关权利见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。感谢社区依赖项目，以及 Claude / Codex 在开发整理中的辅助。

这是非官方项目，与 Mojang、Microsoft、Fabric、Forge、NeoForge、Modrinth 无隶属或背书关系。不随包分发 Minecraft 游戏本体、游戏资产、微软令牌或用户存档。
