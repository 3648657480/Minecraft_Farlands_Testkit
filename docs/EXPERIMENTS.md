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

**结论先行（2026-09-22 晚）：字节级区块对比不是有效的等价性仪器。**
即使同配置、单线程、关随机刻、单目标，运行间仍会出现间歇性区块内容差异（原版侧同样存在）。
以下协议只降低噪声，不能消除它；F0 判定需要新仪器（见文末）。

**并行生成的区块内容跨运行不可复现**（跨区块特征写入的顺序敏感性；同配置 A3 vs A4 出现 6/6 全不同）。
对照实验必须使用以下协议，否则测量被时序噪声支配：

| 要求 | 值 | 理由 |
|---|---|---|
| 后台执行器单线程 | `-Dmax.bg.threads=1` | 生成顺序确定（降低噪声） |
| 生成延迟 | `Delay=300`（tick） | 避开出生区初始生成并发 |
| 沉降窗口 | `Settle=400`（tick） | 等后处理/光照完成（full 状态 ≠ 管线完成） |
| 保存 tick 固定 | delay+settle | 时序效应（ticking）可复现 |
| 关闭随机刻 | harness 设 `random_tick_speed=0` | 随机刻 RNG 随运行推进不可复现 |
| 比较过滤 | 仅 `Status=minecraft:full`；忽略 `LastUpdate` | 半生成区块与易变元数据不是地形内容 |
| 种子校验 | 读 `data/minecraft/world_gen_settings.dat` | 种子不一致时对照无效（工具会 WARNING） |

**注意**：确定性协议只降低噪声；它不能消除管线固有的运行间差异。

### F0 噪声特征（实测汇总，2026-09-22）

| 对比 | 配置 | 协议 | 结果 |
|---|---|---|---|
| A3 vs A4 | 同（flags off） | 无控制 | 6/6 不同 |
| A5 vs A6 | 同（flags off） | Delay 300 | 4/6 不同 |
| A7 vs A8 | 同（flags off） | +单线程 | 6/6 一致 ✅ |
| A7 vs B3 | off vs on | +单线程 | 5/5 一致 ✅ |
| B3 vs V3 | on vs 原版 | +单线程 | 5/5 一致 ✅ |
| V3 vs V4 | 原版同配置 | +单线程 | 6/6 一致 ✅ |
| C1 vs B3 | clamp vs raw | 单线程 settle 400 | 2/5 不同 ❌ |
| B3 vs B5 | 同（on） | 同上 | 3/5 不同 ❌ |
| B4 vs C2 | raw vs clamp | settle 1200 | 4/5 不同 ❌ |
| B6 vs B7 | 同（on）+关随机刻 | 同上 | 2/5 不同 ❌ |
| B8 vs B9 | 同（on）单目标 | 同上 | 1/1 一致 ✅ |
| B10 vs B11 | 同（off）单目标 | 同上 | 1/2 不同 ❌ |
| V5 vs V6 | 原版单目标 | 同上 | 2/2 一致 ✅ |

**判定**：间歇性噪声在**所有**配置（含纯原版）都存在；"一致"与"不同"都不能归因于补丁组差异。
字节级区块对比只能作为粗筛（smoke test），不能作为 F0 的等价性证据。

### F0-1 jar 补丁组隔离（记录）

- 日期：2026-09-22
- 假设：wide/continuity/epoch 在 vanilla 范围内对地形生成是 no-op。
- 仪器：**WG 指纹**（生成期、特征前）：`WORLD_SURFACE_WG` + `OCEAN_FLOOR_WG` 全网格 SHA-256 + 4×4 生物群系哈希。
  该数据由 testgen 在 `getChunk` 后立即计算并打印到日志（确定性：同配置重复运行逐位一致）。
- 实测（M1 flags-on vs M2 flags-off，5 目标点）：**5/5 指纹完全一致** ✅
- 判定：**通过**（地形数学层）。wide/continuity/epoch 补丁组在 P0-P4 对噪声→高度→生物群系无影响。
- 证据：`exp-F0-4-M1.log` / `exp-F0-4-M2.log`。

### F0-2 完整管线 vs 原版（记录）

- 原版参照：`vanilla-rig`（无补丁 jar + 同款测量桩）。
- 实测（M1/M2/M5 我们 vs M3/M4 vanilla，5 目标点）：
  - **P0 (0,0)、P1 (62,62)、P2 (62500,62500)、P4 (1812500,1812500)：指纹一致** ✅
  - **P3 (625000,625000)：不一致，且可复现** ❌
    - 我们（两轮一致）：`surf=73affaa4040f floor=9f242b6d9ef2`
    - 原版（两轮一致）：`surf=6770cdff96bb floor=81d0572280b2`
    - 生物群系哈希相同（`6e0ce61e6efb`）→ 差异在固体地形高度，不是生物群系
