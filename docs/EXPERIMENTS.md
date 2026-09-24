# 实验协议与记录（S0 / F0）

> 版本 1.0.0（权威：仓库根目录 VERSION）
> 适用红线：R5、R8、R9。本文档是地形生成器深层调试域的**唯一**实验规范与记录。
> 违反任一前提的实验结论一律作废。

---

## 1. 固定参数（所有实验共用，不得随意变更）

| 项 | 值 |
|---|---|
| 种子 | `12345`（两侧 server.properties 的 `level-seed`） |
| MC 版本 | 26.2（Fabric Loader 0.19.3） |
| 原版参照 | `vanilla-rig` 子项目（无补丁 jar + 同款测量桩） |
| 坐标集 | P0-P4（下表） |
| 记录 | 编号/假设/参数/预期/实测/结论/证据（§6 模板） |

| 点 | 区块坐标 | 方块坐标 (x,z) | 说明 |
|---|---|---|---|
| P0 | (0,0) | (8,8) | 原点/出生区 |
| P1 | (62,62) | (1000,1000) | 1e3 |
| P2 | (62500,62500) | (1000008,1000008) | 1e6 |
| P3 | (625000,625000) | (10000008,10000008) | 1e7 |
| P4 | (1812500,1812500) | (29000008,29000008) | 2.9e7 |

testgen 规格串：`0,0;62,62;62500,62500;625000,625000;1812500,1812500`

> **种子是变量**：§8 的远域现象与**种子强相关**（"起点坐标"随种子浮动）。每条现象记录必须写明
> 当时的 `level-seed` 与 epoch；不写种子的现象记录**不可复现**（R8/R9）。

---

## 2. 工具与协议

| 工具 | 用途 | 用法 |
|---|---|---|
| `tools/exp-run.ps1` | 生成固定区块集 → 沉降 → 保存 → 退出 → 移出世界 | `-Tag <标签> -OutDir <目录> -TestGen "<规格串>" -Rig mod\|vanilla [-Delay 300] [-Settle 400] [-BgThreads 1] [-Extra "-Dfarlands.xxx=..."]` |
| `gradlew :mod:worldDiff` | 区块字节对比（**仅粗筛**，见下） | `-PworldA=<目录> -PworldB=<目录> -Preport=<文件> --no-daemon` |
| **WG 指纹** | **有效仪器**：生成期（`getChunk` 后立即）计算 `WORLD_SURFACE_WG` + `OCEAN_FLOOR_WG` 全网格与 4×4 生物群系网格的 SHA-256，打印到日志 | testgen 自动输出 `wgHash=surf=... floor=... biome=...` |

### 2.1 仪器有效性（2026-09-22 结论）

- **字节级区块对比已证伪**：保存的区块内容跨运行不可复现（同配置也出现 3/5 不同；
  并行/单线程、有无延迟/沉降、有无随机刻、单/多目标都试过）。差异集中在特征层
  （水草年龄、樱花树布局、block_ticks、后处理），**不能**用于等价性判定，只作粗筛。
- **WG 指纹有效**：同配置重复运行逐位一致；差异只可能来自生成数学本身。

### 2.2 运行协议（降低噪声用）

| 要求 | 值 |
|---|---|
| 后台执行器单线程 | `-Dmax.bg.threads=1` |
| 生成延迟 | `Delay=300` tick（避开出生区并发） |
| 沉降窗口 | `Settle=400` tick |
| 关闭随机刻 | harness 设 `random_tick_speed=0` |
| 保存 tick | delay+settle（固定） |

**重要（dev 缓存）**：dev jar 的补丁组由 `G1JarProcessor.Spec` 缓存；缓存键 =
`wide/continuity/epoch/unlock` + `FarLandsPatcher.PATCH_REVISION`。
**任何补丁集改动都必须 bump `PATCH_REVISION`**，否则 loom 会复用旧 jar（曾因此误判补丁是否生效）。
若结果可疑，看构建日志 `[FarLands-G1] Scanned ... patched ...` 行确认补丁真的应用了。

