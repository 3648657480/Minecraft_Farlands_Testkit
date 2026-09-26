# 基础知识复习（FarLands G1）

> 版本：见仓库根目录 `VERSION`（唯一权威）
> 当前架构版（2026-09-23）。每个概念一句话，细节看对应源码。

## 1. 项目目标与进度

在 MC 26.2（Fabric）里让玩家在**真实坐标**（不缩放、不假坐标）下探索到 2^63（终局 1e306）。

| 阶段 | 状态 |
|---|---|
| 2^31-1 内完全稳定 | ✅ J3（2026-08） |
| **2^31-1 边界跨越**（内外地形连贯 + AABB） | ✅ E4（2026-09-19） |
| **任意距离传送**（归档重定位） | ✅ E5（2026-09-20） |
| **BigInteger 精确坐标**（任意大） | ✅ E5 |
| **精度现象验证**（2^53/2^55/2^56/2^63/1e306） | ✅ 实测 |
| **远域现象表**（~1.8088e24 远地起点等，特定实验条件下） | ✅ EXPERIMENTS §8 |
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

## 3. 精度现象

精度现象的坐标、ulp 与机制表见 [docs/EXPERIMENTS.md](EXPERIMENTS.md) §8
（本项目实验条件下的特定结果，红线 R10）。本节不再复制该表。

## 4. 机制清单

| 机制 | 实现 | 备注 |
|---|---|---|
| epoch 存储 | `FarConfig`（`world/farlands.properties`，BigInteger） | 自动创建（新世界 = 原点） |
| 窗口内 tp | `/realtp`（BigDecimal 解析 → local 换算） | 支持 1e1000+ |
| 超窗口重定位 | **归档式**（`world/farlands_epochs/<key>/`） | 起点归档 + 终点恢复 + 中途不生成 |
| 走路自动重定位 | `auto_relocate`（平移模式） | 视觉连续；可配置关闭 |
| 生成采样 | `FarProjection.unwrapX/Z` = EPOCH + local | double 精度现象在此 |
| 显示 | F3：XYZ/Block/Chunk 真实坐标（超 double 用 BigInteger 精确值）+ `Local (in-epoch)` + `Epoch ... Laps (2^31)` + `Real double ULP` | 见 F3Helper |
| 碰撞 | `collisionX/Z`（epoch 激活 = local） | 修复方块盒被移到纪元原点 |
| 边界跨越 | `epochInitialized` + local 域全程 | E4 里程碑 |

## 5. 命令与配置入口

- 命令（`/realtp`）见 [docs/USAGE.md](USAGE.md)。
- 配置（全局模板、创建世界标签页、世界文件、JVM 覆盖）见 [docs/CONFIG.md](CONFIG.md)。

## 6. 已知限制

- **> 1.8e308**：double 溢出 = Infinity → 噪声 (long) 饱和 → 固定值（现象，非真实地形）
- **实体位置 = local**（未真实化）——2^53 的移动精度现象不存在
- **渲染 = local**（floating origin）——精度现象只在采样层（地形塌缩），顶点渲染仍平滑
- **超远单次重定位**：平移 > 2^31 chunk 时走丢弃模式（无法保留起点内容——归档模式可保留）