- 判定：**F0-2 未通过（P3 存在真实、可复现的差异）**。
- 归因：flags-on 与 flags-off 的 P3 指纹相同 → 差异来自**无条件集**（无条件 jar 补丁 + 始终生效的 mod mixin）。
- 已排除（静态审计）：
  - `Vec3iPatch`：只新增 `getRealX/Y/Z` 访问器，无人调用（惰性）。
  - `GsuPatch`：客户端渲染器（GlobalSettingsUniform）。
  - `WgrPatch`：仅 `|centerChunk| > 134M` 触发（P3 = 625k chunk，不触发）。
  - `BoundingBoxPatch`：`minX/maxX` 在正常坐标 clamp 为 no-op；`getLength` 与原版一致（无 +1）；
    Beardifier 只用 `minX/maxX/isInside`，正常坐标不受影响（`getXSpan` 的 [1,256] clamp 只在跨度 >256 时有别）。
  - `NoiseChunkMixin`（Aquifer 包装）：无异常时纯委托。
- 待归因（下一步二分）：
  1. **vanilla rig + 仅 jar 补丁（无 farlands mod）**：若 P3 仍不同 → jar 补丁（首要嫌疑 `FunctionContextRealPatch.noiseOnly`）；
     若一致 → farlands mod 的常驻 mixin。
  2. 视结果再细分子集。
- 证据：`exp-F0-4-M1/M2/M3/M4/M5/M8/M9.log`。

### F0-3 自有配置变量 no-op（记录）

- clamp（阈值内应 no-op）：C1 vs B3 = 2/5 不同 ❌；B4 vs C2 = 4/5 不同 ❌。
- 代码审计：`applySamplePolicy` 在 `|real| < worldgen_far_threshold`（默认 2^53）时直接返回 real，
  P0-P4 全部 < 2^53 → 理论上 no-op。
- 判定：**待用 WG 指纹重做**（旧的区块对比结果不可用）。

---

## 7. 仪器改造方案（F0 重做的前置）

字节级区块对比被证伪后，F0 需要确定性仪器。候选（建议 A+B+C 组合）：

| 方案 | 覆盖 | 确定性 | 工程量 |
|---|---|---|---|
| A. DF 探针：两侧对固定坐标求密度函数值并逐位比较 | 噪声/坐标数学（F0-1 主要风险面） | 高（纯函数） | 中（探针命令/headless） |
| B. WG 高度图哈希：testgen 记录目标区块 `WORLD_SURFACE_WG`/`OCEAN_FLOOR_WG` 全网格哈希 | 噪声→高度链 | 高（特征前生成期数据） | 小 |
| C. 静态审计：容器补丁是否改 hashCode/equals/迭代顺序 | 容器/放置风险面 | 高（代码审查） | 小 |
| D. 同步生成 harness：绕开异步管线（直接 ChunkGenerator + 彻底排空） | 全链 | 需验证 | 大 |

**D 只在 A+B+C 不足时启动。**

**重要（dev 缓存）**：dev jar 的补丁组由 `G1JarProcessor.Spec` 缓存（wide/continuity/epoch 已纳入缓存键）。
不同 flags 的运行会触发重新打补丁；若结果可疑，先看构建日志里的 `[FarLands-G1] Scanned ... patched ...` 行确认补丁真的应用了。

**无条件补丁集**（flags 全关也生效）：`FunctionContextRealPatch.noiseOnly`（Noise/ShiftedNoise 坐标重写）、
`Vec3iPatch`、`GsuPatch`、`AabbClipPatch`、`BlockCollisionsPatch`、`BoundingBoxPatch`、
`ClientChunkCachePatch`、`ClientChunkCacheStoragePatch`、`ViewAreaPatch`、`DebugEntryPositionPatch`、
`SectionOcclusionGraphPatch`、`WgrPatch`。
因此 **flags 全关 ≠ 原版**；"完整管线 vs 原版"必须用 F0-2（纯净版参照）。

---

## 3. F0 干扰基线（首轮实验）

> 仪器更新（2026-09-22 晚）：以下设计中的"区块逐字节对比"已被证伪为无效仪器
> （见上方"F0 噪声特征"）。实验设计保留，但验收改用确定性仪器（DF 探针 + WG 高度图哈希 + 静态审计，见 §7）。

### F0-1 jar 补丁组隔离（dev，自动）

- **假设**：wide/continuity/epoch 三个补丁组在 vanilla 范围内对地形生成是 no-op。
- **方法**：同种子、同坐标集，两次 dev 运行：
  - A：`Wide=false Continuity=false Epoch=false`（仅无条件补丁集 + mod）
  - B：`Wide=true Continuity=true Epoch=true`（完整管线）
- **预期**：新仪器下逐位一致（DF 值 + WG 高度图哈希）。
- **若不同**：差异坐标/距离分布 + 差异路径记录，定位到具体补丁组。

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

记录见本文档上方的 "F0 噪声特征"、"F0-1/F0-2/F0-3 记录"（判定均为**未决**，待新仪器）。
旧记录（"F0-1 通过"）已作废：当时的"一致"不可复现（同配置 B3 vs B5 即不同）。
