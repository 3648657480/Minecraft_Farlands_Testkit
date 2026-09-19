# 基础知识复习（FarLands G1）

> 长期不碰项目后的快速回忆。每个概念一句话，细节看对应源码。

## 1. 项目目标

在 MC 26.2（Fabric）里让玩家在**真实坐标**（不缩放、不假坐标）下探索到
2^63（终局目标 1e306）。分阶段：

| 阶段 | 目标 | 状态 |
|---|---|---|
| 2^31-1 | 边界内完全稳定可玩 | ✅ 已达成（J3） |
| 2^31+ | 跨过 int 边界继续真实探索 | 🔨 E 线进行中 |
| 2^53 | double 精度极限（地形开始量化） | 远景 |
| 2^63 / 1e306 | 终极目标 | 远景 |

## 2. 三个域（最重要的概念）

| 域 | 定义 | 谁在用 |
|---|---|---|
| **real 真实域** | long / double，世界的真实坐标 | 玩家位置（double）、噪声采样、F3 显示、TP 目标 |
| **local 局部域** | int，= real - EPOCH | 引擎全部 int 域：区块键、截面、方块、生成单元、门票、任务缓存 |
| wrapped 回绕域 | int 溢出后的值 | J3 时代的遗留概念，已被 local 取代 |

**铁律（域账本）**：动任何坐标代码前，先写下"这个值现在在哪个域、
消费者期望哪个域"。历史上所有崩溃都是混合域造成的。

## 3. 纪元（EPOCH）

- **EPOCH** = 世界原点的真实坐标（double 常量，如 2147481648）
- 关系：`real = EPOCH + local`
- **固定纪元**：一个会话内不变（世界加载时设定，之后不重定位）
- 激活条件：`EPOCH != 0`（`FarProjection.isEpochActive()`）
- **生成采样**：噪声用 real（`epochBlockX + local`），方块放置用 local
- **存档翻译（E2）**：离线把世界整体平移（region + level.dat + 玩家数据），
  让远侧世界进入 int 范围
- **自动重定位（E3）**：边界检测 → halt → 翻译 → openWorld 重载（伪无缝跨越）

## 4. 修复线（J 里程碑）

| 线 | 内容 | 状态 |
|---|---|---|
| A / J1 稳定线 | 2^31 不崩不卡不 OOM | ✅ |
| B 容器宽化 | Vec3i/BlockPos/ChunkPos/SectionPos int→long | ✅ |
| C 统一投影 | FarProjection 单一约定 | ✅ |
| D / J3 生成连续性 | 2^31 两侧无缝、无沟、双半轴唯一 | ✅ |
| E 纪元线 | local 域、2^31+ | 🔨 进行中 |
| F | 光照/实体/tick/结构（J5） | 待排 |

## 5. 为什么 2^31 是个坎

- int 最大 2^31-1 = 21 亿；区块坐标 = block/16 → 2^27 chunk
- 区块键 `pack(x,z)` = long（**x 低 32 位、z 高 32 位**）
- 超过 2^31 → int 溢出 → 键回绕/碰撞 → ChunkMap/门票/任务缓存错乱
- **local 域**把远侧坐标映射回小值 → int 安全 → 这是整个 E 线存在的理由

## 6. 关键组件

| 组件 | 位置 | 作用 |
|---|---|---|
| FarProjection | `mod/.../util/` | 纪元状态 + unwrap + 域换算 |
| patcher-cli | `patcher-core/` | 字节码补丁（改原版 jar，如 ChunkPos 宽化/标记） |
| mod | `mod/` | mixin（运行时行为，不改 jar） |
| server-test.ps1 | `tools/` | 无头服务器测试环（rig） |
| chunk-translator | `tools/chunk-translator/` | 存档平移工具（E2） |
| mc-command bridge | 客户端 mod | 游戏内控制（tp/命令/截图，客户端测试用） |

## 7. 当前 E 线的域账本（2026-09 状态）

```
世界 = 全 local 域（单机）
├─ 玩家位置 = local（double 小值）
├─ level.dat spawn = local（(0,100,0)，SetInitialSpawnEpochMixin 跳过原版搜索）
├─ 区块键/门票/任务 = local（ChunkPosEpochPatch = 标记 only，字段即 local）
├─ 生成采样 = real（NoiseChunk.unwrap = epochBlockX + local）
├─ 渲染 = local（floating origin）
└─ F3 显示 = real（epochBlockX + local）
```

**已知的坑**（复活时的检查清单）：
- 任何"出生点/玩家数据/TP 坐标"进入引擎前必须转成 local
- `setInitialSpawn`、`PrepareSpawnTask`、`PlayerSpawnFinder` 三个 spawn 入口
  都要 local 化（对应三个 mixin）
- 存档翻译后 level.dat 的 spawn/玩家数据都要平移（players/data 不是 players/！）
