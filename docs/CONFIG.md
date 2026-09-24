# FarLands G1 配置文件指南

> 版本 1.0.0（权威：仓库根目录 VERSION）
> 文体：严格尊重体。本文档是配置的唯一参考：全局模板、创建世界标签页、世界文件与逐项详解。
> 每条后果均标注验证状态。未验证项按红线对待。

---

## 0. 文件基础

配置有三个来源，优先级（高 → 低）：

```
JVM 覆盖   -Dfarlands.<key>=<value>             进程启动时
  > 世界文件   <世界目录>\farlands.properties      世界加载时
    > 全局模板   config\farlands-g1.properties     模组初始化时创建
```

### 0.1 全局模板

文件位置：`config\farlands-g1.properties`（Fabric 配置目录）。

生成时机：**模组初始化时**（任何世界存在之前）自动创建，带注释的默认值。

作用：新世界的默认值。你可以在**创建世界之前**先改好它；之后创建的世界继承这些值。
专用服务器没有创建界面，直接使用全局模板。

| 状态 | 行为 |
|---|---|
| 文件不存在 | 自动创建（带默认值） |
| 文件存在 | 作为新世界的种子值 |

### 0.2 创建世界标签页

创建世界界面有三个 FarLands 标签页（通过 CreateWorldScreen 的 mixin 注入）：

| 标签页 | 字段 |
|---|---|
| **FarLands** | `epoch_x`、`epoch_z`、`auto_relocate`、`relocate_margin`、`debug`；另有 **"Save as global default"** 按钮 |
| **地形 / Terrain** | `worldgen_sample_mode`、`worldgen_sample_clamp`、`worldgen_far_threshold`、`pro_sample_offset_x`、`pro_sample_offset_z`、`pro_sample_scale` |
| **测试 / Test** | `testgen`、`testgen_stop`、`testgen_settle`、`testspawn`、`spawnset` |

**执行**：世界创建时，这些值在**地形生成之前**写入 `<世界>\farlands.properties`。
未列在标签页上的键（`fluid_tick_limit`、`archive_dir`）取全局模板的值。
若标签页从未改动，世界文件在**世界加载时**从全局模板播种。

### 0.3 世界文件

文件位置：`<世界目录>\farlands.properties`。这是该世界的配置。

| 状态 | 行为 |
|---|---|
| 文件不存在 | 世界加载时从全局模板播种（epoch = 原点） |
| 文件存在但缺键 | 缺失键取默认值；epoch 缺失视为原点 |
| 文件存在且格式错误 | **`POLICY VIOLATION` → JVM 中断**（见 R2） |

### 0.4 生效时机

**配置绝不在运行中的世界内改动。**

| 来源 | 生效时机 |
|---|---|
| 创建世界标签页 | 世界创建时（地形生成前写入世界文件） |
| 世界文件 | 世界加载时（退出到主菜单再进入即可，不必重启进程） |
| 全局模板 | 作为**新世界**的播种值；对已存在的世界无效 |
| JVM 覆盖 | 进程启动时（不落盘） |

### 0.5 备份

**执行**：编辑前复制一份 `farlands.properties.bak`。

原因：格式错误会导致 JVM 拒绝启动；有备份可立即回退。

---

## 1. 逐项详解

### 1.1 `epoch_x` / `epoch_z`

| 项 | 值 |
|---|---|
| 含义 | 世界原点（真实坐标）。引擎所有 local 坐标 = 真实坐标 − 原点 |
| 类型 | 任意整数（任意精度，`1e306` 合法） |
| 默认 | `0` / `0`（世界原点） |
| 验证 | 手改 → 区块错位：**已模拟**（由跨世界残留事故推演） |

**怎么用**：

- 正常情况：不改。用 `/realtp` 传送，系统自动维护原点。
- 想在新位置长期游玩：`/realtp <目标>` 会自动把原点设为目标（归档式重定位）。
- 想重置回原点：改 `epoch_x=0`、`epoch_z=0`，重新进入世界。

**错误后果**：手改已有世界的原点 → **所有已生成区块错位**（引擎按新原点解释旧数据）。

**为什么无限制**：系统无法区分"正确迁移"（先平移存档再改原点）与"错误修改"。这是 R1。