### 2.3 无条件补丁集（flags 全关也生效）

`FunctionContextRealPatch.noiseOnly`（Noise/ShiftedNoise 坐标重写）、`Vec3iPatch`、
`GsuPatch`、`AabbClipPatch`、`BlockCollisionsPatch`、`BoundingBoxPatch`、
`ClientChunkCachePatch`、`ClientChunkCacheStoragePatch`、`ViewAreaPatch`、
`DebugEntryPositionPatch`、`SectionOcclusionGraphPatch`、`WgrPatch`。
**flags 全关 ≠ 原版**；"完整管线 vs 原版"必须用 vanilla rig。

---

## 3. F0 干扰基线：设计与结果

### F0-1 jar 补丁组隔离（wide/continuity/epoch）

- **假设**：三个补丁组在 vanilla 范围内对地形生成是 no-op。
- **方法**：同种子同坐标集，`Wide/Continuity/Epoch` 全关 vs 全开。
- **结果（2026-09-22）**：M1（全开）vs M2（全关），**5/5 目标点 WG 指纹完全一致** → **通过**。
- **结论**：wide/continuity/epoch 在 P0-P4 对噪声→高度→生物群系无影响。
- 证据：`exp-F0-4-M1.log` / `exp-F0-4-M2.log`。

### F0-2 完整管线 vs 原版

- **假设**：完整管线（补丁 + mod）在 vanilla 范围内与原版逐位一致。
- **结果（2026-09-22/23，经二分定位与修复）**：

| 阶段 | floor（固体地形） | surf（含流体/植被表层） |
|---|---|---|
| 修复前（M8，全配置） | ≠ 原版（`9f242b6d9ef2`） | ≠ 原版 |
| **BlockPos 修复后**（FIX1/PROD1/PROD2） | **= 原版（`81d0572280b2`，两轮一致）** ✅ | 两轮不同（`bc1b…`/`5c1d…`）⚠ |
| 禁用 epoch+border 配置（BIS4） | = 原版 ✅ | = 原版 ✅（bit-exact） |

- **根因与修复**：
  1. **`BlockPosMixin`（真 bug，已修复）**：其 `<clinit>` 重定向把 `PACKED_HORIZONTAL_LENGTH` 从 25 加宽到 26，
     改变了 `BlockPos.asLong` 的打包布局 → **hashCode 全变** → 所有 HashSet/HashMap<BlockPos> 的迭代顺序改变
     → 特征放置顺序改变（可复现）。修复：删除该重定向（vanilla 打包已覆盖 ±33,554,431；更远走 handle 回退）。
  2. **epoch 出生链 mixin（设计性偏差）**：`SetInitialSpawnEpochMixin` 等把出生点从原版的
     `(96,136,-32)` 改为 `(0,100,0)`（跳过原版出生搜索），出生区生成队列不同 → 表层（流体/植被）放置顺序
     在两轮间波动。**这是架构的设计行为**，不是地形数学问题。
  3. **`WorldBorderMixin`（已收敛）**：移除 `getSize()`/`getAbsoluteMaxSize()` 覆盖（正常坐标唯一可见差异），
     保留 `isWithinBounds`（正常坐标本来就是 true）。二分证据：BIS4（去掉两个 border mixin）= bit-exact 原版。
- **判定**：**固体地形数学 = 原版（修复后，可复现）**；表层波动源自设计性的出生点变更；
  禁用 epoch/border 配置时全管线 bit-exact 原版。
- 证据：`exp-F0-6-FIX1/PROD1/PROD2/BIS2/BIS3/BIS4/BIS5/BIS6.log`、`report-FIX1-vs-M9.txt`。

### F0-2 二分过程记录（2026-09-23）

