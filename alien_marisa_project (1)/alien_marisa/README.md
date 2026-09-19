# 异形魔理沙 / Alien Marisa —— Step 1+2 工程

NeoForge **1.21.1** / Java 21。本阶段只做两件事：**观测录制**（看一眼就学会）与**复现回敬**（用你的招打你）。
刻意**不含 Boss 实体 / 模型 / 渲染器**，先把「学习」这条最独特的机制跑通，确认手感后再给她配身体。

---

## 0. 首次编译（重要）

仓库未附带 `gradle-wrapper.jar`（需本机生成一次），三种启动方式任选：

| 方式 | 操作 |
|---|---|
| **A. IDEA（推荐）** | File → Open 选中本目录 → 让 IDEA 自动下载 Gradle 与 NeoForge 依赖 → Build |
| **B. 命令行** | 先装 Gradle 8.x，在项目根目录执行 `gradle wrapper --gradle-version 8.8`，之后 `./gradlew build` |
| **C. 已有 Gradle** | 直接 `gradle build`（等价于 `./gradlew build`） |

产物：`build/libs/alien_marisa-0.1.0.jar`，丢进 `mods/` 即可。

> 依赖版本在 `gradle.properties`：Minecraft `1.21.1`、NeoForge `21.1.236`、ModDev Plugin `2.0.143`。
> 如果你的开发环境锁定了别的 NeoForge 小版本，改 `neo_version` 一行即可。

---

## 1. 三分钟内看到效果

1. 进存档，随手打几下怪、射几箭、丢几个雪球 / 药水
   → 聊天栏飘 `§5【异形魔理沙】「……我收下了，da☆ze」`
2. `/alienmarisa list` —— 看她学会了什么（含类型、弹速、距离、使用次数、`[有道具]` / `[有NBT]` 标记）
3. `/alienmarisa last` / `random` / `replay <n>` —— 她还给你（在你正前方生成，弹道与你当时完全一致）
4. `/alienmarisa canhit` —— 只放**此刻真能打中目标**的招（压迫感核心）
   * 不带参数时目标 = 你准星 24 格内指向的实体；`/alienmarisa canhit <实体>` 可显式指定
   * ⚠️ 别拿自己当目标：距离恒为 0，永远判定命中，测不出「只放有效招」
5. `/alienmarisa burst 20` —— 弹幕海演出
6. `/alienmarisa item <n>` —— 她把你当时用的道具具现出来（原作里她连冈格尼尔都能掏出来）
7. `/alienmarisa form` —— 随机形态名播报：`第 3,271,845 号形态『螺旋·燃烧·回旋·弹幕海·扫帚』`
8. `/alienmarisa set <字段名> <值>` —— 运行时改配置，例：`set REPLAY_DAMAGE true`、`set PROJECTILE_SPEED_CAP 3`
   （可用字段名见 `/alienmarisa config`）

> 默认 `REPLAY_DAMAGE=false`：复现出的弹幕不造成伤害，只验证弹道与演出。
> 想验证杀伤，改 `AlienConfig.REPLAY_DAMAGE = true`。

---

## 2. 机制骨架

```
观测：AttackEntityEvent（近战）/ LivingEntityUseItemEvent.Stop（弓弩）
      / EntityJoinLevelEvent（飞行物与弹幕，直抄 getDeltaMovement）
   ↓  参数化（弹道转局部坐标、蓄力档、距离档、暴击、主副手道具、增益 buff）
录制：AlienLibrary（去重键计数 / LRU 淘汰 / TTL 过期遗忘）
   ↓
筛选：AlienReplay.canHit() —— 逐 tick 推进弹道预判命中，只放有效的
   ↓
复现：按参数重建实体（速度、伤害、药水效果）+ 形态名播报
```

### 关键点

- **弹道用局部坐标存**：录制时把世界速度转成「相对施法者朝向」的 x(右)/y(上)/z(前)，
  复现时按她当前朝向转回世界坐标 —— 她换任何角度站，打出来的都是你的弹道。
- **防自噬**：复现出的实体打上 `alien_marisa:replica` 标记，观测端遇到直接跳过，
  否则她会学着学着自己递归。
- **不依赖 EpicFight**：Mimic 那套 `AttackAnimation` + 逐帧判定框在这里换成「弹道向量 + 距离/角度」，
  对鬼巫女/妖归这类弹幕模组反而更贴。
- **招式有保质期**：`ABILITY_TTL_TICKS`（默认 10 分钟）到期遗忘，逼玩家持续掏新招，也防内存膨胀。
- **实体 NBT 快照**：录制时顺带抓一份原实体 NBT（去掉 Pos / UUID / Owner 等污染字段），
  复现时经 `EntityType.loadEntityRecursive` 还原 —— 这是她能抄妖归这类**自定义弹幕**的命门。
  只造类型不还原 NBT 的话，这类弹幕生成出来就是空壳。
  `serializeNBT` 在 1.21.1 / 1.21.2+ 之间改过签名，`core/AlienNbt` 用反射探测，两边都能编过。
