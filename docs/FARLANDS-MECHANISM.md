# 远域机制笔记（可信源版）

> 版本：见仓库根目录 `VERSION`（唯一权威）
> 适用红线：R8（严谨）、R10（尊重外部记录）。本文档只记录**本项目在自研管线条件下的**机制证据。

## 0. 警告：本地 `-src` 不可信

`D:\Minecraft\.minecraft\versions\26.2-*-src\` 里的反编译源码（JD-Core）**有错**：
`PerlinNoise.wrap` 被反编译成 `return x`（no-op），而字节码与官方映射反编译（Vineflower）
都显示它是**模运算补丁**。**参考一律以字节码或 genSources(Vineflower) 为准。**

可信源获取（**不要再用 `-src`**）：
- 本项目版本：`gradlew genSources` → `mod/.gradle/loom-cache/minecraftMaven/…-sources.jar`（Vineflower）
- **任意版本**：`tools\gen-mc-sources.ps1 -Version 1.18.2`
  （Mojang client jar + 官方 ProGuard 映射 → SpecialSource 重映射 → Vineflower 反编译）
- 本文档的 1.18.2 样例即由该脚本生成。**注意："1.18.3" 不存在**（1.18 系列止于 1.18.2）。

## 1. Far Lands 机制（1.14.4 – 26.2）

`PerlinNoise.getValue` 对每个 octave 做 `wrap(x * factor)`，其中：

```java
public static double wrap(final double x) {
    return x - Mth.lfloor(x / 3.3554432E7 + 0.5) * 3.3554432E7; // mod 2^25
}
```

- **与版本无关**：`synth/`（`PerlinNoise/NormalNoise/ImprovedNoise/SimplexNoise/BlendedNoise/NoiseUtils`）
  在 **1.18.2 与 26.2 字节级一致**；`wrap` 两版相同。
- **失效边界不是干净功率点**：当 `x*factor/2^25` 逼近 double 精度边界时，`+0.5` 的舍入会
  丢掉 1 → `lfloor` 多/少 1 → `wrap` 偏 `±2^25`（33,554,432 格）→ 噪声输入跳变（Far Lands）。
- 数值探针（`WrapProbe`）：在 `x = 1,808,764,368,955,220,466,364,897` 处，**factor = 2^-3** 的
  octave 正好触发该误舍入（偏差 = `2^25`）；这解释了**起点是"杂乱值"**（取决于 double 舍入与
  `x*factor/2^25` 的奇偶），而不是 round 值。
- 公认起点（外部/编年史）：X/Z = `1,808,764,368,955,220,466,364,897`（≈1.808×10²⁴）。

## 2. 与"编年史值"的差异（已定案）

外部/编年史记的起点是 `1,808,764,368,955,220,466,364,897`；我们曾记录
`1,808,764,368,950,000,000,099,728`。**不是"对不上"，是差最后几位**——都在同一量级：

- 该量级（≈2⁸⁰·⁶）**double 的 ULP = 2²⁸ = 268,435,456 格**；"起点坐标"只能精确到**一个 double 桶**。
- 编年史值 与 INF32768 给的 `1,808,764,368,955,220,359,643,137` 是**同一个 double**
  `1808764368955220493860864`（桶 **k0**）；
- 我们旧记录落在桶 **k0 − 19448**（低 19448 ULP），**在 onset 之下** → 所以看着"对不上"。
- 理论式（INF）`2⁸⁸ / 171.103 = 1808764368955220356889014.5` 落在桶 **k0−1**（距 k0−1/k0
  分界仅 0.0103 ULP）；折叠要求 `q` 为奇数，故第一个触发折叠的 double 是 **k0**（+1 上取整），
  与 INF 的整数、与我们实测一致。

> **本项目条件下的结论**：主噪声损毁 onset = 桶 **k0** = `1808764368955220493860864`；
> 与编年史/INF 在 **1 ULP 内一致**；旧记录值作废。机测见 §8。

## 3. 整数子系统缺口（外部第 3 条，已核实）

`FunctionContextRealPatch` 只重写 `blockX/Y/Z()I` **后紧跟 `i2d`** 的调用点（`DensityFunctions$Noise/$ShiftedNoise`）。
以下**基于 int 坐标**的子系统**不覆盖**，远域仍走 local int → 周期/错位：

| 子系统 | 证据（26.2 可信源） |
|---|---|
| **末地岛屿密度** | `DensityFunctions.EndIslandDensityFunction.compute`：`getHeightValue(islandNoise, context.blockX()/8, context.blockZ()/8)` —— `blockX()/8` 是 **int**，非 `i2d` → 不被重写 |
| 雕刻器 | `levelgen/carver/WorldCarver` 用 `ChunkPos`/int 坐标 |
| 地物 | `levelgen/feature/*`（含 `EndIslandFeature`）用 int 坐标 |

→ 要"真实距离现象"，必须把这些整数子系统也纳入真实坐标（本质是本项目的**宽化**路线）。

> 状态：截至增量 5，以上三处，以及后续批量扫描发现的更多整数消费者
> （`Climate$Sampler`、`SurfaceSystem`、`FindTopSurface`、`WorldCarver`、`TheEndBiomeSource`、
> `GeodeFeature`、结构播种、`OreVeinifier`、`SurfaceRules` 噪声/随机）均已真实坐标化，见 §7。
> 上表为**立项时**的缺口清单，保留以说明来源。

## 4. 待办

- [ ] A：把 `wrap` 失效边界做成可复现的数值/无头实验（对照 1.808e24）。
- [~] B：整数子系统真实坐标化——末地岛屿密度已通过无头验收（§6）；其余消费者（§7）已逐项改并验证
      "正常坐标逐位零变化 + 远域不再按 2^32 周期"。仍未闭环：远域"可见起点随种子浮动"、崩溃面
      （`WorldGenRegion.getChunk` 兜底）等需实机复核。
- [ ] 清理：不再依赖本地 `-src`，参考统一走可信源。

## 5. B 实现方案（可直接照做）

现状：`FunctionContextMixin` 用 mixin 给 `DensityFunction.FunctionContext` **注入** default
`getBlockXDouble()`；普通 Java 类**看不到**它，所以只能靠 patcher 注入字节码调用。
mixin 里要用真实坐标，需要一个 mod 侧接口。

1. 新增 `mod/.../runtime/RealCoords.java`：
   ```java
   public interface RealCoords {
       double getBlockXDouble();
       double getBlockYDouble();
       double getBlockZDouble();
   }
   ```
2. 让 `FunctionContextMixin` **extends RealCoords**（default 方法即实现它）：
   ```java
   @Mixin(DensityFunction.FunctionContext.class)
   public interface FunctionContextMixin extends RealCoords { /* 现有 default 方法不变 */ }
   ```
   这样任意 `FunctionContext` 运行时都可 `(RealCoords) ctx` 取真实坐标。
3. `EndIslandDensityFunction`（`DensityFunctions` 内部私有静态类）加 mixin：
   - `@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction")`
   - `@Shadow private SimplexNoise islandNoise;`
   - `@Overwrite public double compute(DensityFunction.FunctionContext ctx)`：
     `RealCoords rc = (RealCoords) ctx;` → 用 `rc.getBlockXDouble()/8`、`getBlockZDouble()/8`
   - `@Unique` 宽版 `getHeightValue`（把原 int 逻辑改成 **double**：`chunkX=truncate(sectionX/2)`、
     `subSection=truncate(sectionX)%2`，与 vanilla 截断语义一致；`SimplexNoise.getValue(double,double)`）。
   - **careful**：为满足"正常坐标逐位零变化"，必须在 |blockX|<2^31 时与 vanilla 完全同值
     （负数的截断方向要对齐：用 `(long)(sectionX/2)` 而非 `Math.floor`）。
