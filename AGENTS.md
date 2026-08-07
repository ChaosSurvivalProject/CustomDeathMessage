# AGENTS.md

面向 AI 编码助手 / 协作者的仓库指南。请先阅读本文件再改动代码。

## 项目概述

**CustomDeathMessages** — Minecraft Paper 服务端插件：拦截玩家死亡事件，用配置中的个性化文案（MiniMessage 富文本）替代原版死亡消息，支持死亡分类、随机/轮询选文、广播范围控制、死亡音效与武器悬浮详情。

- 目标服务器：**Purpur 26.2（MC 1.21.1）**
- 语言/运行时：**Java 17+**
- 消息格式：**MiniMessage**（Adventure），不是旧版 `&` 颜色码

## 构建与产物

```bash
mvn clean package
```

- 产物：`target/CustomDeathMessages-1.0.0.jar`（finalName 已配置，勿改名）
- 唯一依赖 `io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT`，scope 为 `provided`——**编译期依赖，不可打包进 jar**
- **Java 21 是硬性要求**：paper-api 1.21.1 的 class 版本为 65（Java 21），JDK 17 编译会报「类文件具有错误的版本 65.0」。请使用任意 JDK 21+（如 Temurin 21、Zulu 21 等）并设置 `JAVA_HOME` 后构建。
- `maven.compiler.release=17` 保留（输出 Java 17 bytecode，兼容性更好）；不要改依赖版本。
- 若 `mvn` 命令不可用（常见于 Git Bash/MinGW 环境，报「找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher」），可绕过 `mvn` 脚本，直接用 `java` 调用 classworlds Launcher 启动 Maven。将下方 `<MAVEN_HOME>` 替换为你的 Maven 安装根目录（即 `bin/mvn` 所在目录的上级），且传给 `java` 的路径须用 Windows 格式（`C:/...`，而非 Unix 的 `/c/...`）：
  ```bash
  # <MAVEN_HOME>  = 你的 Maven 安装根目录（Windows 格式，如 C:/devTools/apache-maven-x.x.x）
  # <项目根目录>   = 本仓库根目录（Windows 格式，如 E:/path/to/custom-death-message）
  # 注意：Java 是 Windows 程序，传给它的路径必须用 Windows 格式（C:/...），不能用 Unix 格式（/c/...）
  java -classpath "<MAVEN_HOME>/boot/*" \
    "-Dclassworlds.conf=<MAVEN_HOME>/bin/m2.conf" \
    "-Dmaven.home=<MAVEN_HOME>" \
    "-Dmaven.multiModuleProjectDirectory=<项目根目录>" \
    org.codehaus.plexus.classworlds.launcher.Launcher clean package
  ```
- 项目无测试目录，改动后以编译通过 + 服务器实测为准

## 目录结构

```
src/main/java/com/customdeath/
├── CustomDeathMessages.java   # 主类 (JavaPlugin)：装配各组件，注册监听器与命令
├── config/ConfigManager.java  # config.yml 加载、分类模板存取、随机/轮询选文、护身/回城/回溯卷轴构造
├── data/DeathPointManager.java # 最近死亡点记录（YAML 持久化，供回溯卷轴使用）
├── listener/DeathListener.java# 死亡事件监听：原因→分类解析、广播、音效、连杀/首杀、死亡点记录
├── listener/ScrollListener.java # 卷轴监听：护身卷轴（死亡免掉落，HIGH）+ 回城/回溯卷轴（右键施法倒计时后传送）
├── message/MessageFormatter.java # MiniMessage 格式化与占位符替换
├── recipe/RecipeManager.java   # 合成配方注册（护身/回城/回溯卷轴，无序合成）
└── command/ReloadCommand.java # /customdeath 命令（reload / version / scroll）
src/main/resources/
├── plugin.yml                 # 插件元数据、命令、权限（api-version: '1.20'）
└── config.yml                 # 全部可配置项 + 死亡文案模板（占位符见下文）
```

## 架构与数据流

```
PlayerDeathEvent (HIGHEST)
  └─ DeathListener.onPlayerDeath
       ├─ resolveDeathInfo() → 死亡分类 / 击杀者 / 武器 / 伤害
       ├─ ConfigManager.pickTemplate(category) → 随机或轮询选一条模板
       ├─ MessageFormatter.format(...) → MiniMessage 解析为 Component
       ├─ event.deathMessage(null)  # 取消原版消息，必须置空
       ├─ broadcast()  # 按 global / world / nearby 分发
       └─ playDeathSound()  # victim / nearby / everyone
```

- 依赖注入方式：各组件构造器接收 `CustomDeathMessages` 实例，通过 `getConfigManager()` / `getMessageFormatter()` 访问（参考 `ReloadCommand`、`DeathListener` 写法）。
- 配置重载：`/customdeath reload` 直接调用 `ConfigManager.loadConfig()`，所有状态（模板、轮询索引）在 load 时重建，**勿在别处缓存配置状态**。

