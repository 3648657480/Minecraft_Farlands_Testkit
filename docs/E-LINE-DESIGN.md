# E 线收官设计：单一纪元域（2^31-1 稳定 + 通向 2^63）

## 0. 问题复盘（几十轮实验沉淀的认知）

### 0.1 三个域与它们的冲突

| 域 | 表达 | 消费者 | 现状 |
|---|---|---|---|
| 真实域 real | long / double | 玩家位置、生成噪声采样、TP 目标 | ✅ 正常 |
| 回绕真实 wrapped-real | int | J3 时代的全部 int 域 | ✅ ±2^31-1 全通 |
| 纪元局部域 local | int（= real - EPOCH） | E1 以来逐步接入 | ⚠️ 接入了一半 |

**根本矛盾：world 里同时存在两个 int 域，键碰撞。**

- 出生点区块：J3 时代按 **真实域** 存储（asLong = pack(真实)）
- 远侧区块（E1 后）：按 **局部域** 请求/存储（asLong = pack(局部)）
- **真实 (0,0) 与 局部 (0,0) 的 pack 结果相同** → ChunkMap/门票/任务缓存里两个不同区块抢同一个键 → 覆盖/串扰 → 依赖竞态崩溃

### 0.2 全部已观察到症状 → 单一根因

| 症状 | 机制 |
|---|---|
| 穿越 2^31-1 地形重载 | 纪元激活瞬间，键域切换，已加载区块"消失"重拉 |
| 坐标变大仍显示旧地形 | 客户端渲染区块键 = 真实域，越界回绕 → 读回 <2^31-1 的区块 |
| tp 2^32 直接崩 | 32 位整周回绕，键饱和/碰撞 → 依赖链碎 |
| surface 阶段 "Requested chunk unavailable" | 邻居的 structure_starts future 被门票清除（键域不一致，claim 挂不上） |
| acquireGeneration NPE（E1q） | 任务缓存（局部键）查 ChunkMap（真实键）→ 空 |

**结论：所有崩溃/错乱 = 同一个病：键域不统一。**

## 1. 设计原则

**整个世界（一个会话内）只活在单一局部域里：**

```
local = real - EPOCH
real = EPOCH + local
```

- **EPOCH = 世界出生点区块的方块坐标**，加载时一次性设定，**会话内不变**（"固定纪元"，不是滑动重定位）。
- 所有 int 域：区块键、截面、方块坐标、生成单元、实体块访问器、渲染截面、门票、任务缓存 —— 全部 local。
- 真实域只出现在**边界**：玩家位置（double）、网络包、F3 显示。
- 2^31-1 稳定 = 这个架构的直接结果（世界 = 一个 local 域，无碰撞、无切换）。
- 2^63 = 未来"纪元切换"（世界边缘再选新 EPOCH + 重键）——E2 设计，本方案不覆盖。

**为什么固定纪元可行：**
- 出生点 local ≈ 0，远侧 2^31 处 local ≈ 2^31（int 装得下，±2^31 区块键范围 = ±2^35 方块 ✓）。
- 整个生命周期一个 EPOCH，无重键、无切换、无竞态窗口。

## 2. 域账本（改动后的全貌）

| 环节 | 域 | 说明 |
|---|---|---|
| EPOCH 设定 | — | 服务端加载世界时，从 level.dat 出生点读取（真实），`setEpoch(spawn)` |
| ChunkPos.x()/z()/asLong()/hashCode/equals | local | 访问器级 rebase（E1q 的补丁，**前提是 EPOCH 先于一切区块加载设定**） |
| ChunkPos.xLong()/zLong() | real | 保留（生成/边界用） |
| getMinBlockX/Z、getMaxBlockX/Z | local | 访问器 rebase 自动覆盖（无需独立 epochMinBlock 补丁） |
| SectionPos 相关 | local | 随区块键自动 local |
| 实体块访问器（EntityMixin.getBlockX 等） | local | 已做 ✓ |
| 生成噪声采样 getBlockXDouble | real | = EPOCH + local（unwrapX 的纪元分支，已做 ✓） |
| 世界生成区域 WorldGenRegion | local | 中心 = 区块 pos（local）→ 全 local，**无需翻译补丁**（删掉 WorldGenRegionEpochPatch） |
| ChunkMap / DistanceManager / 门票 | local | asLong rebase 后自动统一 |
| 任务缓存 StaticCache2D | local | 随请求域统一 |
| 渲染（ViewArea/相机→截面） | local | 相机位置（real double）→ 截面需减 EPOCH（新补丁） |
| 网络包（服务端↔客户端区块坐标） | local | 双方同 EPOCH（单机集成 ✓；联机需协议带 EPOCH，E2） |
| 玩家位置 / TP / F3 | real | 保持 |

