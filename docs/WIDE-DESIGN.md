# 宽域/缝 设计账本

稳定里程碑 = B5 范围 + 稳定性修复（矿井跳过 / 生成限流 / 包节流 / 光照守卫 / 看门狗）。

## 已验证的技术结论

1. **沟的真实来源 = 含水层网格 int 回绕**，不是密度噪声。
   - `Aquifer$NoiseBasedAquifer` 构造器：`minGridX = gridX(chunkPos.getMinBlockX() - 5)`（int）
   - 2^31 处 `getMinBlockX()` 回绕 → 网格原点回绕 → 屏障噪声采样到不连续区域 → 深腔 + 岩浆 = 沟
   - `getIndex` 的 int 减法溢出已由 `AquiferIndexMixin`（long+clamp）修复，但**缓存填充循环**的坐标仍是 int
2. **密度宽域化救不了沟**，反而让"密度连续 + 含水层不连续"两个子系统互相撕扯。
3. **宽化必须全链路同船**：密度、含水层、光照 key、实体、tick 一次做完，不能分阶段半开。
4. **64 位打包 key 是硬墙**：SectionPos 22bit（2^26 块周期）、ChunkPos 32+32（2^31 区块）——2^63 需要 128bit 存储 key（光照/区块存储换 key 类型）。

## 重新设计（顺序 = 依赖序，不是进度序）

1. **含水层宽化**（沟的直接修复）：`minGridX/Y/Z` → long，缓存填充循环坐标从宽 chunk origin 推导，`getIndex` 保持 long。
2. **噪声采样双域**：NoiseChunk 宽 origin + getBlockXDouble 宽域（已实现过，正确）。
3. **存储 key 128bit**：光照（SectionPos）、区块存储（ChunkPos）的 key 从 64bit 打包 long 换成长对/BigInteger。
4. **消费者**：光照、实体、tick、结构逐个打通（用看门狗/jstack 兜底）。

## 关键 API 坐标（26.2）

- `Aquifer$NoiseBasedAquifer.<init>(NoiseChunk, ChunkPos, NoiseRouter, PositionalRandomFactory, int, int, FluidPicker)`
- `minGridX = gridX(chunkPos.getMinBlockX() - 5)`；`gridX(int)` = floorDiv(x, X_SPACING)*X_SPACING
- `getIndex(int x, int y, int z)` = `(j*gridSizeZ + k)*gridSizeX + i`
- `computeSubstance(ctx, d)` 用 `ctx.blockX/Y/Z()`（int）+ `globalFluidPicker.computeFluid`

## 存储 key 128bit（阶段 3 前置）

实际结构（已确认）：
- 光照：`DataLayerStorageMap.map: Long2ObjectOpenHashMap<DataLayer>`，key = SectionPos.asLong（27bit 重打包后 2^30 块）
- 区块：`ChunkMap.updatingChunkMap/visibleChunkMap/pendingUnloads: Long2ObjectLinkedOpenHashMap<ChunkHolder>`，key = ChunkPos.pack（32+32 = 2^31 区块）；`chunkTypeCache: Long2ByteMap`、`nextChunkSaveTime: Long2LongMap`

64 位 key 无法装下 2^63 的截面/区块坐标（需 59+59 位）。两条路线：

**A. 换 key 类型（真 128bit）**：这些 map 换成 `Object2ObjectOpenHashMap<Long128, T>`（自定义 128 位 key 类）。彻底、可持久化，但改动面大（含序列化/region 文件路径）。

**B. 滑动 epoch（64 位 key 复用）**：任意时刻只有当前 epoch 一个区域被加载；key 沿用 64 位、存储层挂 epoch 标记；玩家跨 epoch → 全刷（类似跨维度）。改动小、单人探索够用；代价是跨 epoch 重访=从种子重新生成、存档在 epoch 边界重叠。region 文件路径需按 epoch 打标记避免覆盖。

建议：2^63 里程碑用 **B**（快、可验证）；1.05e306 终局用 **A**。