## 死亡分类匹配规则（改这部分时务必同步 config.yml）

`resolveDeathInfo()` 按以下优先级解析，最终 key 会去 `config.yml` 的 `messages:` 下查找，找不到则回退 `default`：

1. **PVP**（玩家/玩家射出的投射物）→ `pvp`
2. **怪物**：先试实体英文小写名（如 `zombie`），再试 `MOB_CN_NAMES` 的中文名（如 `僵尸`），都没有 → `monster`
3. **环境伤害**：`CAUSE_CATEGORIES` 映射（`DamageCause` → 分类，如 `FALL→fall`、`FIRE/FIRE_TICK→fire`），未列出的原因 → `default`
4. 非玩家/怪物的实体（如 TNT）→ 英文小写名或 `monster`

新增怪物的完整步骤（三处都要改）：

- `DeathListener.MOB_CN_NAMES`：实体类型 → 中文名（影响分类 key 与悬浮详情）
- `DeathListener.MOB_WEAPON`：实体类型 → 致死方式名（**未列出的怪物不显示武器**）
- `config.yml messages:` 下新增对应分类（英文 key 或中文 key 均可）

新增附魔中文名：加入 `DeathListener.ENCHANT_CN`（key 为附魔注册名，如 `sharpness`）。未收录的附魔回退为可读英文（`prettify()`）。

## 消息模板与占位符（config.yml 与 MessageFormatter 的契约）

模板支持 5 个占位符，均在 `MessageFormatter.format()` 中替换：

| 占位符        | 含义    | 说明                                               |
| ---------- | ----- | ------------------------------------------------ |
| `{player}` | 死亡玩家名 | 自动套用 `player-name-color`                         |
| `{killer}` | 击杀者名  | 自动套用 `killer-name-color`；无击杀者时为空                 |
| `{weapon}` | 武器显示名 | 无武器时为空；有 `weaponDetail` 时自动包 `<hover:show_text>` |
| `{damage}` | 最终伤害  | 保留 1 位小数                                         |
| `{world}`  | 所在世界名 |                                                  |

特殊机制（勿破坏）：

- 模板**未写** `{weapon}` 但实际发生了击杀时，`MessageFormatter` 会自动在末尾追加 `<gold>用 {weapon} 击杀</gold>`。
- hover 详情中的单引号会被转义为 `\'`（`buildWeaponFragment`），构造 `weaponDetail` 时遵循 `<gray>标签</gray><newline>...` 片段拼接风格（参考 `buildItemDetail` / `entityInfo`）。
- MiniMessage 解析失败时降级为纯文本 `Component.text`，**不要抛异常到事件链路**。
- **附加片段（`formatExtra`）**：连杀称号经 `MessageFormatter.formatExtra` 生成，支持 `{player} {killer} {count} {streak} {title}` 五个占位符（`{title}` 为连杀等级称号，如"五连绝世"，仅连杀称号标题使用），在 `DeathListener.onPlayerDeath` 中用于渲染 **Minecraft 标题（屏幕中央大字）** 的主/副标题，展示给广播范围内的所有玩家；击杀反馈则单独经 **actionbar** 仅发给击杀者本人。聊天栏不再拼接任何互动文本。
- **击杀者互动统计**：连杀 Map 为**内存态，重启清零**；仅在 PVP（`category=="pvp"`）且击杀者在线时更新。连杀（`streak>=2`）达到阈值即以 **Minecraft 标题（屏幕中央大字）** 经 `player.showTitle(...)` 展示给广播范围内的所有玩家，主/副标题模板与停留时长由 `settings.kill-streak` 的 `show-title` / `title-main` / `title-sub` / `title-fade-*` 控制（`DeathListener.onPlayerDeath` 触发）。原「复仇提示」（同一击杀者多次击杀同一玩家）已从聊天栏移除，相关配置与统计一并删除。
- **连杀等级称号**：`settings.kill-streak.titles` 为「连杀数 → 称号」映射（参考 MOBA 播报：二连击破/三连决胜/四连超凡/五连绝世），`ConfigManager.getKillStreakTitle` 精确匹配，未命中时沿用不超过当前连杀数的最高档称号（如 6 连杀显示"五连绝世"）。
- `messages.killer-feedback`（击杀者 actionbar 反馈）与 `messages.first-blood`（服务器启动后首杀）**不是死亡原因分类**，不走 `resolveDeathInfo` 匹配：前者由 PVP 触发并经 actionbar 发给击杀者本人，后者由一次性标记触发（`cfg.hasCategory` 未配置时自动回退正常逻辑）。

