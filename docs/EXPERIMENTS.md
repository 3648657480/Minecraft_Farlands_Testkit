# 实验协议（S0 测量基建）

> 适用红线：R5、R8、R9。本文档是地形生成器深层调试域的实验纪律与记录格式。
> 违反任一前提的实验结论一律作废。

---

## 1. 固定参数（所有实验共用，不得随意变更）

| 项 | 值 | 说明 |
|---|---|---|
| 种子 | `12345` | dev 侧写在 `mod/run/server.properties` 的 `level-seed`；客户端建世界时手动填 |
| MC 版本 | 26.2（Fabric Loader 0.19.3） | 原版参照：`D:\Minecraft\.minecraft\versions\26.2-Fabric 0.19.3` |
| 坐标集 | P0-P4（见下表） | 全部在 vanilla 边界（±29,999,984）内 |
| 渲染距离 | 客户端 8 chunk | 人工跑客户端时固定 |
| 记录 | 编号/假设/参数/预期/实测/结论/证据 | 见 §5 模板 |

### 坐标集

| 点 | 区块坐标 | 方块坐标 (x,z) | 说明 |
|---|---|---|---|
| P0 | (0,0) | (8,8) | 原点/出生区 |
| P1 | (62,62) | (1000,1000) | 1e3 |
| P2 | (62500,62500) | (1000008,1000008) | 1e6 |
| P3 | (625000,625000) | (10000008,10000008) | 1e7 |
| P4 | (1812500,1812500) | (29000008,29000008) | 2.9e7（边界内） |

testgen 规格串（dev 侧）：`0,0;62,62;62500,62500;625000,625000;1812500,1812500`

---

## 2. 工具

| 工具 | 用途 | 用法 |
|---|---|---|
| `tools/exp-run.ps1` | dev 侧：生成固定区块集 → 沉降 → 保存 → 退出 → 移出世界 | `.\tools\exp-run.ps1 -Tag <标签> -OutDir <目录> -TestGen "<规格串>" -Wide <bool> -Continuity <bool> -Epoch <bool> [-Delay 300] [-Settle 400] [-BgThreads 1]` |
| `gradlew :mod:worldDiff` | 对照：逐区块比较两个世界（含种子校验、合并哈希、判定） | `.\gradlew.bat :mod:worldDiff -PworldA=<目录> -PworldB=<目录> -Preport=<报告文件> --no-daemon` |
| testgen（mod 内） | 强制生成指定区块并打印摘要；`-Dfarlands.testgen.stop=true` 时沉降后保存退出 | 见 `MinecraftServerTestGenMixin` |

### 确定性协议（关键，2026-09-22 建立）

**并行生成的区块内容跨运行不可复现**（跨区块特征写入的顺序敏感性；同配置 A3 vs A4 出现 6/6 全不同）。
对照实验必须使用以下协议，否则测量被时序噪声支配：

| 要求 | 值 | 理由 |
|---|---|---|
| 后台执行器单线程 | `-Dmax.bg.threads=1` | 生成顺序确定（A7 vs A8：6/6 一致，哈希相同） |
| 生成延迟 | `Delay=300`（tick） | 避开出生区初始生成并发 |
| 沉降窗口 | `Settle=400`（tick） | 等后处理/光照完成（full 状态 ≠ 管线完成） |
| 保存 tick 固定 | delay+settle | 时序效应（ticking）可复现 |
| 比较过滤 | 仅 `Status=minecraft:full`；忽略 `LastUpdate` | 半生成区块与易变元数据不是地形内容 |
| 种子校验 | 读 `data/minecraft/world_gen_settings.dat` | 种子不一致时对照无效（工具会 WARNING） |

**注意**：确定性协议只保证"同配置可复现"；它不消除"配置差异"本身的比较——那正是实验对象。

**重要（dev 缓存）**：dev jar 的补丁组由 `G1JarProcessor.Spec` 缓存（wide/continuity/epoch 已纳入缓存键）。
不同 flags 的运行会触发重新打补丁；若结果可疑，先看构建日志里的 `[FarLands-G1] Scanned ... patched ...` 行确认补丁真的应用了。

**无条件补丁集**（flags 全关也生效）：`FunctionContextRealPatch.noiseOnly`（Noise/ShiftedNoise 坐标重写）、
`Vec3iPatch`、`GsuPatch`、`AabbClipPatch`、`BlockCollisionsPatch`、`BoundingBoxPatch`、
`ClientChunkCachePatch`、`ClientChunkCacheStoragePatch`、`ViewAreaPatch`、`DebugEntryPositionPatch`、
`SectionOcclusionGraphPatch`、`WgrPatch`。
因此 **flags 全关 ≠ 原版**；"完整管线 vs 原版"必须用 F0-2（纯净版参照）。

---

## 3. F0 干扰基线（首轮实验）

### F0-1 jar 补丁组隔离（dev，自动）

- **假设**：wide/continuity/epoch 三个补丁组在 vanilla 范围内对地形生成是 no-op。
- **方法**：同种子、同坐标集，两次 dev 运行：
  - A：`Wide=false Continuity=false Epoch=false`（仅无条件补丁集 + mod）
  - B：`Wide=true Continuity=true Epoch=true`（完整管线）
