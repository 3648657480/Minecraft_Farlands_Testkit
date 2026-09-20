# 基础知识复习（FarLands G1）

> 当前架构版（2026-09-20）。每个概念一句话，细节看对应源码。

## 1. 项目目标与进度

在 MC 26.2（Fabric）里让玩家在**真实坐标**（不缩放、不假坐标）下探索到 2^63（终局 1e306）。

| 阶段 | 状态 |
|---|---|
| 2^31-1 内完全稳定 | ✅ J3（2026-08） |
| **2^31-1 边界跨越**（内外地形连贯 + AABB） | ✅ E4（2026-09-19） |
| **任意距离传送**（归档重定位） | ✅ E5（2026-09-20） |
| **BigInteger 精确坐标**（任意大） | ✅ E5 |
| **精度现象验证**（2^53/2^63/1e306） | ✅ 实测 |
| 实体位置真实化（2^53 移动现象） | 📋 待做 |
| 超 double.MAX（>1.8e308）真实地形 | ⚠️ 现象（Infinity 固定值） |

## 2. 坐标架构（三个域）

| 域 | 表达 | 用途 |
|---|---|---|
| **real 真实域** | BigInteger（精确）+ double（近似） | 玩家输入、显示、重定位计算 |
| **local 局部域** | int（= real - EPOCH） | 引擎全部 int 域（区块键/门票/任务/存档） |
| **EPOCH 纪元原点** | BigInteger 存储 + double 采样视图 | 真实 ↔ local 的换算基准 |

**数据流**：
```
玩家输入（真实坐标字符串/BigDecimal）
  → /realtp 判定：窗口内直接 tp / 超窗口归档重定位
  → local = real - EPOCH（引擎 int 安全）
  → 生成采样 = EPOCH.double + local（double 噪声——精度现象在此发生）
  → 显示 = EPOCH.big + local（BigInteger 精确）
```

**关键常量**：
- 窗口 = `EPOCH ± (2^31 - 1e5)`（local int 安全范围）
- `isEpochActive` = epoch 已初始化（哪怕 0）——新世界自动初始化

## 3. 精度现象坐标表（实测）

| 坐标 | ulp（格） | 现象 |
|---|---|---|
| 2^52（4.5e15） | 1 | 完全正常 |
| **2^53（9.007e15）** | 2 | **地形急停点**（相邻格采样合并） |
| 2^56（7.2e16） | 16 | chunk 内同质 |
| **2^63（9.223e18）** | 2048 | **64 位边境之地**（128 chunk 同质块拼图/水晶柱） |
| 1e306 | ~1e290 | 完全冻结 |
| 1.8e308（double MAX） | ~2^1024 | 水柱世界（水平塌缩+垂直正常+海洋填水） |

## 4. 机制清单

| 机制 | 实现 | 备注 |
|---|---|---|
| epoch 存储 | `FarConfig`（`world/farlands.properties`，BigInteger） | 自动创建（新世界 = 原点） |
| 窗口内 tp | `/realtp`（BigDecimal 解析 → local 换算） | 支持 1e1000+ |
| 超窗口重定位 | **归档式**（`world/farlands_epochs/<key>/`） | 起点归档 + 终点恢复 + 中途不生成 |
| 走路自动重定位 | `auto_relocate`（平移模式） | 视觉连续；可配置关闭 |
| 生成采样 | `FarProjection.unwrapX/Z` = EPOCH + local | double 精度现象在此 |
| 显示 | F3 `Real position` + `Real (exact)`（BigInteger） | %.6g 防饱和 |
| 碰撞 | `collisionX/Z`（epoch 激活 = local） | 修复方块盒被移到纪元原点 |
| 边界跨越 | `epochInitialized` + local 域全程 | E4 里程碑 |

## 5. 常用命令与配置

```
/realtp <x> <y> <z>              自己（真实坐标，支持 1e1000）
/realtp <targets> <x> <y> <z>    实体（@p/@e/...，命令方块可用）
```

`world/farlands.properties`：
```properties
epoch_x=10000000000        # 精确（BigInteger 完整数字）
epoch_z=0
auto_relocate=true         # true=走路到边缘自动重定位；false=只提示
relocate_margin=100000     # 触发/提示距离（格）
```

JVM 参数覆盖：`-Dfarlands.spawnset=x,y,z`、`-Dfarlands.auto_relocate=...`、`-Dfarlands.relocate_margin=...`

## 6. 已知限制

- **> 1.8e308**：double 溢出 = Infinity → 噪声 (long) 饱和 → 固定值（现象，非真实地形）
- **实体位置 = local**（未真实化）——2^53 的移动精度现象不存在
- **渲染 = local**（floating origin）——精度现象只在采样层（地形塌缩），顶点渲染仍平滑
- **超远单次重定位**：平移 > 2^31 chunk 时走丢弃模式（无法保留起点内容——归档模式可保留）
