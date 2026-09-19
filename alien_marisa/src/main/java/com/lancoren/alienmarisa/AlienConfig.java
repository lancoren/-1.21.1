package com.lancoren.alienmarisa;

/**
 * 全局开关。
 *
 * <p>先用静态字段而不是 NeoForge Config API：原型阶段改起来最快，
 * 等定稿后再整体迁到 {@code NeoForgeConfig} / {@code ModConfigSpec}。</p>
 *
 * <p>所有数值都可以在游戏里用 {@code /alienmarisa config} 查看。</p>
 */
public final class AlienConfig {

    private AlienConfig() {
    }

    // ===================== 观测端 =====================

    /** 总开关：false 则什么都不录、什么都不复现 */
    public static boolean ENABLED = true;

    /** 录制近战（蓄力档 / 距离 / 武器 / 暴击） */
    public static boolean OBSERVE_MELEE = true;

    /** 录制弓弩、三叉戟等蓄力释放 */
    public static boolean OBSERVE_RANGED = true;

    /** 录制投射物与弹幕（含妖归 Danmaku，只要它继承 Projectile 或可被 owner 追溯） */
    public static boolean OBSERVE_PROJECTILE = true;

    /** 一并复制目标身上的增益效果（BENEFICIAL 类） */
    public static boolean COPY_EFFECTS = true;

    /** 一并记录主手 / 副手道具（原作精髓：她连冈格尼尔都能具现） */
    public static boolean COPY_ITEMS = true;

    /** 只学玩家。关掉则连其他生物 / 其他 Boss 的攻击一起学（更「异形」，但库膨胀很快） */
    public static boolean OBSERVE_ONLY_PLAYER = true;

    /** 观测半径（格）：超出这个距离的人她懒得看 */
    public static double OBSERVE_RADIUS = 64.0D;

    // ===================== 能力库 =====================

    /** 能力库容量上限。超出后淘汰最久没用过的一条（防止无限膨胀） */
    public static int MAX_ABILITIES = 240;

    /** 单条能力的保质期（tick）：默认 10 分钟。到期遗忘 —— 逼玩家持续掏新招，也防内存膨胀 */
    public static int ABILITY_TTL_TICKS = 20 * 60 * 10;

    // ===================== 复现端 =====================

    /** 复现出的弹幕是否真的造成伤害。测试期建议 false，只验证弹道与演出 */
    public static boolean REPLAY_DAMAGE = false;

    /** 复现时是否播报「第 X 号形态」台词 */
    public static boolean REPLAY_BROADCAST = true;

    /** 播报的听觉半径（格） */
    public static double BROADCAST_RADIUS = 48.0D;

    /** 复现飞行物时的速度上限（格/tick），防止抄到离谱弹道把客户端卡爆 */
    public static double PROJECTILE_SPEED_CAP = 4.0D;

    /** 命中预判的模拟步数：越大越准、越吃性能 */
    public static int PREDICT_STEPS = 80;

    /** 命中判定容差（格） */
    public static double HIT_TOLERANCE = 1.8D;

    /** 复现冷却（tick）。0 = 不冷却 */
    public static int REPLAY_COOLDOWN = 0;

    // ===================== 调试 =====================

    /** 调试日志：每次录制 / 复现都往控制台打一行 */
    public static boolean DEBUG = false;
}
