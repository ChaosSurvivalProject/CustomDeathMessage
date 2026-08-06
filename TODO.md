# CustomDeathMessages 功能待办列表

> 从需求到实现请遵循 `AGENTS.md` 的「开发步骤流程」。
> 每完成一项：勾选该项 → 若改动分类/占位符/配置契约则同步更新 AGENTS.md 与 config.yml 头部注释 → 追加当日工作日志。
> 列表顺序即建议实现顺序：**P0 快速见效 → P1 互动增强 → P2 数据与生态**。

## 使用说明

- `[ ]` = 待办，`[x]` = 已完成
- 每项注明「改动位置」，便于按 Step 1 影响面分析直接开工
- 验证约定：构建验证（Step 3）必须；运行时验证（Step 4）仅在本地有 MC 服务端时执行

---

## P0 · 快速又有趣（改动小、见效快，建议优先）

- [ ] **死亡彩蛋** —— 小概率（如 2%）触发彩蛋文案，替换正常死亡播报，制造惊喜感。
  改动：`ConfigManager` 新增彩蛋列表读取 + `DeathListener` 选文前做概率替换；`config.yml` 新增 `easter-eggs:` 段落。
- [ ] **伪装死因（恶搞模式）** —— 配置开关开启后，真实死因被随机伪装成其他分类播报（如淹死播成被苦力怕炸死）。
  改动：`DeathListener.resolveDeathInfo()` 之后对分类做一次随机重映射；`config.yml settings` 新增开关。
- [ ] **死亡坐标 + 点击传送** —— 新增 `{location}` 占位符显示 X/Y/Z；消息可 hover 显示坐标、点击传回死亡点（需权限校验）。
  改动：`MessageFormatter` 新增占位符替换 + MiniMessage `<hover:show_text>` / `<click:run_command>` 包装；`plugin.yml` 新增传送权限。
- [ ] **节日/主题文案组** —— `config.yml` 支持 `themes:` 多组文案池，按日期自动启用或 `/customdeath theme` 手动切换。
  改动：`ConfigManager` 支持多主题 + `DeathListener` 按当前主题取文案；`ReloadCommand` 新增 theme 子命令。

## P1 · 击杀者互动（需引入轻量内存统计，重启清零）

- [x] **击杀者专属反馈** —— 击杀者收到独立确认消息（"你击杀了 Steve！"），以 **ActionBar** 形式展示（仅击杀者可见），与死亡播报解耦，提升 PVP 手感。
  改动：`DeathListener` 向 killer `sendActionBar`；`config.yml` 新增 `killer-feedback:` 配置与 `messages.killer-feedback` 文案池。
- [x] ~~**复仇提示**~~ —— 记录玩家间击杀关系，播报时附「这是 xxx 第 N 次击杀你」。
  状态：曾实现，后于展示升级提交 `07c66ba` **移除**（聊天栏互动文本清理，killsByKiller / revenge 配置与 getter 已删除）。如需恢复需重新实现。
- [x] **连杀计数** —— 玩家连续击杀数按等级称号展示（二连击破/三连决胜/四连超凡/五连绝世），以 **Title 屏幕大字** 展示给广播范围玩家，死亡时清零。
  改动：`DeathListener` 维护连杀 Map；`ConfigManager.getKillStreakTitle` 解析称号；`settings.kill-streak.titles` 配置称号映射。
- [x] **首杀（第一滴血）文案** —— 服务器启动后的第一次玩家死亡播报特殊「首杀」文案。
  改动：`DeathListener` 加一次性标记，命中时强制使用首杀模板。

## P2 · 统计与数据（需新增 YAML 持久化）

- [ ] **死亡统计命令** —— `/customdeath stats [player]` 查看总死亡数、各分类死亡占比、最近死亡记录。
  改动：新增 `DataManager`（YAML 持久化）+ `ReloadCommand` 新增 stats 子命令。
- [ ] **TOP 死亡排行榜** —— `/customdeath top` 展示全服死亡次数排行。
  改动：基于 DataManager 数据聚合排序。
- [ ] **死亡记录持久化** —— 玩家死亡记录（时间/分类/击杀者/坐标）落盘，重启不丢；P1 的连杀统计可升级为持久化版本。
  改动：新增 `DataManager` + 数据目录 `plugins/CustomDeathMessages/data/`。
- [ ] **摔落高度** —— `{fall_height}` 占位符，坠亡时显示"从 52 格坠落"。
  改动：需追踪玩家落地前最高点（监听移动或记录伤害事件），`MessageFormatter` 新增占位符。

## P2 · 展示与系统增强

- [x] **Title / ActionBar 播报通道** —— 击杀反馈以底部 ActionBar 展示（仅击杀者可见），连杀称号以屏幕中央 Title 大字展示（广播范围玩家可见），不占用聊天栏。
  实现：`DeathListener` 使用 adventure `sendActionBar` / `Title.title`；配置见 `settings.kill-streak.show-title / title-main / title-sub / title-fade-in / title-fade-stay / title-fade-out`（提交 07c66ba）。
- [ ] **PlaceholderAPI 软依赖** —— 模板支持 `%player_name%` 等外部变量，向服务器生态靠拢。
  改动：`pom.xml` 加可选依赖 + 运行时检测，`MessageFormatter` 增加变量解析分支。
- [ ] **死亡命令钩子** —— 死亡时按配置执行命令列表（联动 back/保险等插件），可设权限与概率。
  改动：`DeathListener` 执行 `Bukkit.dispatchCommand`；`config.yml settings` 新增 `death-commands:`。
- [ ] **Discord Webhook 播报** —— 死亡消息异步推送到 Discord 频道。
  改动：新增 HTTP 推送工具类（异步线程）；`config.yml` 新增 webhook 配置。

---

## 已完成

- （历史完成项按此记录，例如：`[x] 初版 1.0.0 —— 死亡分类播报 / 随机·轮询选文 / 广播范围 / 死亡音效 / 武器 hover 详情`）
- [x] **死亡保护券** —— 背包携带保护券时死亡免掉落（可选保留经验），消耗一张。PDC 标识防伪造；`/customdeath ticket <玩家> [数量]` 发放（权限 `customdeath.ticket`）。配置：`settings.death-ticket`。
- [x] **回城 / 回溯卷轴** —— 右键使用：回城卷轴传送到床位置（无床不可用）、回溯卷轴传送到最近死亡点（无记录不可用）。死亡点记录持久化到 `data/death-points.yml`（重启不丢）。`/customdeath scroll <town|recall> <玩家> [数量]` 发放（权限 `customdeath.scroll`）。配置：`settings.scrolls`。
- [x] **合成配方** —— 三种物品均有无序合成配方，成本梯度：回城卷轴 = 纸×2+任意床+末影珍珠；回溯卷轴 = 纸×2+末影珍珠×2+回响碎片；死亡保护券 = 纸×2+金锭×2+下界之星，产物带 PDC 标识。总开关 `settings.recipes.enabled`。`RecipeManager` 启动时注册。
