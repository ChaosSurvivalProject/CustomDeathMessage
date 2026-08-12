# CustomDeathMessages

个性化死亡播报插件，替代原版死亡信息，支持自定义文案与颜色（MiniMessage 富文本）。

> 目标服务器：**Purpur 26.2（MC 1.21.1）** · 消息格式：**MiniMessage**（Adventure）

## 功能特性

- 🗣️ **个性化死亡播报**：每类死因可配置多条文案，随机或轮流播报，告别原版干巴巴的「xxx 死了」
- 🎨 **MiniMessage 富文本**：颜色、渐变、彩虹、粗斜体、悬停详情等丰富样式（非旧版 `&` 颜色码）
- 🧩 **智能死因分类**：PVP / 怪物 / 环境伤害自动识别，支持按怪物定制专属文案（如僵尸、骷髅、苦力怕）
- 📢 **广播范围控制**：全服 / 本世界 / 附近半径（`global` / `world` / `nearby`）
- 🔔 **死亡音效**：可配置音效类型、音量音调、播放对象（本人 / 附近 / 全服）
- 🏆 **击杀者互动**：击杀者 actionbar 反馈、连杀称号（屏幕中央大字标题，二连击破→五连绝世）、首杀播报
- 📜 **三种卷轴**：护身卷轴（死亡免掉落）、回城卷轴（传送回家）、回溯卷轴（传送回死亡点）
- 📅 **每日签到**：玩家每天签到领取卷轴奖励，跨天自动重置（YAML 持久化，重启不丢）
- 🛠️ **合成配方**：三种卷轴均有合成配方，自动解锁到配方书（含基岩版玩家友好提示）
- ✅ **兼容基岩版**：软依赖 Geyser-Spigot，基岩玩家可获得完整签到/配方体验

## 环境要求

| 项目 | 要求 |
| ---- | ---- |
| 服务端 | Paper / Purpur 1.20+（推荐 1.21.1，即 Purpur 26.2） |
| Java | 服务器运行时需 **Java 21**（MC 1.21.1 硬性要求） |
| 可选 | Geyser-Spigot（基岩版玩家体验优化，未安装不影响核心功能） |

## 安装

