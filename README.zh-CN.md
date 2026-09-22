# FarLands G1

> **中文** | [English](README.md)

Minecraft 26.2 边境之地工具集：在**真实坐标**下探索到 2^63（终局 1e306）——
不缩放、不假坐标、地形真实。

**玩家指南**：[中文](docs/USAGE.md) | [English](docs/USAGE.en.md)

## 这个仓库是什么（以及不是什么）

本仓库**只包含原创代码**：ASM 补丁库、独立 CLI、Fabric 模组（mixins + 工具类）。
**不包含任何 Minecraft 源码、反编译产物或游戏文件**。

补丁工具读取**你自己提供**的 Minecraft 客户端 jar（你从 Mojang 合法获取的副本），
在你的机器上就地改写字节码。

## 功能

- **2^31 边界外可玩**：地形连贯、AABB/选取/挖掘/放置正常（E4）
- **任意距离传送**：`/realtp` 真实坐标（命令方块可用），归档式重定位——
  当前纪元归档、目标纪元恢复、中途从不生成（E5）
- **BigInteger 精确坐标**：纪元精确存储、任意精度字符串解析
  （`/realtp @p 1e1000 100 0` 有效）
- **F3 真实坐标显示**：XYZ / Block / Chunk 显示真实坐标，超 double 精度自动切精确
  BigInteger 值；另有 `Local (in-epoch)`、`Epoch ... Laps (2^31)`、`Real double ULP`
  （量化步长）行；噪声读数改为真实坐标处采样
- **世界配置自动创建**：新世界自动生成 `world/farlands.properties`
  （纪元=原点）——开箱即用，无需 JVM 参数
- **流体 tick 限流**：压力测试发现的递归流体 tick 爆炸守卫

## 环境要求

- Java 25（与 Minecraft 26.2 相同）
- 官方的 Minecraft 26.2 客户端 jar（自行获取）

## 构建

```
gradlew clean build
```

产物：`patcher-cli/build/libs/patcher-cli-1.0-SNAPSHOT.jar` 与
`mod/build/libs/farlands-g1-mod-1.0-SNAPSHOT.jar`。

## 安装

1. 给你的客户端 jar 打补丁：
```powershell
java "-Dfarlands.wide=true" "-Dfarlands.continuity=true" "-Dfarlands.epoch=true" `
  -jar patcher-cli-1.0-SNAPSHOT.jar --in <你的26.2.jar> --out <fork.jar>
```
2. 用 fork jar 作为版本 jar（备份原版）
3. 把模组 jar 放进 `mods/`
4. 启动——无需任何 JVM 参数

完整玩家指南见 [docs/USAGE.md](docs/USAGE.md)。

## 游戏内

```
/realtp <x> <y> <z>                用真实坐标传送自己
/realtp <targets> <x> <y> <z>      传送实体（@p/@e/...；命令方块可用）
```

超出窗口的目标会触发归档式重定位：当前纪元区块移到
`world/farlands_epochs/`，目标纪元归档存在则恢复，中途从不生成。
玩家落在新纪元的 local 原点——真实坐标不变，地形无缝。

## 距离现象

本项目管线下的测量结果。全部数值均为**特定实验条件下的特定结果**（本管线、其版本与位置），
不构成对任何外部记录的否定或更正——见 [docs/USAGE.md](docs/USAGE.md) 红线 R10。

| 真实坐标 | 现象（本项目条件下） |
|---|---|
| 2^53（9.007e15） | 症状起点：1 格采样量化为 2 格成对（地下细微、地表正常） |
| 2^55（3.603e16） | 主地形量化：规则 8 格阶梯/条带（奶酪状） |
| 2^56（7.206e16） | 平板结构 + 水面瓷砖（16 格） |
| 2^63（9.223e18） | 128 区块同质拼图 |
| ~1.80876436895e24 | 实测"地表主地形改变（条板/墙结构）"起点 |
| >2.43e27 | 本条件下地形未终止（高度重复） |
| 1.8e308 | 水柱世界（水平塌缩 + 垂直正常） |

远域退化是**级联**：不同噪声在不同阈值失效（地下先、地表主地形后），
因此不存在单一"起点"。

现象区 = 观景区——几何极重，不适合长玩。

## 文档

- [docs/USAGE.md](docs/USAGE.md) / [docs/USAGE.en.md](docs/USAGE.en.md) - 玩家指南
- [docs/CONFIG.md](docs/CONFIG.md) / [docs/CONFIG.en.md](docs/CONFIG.en.md) - 配置参考 + 预设组合
- [docs/EXPERIMENTS.md](docs/EXPERIMENTS.md) - 实验协议、F0 结果、远域现象表
- [docs/REVIEW.md](docs/REVIEW.md) - 架构复习（坐标域、机制）
- [docs/ROADMAP.md](docs/ROADMAP.md) - 里程碑与教训
- [docs/WORKFLOW.md](docs/WORKFLOW.md) - 构建/测试/部署工作流
- [docs/archive/](docs/archive/) - 历史设计文档（E 线、宽化容器）

## 许可

MIT。本项目不分发任何 Minecraft 资产；所有 Minecraft 代码均由用户在自己机器上
从自己的副本就地打补丁。