4. 同样套路推进 `WorldCarver`、`levelgen/feature/*`（用 `ChunkPos.xLong()/zLong()` 宽坐标，B 线已有）。
5. 注册到 `farlands-world.mixins.json`（与 `NoiseChunkRealCoordsMixin` 同组）。
6. 验收（无头）：
   - `tools/server-test.ps1 -Tag x -TestGen "0,0;62,62"` 正常坐标 → 与改动前逐位一致；
   - `-SpawnSet "<远域>,100,0"` 远近对照 → 末地/地表不再按 2^32 周期；
   - `gradlew :mod:worldDiff` 粗筛。

### 5.1 carver / feature 切入点（已定位，可信源）

- **雕刻器（精确节点）**：`NoiseBasedChunkGenerator.applyCarvers` 内：
  ```java
  random.setLargeFeatureSeed(seed + index, sourcePos.x(), sourcePos.z());
  if (carver.isStartChunk(random)) { carver.carve(...); }
  ```
  目标描述符 `setLargeFeatureSeed(JII)Lnet/minecraft/world/level/levelgen/WorldgenRandom;`
  用的是 **int chunk 坐标** `sourcePos.x()/z()`（= local）→ 远域按 local 周期。
  **改法**：对该调用点做 `@Redirect`，`isEpochActive()` 时用**真实 chunk 坐标**播种
  （真实坐标超出 int，需自定义宽播种：参考 `WorldgenRandom.setLargeFeatureSeed` 的
  `seed`+`x*341873128712L + z*132897987541L` 组合，把 x/z 换成真实 chunk 坐标的
  BigInteger→long 混合）；未激活时原样调用。
