# 工作流速查（FarLands G1）

## 构建

```powershell
cd C:\Project-G1
.\gradlew.bat :patcher-cli:build :mod:build --no-daemon
```

产物：`mod/build/libs/farlands-g1-mod-1.0-SNAPSHOT.jar`（mod）
+ `patcher-cli/build/libs/patcher-cli-1.0-SNAPSHOT.jar`（补丁工具）

**铁律**：构建失败（`build: False`）**禁止部署**旧产物。

## 打 fork jar（patcher）

```powershell
$java = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\java.exe"
& $java "-Dfarlands.wide=true" "-Dfarlands.continuity=true" "-Dfarlands.epoch=true" `
  -jar "C:\Project-G1\patcher-cli\build\libs\patcher-cli-1.0-SNAPSHOT.jar" `
  --in "D:\Minecraft\.minecraft\versions\26.2-Fabric 0.19.3\26.2-Fabric 0.19.3.jar" `
  --out "D:\Minecraft\.minecraft\versions\26.2-Fabric 0.19.3_fork\26.2-Fabric 0.19.3_fork.jar"
```

**注意**：fork jar 与 mod 的补丁集必须匹配（identity 补丁 + epoch 标记）——
旧 fork jar（全量 rebase）与 mod 不匹配会产生"双重转换"键错乱。

## 无头测试（服务器 rig）

```powershell
# 删世界跑（干净测试）
tools\server-test.ps1 -Tag "名字" -TestGen "0,0" [-SpawnSet "1e306,100,0"] [-TimeoutMin 20]

# 保留世界跑
tools\server-test-nodel.ps1 -Tag "名字" -TestGen "0,0"
```

- `-TestGen "cx,cz[,n]"`：生成 chunk（n×n 区域），输出 topY/biome/相邻格高度
- `-SpawnSet "x,y,z"`：设 epoch（写配置文件）+ respawn
- 结果看 `RESULT(tag):` 行；日志在
  `C:\Users\EASON\AppData\Local\Temp\opencode\server-<tag>.log`

**注意**：rig = dedicated（无客户端流程）。客户端特有路径（prepare_spawn/渲染/F3）
rig 覆盖不到——**无头 ≠ 实际**。

### 实验基建（F0/S0）

```powershell
# 确定性生成固定区块集 + WG 指纹（exp-F0-4-M1.log 等）
tools\exp-run.ps1 -Tag F0-x -OutDir <证据目录> -TestGen "0,0;62,62;..." `
  -Rig mod|vanilla [-Delay 300] [-Settle 400] [-BgThreads 1] [-Extra "-Dfarlands.xxx=..."]

# 区块字节对比（粗筛；含种子/出生点校验、合并哈希）
.\gradlew.bat :mod:worldDiff -PworldA=<世界A> -PworldB=<世界B> -Preport=<报告> --no-daemon
```

- `vanilla-rig` 子项目 = 原版参照（无补丁 jar + 仅测量桩）
- 指纹行：`[FarLands-Test] ... wgHash=surf=... floor=... biome=...`（生成期确定性指标）

## 客户端测试

1. 部署 mod jar 到 `D:\Minecraft\.minecraft\versions\26.2-Fabric 0.19.3_fork\mods\`
2. PCL 启动（epoch 从世界配置读，无需 JVM 参数）
3. 日志：fork 目录 `logs/latest.log`（grep `[FarLands`）
4. 游戏内：`/realtp`（真实坐标）+ F3（XYZ/Block/Chunk 真实坐标 + `Local (in-epoch)` + `Epoch/Laps (2^31)` + `Real double ULP`）

## 游戏内工具

```
/realtp <x> <y> <z>              真实坐标传送（支持 1e1000+）
/realtp <targets> <x> <y> <z>    实体传送（命令方块可用）
```

世界配置 `world/farlands.properties`（自动创建）：
```properties
epoch_x=0
epoch_z=0
auto_relocate=true
relocate_margin=100000
```

## 日志标记

| 标记 | 来源 |
|---|---|
| `[FarLands-G1] v3.x epoch build` | mod 版本（测试第一项必看） |
| `[FarLands-G1] EPOCH set to real (...)` | epoch 设定（from farlands.properties / spawnset） |
| `[FarLands-G1] config auto-created` | 新世界自动初始化 |
| `[FarLands] /realtp ...` | 命令执行（real → local） |
| `[FarLands] ...relocate...` | 重定位流程（归档/平移） |
| `[FarLands-Test]` | 无头探针 |

## 诊断习惯（教训五条铁律）

1. **先写域账本**：动代码前先写下坐标的域（real/local/BigInteger）
2. **先验证代码在跑**：println 必须 `flush()`；构建失败禁部署；测试第一项看版本行
3. **两次理论失败转测量**：二分 / JFR / 打印键值
4. **每轮单变量**
5. **双端共享状态第一轮上日志**（谁设的、什么时候设的）

## 提交与推送

- 每过一个里程碑一个 commit
- **推送由用户控制**（`git config --global http.proxy http://127.0.0.1:17891` 已配置）
- 公共仓库只提交可用态

## 目录速查

```
C:\Project-G1\
├─ patcher-core\     字节码补丁（ChunkPosEpochPatch=标记only 等）
├─ patcher-cli\      补丁 CLI
├─ vanilla-rig\      原版参照（无补丁 jar + 仅测量桩）
├─ mod\              Fabric mod（mixin 分类）
│   └─ src/main/resources\
│       ├─ farlands-core.mixins.json      坐标容器/投影
│       ├─ farlands-epoch.mixins.json     纪元域（epoch/realtp/spawn 链）
│       ├─ farlands-world.mixins.json     世界/生成/光照
│       ├─ farlands-entity.mixins.json    实体/玩家/交互
│       ├─ farlands-stability.mixins.json 稳定性/性能
│       ├─ farlands-test.mixins.json      无头探针
│       └─ farlands-client.mixins.json    客户端渲染 + 重定位
├─ tools\
│   ├─ server-test.ps1       无头 rig（删世界）
│   ├─ server-test-nodel.ps1 无头 rig（保留世界）
│   ├─ exp-run.ps1           实验运行（确定性协议 + WG 指纹）
│   ├─ world-diff\           区块字节对比工具
│   └─ chunk-translator\     存档平移工具
└─ docs\
    ├─ USAGE(.en).md         玩家手册（红线 R1-R10）
    ├─ CONFIG(.en).md        配置参考 + 预设组合
    ├─ EXPERIMENTS.md        实验协议 + 远域现象表
    ├─ ROADMAP.md            路线图/里程碑/教训
    ├─ REVIEW.md             基础知识复习（当前架构版）
    ├─ WORKFLOW.md           本文件
    └─ archive\              历史设计文档（E 线、宽化容器）
```
