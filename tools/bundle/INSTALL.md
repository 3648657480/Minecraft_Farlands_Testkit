# FarLands G1 — 安装（快速开始）

> 本压缩包版本：**@VERSION@**（也见压缩包名 / 仓库根 `VERSION`）。下文 `<版本>` = 该版本号。

## 这个包里有

- `patch.bat` / `patch.ps1` —— **一键打补丁**（自动挑 Java 21+，并处理路径）
- `patcher-cli-<版本>.jar` —— 打补丁命令行工具
- `farlands-g1-mod-<版本>.jar` —— Fabric 模组
- `docs/`（USAGE / CONFIG / EXPERIMENTS）+ `README` + `LICENSE.txt`

## 前置

- **Java 21 或更新（推荐 25）。**
  ⚠️ 若 `java -version` 显示 **`1.8`**，直接 `java -jar` 会报
  `UnsupportedClassVersionError`。用 `patch.bat` 会自动挑 Java 21+；手动的话要用完整路径。
- 你自己**合法获取**的 **Minecraft 26.2 客户端 jar**（**需为命名（mojmap）的版本 jar**，
  例如 Fabric 的 `...\versions\26.2-Fabric 0.19.3\26.2-Fabric 0.19.3.jar`；
  若补丁报告 `patched 0`，说明你给的是混淆 jar）。
- ⚠️ 本项目**不分发任何 Minecraft 文件**。

## 重要提示（务必先读）

1. **必须移除 / 禁用 Fabric API**（`mods\fabric-api-*.jar`）：本项目自带所需功能，且其 mixin 与
   Fabric API 冲突——**`mods\` 里只放 `farlands-g1-mod-*.jar`**，否则 FarLands 的 mixin 可能不生效
   （表现为创建世界不出现 FarLands 标签页 / 不生成 `farlands.properties` 等）。
2. **版本必须对上**：补丁是针对 **Minecraft 26.2 + Fabric Loader 0.19.3** 做的；请用
   `26.2-Fabric 0.19.3` 这个版本（其他版本/整合包请勿直接套用）。
3. **替换的是版本 jar**：把 patch 出来的 `..._fork.jar` **改名为原来的版本 jar 名**
   （如 `26.2-Fabric 0.19.3.jar`）来**替代原 jar**（原版先备份成 `.bak`）。
4. **关闭启动器的「文件校验 / 完整性校验 / 版本校验」**：patcher 改过字节码，jar 不再是 Mojang
   签名；开着校验会把 fork jar 判成损坏、或被启动器自动“修复/重下”覆盖掉你的补丁。
5. **建议用离线账号启动（不要用正版登录）**：改过的客户端与正版鉴权/完整性流程不兼容，离线模式最稳。
   （请自备你合法拥有的游戏副本。）
6. **正常现象**：patcher 会**丢弃 2 个 jar 签名文件**（`META-INF/MOJANGCS.SF/.RSA`），所以输出比输入少
   2 项；其余条目（含语言、材质、目录）**原样保留**。

## 方式 A（推荐）：用 patch.bat

1. 备份你的版本 jar。
2. 把 Minecraft 26.2 客户端 jar **拖到 `patch.bat` 上**（或双击 `patch.bat`，按提示拖/输入路径）。
3. 它自动找 Java 21+，并在 jar 同目录生成 `..._fork.jar`。
4. 用 `_fork.jar` 作为该版本的 jar。
5. 把 `farlands-g1-mod-<版本>.jar` 放进 `mods\`，启动。

## 方式 B：手动命令

先 `cd` 到**本包目录**（这样 `-jar` 能直接找到 patcher），并给 `--in/--out` 写**完整路径**。

⚠️ 若默认 `java` 是 1.8，把 `java` 换成 Java 21+ 的**完整路径**，例如
`C:\Program Files\Java\jdk-21\bin\java.exe` 或
`C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\java.exe`。

```powershell
java "-Dfarlands.wide=true" "-Dfarlands.continuity=true" "-Dfarlands.epoch=true" `
  -jar "patcher-cli-<版本>.jar" `
  --in  "<你的 26.2 客户端 jar 完整路径>" `
  --out "<输出 fork jar 完整路径>"
```

要点：
- `-jar` 后面是 **patcher 的路径**（或先 `cd` 到本包目录再用文件名）；
- `--in` / `--out` 写**完整路径**，避免“当前目录”歧义；
- **参数可省略**：patcher 默认就带齐补丁（wide/continuity/epoch，约 38 处）；上面那串 `-D...` 只是显式写法。
- `--out` 指向的文件**不能已存在**（patcher 拒绝覆盖）；
- **别中途关窗口**、也别把输出接进“只看前几行”的管道——那会中断进程。patcher 先写 `*.part` 临时文件、**校验通过才替换**成正式文件，所以中断只会留下 `.part`，**不会**留下坏 jar；
- 完成后确认输出 jar 大小接近输入（约几十 MB）；成功后：fork jar 当版本 jar，mod jar 放 `mods\`。

---

## English (quick start)

Requirements: **Java 21+** (25 recommended; Java 8 fails with
`UnsupportedClassVersionError`), and your own named (mojmap) Minecraft 26.2 client jar.

**Recommended**: drag your client jar onto `patch.bat` (it auto-picks Java 21+).

**Manual** (run from this folder, use full paths):

```powershell
java "-Dfarlands.wide=true" "-Dfarlands.continuity=true" "-Dfarlands.epoch=true" `
  -jar "patcher-cli-<version>.jar" `
  --in  "<full path to your 26.2 client jar>" `
  --out "<full path to the fork jar>"
```

Then use the fork jar as the version's jar and put `farlands-g1-mod-<version>.jar` in `mods\`.
See `docs/USAGE.en.md`.