- **地物**：`levelgen/feature/*`（含 `EndIslandFeature`，及已有 `TreeFeatureMixin`）——
  放置同样由 chunk 坐标播种（`setLargeFeatureSeed`/`setFeatureSeed`）→ 同法。
- **注意**：改动会改变远域的全部笔刷/结构布局，属"真实距离现象"目标；必须与正常坐标
  隔离（阈值门控 + 无头 A/B）。

### 5.2 已有测试维度支持（`02cac3e`）

`MinecraftServerTestGenMixin` 支持 `-Dfarlands.testgen.dim=the_end|nether`；`worldDiff` 加 `-Pdim`
（vanilla-rig 同步；`tools/exp-run.ps1`、`server-test.ps1` 加 `-Dim`）。
用于在末地直接验证 5.1 的末地岛屿/地物改造。

## 6. B 验证结果（增量 1：末地岛屿密度）

> 全部数值为**特定实验条件下的特定结果**（R10）。条件：MC 26.2 / Fabric 0.19.3；
> 种子 `12345`；WG 指纹仪器（`WORLD_SURFACE_WG`+`OCEAN_FLOOR_WG`+4×4 生物群系 SHA-256，
> 见 `EXPERIMENTS.md` §2）；`-Dfarlands.wide/continuity/epoch=true`；`-Dim the_end`。

### 6.1 正常坐标逐位零变化（端到端）

mod（epoch=0）对照 vanilla-rig（无补丁 jar），同种子同坐标：

| 区块 (cx,cz) | mod surf/floor | vanilla-rig surf/floor |
|---|---|---|
| (0,0) | `4f2ee214f69f` | `4f2ee214f69f` |
| (62,62) | `5f4ecdb7b71c` | `5f4ecdb7b71c` |
| (25000,25000) | `5f4ecdb7b71c` | `5f4ecdb7b71c` |

`gradlew :mod:worldDiff -Pdim=the_end`：3 个 full/full 区块 identical、combined hash 相同、
`VERDICT: IDENTICAL`。(25000,25000)=40 万方块（section=50000，落在 vanilla int 平方溢出区）。

### 6.2 远域不再按 2^32 周期（端到端）

同一局部区块，仅改 epoch（`-Dfarlands.spawnset`）：

| epoch | (0,0) surf | (62,62) surf |
|---|---|---|
| `2^32 = 4294967296` | `d6f4a44b7018` | `da1504748017` |
| `2^33 = 8589934592`（= 2^32 个方块之后） | `8813938d4b2c` | `b0b9fdb53ac2` |

