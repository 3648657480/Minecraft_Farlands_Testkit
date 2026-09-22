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
  （`/realtp @p 1e1000 100 0` 有效）；F3 有 `Real (exact)` 行
- **世界配置自动创建**：新世界自动生成 `world/farlands.properties`
  （纪元=原点）——开箱即用，无需 JVM 参数
- **距离现象已验证**：2^53 地形急停点、2^63 边境之地（2048 格同质块拼图）、
  1.8e308 水柱世界
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

| 真实坐标 | 现象 |
|---|---|
| 2^53（9.007e15） | 地形急停点（ulp=2，相邻采样合并） |
| 2^63（9.223e18） | 边境之地：128 区块同质块拼图 |
| 1.8e308 | 水柱世界（水平塌缩 + 垂直正常） |

现象区 = 观景区——几何极重，不适合长玩。

## 文档

- [docs/USAGE.md](docs/USAGE.md) / [docs/USAGE.en.md](docs/USAGE.en.md) - 玩家指南
- [docs/REVIEW.md](docs/REVIEW.md) - 架构复习（坐标域、机制）
- [docs/ROADMAP.md](docs/ROADMAP.md) - 里程碑与教训
- [docs/WORKFLOW.md](docs/WORKFLOW.md) - 构建/测试/部署工作流
- [docs/E-LINE-DESIGN.md](docs/E-LINE-DESIGN.md) - E 线设计笔记

## 许可

MIT。本项目不分发任何 Minecraft 资产；所有 Minecraft 代码均由用户在自己机器上
从自己的副本就地打补丁。
