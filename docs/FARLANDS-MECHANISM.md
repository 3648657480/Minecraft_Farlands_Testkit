# 远域机制笔记（可信源版）

> 版本 1.0.0（权威：仓库根目录 VERSION）
> 适用红线：R8（严谨）、R10（尊重外部记录）。本文档只记录**本项目在自研管线条件下的**机制证据。

## 0. 警告：本地 `-src` 不可信

`D:\Minecraft\.minecraft\versions\26.2-*-src\` 里的反编译源码（JD-Core）**有错**：
`PerlinNoise.wrap` 被反编译成 `return x`（no-op），而字节码与官方映射反编译（Vineflower）
都显示它是**模运算补丁**。**参考一律以字节码或 genSources(Vineflower) 为准。**

可信源获取：
- `gradlew genSources` → `mod/.gradle/loom-cache/minecraftMaven/…-sources.jar`（Vineflower）
- 其它版本：Mojang client jar + 官方 ProGuard 映射 → SpecialSource 重映射 → Vineflower。

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

## 2. 我们的差异（问题在本侧）

- 我们记录的 `1,808,764,368,950,000,000,099,728` 比公认值低 ~5.2×10¹²（≠19450 ulp）。
- 根因：**round epoch 采样到的是另一个 double** + 下列**整数子系统未处理**。

## 3. 整数子系统缺口（外部第 3 条，已核实）

`FunctionContextRealPatch` 只重写 `blockX/Y/Z()I` **后紧跟 `i2d`** 的调用点（`DensityFunctions$Noise/$ShiftedNoise`）。
以下**基于 int 坐标**的子系统**不覆盖**，远域仍走 local int → 周期/错位：

| 子系统 | 证据（26.2 可信源） |
|---|---|
| **末地岛屿密度** | `DensityFunctions.EndIslandDensityFunction.compute`：`getHeightValue(islandNoise, context.blockX()/8, context.blockZ()/8)` —— `blockX()/8` 是 **int**，非 `i2d` → 不被重写 |
| 雕刻器 | `levelgen/carver/WorldCarver` 用 `ChunkPos`/int 坐标 |
| 地物 | `levelgen/feature/*`（含 `EndIslandFeature`）用 int 坐标 |

→ 要"真实距离现象"，必须把这些整数子系统也纳入真实坐标（本质是本项目的**宽化**路线）。

## 4. 待办

- [ ] A：把 `wrap` 失效边界做成可复现的数值/无头实验（对照 1.808e24）。
- [ ] B：整数子系统（末地密度/carver/feature）真实坐标化（宽化）。
- [ ] 清理：不再依赖本地 `-src`，参考统一走可信源。