- **预期**：world-diff `VERDICT: IDENTICAL`（含出生区区块）。
- **若不同**：差异区块坐标/距离分布 + 差异路径（首个 tag 路径）记录，定位到具体补丁组。

### F0-2 完整管线 vs 原版（金标准）

- **假设**：完整管线（补丁 + mod）在 vanilla 范围内与原版逐块一致。
- **方法**：
  - 参照：纯净版 `26.2-Fabric 0.19.3`（无 mod 无补丁），固定种子建世界，tp 到 P0-P4（渲染距离 8，每点等待加载），保存退出；
  - 被测：fork 客户端（同种子同步骤）**或** dev 完整管线世界（F0-1 B）。
- **预期**：交集区块 `VERDICT: IDENTICAL`。
- **注意**：客户端人工跑只比较**交集**（双方都生成的区块）；世界哈希不同属正常（区块集合不同）。
- **局限（必须写进结论）**：原版参照仅覆盖 ±29,999,984；更远距离**无原版参照**，
  远距离结论只能来自 F0 通过后的受控 A/B（我们自己的变量）。

### F0-3 自有配置变量 no-op 验证

- **假设**：epoch 激活（epoch=0）与 policy（阈值内）在 vanilla 范围内对地形是 no-op。
- **方法**：
  - epoch：F0-1 的 A（epoch 休眠）vs B（epoch 激活）已覆盖；
  - policy：`worldgen_sample_mode=raw`（默认）为基准；`clamp` 在 P0-P4 全部 < 2^53 → 应无差异（可作补充运行）。
- **预期**：IDENTICAL。

**F0 全部通过后**才进入 S0b（探针：RealContext DF 求值 + 生成期采样日志）与 F1 远距离现象扫描。

---

## 4. 已知的干扰嫌疑（F0 要检验的对象）

| 嫌疑 | 位置 | 为什么可能干扰 |
|---|---|---|
| 无条件坐标重写 | `FunctionContextRealPatch.noiseOnly` + mod `NoiseChunkRealCoordsMixin` | 改了 Noise/ShiftedNoise 的坐标取值路径 |
| 容器访问器 | `Vec3iPatch`、`Vec3iWidePatch`、`BlockPosPatch`、`ChunkPosPatch`、`SectionPosPatch` | 若改了 hashCode/equals/迭代顺序 → 特征/结构放置的 RNG 可能变化 |
| 碰撞/边界 | `AabbClipPatch`、`BlockCollisionsPatch`、`BoundingBoxPatch` | 结构生成的边界判定 |
| 生成区稳定 | `WgrPatch` | WorldGenRegion 的可用性判定 |
| mod 包装 | `NoiseChunkMixin`（Aquifer try-catch 包装）、`FunctionContextMixin` | 理论上行为保持，待验证 |

---

## 5. 记录模板

```
## <编号> <标题>
- 日期：
- 假设：
- 参数：种子/坐标集/配置/JVM flags
- 版本标记：<两侧的构建/补丁报告行>
- 预期：
- 实测：<world-diff 报告摘要：compared/identical/differing/onlyA/onlyB/合并哈希/VERDICT>
- 结论：
- 证据：<报告文件路径、世界目录路径、日志路径>
```

---

## 6. 实验记录

### F0-1 jar 补丁组隔离

- 日期：2026-09-22
- 假设：wide/continuity/epoch 在 vanilla 范围内对地形生成是 no-op。
- 参数：种子 12345；坐标集 P0-P4；A: flags 全关；B: flags 全开；确定性协议（`max.bg.threads=1`，Delay=300，Settle=400）。
- 版本标记：
  - A 侧（flags off）：loom 缓存 jar mtime 21:00:47，`ChunkPos.class` 无 `farlands$epoch` 标记、无 `xLong`（无补丁）。
  - B 侧（flags on）：jar mtime 21:19:27，有 `farlands$epoch` 标记；构建日志 `[FarLands-G1] Scanned 10952 classes, patched 35`（含 FunctionContextRealPatch×18 等）。
- 预期：world-diff `VERDICT: IDENTICAL`（full 状态区块）。
- 实测（A7 vs B3）：
  - 5 个 full/full 区块（P0-P4 中 5 个）全部一致；合并哈希相同（`8adec8b5…6bd2c`）。
  - `VERDICT: IDENTICAL`；`only in A: 172（full: 0）`——均为半生成区块（时序差异，非内容）。
  - 侧记：A 有 1 个出生区区块达到 full 而 B 未达到（生成状态推进差异，内容无比较意义）。
- 结论：**通过**。wide/continuity/epoch 补丁组在 vanilla 范围（P0-P4）对区块内容为 no-op（在 mod 无条件补丁集同在的前提下）。
- 协议迭代记录（重要）：
  - A3 vs A4（同配置，无单线程/无延迟）：6/6 不同 → 并行生成不可复现。
  - A5 vs A6（Delay 300，无单线程）：4/6 不同（水草年龄、block_ticks、Pending 后处理）。
  - A7 vs A8（`max.bg.threads=1` + Delay 300 + Settle 400）：6/6 一致 → 协议确定性达成。
- 证据：`C:\Users\EASON\AppData\Local\Temp\opencode\experiments\F0-1\report-A7-vs-B3.txt`（另有 A7-vs-A8、A5-vs-A6、A3-vs-A4 报告与日志）。
