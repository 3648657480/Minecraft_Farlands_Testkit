# 修复路线图 & 交界点

原则：**每条修复线独立推进，每次只修到一个交界点；交界点之间的一切中间态用开关隔离，绝不混入主线。**

## 修复线

| 线 | 内容 | 入口点清单 |
|---|---|---|
| **A 稳定线** | 2^31 可玩不崩 | 矿井跳过、生成限流、包节流、光照守卫(long-abs)、看门狗、调试清理 |
| **B 容器宽化线** | int→long 容器 | Vec3i/BlockPos/ChunkPos(+$2)/SectionPos(27bit)/NoiseChunk 宽 origin |
| **C 统一投影线** | 单一投影函数 | FarProjection.unwrapX/Z 为唯一块域约定；chunkNorm(区块域)、FarLandsEpoch(截面域) 独立并存 |
| **D 生成连续性线** | 2^31 无缝 | 全局重定向 → RealContext(含水层/地表) → FarAquiferHelper(随机种子) → **待审计**：含水层网格 minGridX、surface rules、生物群系采样 |
| **E 存储 key 线** | 128bit key | 光照 DataLayerStorageMap、ChunkMap 三张图（滑动 epoch 方案 B 已设计） |
| **F 消费者线** | 光照/实体/tick/结构 | 光照守卫(过渡)、实体区段、tick 偏移、结构放置 |

## 交界点（可验证的里程碑）

```
J1  [A 完成]     稳定版：出生点正常、2^31 不卡不OOM、有缝但可玩
J2  [B+C 完成]   全部块域访问器路由 FarProjection；正常坐标区逐位零变化
J3  [D 完成]     2^31 ± 两侧地形连续、无沟、正负半轴各唯一
J4  [E 完成]     2^63 区块寻址不碰撞（滑动 epoch），光照在 2^30 内正常、以外安全降级
J5  [F 完成]     2^63 里程碑：光照/实体/tick/结构全通
```

## 隔离机制

- 每条线一个开关：`farlands.wide`(B) / `farlands.continuity`(D) / `farlands.storage128`(E)
- **只有"整线完成并通过交界点验收"才并入默认构建**
- 每到一个交界点 = 一个 git 提交（G1 需要 `git init`，以当前稳定态为 J1 提交锚点）

## 当前状态

| 线 | 状态 | 所在位置 |
|---|---|---|
| A | ✅ 完成 | J1 已验证（2026-08-15） |
| B | ✅ 完成 | J2 已验证（正常坐标零变化） |
| C | ✅ 完成 | J2 已验证（全部访问器路由 FarProjection） |
| D | ✅ 完成 | **J3 已验证（2026-08-18）：沟消失、双半轴唯一、碰撞正常、无 OOM/冻结** |
| E | 🔨 进行中 | 滑动 epoch：已实验 5 版（e1-e1e），混合域问题已定位（见下）；**部署态 = J3** |
| F | 📋 待排 | J4 后 |

## D 线遗留（记录在案）

- 流体交互在 ±20 亿外被守卫跳过（`EntityFluidInteractionFarGuardMixin`），
  根因是跨 2^31 的方块查询回绕——E 线滑动 epoch 落地后自然修复。
- unsigned 解释已全部移除（镜像/无碰撞根因），统一为有符号域。
  unsigned 只能撑到 2^32，不满足 2^63——E 线用滑动 epoch 替代。

## E 线实验记录（2026-08-18，5 轮）

设计目标：所有 int 域（区块/截面/方块/生成单元）相对 epoch 原点，
真实坐标 = epoch + 局部值。已落地并验证的机制：
- `FarProjection` epoch 状态（setEpoch/realBlockX/epochChunkX…）
- epoch 原点设定：传送（ServerPlayerEpochMixin）、进世界远区块请求重定
  （`farlands$recenterFromRequest`）、客户端包（ClientPacketListenerEpochMixin）
- 生成采样 epoch 公式（NoiseChunkRealCoordsMixin）
- 实体块访问器纪元相对（EntityMixin）
- 客户端 jar 探测（ChunkPos.farlands$epoch 标记）

已知的混合域问题（崩溃链）：
1. WorldGenRegion 中心=真实域 vs 地表规则查询=局部域 → 翻译后结构引用
   （真实域）被误译 → "Requested chunk unavailable"
2. 访问器级全量 rebase（asLong/x() 全部纪元相对）→ DistanceManager/
   出生点票据的真实坐标对不上 ChunkMap 局部键 → acquireGeneration NPE
3. 量级护栏（|x|<1M）不能区分"epoch 附近的局部坐标"与"世界出生点的
   真实坐标"——两者都小

下一轮 E1 方向（按可行性排序）：
A. 仅 rebase 方块域（getMinBlockX + 噪声单元），区块键保持真实（int
   能装到 ±2^35 方块），在 WorldGenRegion 边界做"最近邻 epoch"翻译
B. 先隔离"2^31+300 地形变化"的确切溢出点（J3 状态单点排查），再决定
   最小 patch 集
C. 完整滑动 epoch（动态重定位）作为 2^63 的最终方案，分阶段实施

## 下一次会话模板

1. 确认上一交界点的验收状态（用户测试结果）
2. 只做**当前线到下一个交界点**的活
3. 中途态全部挂开关，不碰主线默认构建