两者不同 → 无 2^32 周期。
A/B 归因（E=2^32 时临时移除 `DensityFunctionsEndIslandMixin`）：(62,62) = `5f4ecdb7b71c`
（即 epoch=0 的值）→ 未打补丁时整数子系统按 local 重复；打补丁后为 `da1504748017`。

### 6.3 数值探针（`gradlew :mod:endIslandProbe`）

- **正常范围**：对全部 vanilla 可达 section（`|section| ≤ 2^28`，含 int 平方溢出区）与
  vanilla int 参照逐位一致（3 个种子，72033 点，0 mismatch）。
- **路径切换**：`section=2^28` 走 vanilla-int 分支；`2^28+1` 走 wide 分支。
- **远域**：epoch 2^32 与 2^33 的 wide 结果不同；local-int 参照两者相同（即 2^32 周期）。

### 6.4 发现与修正（R8）

增量 1 原实现是**纯 double** 版 `getHeightValue`。探针显示：当 `section ≥ 32768` 时它与 vanilla
**不一致**——vanilla 的 `sectionX*sectionX` 是 **int** 运算会溢出，而末地世界边界（3000 万方块 →
section 375 万）正落在此区间，属 **vanilla 可达**。
修正：`EndIslandMath.heightValue` 在 `|section| ≤ 2^28` 时直接调用 vanilla int 版
（`vanillaHeightValue`），超出才走 `wideHeightValue`。这样"正常坐标逐位零变化"覆盖
vanilla 的全部可达范围，宽化只发生在 vanilla 无法表示的远域。
实现见 `mod/.../runtime/EndIslandMath.java` + `mod/.../mixin/DensityFunctionsEndIslandMixin.java`；
探针见 `tools/end-island-probe/`。

## 7. B 缺口批量扫描（26.2 可信源）

判据：某处**直接拿整数/区块坐标当世界坐标去采噪声或播种**，而引擎在 epoch 下拿到的整数是
**local**，就会在远域按 local 重复、不反映真实坐标。扫描对象：`NormalNoise.getValue` 直接调用、
`SinglePointContext`、`PositionalRandomFactory`/`set*Seed`、以及 feature/structure 的整数字段。

### 7.1 已覆盖

| 子系统 | 实现 |
|---|---|
| 主噪声密度（Noise/ShiftedNoise） | `FunctionContextRealPatch`（global，continuity） |
| 末地岛屿密度 | `EndIslandMath` + `DensityFunctionsEndIslandMixin`（增量1） |
| 含水层上下文 / NoiseChunk 预表面 | `AquiferContextPatch`（SinglePointContext→RealContext） |
| 生物群系气候（Climate$Sampler） | `AquiferContextPatch` 纳入 `Climate$Sampler`（增量4） |
| 地表材质（SurfaceSystem） | `SurfaceSystemRealCoordsMixin`（增量4） |
| 雕刻器播种 | `NoiseBasedChunkGeneratorCarverMixin`（seed offset，**近似**） |
| 地物/装饰播种 | `ChunkGeneratorDecorationMixin`（seed offset，**近似**） |
| `FindTopSurface` 内层上下文 | `AquiferContextPatch` 纳入 `DensityFunctions$FindTopSurface`（增量5） |
| 雕刻器内含水层 | `AquiferContextPatch` 纳入 `carver/WorldCarver`（增量5） |
| 末地生物群系 | `AquiferContextPatch` 纳入 `biome/TheEndBiomeSource`（增量5） |
| 晶洞噪声 | `GeodeFeatureRealCoordsMixin`（增量5） |
| 结构播种 | `StructureGenerationContextRealSeedMixin` / `StructurePlacementRealSeedMixin` / `OceanMonumentStructureRealSeedMixin` / `StrongholdStructureRealSeedMixin`（增量5，seed offset） |
| 矿脉随机 | `OreVeinifierRealRandomMixin`（增量5，`FarRandom.at`） |
| 地表规则噪声条件（2D/3D） | `SurfaceRulesContext1NoiseMixin` / `SurfaceRulesContext2NoiseMixin`（增量5） |
| 地表规则竖直渐变随机 | `SurfaceRulesVerticalGradientMixin`（增量5） |

