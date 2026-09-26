# FarLands G1

> **中文** | [English](README.md)
> 版本：见仓库根目录 `VERSION`（唯一权威）

> **1.0.0 有致命远域崩溃，请升级到最新版**（见 [CHANGELOG.md](CHANGELOG.md)）。
> 启动横幅显示 `1.0.0` 的话就更新。

> **状态**：G1 作为「可玩 / 可研究」版本**暂结项**（epoch / local 域路线已到其设计边界）。

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
- **世界创建时配置**：创建世界界面的三个 FarLands 标签页（FarLands / 地形 / 测试）
  在生成任何地形前设定纪元、地形策略与无头测试工具键；新世界继承全局模板
  （见 [配置指南](docs/CONFIG.md)）
- **流体 tick 限流**：压力测试发现的递归流体 tick 爆炸守卫

## 环境要求

- Java 25（与 Minecraft 26.2 相同）
- 官方的 Minecraft 26.2 客户端 jar（自行获取）

## 下载（推荐，一次搞定，无需编译）

从 [Releases](https://github.com/3648657480/Minecraft_Farlands_Testkit/releases) 下载**最新版分发包**
`FarLands-G1-<版本>.zip`。**一个文件，解压即含**：

- `patcher-cli-<版本>.jar` —— 给你自己的客户端 jar 打补丁的命令行工具
- `farlands-g1-mod-<版本>.jar` —— Fabric 模组
- `docs/`（玩家指南）+ `INSTALL.md`（一页安装说明）+ `README` + `LICENSE`

解压后照其中的 `INSTALL.md` 走即可。**你不需要编译本项目。**

## 安装

1. **备份**：把版本目录里的 `26.2.jar` 复制成 `26.2.jar.bak`。
2. **打补丁**：用 `patcher-cli-<版本>.jar` 给你的官方 26.2 客户端 jar 打补丁，得到 fork jar
   （完整命令见 [docs/USAGE.md](docs/USAGE.md) §1.1）。
3. **替换**：用 fork jar 作为该版本的 jar。
4. **放模组**：把 `farlands-g1-mod-<版本>.jar` 放进 `mods/`。
5. **启动**：横幅显示 `[FarLands-G1] <版本> epoch build` 即成功。
6. **建世界**：在 FarLands 标签页设定纪元与地形选项。

完整流程见 [docs/USAGE.md](docs/USAGE.md)。

## 从源码构建（仅开发者）

```
gradlew clean build
```

产物在 `patcher-cli/build/libs/` 与 `mod/build/libs/`。只有想改代码或自己编译时才需要。

## 游戏内

```
/realtp <x> <y> <z>                用真实坐标传送自己
/realtp <targets> <x> <y> <z>      传送实体（@p/@e/...；命令方块可用）
```

超出窗口的目标会触发归档式重定位：当前纪元区块移到
`world/farlands_epochs/`，目标纪元归档存在则恢复，中途从不生成。
玩家落在新纪元的 local 原点——真实坐标不变，地形无缝。

## 距离现象

测量过程、方法与实验条件见 [docs/EXPERIMENTS.md](docs/EXPERIMENTS.md)。
全部数值均为**特定实验条件下的特定结果**，不构成对外部记录的更正——
红线见 [AGENTS.md](AGENTS.md)（R10）。

## 文档

- [docs/USAGE.md](docs/USAGE.md) / [docs/USAGE.en.md](docs/USAGE.en.md) - 玩家指南（安装、`/realtp`、配置入口、安全）
- [docs/CONFIG.md](docs/CONFIG.md) / [docs/CONFIG.en.md](docs/CONFIG.en.md) - 配置参考 + 预设组合
- [docs/EXPERIMENTS.md](docs/EXPERIMENTS.md) - 实验协议、F0 结果、远域现象表
- [docs/REVIEW.md](docs/REVIEW.md) - 架构复习（坐标域、机制）
- [docs/ROADMAP.md](docs/ROADMAP.md) - 里程碑与教训
- [docs/WORKFLOW.md](docs/WORKFLOW.md) - 构建/测试/部署工作流
- [docs/UNLOCK-DESIGN.md](docs/UNLOCK-DESIGN.md) - 解限模式设计稿（opt-in 实验室工具，不并入默认构建）
- [AGENTS.md](AGENTS.md) - 约束性红线（R7-R10）与五条铁律
- [docs/archive/](docs/archive/) - 历史设计文档（E 线、宽化容器）

## 许可

MIT。本项目不分发任何 Minecraft 资产；所有 Minecraft 代码均由用户在自己机器上
从自己的副本就地打补丁。