## 3. 施工步骤（每步可无头验证）

### 步骤 1：EPOCH 提前设定（关键前提）
- 在**服务端世界加载的最早时刻**（MinecraftServer 启动 level 之前 / ServerLevel 构造），读 `level.getLevelData().respawnData`（出生点 GlobalPos）→ `setEpoch(spawnBlockX, spawnBlockZ)`。
- 删除所有"运行时重定位"：ServerPlayerEpochMixin（tp 时重设）、recenterFromRequest（请求时重设）、ClientPacketListenerEpochMixin（包时重设）——**全部移除，只保留加载时一次设定**。
- isEpochActive()：**EPOCH != 0 即活跃**（不再用 2e9 阈值）——因为全流程都 local，出生点也是 local，阈值判断不再需要。
- 验证：无头 rig 出生点生成正常（local=real-出生点，偏差 = 出生点坐标，等价）。

### 步骤 2：存储域全量 rebase
- 恢复 E1q 的 ChunkPosEpochPatch（访问器级：GETFIELD+L2I → epochChunkX/Z），加 farlands$epoch 标记。
- **前提 = 步骤 1**（EPOCH 在一切区块加载前设定）→ 区块从第一块起就是 local 键 → 无混合、无 acquireGeneration NPE、无键碰撞。
- 验证：无头 rig 出生点 + 远侧（134217750 local = real-出生点）生成正常；无崩溃。

### 步骤 3：渲染域 rebase
- 相机位置（real double）→ 截面坐标处减 EPOCH：
  - ViewArea.repositionCamera 的相机截面、LevelRenderer 的相机相关、RenderSection 的定位 —— 找到所有"相机 double → 截面 int"的转换点，统一走 `localSection = blockToSectionCoord(floor(camX) - EPOCH)`。
  - atLowerCornerOf（Vec3RealCoordsMixin）：纪元分支改为 `new Vec3(localX, y, localZ)`（局部，不 ×16、不加 EPOCH——渲染器内部全局部）。
- 验证：客户端（dev client）tp 远侧，看到真实地形（不再回绕显示旧地形）。

### 步骤 4：客户端区块键（包处理）
- 客户端收到区块包（真实 or 服务端同 EPOCH 的 local）——单机集成双方同 EPOCH，键自然一致，无需改动（验证即可）。
- 若服务端/客户端 EPOCH 不同步 → 显示错乱 → 排查同步点（步骤 1 的服务端 + 客户端各自从 level.dat 读，天然一致）。

### 步骤 5：回归 + 交界点验收
- 出生点零变化（local 与 real 的差 = 出生点常量，所有相对运算等价）。
- tp 2147483000（2^31-1 边界内）：稳定，J3 行为。
- tp 2147484000（边界外）：真实地形、碰撞、渲染正确。
- tp 4294967296（2^32）：不再崩（键 = local 全程合法）。
- 通过 = **J4 候选**，提交。

## 4. 删除清单（简化架构）
- WorldGenRegionEpochPatch（翻译不再需要——域统一）
- ServerPlayerEpochMixin、ClientPacketListenerEpochMixin、recenterFromRequest
- isEpochActive 的 2e9 阈值（改为 != 0）
- epochMinBlockX/Z（被访问器 rebase 取代）
- epochChunkDeltaX/Z、epochRealChunkX/Z、epochTranslatedChunkX/Z（被统一域取代）

## 5. 风险与对策
| 风险 | 对策 |
|---|---|
| 出生点 local 键与真实键行为差异 | 相对运算等价；rig 出生点全量回归 |
| EPOCH 设定时机太晚（已有区块加载） | 在 ServerLevel 构造内、首个区块请求前设定；无头测试验证"第一块就是 local" |
| 渲染域遗漏的 double→int 转换点 | 步骤 3 用 dev client 视觉验收；F3 显示真实坐标核对 |
| 2^63 的纪元切换 | E2 单独设计（重键算法），本方案保证 2^31-1 稳定 + 2^32 不崩 |

## 6. 为什么这次不会再金丝雀失败
- 单一根因（键域不统一）一次性解决，不是打补丁
- 每步有独立验证（无头 rig / dev client）
- 删掉的代码 = 消除复杂度，不是堆新逻辑
- 域账本 = 施工图，改动前对照，避免再次混合域