- **弹道预测按类型取重力**：箭矢 0.05 重力 + 0.99 阻力，弹幕类无重力。
  硬编码箭矢参数会让妖归弹幕的命中预判整体算偏。
- **能力库上限**：`MAX_ABILITIES=240`，满了淘汰最久没用过的（LRU），她永远记得你最近在用的招。

---

## 3. 文件说明

| 文件 | 作用 |
|---|---|
| `AlienMarisaMod.java` | `@Mod` 主类，注册指令 |
| `AlienConfig.java` | 全部开关与阈值（观测范围 / 上限 / 是否造成伤害 / 速度上限） |
| `core/LearnedAbility.java` | 单条学会的能力 + NBT 持久化（只存标量与注册名，避开 HolderLookup） |
| `core/AlienLibrary.java` | 能力库：去重计数 / LRU 淘汰 / TTL 过期 / 存取 |
| `core/AlienFormNamer.java` | 427 万形态：组合生成 + 编号播报 |
| `core/AlienNbt.java` | 实体 NBT 快照读写（反射兼容 1.21.1 / 1.21.2+ 签名差异） |
| `core/AlienMath.java` | 局部 ⇄ 世界坐标换算、速度限幅、分档 |
| `observe/AlienObservation.java` | 三个观测事件 + tick 清理 |
| `replay/AlienReplay.java` | 复现引擎：命中预判 / 实体重建 / 弹幕海 / 播报 |
| `command/AlienTestCommand.java` | `/alienmarisa` 调试指令 |
| `形态采样表.md` | 1000 条形态采样，可直接当台词素材 |

---

## 3.5 已知取舍

| 项 | 现状 |
|---|---|
| 道具引用（`ItemStack`） | **不进 NBT**，只活在当前会话。`/alienmarisa item` 重启后失效，`list` 里用 `[有道具]` 标记。要持久化需走 `HolderLookup` 的 `ItemStack.save`，等接实体时一起做 |
| `RANGED`（弓 / 弩）的命中判定 | 蓄力释放那一刻还没生成箭，没有弹道可推 → 用「32 格 + 25° 瞄准扇区」近似，不是真弹道预测 |
| 弹道预测 | 只考虑重力 + 阻力，不考虑碰撞体与目标移动；目标静止时才准 |
| 能力库 | 进程内静态库，服务端重启即清空 |

---

## 4. 接入 Boss 实体时要改的三个地方

复现端是**施法者无关**的，等她有了身体：

1. **能力库挂到实体**：把 `AlienLibrary` 的静态 `Map` 换成实体字段，
   用 `saveAll()` / `loadAll(CompoundTag)` 写进她的持久化数据。
2. **复现时传她自己**：`AlienReplay.replay(marisa, ability, target)`，
   caster 一换，飞行物的 owner、朝向、枪口位置全自动跟着变。
3. **观测半径改成她的感知范围**：`AlienObservation` 里加一条「距离她 N 格内才录」。

其余逻辑一行不用改。

---

## 5. 已知需要留意的 API 点（若编译报错照此调整）

Minecraft 1.21.x 小版本之间有过几次改名，本工程已按 1.21.1 写，但仍建议留意：

| API | 若报错的替代写法 |
|---|---|
| `DamageSources#source(ResourceKey, Entity)` | 换成 `level.damageSources().mobAttack(caster)` |
| `EntityType#loadEntityRecursive(CompoundTag, Level, Function)` | 换成 `EntityType.create(level)` 后手动 `spawn.load(tag)` |
| `Registry#wrapAsHolder(MobEffect)` | 换成 `BuiltInRegistries.MOB_EFFECT.getOrThrow(...)` 或直接用 `MobEffectInstance` 的 Holder |
| `ServerLevel#sendParticles(...)` | 签名一致；若提示不存在，改用 `level.sendParticles(...)` |
| `LivingEntityUseItemEvent.Stop#getDuration()` | NeoForge 里若改名，用 `getUseItem().getUseDuration(entity)` 反推蓄力 |
| `BuiltInRegistries.ENTITY_TYPE.getValue(ResourceLocation)` | 1.21.2+ 用 `getOrThrow` / `ResourceKey` 版本 |

---

## 6. 后续（本包未包含，确认手感后再做）

- Step 3 演出：断肢瞬复（血量归零不真死）/ 捏碎弹丸 / **伤害不可回复标记** / 飞行扫帚 AI
- Step 4 Boss 实体：阶段机（观测 5 / 15 / 30 种招式推进）、虚影、掉落物
- 击破条件：**本包完全不涉及**，等你后续定机制再单独设计

---

## 7. 许可

MIT。东方 Project 为 ZUN 的二次创作作品，本模组为同人非商用性质。