| 编号 | 配置 | P3 floor | P3 surf |
|---|---|---|---|
| BIS2 | world+test | = 原版 | = 原版 |
| BIS3 | core+world+test | ≠ | ≠ |
| BIS4 | core（去两个 border）+world+test | = 原版 | = 原版 |
| BIS5 | core（去 Extent）+world+test | ≠ | ≠ |
| BIS6 | core（border 仅 isWithinBounds）+world+test | ≠ | ≠ |
| FIX1/PROD1/PROD2 | 全配置 + BlockPos 修复 | = 原版 | 波动 |

（BIS3/BIS5/BIS6 的 surf 波动即上文"表层顺序敏感"；floor 的差异在 BlockPos 修复后消除。）

### F0-3 自有配置变量 no-op

- **假设**：`worldgen_sample_mode=clamp` 在阈值内（|coord| < 2^53）对地形是 no-op。
- **结果**：旧区块对比结果（2/5、4/5 不同）**因仪器无效而作废**；待用 WG 指纹重做。
- **状态**：⏸ 待重做（优先级低于 F0-2 归因）。

---

## 4. 静态审计结论（无条件集嫌疑）

| 对象 | 结论 |
|---|---|
| `Vec3iPatch` | 新增 `getRealX/Y/Z` 访问器，**被 `AabbClipPatch` 调用**（改 `AABB.clip` 的 move 参数）→ 正常坐标下为恒等，无影响 |
| `GsuPatch` | 客户端渲染器（GlobalSettingsUniform）→ 与地形无关 |
| `WgrPatch` | 已改为 `isEpochActive()` 门控（原 `\|centerChunk\|>134M` 守卫在 local/epoch 域永不触发，且因幂等检测写错实际从未注入——见 WgrPatch 修复提交）。正常坐标下只是"查不到 chunk 返回 center"的兜底，不改变生成 |
| `BoundingBoxPatch` | 正常坐标下 `minX/maxX` clamp 为 no-op；`getLength` 与原版一致（无 +1）；Beardifier 只用 `minX/maxX/isInside` → 正常坐标无影响（`getXSpan` 的 [1,256] clamp 仅在跨度 >256 时有别） |
| `NoiseChunkMixin`（Aquifer 包装） | 无异常时纯委托 → 行为保持 |

**未排除**：`FunctionContextRealPatch.noiseOnly`（坐标重写）、mod 的 `NoiseChunkRealCoordsMixin`，
以及 B 线真实坐标化的常驻 mod mixin 组。
注：`SurfaceSystemProbeMixin`、`ChunkMapEpochMixin`、`GenerationChunkHolderMixin` 从未注册进任何
`farlands-*.mixins.json`，属死代码，已随清理批次删除。

---

## 5. 待归因：二分计划（已完成，2026-09-23）

1. ~~仅 jar 补丁（无 farlands mod）~~：不可行（补丁调用 mod 提供的接口方法）；已用 shim 变体 J3 证明
   **无条件 jar 补丁集无罪**（J3 的 P3 = 原版）。
2. ~~仅 mod（无 jar 补丁）~~：改为配置级二分（BIS2-BIS6）+ 方法级定位，见 §3 F0-2。
3. 结论：**`BlockPosMixin` 打包加宽 = 真 bug（已修复）**；`WorldBorderMixin` 已收敛；
   epoch 出生链 = 设计性偏差；**BIS4（禁用 epoch+border）= 全管线 bit-exact 原版**。

---

## 6. 记录模板

```
## <编号> <标题>
- 日期：
- 假设：
- 参数：种子/坐标集/配置/JVM flags/协议
- 版本标记：<两侧的构建/补丁报告行>
- 预期：
- 实测：<WG 指纹（surf/floor/biome）或报告摘要>
- 结论：
- 证据：<日志路径、报告路径、世界目录>
```

---

## 8. 远域现象表（本项目实验条件下的实测结果）

> **表述原则（R10）**：本表全部数值均为**特定实验条件下的特定结果**，不构成对 Minecraft Wiki
> 或其他社区记录的否定、纠正或"更准确"的声称。外部记录基于其自身的实验条件（版本、实现、
> 测量方法）；本项目数据来自自研管线（epoch/local 域、特定版本与位置），两者不可直接比较。