## 编码规范

- 包结构按功能分包：`command / config / listener / message`，主类只做装配。
- 类与方法必须有中文 Javadoc（参考现有代码风格）；有意义的魔法表（如 `CAUSE_CATEGORIES`）加注释说明用途。
- 使用 `record` 传多字段返回值（参考 `DeathInfo`）；用 `switch` 表达式 / `Map.ofEntries`，不用样板 if-else。
- 玩家可见文本用 Adventure `Component`，日志用 `plugin.getLogger()`；控制台可打纯文本（`PlainTextComponentSerializer`）。
- 所有用户可配置值从 `ConfigManager` 读取并带默认值（如 `config.getBoolean("settings.enabled", true)`），config.yml 缺键时插件不得崩溃。
- 新配置项：`ConfigManager` 加 getter + `config.yml` 加带中文注释的默认值，两项同步。
- 新权限：`plugin.yml` 的 `permissions` 段声明，命令处理里用 `sender.hasPermission(...)` 校验（参考 `customdeath.reload`）。

## 关键文件行为摘要

- `plugin.yml`：命令 `customdeath`（别名 `cdm`），子命令 `reload`（权限 `customdeath.reload`，默认 op）、`version`、`scroll`（权限 `customdeath.scroll`，默认 op，发护身/回城/回溯卷轴，用法 `scroll <protect|town|recall> <玩家> [数量]`）。**改命令名/权限时保持 yml 与 `ReloadCommand` 一致**。
- `config.yml` 结构：`settings.*`（开关、广播模式、选文策略、颜色、音效、击杀者互动、卷轴（`scrolls.protect` / `scrolls.town` / `scrolls.recall`））+ `messages.<分类>: [文案列表]`。分类注释用 `# ----` 分隔线分组。击杀者互动配置节 `settings.killer-feedback` / `kill-streak` / `first-blood`，与 `DeathListener` 的统计逻辑一一对应。
- **物品类功能统一模式**：护身 / 回城 / 回溯卷轴均为「配置材质+名称+lore + PDC 标识」的物品（key 见 `ConfigManager` 静态常量 `SCROLL_PROTECT_KEY` / `SCROLL_TOWN_KEY` / `SCROLL_RECALL_KEY`），**识别靠 PDC 而非显示名**（防玩家重命名伪造），由 `ConfigManager.buildScrollXxx(amount)` 构造。
  - 护身卷轴：`ScrollListener` 的 `onPlayerDeath`（HIGH 优先级）检测背包（含装备/副手），命中则 `setKeepInventory(true)`（可选保留经验）+ 清空 `drops` 与 `droppedExp` 双保险，并消耗一张。
  - 回城 / 回溯卷轴：`ScrollListener` 监听右键（`PlayerInteractEvent`），`setCancelled(true)` 后进入**施法倒计时**（`cast-time` 秒，0 = 立即传送），每秒以 actionbar 提示剩余秒数；施法期间物品栏显示**冷却条**（`player.setCooldown(材质, castTime*20)`，类似末影珍珠，打断时 `setCooldown(材质, 0)` 立即恢复）并播放**传送粒子**（`settings.scrolls.cast-effect` 控制：`cooldown-enabled` / `particle-enabled` / `particle` 类型 / `particle-amount`，粒子为玩家周身水平圆环，每 5 刻一波）；倒计时结束校验主手仍持有对应卷轴后传送并消耗主手一张。施法可被移动（`cancel-on-move`，阈值约 0.1 格）、死亡、退出、传送打断（打断不消耗卷轴）；同一玩家施法期间重复右键收到提示。回城卷轴传送到 `getBedSpawnLocation()`（无床不消耗），回溯卷轴传送到 `DeathPointManager` 记录的最近死亡点（无记录不消耗；死亡点向上 6 格内无可站立位置时提示不可达不消耗）。倒计时与打断提示模板见 `settings.scrolls.town/recall` 的 `cast-start-message` / `cast-cancel-message` / `countdown-actionbar`（`{time}` 占位符）。
- **死亡点记录**：`DeathListener` 每次死亡时调用 `DeathPointManager.recordDeath`（不受 `settings.enabled` 开关影响），数据持久化到 `plugins/CustomDeathMessages/data/death-points.yml`（重启不丢）。
- **合成配方**：`RecipeManager` 在插件启动时注册 3 个无序配方（产物均为带 PDC 标识的标准物品，可在合成台/配方书使用），成本梯度：回城卷轴 = 纸 ×2 + 任意颜色床 + 末影珍珠；回溯卷轴 = 纸 ×2 + 末影珍珠 ×2 + 回响碎片；护身卷轴 = 纸 ×2 + 金锭 ×2 + 下界之星。总开关 `settings.recipes.enabled`（默认 true；关闭后需重启服务器移除已注册配方）。产物数量若后续调整，注意 ShapelessRecipe 产物为 `buildScrollXxx(1)`。
- 事件优先级 `EventPriority.HIGHEST`；音效 `SoundCategory.PLAYERS`；广播按 `distanceSquared` 比较（nearby 模式）。
- 环境原因分类表 `CAUSE_CATEGORIES` 中，`FALLING_BLOCK→fall_block`、`DRAGON_BREATH/THORNS→magic` 等合并映射，改动前先确认现有归类。