1. 将 `CustomDeathMessages-1.0.0.jar` 放入服务器的 `plugins/` 目录
2. 重启服务器（或执行 `/reload confirm`，建议重启以完成配方注册）
3. （可选）安装 [Geyser-Spigot](https://geysermc.org/)，获得基岩版玩家完整体验

数据文件：`plugins/CustomDeathMessages/data/checkins.yml`（签到记录，可放心随服务器备份）。

## 命令与权限

主命令：`/customdeath`，别名 `/cdm`。

| 命令 | 说明 | 权限 | 默认 |
| ---- | ---- | ---- | ---- |
| `/cdm reload` | 重新加载配置（无需重启） | `customdeath.reload` | op |
| `/cdm version` | 查看插件版本 | 无 | 所有人 |
| `/cdm recipe` | 在聊天栏查看卷轴合成配方 | 无 | 所有人 |
| `/cdm checkin` | 每日签到领取卷轴奖励（每天一次，0 点重置） | 无 | 所有人 |
| `/cdm scroll <protect\|town\|recall> <玩家> [数量]` | 发放护身/回城/回溯卷轴（数量 1~64） | `customdeath.scroll` | op |

## 死亡播报

### 死因分类

插件按以下优先级自动匹配文案分类（`config.yml` 的 `messages:` 下），未命中则回退 `default`：

1. **PVP**（玩家或玩家射出的投射物击杀）→ `pvp`
2. **怪物**：先按实体英文小写名（如 `zombie`），再按中文名（如 `僵尸`）匹配，都没有 → `monster`
3. **环境伤害**：按伤害原因映射（如 `FALL`→`fall`、`FIRE`→`fire`），未列出 → `default`
4. **其他实体**（如 TNT 等）→ 英文小写名或 `monster`

预置分类：`fall`（坠落）、`fire`（火焰）、`lava`（岩浆）、`drowning`（溺水）、`suffocation`（窒息）、`void`（虚空）、`explosion`（爆炸）、`lightning`（闪电）、`magic`（魔法）、`poison`（中毒）、`starvation`（饥饿）、`fall_block`（坠落方块）、`pvp`、`monster`、`zombie`、`skeleton`、`creeper`、`spider`、`enderman`、`default`

### 消息占位符

在 `config.yml` 的文案模板中可直接使用：

| 占位符 | 含义 | 说明 |
| ------ | ---- | ---- |
| `{player}` | 死亡玩家名 | 自动套用 `player-name-color` 颜色 |
| `{killer}` | 击杀者名 | 自动套用 `killer-name-color`；无击杀者时为空 |
| `{weapon}` | 击杀武器名 | 无武器时为空；配置了武器详情时自动附加悬停展示 |
| `{damage}` | 最终伤害 | 保留 1 位小数 |
| `{world}` | 所在世界名 | |

> 模板未写 `{weapon}` 但实际发生击杀时，会自动在末尾追加「用 {weapon} 击杀」。

### 广播范围与选文

- `settings.broadcast-mode`：`global`（全服）/ `world`（本世界）/ `nearby`（死亡地点 `nearby-radius` 格内）
- `settings.message-selection`：`random`（随机）/ `cycle`（轮流，保证每条文案都被用到）

### 死亡音效

`settings.death-sound` 可配置音效类型（Bukkit `Sound` 枚举）、音量、音调与播放对象（`victim` 本人 / `nearby` 附近 / `everyone` 全服）。

## 击杀者互动

| 功能 | 触发 | 表现 |
| ---- | ---- | ---- |
| 击杀反馈 | PVP 击杀 | actionbar 向击杀者本人展示确认消息（`messages.killer-feedback` 文案池） |
| 连杀称号 | 同一玩家连续击杀 ≥2 次 | 屏幕中央大字标题展示称号（二连击破 / 三连决胜 / 四连超凡 / 五连绝世，超过 5 连沿用最高档），广播范围内可见 |
| 首杀 | 每次服务器启动后第一次玩家死亡 | 专属文案播报（`messages.first-blood` 文案池） |

> 连杀记录为内存态，服务器重启后清零。

## 卷轴系统

三种卷轴均以 **PDC 标识识别**（非显示名，防玩家重命名伪造），可通过合成、每日签到、`/cdm scroll` 命令获得。

### 护身卷轴（被动）

背包（含装备栏与副手）携带时，**死亡免于掉落物品与经验**，并消耗一张。可配置是否同时保留经验等级（`keep-level`）。

### 回城卷轴（右键使用）

右键后进入施法倒计时（`cast-time` 秒，0 = 立即传送），读条结束传送到**床（重生点）**位置；未设置重生点时提示不可用、**不消耗卷轴**。

### 回溯卷轴（右键使用）

右键后进入施法倒计时，读条结束传送到**最近的死亡点**（由插件自动记录）；无死亡记录时提示不可用、**不消耗卷轴**。

### 施法表现（回城/回溯共用，可配置）

- 物品栏**冷却条**（类似末影珍珠，`cooldown-enabled`）
- 周身**传送粒子**圆环（默认 `PORTAL`，`particle-enabled` / `particle` / `particle-amount`）
- 倒计时**节拍音效**（仅施法者本人可听，`cast-sound-enabled` / `cast-sound` / `cast-sound-interval`）
- 传送完成后**完成音效**（默认 `ENTITY_ENDERMAN_TELEPORT`）与**白色环绕到达粒子**（默认 `END_ROD`）
- 施法期间**移动**（`cancel-on-move`）、死亡、退出、传送均可打断，**打断不消耗卷轴**

### 合成配方（无序合成）

| 卷轴 | 材料 |
| ---- | ---- |
| 护身卷轴 | 纸 ×1 + 金锭 ×1 + 下界之星 ×1 |
| 回城卷轴 | 纸 ×1 + 任意颜色床 ×1 + 末影珍珠 ×1 |
| 回溯卷轴 | 纸 ×1 + 末影珍珠 ×1 + 回响碎片 ×1 |

配方自动解锁到玩家配方书（`settings.recipes.auto-discover`）；开启进服提示后，玩家加入游戏时会在聊天栏展示一次配方（解决基岩版玩家配方书不显示自定义配方的问题），也可随时用 `/cdm recipe` 查看。

## 每日签到

- 玩家每天可执行 `/cdm checkin` 签到一次，领取卷轴奖励（默认三种卷轴各 ×1，可在 `settings.checkin.rewards` 配置）
- 同一天不可重复签到，跨天（服务器 0 点）自动重置，**无需重启**
- 签到数据持久化于 `data/checkins.yml`
- 进服提示：当天未签到的玩家进服 1 秒后收到提示——Java 玩家为**可点击文本**（点击即签到），基岩版玩家提示手动输入 `/cdm checkin`（基岩端不支持聊天点击交互）
- 背包空间不足时**整体回滚**：要么全发、要么全不发，不标记签到，腾出空间后可重试

## 配置说明

配置文件：`plugins/CustomDeathMessages/config.yml`（首次启动自动生成）。修改后执行 `/cdm reload` 生效。

- 消息格式使用 **MiniMessage 语法**，支持 `<red>`、`<gradient:red:gold>`、`<rainbow>`、`<#FF5500>`、`<hover:show_text:'...'>`、`<click:run_command:'...'>` 等标签
- `settings.*`：插件开关、广播模式、选文策略、颜色、音效、击杀者互动、卷轴、每日签到
- `messages.<分类>`：各分类文案列表（每条一行，支持全部占位符）

> ⚠️ 合成配方的开关（`settings.recipes.enabled`）修改后需**重启服务器**才能从配方书移除。

## 开发者构建

```bash
mvn clean package
```

- 产物：`target/CustomDeathMessages-1.0.0.jar`（finalName 已固定，勿改名）
- **构建需 JDK 21+**：paper-api 1.21.1 的 class 版本为 65（Java 21），JDK 17 会报「类文件具有错误的版本 65.0」
- 唯一依赖 `io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT`（scope `provided`，不打包）
- `maven.compiler.release=17`：产物兼容 Java 17+ 运行时
- Git Bash / MinGW 下 `mvn` 不可用时，可绕过脚本直接用 `java` 调用 classworlds Launcher，详见 `AGENTS.md`

## 常见问题

**Q：改了配置不生效？**
A：执行 `/cdm reload`（需权限 `customdeath.reload`）。仅配方开关相关改动需要重启。

**Q：基岩版玩家看不到合成配方 / 点不了签到提示？**
A：安装 Geyser-Spigot 可获得完整支持；基岩玩家可手动输入 `/cdm recipe` 查看配方、`/cdm checkin` 签到。

**Q：卷轴怎么获得？**
A：三种途径——合成配方、每日签到（`/cdm checkin`）、管理员发放（`/cdm scroll`）。

**Q：为什么回城/回溯卷轴用了却没反应？**
A：回城卷轴需要先睡过床（设置重生点）；回溯卷轴需要存在死亡记录。两种情况均不消耗卷轴，会收到对应提示。

**Q：JDK 17 构建报「类文件具有错误的版本 65.0」？**
A：请使用 JDK 21+ 构建（详见「开发者构建」）。