| 坐标（真实） | 现象 | 机制 | 验证 |
|---|---|---|---|
| 2^53 = 9,007,199,254,740,992 | 症状起点：1 格分辨率采样量化为 2 格成对；地表正常、地下细微 | double ulp = 2 | 本项目条件下已测试 |
| 2^54 = 18,014,398,509,481,984 | 量化步长 = 采样格宽（4 格），仍以自然形态为主 | ulp = 4 | 本项目条件下已测试 |
| 2^55 = 36,028,797,018,963,968 | 主地形量化：规则阶梯/条带（奶酪状） | ulp = 8 > 格宽 4 | 本项目条件下已测试 |
| 2^56 = 72,057,594,037,927,936 | 平板状结构 + 水面瓷砖（16 格） | ulp = 16 | 本项目条件下已测试 |
| 2^63 = 9,223,372,036,854,775,808 | 同质拼图（128 chunk 均匀块） | ulp = 2048 | 本项目条件下已测试 |
| **1,808,764,368,950,000,000,099,728**（≈1.80876436895×10^24） | 本项目条件下测得的"主地形改变/条板墙结构"起点（**种子 `-7820689566140440746`**） | 疑似 `PerlinNoise.wrap()` 取整失效（待归因） | 本项目条件下实测（人工二分） |
| >2.43×10^27 | 本项目条件下地形未终止，仅高度重复 | — | 本项目条件下实测 |

**级联性质（本项目条件下的观察）**：在本管线的实验条件中，不存在单一"起点"——各噪声 wrap
失效阈值不同，按层先后崩坏（地下先、地表后）。因此本项目的记录方式为分层阈值，而非单点。

**与 Wiki 记录值的对照**：Wiki 记录 1,808,764,368,955,220,466,364,897；本项目实测
1,808,764,368,950,000,000,099,728。两者相差 5.22×10^12 格。**该差异不作优劣判断**——
Wiki 记录基于其自身实验条件，本项目结果基于自研管线，二者不可直接比较（见 R10）。
**注意**：该差距的一部分也可能来自**种子不同**（见 §8.1）——对照只有在同种子同 epoch 下才有效。

### 8.1 "精确起点坐标"是浮动的（不存在普适值）

- **固定层（与种子无关）**：量化档位只由**量级**决定——2^53 起 ulp=2、2^55=8、2^56=16、2^63=2048…
  （double 精度效应，任何种子相同）。
- **浮动层（与种子绑定）**：某个量级上"看不看得见条带/墙结构"取决于**噪声场**，而噪声场由**种子**决定
  → **视觉上的"起点坐标"随种子浮动**。上表 `1,808,764,368,950,000,000,099,728` 是
  **种子 `-7820689566140440746`** 条件下的结果；换种子会挪位。
- **第三变量（epoch 精确值）**：采样 = `epoch.double + local`；在 1.8e24 处相邻方块被压进同一 ULP
  （≈2.68×10^8 格），"结构从哪一格出现"还取决于 epoch 取的那个精确数值。
- **结论**：不存在普适的"精确起点坐标"。可复现的只有"**量级阈值**（种子无关）" +
  "**固定（种子, epoch, 管线）下的具体坐标**（种子相关）"。

### 8.2 随机种子下怎么二分找"起点"

前提：**在同一世界内**测（种子固定）；候选坐标一律用 `/realtp <x> 100 0` 传送，再用 F3 的
`Real double ULP` 行读该量级的 ULP，目视判定"是否已出现条带/墙结构"。

1. **定区间**：取一个已知"只有循环/未开始"的量级 `lo`，和一个已知"已出现结构"的量级 `hi`
   （例如 `lo=2^80≈1.2089×10^24`，`hi=2^81≈2.4179×10^24`）。
2. **取中点并对其 ULP 对齐**：`mid=(lo+hi)/2`，把 `mid` 取整到该量级 ULP 的整数倍
   （1.8e24 处 ULP≈2.68×10^8，故最小可分辨粒度就是这一个 ULP）。