**联动**：与 `/realtp`、`farlands_epochs\` 归档目录强相关。

---

### 1.2 `auto_relocate`

| 项 | 值 |
|---|---|
| 含义 | 走路接近窗口边缘时是否自动重定位 |
| 类型 | `true` / `false` |
| 默认 | `true` |
| 验证 | 自动重定位流程：**已测试**（E5 里程碑） |

**怎么用**：

- `true`（默认）：一路走，接近 `epoch ± (2^31 − relocate_margin)` 时自动重定位，无缝继续。
- `false`：只提示"接近窗口边缘，请用 /realtp 继续"。适合想完全手动控制的玩家。

**错误后果**：非布尔值（如 `yes`、`1`）→ **`POLICY VIOLATION` → JVM 中断**。

**为什么严格**：静默降级会把"我明明写了 true"变成"实际 false"，意图被隐藏。见 R2。

**联动**：`relocate_margin` 决定触发距离。

---

### 1.3 `relocate_margin`

| 项 | 值 |
|---|---|
| 含义 | 距窗口边缘多少格触发（或提示）重定位 |
| 类型 | ≥ 0 的整数（格） |
| 默认 | `100000`（10 万格） |
| 验证 | 触发行为：**已测试** |

**怎么用**：

- 默认 10 万：安全余量充足（特征放置需要约 2000 格余量，10 万远大于此）。
- 调大（如 `1000000000`）：边缘更不可见（"边缘永在视野外"思想），代价是重定位更频繁。
- 调小（如 `5000`）：重定位更晚，风险是接近 int 溢出区（**不推荐低于 10000**）。

**错误后果**：负数 → **`POLICY VIOLATION` → JVM 中断**。过大 → 走路时频繁重定位（可用但烦）。

**为什么下限 0**：0 表示"贴边才触发"，语义合法但危险；负数是错误。

**联动**：`auto_relocate`。

---

### 1.4 `fluid_tick_limit`

| 项 | 值 |
|---|---|
| 含义 | 每游戏 tick 允许的流体 tick 次数上限 |
| 类型 | ≥ 0 的整数（0 = 无限） |
| 默认 | `2000` |
| 验证 | 无限流 → CPU 持续满载：**已测试**（Watchdog 堆栈） |

**怎么用**：

- 默认 2000：正常世界无感知；异常地形（水柱世界）防止 CPU 占用飙升。
- 调大（如 `10000`）：流体流动更快，风险是异常地形下 CPU 压力（见 R4）。
- `0`：关闭限流。**仅在正常地形使用**；异常地形 + 0 = R4 红线。

**错误后果**：负数 → **`POLICY VIOLATION` → JVM 中断**；`0` + 异常地形 → 服务器线程持续满载、AppHang（已测试）。

**为什么**：每个流体方块触发递归坡度搜索（深度 4-8 层 × 3 方向）。水柱世界的水方块数以百万计，不限流 = CPU 持续满载。

**联动**：现象区（2^53 以外）游玩。

---

### 1.5 `archive_dir`

| 项 | 值 |
|---|---|
| 含义 | 纪元归档目录名（相对世界目录） |
| 类型 | 纯目录名（不含路径分隔符） |
| 默认 | `farlands_epochs` |
| 验证 | 归档/恢复：**已测试** |

**怎么用**：

- 默认即可。归档结构：`<archive_dir>\e_<前12位>L<位数>_<同Z>\`（每个访问过的纪元一个子目录）。
- 想换位置（如放独立磁盘）：**不支持**（必须相对世界目录）。可改用符号链接。

**错误后果**：空 / 含 `/`、`\`、`..` → **`POLICY VIOLATION` → JVM 中断**。

**为什么**：路径穿越会写到世界目录之外，可能覆盖系统文件。

**联动**：`/realtp` 的归档式重定位。

---

### 1.6 `worldgen_sample_mode`

| 项 | 值 |
|---|---|
| 含义 | 远处（超过 `worldgen_far_threshold`）采样坐标的变换策略 |
| 类型 | `raw` / `clamp` / `quantize` |
| 默认 | `raw` |
| 验证 | 三模式 1e306 生成：**已测试**；地形不连续：**未验证（按红线对待）** |

**怎么用**：

| 值 | 效果 | 适用 |
|---|---|---|
| `raw` | 原生 double 采样。现象自然发生（塌缩/水柱） | 正常游玩、现象观察 |
| `clamp` | 采样坐标限制在 ±`worldgen_sample_clamp`。有限值，地形变成重复图案 | 想避免 Infinity（>1.8e308）的重复地形实验 |
| `quantize` | 采样坐标量化到 2^53 网格。地形非常平坦/稳定 | 平坦世界实验 |

**错误后果**：非法值（拼写错误）→ **`POLICY VIOLATION` → JVM 中断**。`clamp`/`quantize` 改变地形 → 新旧地形不连续（R5）。

**为什么严格**：拼写错误（`Raw`、`raw `）不能静默变成 raw——你会以为策略生效了。

**联动**：`worldgen_sample_clamp`、`worldgen_far_threshold`。

---

### 1.7 `worldgen_sample_clamp`

| 项 | 值 |
|---|---|
| 含义 | clamp 模式的边界（格） |
| 类型 | 正有限数 |
| 默认 | `1e300` |
| 验证 | 生成：**已测试** |

**怎么用**：

- `1e300`：在 double 极限（1.8e308）内，避免 Infinity。
- 调小（如 `1e10`）：地形重复更强（采样点更密集）。
- 调大（如 `1e307`）：接近 double 极限，仍可能塌缩。

**错误后果**：非正数或 NaN/Infinity → **`POLICY VIOLATION` → JVM 中断**。

**为什么**：0 或 NaN 会使采样无意义（所有坐标映射到同一点）。

**联动**：仅在 `worldgen_sample_mode=clamp` 时使用。

---

### 1.8 `worldgen_far_threshold`

| 项 | 值 |
|---|---|
| 含义 | 策略生效的距离（格，距原点） |
| 类型 | ≥ 0 的整数 |
| 默认 | `9007199254740992`（2^53） |
| 验证 | 全域应用（0）：**未验证（按红线对待）** |

**怎么用**：

- 默认 2^53：只在 double 精度极限之外应用策略。2^53 以内地形完全原生。
- 调大：策略生效更晚（如 `1e18`）。
- `0`：全域应用策略——**实验专用**，会使正常区域地形也被改写（R5）。

**错误后果**：负数 → **`POLICY VIOLATION` → JVM 中断**。`0` → 全域地形改写（未验证，按红线对待）。

**为什么**：距离阈值必须非负；0 有明确语义（全域）但危险。

**联动**：`worldgen_sample_mode`。

---

### 1.9 `debug`

| 项 | 值 |
|---|---|
| 含义 | 日志级别（功能性：控制运行期日志输出） |
| 类型 | `0` / `1` / `2` / `3` |
| 默认 | `0` |
| 验证 | 超范围 → halt：**已测试**；>0 各级日志：**已实现**（3 档日志量按红线对待） |

**怎么用**：

| 值 | 输出 | 适用 |
|---|---|---|
| `0` | 关闭（仅关键事件） | 正常游玩 |
| `≥1` | 重定位摘要（方式：archive / translate / discard，移动文件数） | 常规排查 |
| `≥2` | 上式 + 流体限流命中（`fluid tick limit hit (N/tick)`） | 深度排查 |
| `≥3` | 上式 + 逐采样地形变换日志（`sample axis=X mode=... real=... out=...`），**硬上限 2000 行**（超出抑制并提示一次） | 短时诊断（R3） |

各级为**累加**：`debug=2` 同时输出 `≥1` 与 `≥2` 的内容；`debug=3` 再叠加逐采样日志。

**错误后果**：超出 0-3 → **`POLICY VIOLATION` → JVM 中断**。`3` 的逐采样日志已硬限流到 2000 行（`FarProjection`），长期开启不再刷爆日志。

**为什么 4 档**：0/1/2/3 语义固定，越界是错误（不是"自动 clamp 到 3"）。

---

## 2. 预设组合

在创建世界标签页填入，或在世界文件中只写差异项。

### P1 标准（默认）

适用：正常游玩。

```properties
epoch_x=0
epoch_z=0
auto_relocate=true
relocate_margin=100000
fluid_tick_limit=2000
archive_dir=farlands_epochs
worldgen_sample_mode=raw
worldgen_sample_clamp=1e300
worldgen_far_threshold=9007199254740992
debug=0
```

### P2 安全保守

适用：不可替代的存档；只想稳定探索。

```properties
auto_relocate=false
relocate_margin=1000000
fluid_tick_limit=1000
debug=0
```

（其余默认。`auto_relocate=false` = 走路永不打断；重定位只在 `/realtp` 时发生。）

### P3 性能优先

适用：机器较弱（核显/低内存）。

```properties
relocate_margin=1000000000
fluid_tick_limit=500
debug=0
```

（配合：游戏内渲染距离降至 8-10。现象区仍然吃力——见 R6。）

### P4 地形实验

适用：**新建世界**实验 `worldgen_*`（R5：禁止在不可替代的存档实验）。

```properties
worldgen_sample_mode=quantize
worldgen_far_threshold=0
debug=1
```

（其余默认。想试 clamp：`worldgen_sample_mode=clamp` + `worldgen_sample_clamp=1e10`。）

### P5 观景

适用：去现象区看风景/截图。

```properties
fluid_tick_limit=2000
debug=0
```

（配合：**先把渲染距离降到 4-6**，再 `/realtp 9223372036854775807 100 0`。看完 `/realtp 0 100 0` 返回。）

### P6 诊断

适用：排查问题（短时）。

```properties
debug=2
```

（复现问题后改回 0。**禁止**长期 `debug=3`——R3。）

### P7 原版兼容

适用：暂时只想要边界跨越，不要任何自动化。

```properties
auto_relocate=false
worldgen_sample_mode=raw
debug=0
```

（配合：只用 `/realtp` 手动传送。）

---

## 3. JVM 覆盖

用法：

```powershell
java -Dfarlands.debug=1 -Dfarlands.relocate_margin=500000 -jar <游戏启动器>
```

规则：

- 键名：`-Dfarlands.<配置键>=<值>`（与文件键一一对应）。
- 优先级：JVM 覆盖世界文件，世界文件覆盖全局模板（JVM 不落盘）。
- 用途：临时测试，不想改文件。
- **警告**：JVM 参数同样经过 policy 检查；非法值一样 halt。

---

## 4. 配置错误处理

| 错误类型 | 系统行为 |
|---|---|
| 全局模板缺失 | 模组初始化时自动创建 |
| 世界文件不存在 | 世界加载时从全局模板播种（epoch = 原点） |
| 缺键 | 取默认值 |
| 格式错误（非数字/非布尔） | `POLICY VIOLATION` → **JVM 中断** |
| 值越界（debug=5、负数等） | `POLICY VIOLATION` → **JVM 中断** |
| `epoch` 语义错误（手改） | 系统无法检测 → 区块错位（R1） |

**执行**：看到 `POLICY VIOLATION` 后，检查日志里的具体消息（含错误键与值），修正后重启。

---

## 5. 场景问答

**问：我想回到原点。**
答：`/realtp 0 100 0`（归档恢复）。或改 `epoch_x=0`、`epoch_z=0` 后重进世界（**仅在愿意放弃旧位置内容时**）。

**问：我想让走路永不打断。**
答：`auto_relocate=false`（P2）。

**问：我想看 1e306。**
答：`/realtp 1e306 100 0`。**先把渲染距离降到 4-6**（R6）。

**问：我想实验 clamp 地形。**
答：**新建世界** → `worldgen_sample_mode=clamp` + `worldgen_sample_clamp=1e10` + `worldgen_far_threshold=0`（P4）。

**问：我机器卡。**
答：P3 + 降渲染距离。现象区仍会吃力（R6）。

**问：我改了 epoch_x 但没平移存档，怎么救？**
答：把 `epoch_x` 改回原值（区块立即恢复正确）。**这就是为什么系统不阻止**——回退是可行的（前提是你记得原值；所以 0.3 的备份是执行项）。

**问：debug=3 会不会坏档？**
答：不会坏档，但会产生巨量日志（磁盘压力）。**未实测——按红线对待**（R3）。

---

## 6. pro 调试选项（实验性，不建议普通玩家）

> **警告**：这一组直接改写**地形生成的噪声输入**——地形会在**所有位置**发生形变（不只是远距离）。
> 默认值全部是严格的 no-op。只在新世界/测试世界使用。后果自负（见 USAGE 红线）。

| 参数 | 含义 | 默认 | 范围 | 后果 |
|---|---|---|---|---|
| `pro_sample_offset_x` | 加到噪声输入 X 坐标上的偏移（格） | `0` | 有限数 | 地形整体沿 X **平移**；非 0 时与任何已有地形不连续 |
| `pro_sample_offset_z` | 加到噪声输入 Z 坐标上的偏移（格） | `0` | 有限数 | 同上（Z 方向） |
| `pro_sample_scale` | 乘到噪声输入 X/Z 坐标上的缩放 | `1` | > 0 且有限 | 值 >1 地形**放大**（特征变粗）、<1 **缩小**（特征变细）；改变与已有地形的连续性 |

**怎么用**（结合噪声研究）：
- 偏移 = 让生成器"换个地方采样"：在固定坐标上平移地形，用于对照实验（同一坐标、不同输入偏差）。
- 缩放 = 改采样频率：研究各噪声频段对地形的贡献（例如 scale=2 相当于把频率减半）。

**为什么默认必须 no-op**：它们会影响**所有**地形（含出生区）。任何非默认值都会让新生成地形与旧地形不连续——**不要**在正常存档里使用。

**错误后果**：非有限数 / scale ≤ 0 → `POLICY VIOLATION` → JVM 中断。

---

## 7. 测试工具键（实验 / headless）

> **警告**：下列键是**实验 / 无头（headless）控制**，不是普通游玩选项。
> `testgen_stop` 会在测试后**保存并 halt 整合服务器**；只用于**测试世界**。后果自负。

这 5 个键以前只在 JVM `-D` 下可用，现在也可写入全局模板 / 世界文件，或填在创建世界界面的**测试 / Test** 标签页。

| 键 | 默认 | 含义 | 后果 |
|---|---|---|---|
| `testgen` | `""` | 强制生成的区块区域，格式 `cx,cz,n;cx,cz,n`（`n` = n×n 区域，默认 1） | 生成后打印确定性指纹：`wgHash` = WORLD_SURFACE_WG + OCEAN_FLOOR_WG 高度图 + quart 生物群系网格的 SHA-256，另附 topY、biome 与方块探针。这是 A/B 对照仪器 |
| `testgen_stop` | `false` | testgen 后等待 `testgen_settle` tick，再保存并 halt 整合服务器 | 脚本化运行；**会保存并终止游戏** |
| `testgen_settle` | `200` | 保存前的沉降 tick 数 | 越大越慢；过小可能区块未完成沉降 |
| `testspawn` | `false` | 无头运行出生点搜索路径 | 复现进入世界失败（出生点搜索异常） |
| `spawnset` | `""` | 真实坐标 `"x,y,z"` | 一并设置真实出生点与 epoch；空串 = 不改 |

**怎么用**：

- A/B 对照：只改一个 `worldgen_*` / `pro_*`，比较两次 `wgHash`——哈希相同即地形一致。
- 崩溃复现：`testspawn=true` 无头跑出生点搜索；日志出现堆栈即复现。
- 这些键同样可作为 JVM `-D` 标志：`-Dfarlands.testgen=…`（JVM 覆盖文件，见 §3）。

**错误后果**：`testgen_settle` 为负 → **`POLICY VIOLATION` → JVM 中断**。
但 `testgen` / `spawnset` 的**格式**目前**不做校验**：`testgen` 坏格式会在 testgen 阶段抛异常并打印
`gen FAILED`（不中断 JVM）；`spawnset` 少于 3 段则**静默忽略**（不中断）。

---

## 8. 创建世界界面 + 全局模板

### 8.1 标签页

创建世界界面有三个 FarLands 标签页；选择在生成前写入世界文件。

- **FarLands**：`epoch_x`、`epoch_z`、`auto_relocate`、`relocate_margin`、`debug`
- **地形 / Terrain**：`worldgen_sample_mode`、`worldgen_sample_clamp`、`worldgen_far_threshold`、`pro_sample_offset_x`、`pro_sample_offset_z`、`pro_sample_scale`
- **测试 / Test**：`testgen`、`testgen_stop`、`testgen_settle`、`testspawn`、`spawnset`

在创建世界前即可填好 epoch——这正是"配置进世界才创建、进了世界又改不了"矛盾的解法。
**"Save as global default"** 把当前标签页的值写回全局模板。

**界面行为**：

- 顶部红色警告要求先阅读 `docs/CONFIG.md`，并提示"错误值会让游戏立即中断"。
- **必须勾选"我已阅读手册，并照手册填写"**，输入项才可编辑；未勾选时全部禁用。
- 语言自动：Minecraft 语言为中文时显示中文，其他语言显示英文。

### 8.2 全局模板

- 路径：`config\farlands-g1.properties`（Fabric 配置目录）
- 由模组初始化时自动创建（任何世界之前），带注释默认值
- 新世界（有界面或无界面）都从它继承
- 专用服务器没有创建界面，直接使用全局模板

### 8.3 修改已有世界

**执行**：退出世界 → 编辑 `<世界>\farlands.properties`（或全局模板）→ 重新进入世界。

**配置绝不在运行中的世界内改动。** epoch 键请优先用 `/realtp`；手动改会让已生成区块错位（见 R1）。
