# 解限模式设计（极限测试工具 · 未并入默认构建）

> 版本 1.0.0（权威：仓库根目录 VERSION）
> 状态：**已实现（基础）**——`OptionsUnlockPatch` + `unlock` 开关/门禁 + Vulkan 门控告警；**默认不注册**。
> 适用红线：R7（后果写实）、R8（严谨）。

---

## 0.1 实现落点（2026-09-23）

- `patcher-core`：`OptionsUnlockPatch`（抬高 `Options` 中 `options.renderDistance` / `options.simulationDistance` 的 `IntRange` 上限 32/16 → **96**）；`FarLandsPatcher.unlockEnabled()` 双门禁；`createDefault(...,unlock)`。
- `buildSrc`：`G1JarProcessor.Spec` 纳入 `unlock`（进缓存键）。
- `mod`：`client/UnlockWarn` + `mixin/MinecraftUnlockWarnMixin`（tick 前检查 `PreferredGraphicsApi`，非 Vulkan 则告警"顶点限制不会失效"）。
- 已验证：补丁门禁（同时两旗才 patch）、字节码改写形态正确。**运行期启动测试待做。**
- 待做：帧缓冲是否硬件限；Vulkan 路径下其余 mixin 兼容性；运行期实机验证。

---

## 0. 定位

- **致敬 FFmpeg 的 bitstream filter / noise 工具**：工具存在，但**必须显式启用**（`-bsf` / `-vf` 式），默认不生效。
- 实验室**极限 / 稳定性包络测试**工具，不是"爽玩功能"；后果自担。
- 对"距离现象"本身**实验价值低**（现象在采样层）；价值在"管线能扛到多少"的包络数据。

## 1. 开关与门禁

- 默认**不注册进补丁集**（默认构建零影响）。
- 启用：`-Dfarlands.unlock=true`，并纳入 `G1JarProcessor.Spec` 缓存键（与 `wide/continuity/epoch` 同一隔离机制）。
- **门禁**：启用者必须同时显式声明 **「我知道我在做什么」**，例如
  `-Dfarlands.unlock=true -Dfarlands.unlock.i_know_what_im_doing=true`，二者同时为真才生效，并在启动日志打印一行确认。

## 2. 解限项（能解的才做）

| 项 | 原版值 | 可解性 | 备注 |
|---|---|---|---|
| 渲染距离（视距） | 滑块封顶 32 chunk | **待探明**（代码 clamp vs 硬件限） | 放大 = 渲染 + 生成 + 内存三线同爆 |
| 模拟距离 / chunk 加载 | 原版 | **待探明** | **服务端是真炸点** |
| 帧缓冲 / 分辨率 | GPU `GL_MAX_*` | 可能为硬件限（= 假解） | 探明是硬件限则该项直接砍 |
| 顶点上限 | `2^24-1`（16777215，OGL 索引限） | **仅在 Vulkan 下真解**（见 §3） | 保留 `BufferBuilderGrowMixin` 兜底 |

## 3. 顶点解限 = Vulkan 门控（核心）

- **事实**：`BufferBuilder.beginVertex` 的 `16777215 (2^24-1)` 本质是 OpenGL 的索引/顶点上限；把 Java 常量抬到 `Integer.MAX_VALUE`（现有 `BufferBuilderGrowMixin`）在 **OGL 下驱动仍会卡住**，所以常量抬了也不真解。
- **Vulkan**（`com.mojang.blaze3d.vulkan.VulkanBackend`）的 buffer 模型**没有这个 24 位索引限制**，才是真绕过。
- **行为**：启动时检查 `net.minecraft.client.PreferredGraphicsApi` / 当前后端：
  - **≠ VULKAN** → 打印醒目告警：**「顶点限制不会失效；请在 选项 → 图形 → Graphics API 选择 Vulkan」**；**不强制切换**（要求用户尽量打开，而不是替他改）。
  - **= VULKAN** → 顶点解限生效。
- 等价致敬：如同 FFmpeg 的 `-hwaccel vulkan`，由工具**显式指定后端**。

## 4. 护栏（必须）

- **硬上限**：不设 `unlimited`（给出具体上限值，待定）。
- **看门狗**：复用 `Watchdog`——超时 / 超内存自动降档或中止。
- **仪表**：每 N 秒打印 FPS / 已加载区块 / 显存 / 堆，作为稳定性包络数据。
- **文档**：按 R7 写实后果（OOM / GPU 驱动崩 / 过热 / 丢档）。

## 5. 待探明（实现前，只读）

1. RD / 模拟距离 / 帧缓冲 的 **clamp 点**：代码可解 vs 硬件限。
2. 顶点上限**是否后端条件化**（Vulkan 下是否真的不同）。
3. 现有 mixin / 补丁在 **Vulkan 路径**下目标类是否存在、行为是否一致（后端耦合）——实现时**双后端各测一次**。

## 6. 为什么单列（不并入默认）

- 默认开 = 揽来非研究用户 + 事故；默认关 + 显式确认「我知道我在做什么」= 责任可控。
- 现有已解项（世界边界、绝对世界尺寸、`BufferBuilderGrowMixin`）属于核心功能，**不移入本工具**；本工具只加**新增**解限。
