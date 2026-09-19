# 工作流速查（FarLands G1）

## 构建

```powershell
cd C:\Project-G1
.\gradlew.bat :patcher-cli:build :mod:build --no-daemon
```

产物：`mod/build/libs/farlands-g1-mod-1.0-SNAPSHOT.jar`

**铁律**：构建失败（`build: False`）**禁止部署**旧产物。

## 无头测试（服务器 rig）

```powershell
# 删世界跑（干净测试）
tools\server-test.ps1 -Tag "名字" -TestGen "0,0" [-SpawnSet "2147481648,100,0"] [-TimeoutMin 20]

# 保留世界跑
tools\server-test-nodel.ps1 -Tag "名字" -TestGen "0,0"
```

- `-TestGen "cx,cz[,n]"`：生成 chunk（n = n×n 区域）并打印结果
- `-SpawnSet "x,y,z"`：设 epoch + respawn（epoch = x,z 的 chunk 对齐值）
- 结果看 `RESULT(tag):` 行；完整日志在
  `C:\Users\EASON\AppData\Local\Temp\opencode\server-<tag>.log`

**注意**：rig = dedicated（无客户端流程）。客户端特有的路径
（prepare_spawn / 渲染 / 玩家加入）rig 覆盖不到——**用户准则：无头 ≠ 实际**。

## 客户端测试

1. 部署 jar 到 `D:\Minecraft\.minecraft\versions\26.2-Fabric 0.19.3_fork\mods\`
2. PCL 启动，JVM 参数加 `-Dfarlands.spawnset=2147481648,100,0`
3. 日志：fork 目录 `logs/latest.log`（grep `[FarLands`）
4. 游戏内控制：mc-command bridge（tp / 命令 / 截图）

## 日志标记

| 标记 | 来源 |
|---|---|
| `[FarLands-G1]` | mod 启动 / epoch 设定 |
| `[FarLands-Test]` | 无头探针（testgen/spawnset/testspawn） |
| `[FarLands]` | 运行时事件（重定位/ticket/acquire） |

## 诊断习惯（教训五条铁律）

1. **先写域账本**：动代码前先写下坐标的域
2. **先验证代码在跑**：println 必须 `flush()`；构建失败禁部署；测试第一项看版本行
3. **两次理论失败转测量**：二分 / JFR / 打印键值，别继续猜
4. **每轮单变量**
5. **双端共享状态第一轮上日志**（谁设的、什么时候设的）

## 提交

- **不自动 push**（用户开代理时才推）
- 公共仓库只提交可用态
- 每过一个交界点一个 commit

## 目录速查

```
C:\Project-G1\
├─ patcher-core\     字节码补丁（改原版 jar）
├─ patcher-cli\      补丁 CLI
├─ mod\              Fabric mod（mixin）
│   └─ src/main/resources\
│       ├─ farlands-core.mixins.json      坐标容器/投影
│       ├─ farlands-epoch.mixins.json     纪元域（spawn 链）
│       ├─ farlands-world.mixins.json     世界/生成/光照
│       ├─ farlands-entity.mixins.json    实体/玩家/交互
│       ├─ farlands-stability.mixins.json 稳定性/性能
│       ├─ farlands-test.mixins.json      无头探针
│       └─ farlands-client.mixins.json    客户端渲染 + E3 重定位
├─ tools\
│   ├─ server-test.ps1      无头 rig（删世界）
│   ├─ server-test-nodel.ps1 无头 rig（保留世界）
│   └─ chunk-translator\    存档平移工具
└─ docs\
    ├─ ROADMAP.md           路线图/教训
    ├─ E-LINE-DESIGN.md     E 线设计
    ├─ REVIEW.md            基础知识复习
    └─ WORKFLOW.md          本文件
```