## 改动后的验证清单

1. `mvn clean package` 编译通过（无警告级别强制要求，但不得新增编译错误）。
2. 若改了解析/分类逻辑：覆盖 PVP（近战+弓箭）、怪物（已收录/未收录/中文 key）、环境伤害、未匹配（default 兜底）四类路径。
3. 若改了模板或新增分类：确认 config.yml 中 `messages:` 的 key 与代码产出的分类 key 严格一致。
4. 若改了 `MessageFormatter`：用含 `{weapon}` 与不含 `{weapon}` 的模板各测一次，确认 hover 与自动追加逻辑正常。
5. （需本地 MC 服务端，无服务端时跳过）实际部署到 Purpur 26.2 验证：消息样式、hover 悬浮、音效、`/customdeath reload` 热重载。

## 开发步骤流程

任何改动（新功能 / 修复 / 文案调整）一律按以下 6 步执行，每步有明确出口条件，**不满足不得进入下一步**。其中 Step 4 为**可选步骤**，仅在本地有可用 MC 服务端时执行（见下）。

### Step 0 · 环境准备（一次性）

- JDK 17+、Maven 可用。
- 本地测试服务器：Purpur 26.2 jar，插件放入 `plugins/`；首次启动后自动生成 `plugins/CustomDeathMessages/config.yml`。
- 出口：`mvn clean package` 能正常出包。

### Step 1 · 需求与影响面分析

先明确需求类型，再对照下表确定**必须同步修改**的位置（漏改任何一项都会导致代码与配置行为不一致）：

| 需求类型      | 必须同步修改的位置                                                                     |
| --------- | ----------------------------------------------------------------------------- |
| 新增/调整死亡分类 | `DeathListener.resolveDeathInfo()`（如需新增原因→分类映射）+ `config.yml messages:` 新增分类  |
| 新增怪物      | `DeathListener.MOB_CN_NAMES` + `MOB_WEAPON` + `config.yml` 分类（英文 key 或中文 key） |
| 新增附魔中文名   | `DeathListener.ENCHANT_CN`                                                    |
| 新增配置项     | `ConfigManager` 加 getter（带默认值）+ `config.yml` 加默认项                             |
| 新增命令/权限   | `plugin.yml` 的 commands/permissions + `ReloadCommand`                         |
| 仅调整文案/样式  | 只改 `config.yml`，不动 Java 代码                                                    |
| Bug 修复    | 按「架构与数据流」定位所属环节，改对应类                                                          |

### Step 2 · 实现

- 一次只改一件事，遵循「编码规范」章节。
- 按 Step 1 表格逐项核对同步改动。
- 出口：所有同步项已完成，代码自检通过。

### Step 3 · 构建验证

```bash
mvn clean package
```

- 确认产物 `target/CustomDeathMessages-1.0.0.jar` 生成。
- 出口：编译通过、无新增编译错误。

### Step 4 · 运行时验证（可选，非必须）

**仅在本地有可用的 MC 服务端（Purpur 26.2）时执行**；本地无服务端则跳过此步，以编译通过 + 静态走查代替（按「改动后的验证清单」逐条人工核对代码路径与配置）。

- 按「改动后的验证清单」覆盖受影响路径。
- 重点回归：PVP（近战/弓箭）、怪物（已收录/未收录/中文 key）、环境伤害、default 兜底；`/customdeath reload` 热重载。
- 出口（仅执行时适用）：受影响功能全部实测通过。

### Step 5 · 发布

- 版本号同步：`pom.xml <version>` 与 `plugin.yml version` 保持一致（当前 1.0.0）。
- 整理变更点（无 CHANGELOG 时在交付说明中写明改动内容）。
- 出口：交付 jar 与变更说明。

### Step 6 · 收尾

- 若改动了分类 / 占位符 / 配置契约，同步更新本文件与 `config.yml` 头部注释。
- 追加当日工作日志到 `.workbuddy/memory/YYYY-MM-DD.md`。
- 出口：文档与代码一致。

> **纯文案捷径**：仅调整文案/样式时，只需 只改 `config.yml` → Step 3 构建即可完成，跳过 Step 1/2/5/6 的代码相关环节；本地有服务端时可再执行 Step 4 抽查 reload。