### 7.2 未覆盖

| # | 子系统 | 位置 | 备注 |
|---|---|---|---|
| 1 | 其余 feature 播种 | `levelgen/feature/*` | `TreeFeatureMixin` 已覆盖树木；其余按需补 |
| 2 | 其它 `PositionalRandomFactory`/`set*Seed` 调用点 | 全局 | 需继续审计（本表按已发现项维护） |

> 判据不变：凡"直接拿 int/local 坐标当世界坐标"的采噪声/播种点，都要像增量4/5一样逐个
> 真实坐标化（epoch 门控、原点逐位零变化）。整批改动后 正常坐标（epoch=0）与原版逐位一致
> 已复验；远域 epoch=2^32 无崩溃。

### 7.3 不相关

- `Blender`（quart 坐标）：仅用于旧区块边界混合，非远域。
- `NoiseBasedChunkGenerator` 165：仅 F3 调试屏。

> 备注：3 是"链内"缺口（外层是 NoiseChunk 真实上下文，建内层 SinglePointContext 时又退回 int）；
> 5/6 是结构与矿脉 —— 远域结构与矿脉布局会整体按 local 重复。这些都应像增量4一样逐个真实坐标化。

## 8. 起点定案（无头机测）

**判据**（可机测）：主噪声损毁后地表会冲到接近建造上限；用 testgen 的 `topY` 逐坐标扫。
**方法**：`spawnset` 把 epoch 设到目标坐标 → 生成 (0,0)/(62,62) → 读 `topY`。两个种子。

| 坐标 | seed 12345 topY | seed -7820689566140440746 topY |
|---|---|---|
| 1.3e24 / 1.5e24 / 1.7e24 / 1.805e24 | 62 | 62 |
| 我们旧记录 `1.80876436895000e24` | 62 | – |
| 桶 k0−1（`…225425408`） | 62 | 62 |
| **桶 k0（`…493860864` = 编年史桶）** | **252** | **251** |
| 2^81 / 2^85 / 2^90 | 252 | 252 |

**结论**：转折在 **1 个 ULP 内**，正好落在桶 **k0**，**两个种子一致** → 主噪声 onset 固定、
不随种子变；旧记录 `1.80876436895000e24` 低 19448 ULP，在 onset 之下，作废。

> 说明：更早、更弱的"地表条带/变平"（约 2^60–2^75）确实随种子变——那是级联里更早的弱层，
> 不是主噪声损毁（层级错开见 §3 与 `EXPERIMENTS` §8）。

## 9. 本次修复的致命 bug（1.0.1）

| bug | 现象 | 根因 | 修 |
|---|---|---|---|
| **远域崩溃** | 生成中抛 `Requested chunk unavailable during world generation`（结构装饰阶段） | `WgrPatch` 幂等检测用常量 `134000000`（vanilla 自己也有）→ 补丁**从未注入**；local 域硬失败分支恒成立 | 改认自身注入的 `FarProjection.isEpochActive` 标记 + epoch 守卫返回 center |
| **`/realtp` 采样低 1 ULP** | 极远传送后边境之地不触发 / 地形不对 | epoch `floor(realX/16)*16` 跨 double 桶；分界点 round-half-even 落到低格 | `floorTo16PreservingBucket`：取整后若 `double(epoch)!=double(目标)` 则对齐目标桶 |
| **整数子系统按 local 重复** | 远域地形/群系/结构/矿脉按 2^32 周期 | 子系统拿 local int 当世界坐标 | 逐个真实坐标化（§7.1，13 处） |

诊断仪器：`-Dfarlands.diag.exactwrap=true`（默认关）把 `PerlinNoise.wrap` 换成精确取模，
用于隔离 wrap 的地形影响。