3. `/realtp mid 100 0` 观察：
   - **已出现结构** → `hi = mid`
   - **未出现** → `lo = mid`
4. 重复 2–3，直到 `hi - lo` 缩小到 **1 个 ULP**（该量级能达到的判定精度）。
5. **记录**：`种子`、`epoch(=该坐标)`、该量级 `ULP`、判定截图/描述。**换种子必须整段重做。**

注意：
- 判定是**目视**（"结构" vs "循环"）；不同判定标准的边界可能差 1–2 个 ULP，记录里必须注明判定标准。
- 不要走过去——必须 `/realtp` 到目标真实坐标并把该坐标作为 epoch，保证采样 = 该坐标的 double 值。

**F3 显示（本项目 1.0.0）**：XYZ/Block/Chunk 显示真实坐标（超 double 精度用 BigInteger 精确值）；
另有 `Local (in-epoch)`、`Epoch ... Laps (2^31)`、`Real double ULP` 行。

---

## 9. B（整数子系统真实坐标化）记录

### B-1 末地岛屿密度（`EndIslandDensityFunction`）—— 通过

- 日期：2026-09-24
- 假设：把 `getHeightValue(islandNoise, blockX()/8, blockZ()/8)` 的 **int local section**
  换成真实 section（宽版），远域不再按 local int 重复；正常坐标逐位零变化。
- 参数：种子 `12345`；flag wide/continuity/epoch=true；`-Dim the_end`；
  `-Dfarlands.spawnset=<epoch>,100,0`；`Settle=200`；WG 指纹仪器（§2）。
- 版本标记：`[FarLands-G1] 1.0.0 epoch build`；`[FarLands-Test] dimension=minecraft:the_end`。
- 预期：正常 epoch=0 与 vanilla-rig 逐位一致；epoch 2^32 vs 2^33 指纹不同。
- 实测（WG 指纹，surf=floor）：
  - 正常：mod (0,0)=`4f2ee214f69f`、(62,62)=`5f4ecdb7b71c`、(25000,25000)=`5f4ecdb7b71c`
    = vanilla-rig；`worldDiff -Pdim=the_end` IDENTICAL（3/3 full 相同，combined hash 同）。
  - 远域：epoch 2^32 → (0,0)=`d6f4a44b7018`、(62,62)=`da1504748017`；
    epoch 2^33 → (0,0)=`8813938d4b2c`、(62,62)=`b0b9fdb53ac2`（不同）。
  - A/B（临时移除 `DensityFunctionsEndIslandMixin`，E=2^32）：(62,62)=`5f4ecdb7b71c`
    （= epoch=0）→ 未补丁时 local 重复。
- 数值探针（`:mod:endIslandProbe`）：正常 section（含 int 溢出区）3 种子 72033 点 0 mismatch；
  `2^28` 走 vanilla-int、`2^28+1` 走 wide；广域 epoch 2^32/2^33 宽版不同、local 版相同。
- 修正：原纯 double 版在 `section ≥ 32768`（vanilla int 平方溢出，末地世界边界内）与 vanilla 不一致；
  改为 `|section| ≤ 2^28` 调 vanilla int 版、超出才 wide（`EndIslandMath`）。
- 结论：本项目条件下，末地岛屿密度已真实坐标化；正常坐标（vanilla 全部可达范围）逐位零变化，
  远域（epoch 2^32/2^33）不再周期。**不构成对外部记录的否定/更正**（R10）。
- 证据：`exp-B-end-norm2-{mod,van}.log`、`exp-B-end-E232b.log`、`exp-B-end-E233b.log`、
  `exp-B-end-E232-nomix.log`、`report-B-end-norm2.txt`；代码 `EndIslandMath` + `DensityFunctionsEndIslandMixin`。
- 待办：carver/feature（增量 2/3，代码层已改）按同样协议做无头验收。
