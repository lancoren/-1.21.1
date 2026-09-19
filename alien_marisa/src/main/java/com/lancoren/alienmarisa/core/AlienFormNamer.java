package com.lancoren.alienmarisa.core;

import net.minecraft.util.RandomSource;

/**
 * 「427 万种形态」的命名器。
 *
 * <p>427 万不是清单，是组合数：
 * 形状 × 属性 × 轨迹 × 规模 × 载体 × 连续参数（角度 / 密度 / 速度）。
 * 12 × 14 × 10 × 8 × 10 ≈ 134 万，再乘连续参数的档位轻松破 427 万。
 * 实际战斗只从池里采样几百种，但对外永远显示「427 万种形态」—— 观感无限，性能可控。</p>
 *
 * <p>演出关键：每次切换形态都报出编号与形态名（对应原作「用我 427 万种形态中的一种将你杀死」）。</p>
 */
public final class AlienFormNamer {

    /** 对外宣称的总数 */
    public static final long CLAIMED_TOTAL = 4_270_000L;

    private static final String[] SHAPE = {
            "星形", "激光", "魔法阵", "螺旋", "扇形", "环状", "十字", "散射",
            "贯穿", "追踪", "随机", "网目"
    };

    private static final String[] ATTR = {
            "燃烧", "冰冻", "中毒", "穿透", "爆炸", "即死", "削弱", "反治疗",
            "麻痹", "重力", "吸血", "诅咒", "净化", "光"
    };

    private static final String[] TRAJECTORY = {
            "匀速", "加速", "减速", "回旋", "分裂", "折返", "停滞", "跳跃", "缠绕", "追随"
    };

    private static final String[] SCALE = {
            "单发", "连射", "散射", "弹幕海", "全屏", "压缩", "延伸", "重叠"
    };

    private static final String[] CARRIER = {
            "扫帚", "八卦炉", "魔导书", "冈格尼尔", "星屑", "迷你八卦炉", "魔符", "音符", "螺丝", "彗星"
    };

    private static final RandomSource RANDOM = RandomSource.create();

    private AlienFormNamer() {
    }

    /** 理论组合上限（不含连续参数档位） */
    public static long combinatorics() {
        return (long) SHAPE.length * ATTR.length * TRAJECTORY.length * SCALE.length * CARRIER.length;
    }

    /** 抽一个形态编号（1 ~ 4,270,000） */
    public static long nextId() {
        return 1L + RANDOM.nextInt((int) CLAIMED_TOTAL);
    }

    /** 抽一个形态名，例：「螺旋·燃烧·回旋·弹幕海·扫帚」 */
    public static String nextName() {
        return pick(SHAPE) + "·" + pick(ATTR) + "·" + pick(TRAJECTORY) + "·" + pick(SCALE) + "·" + pick(CARRIER);
    }

    /** 完整播报：「第 3,271,845 号形态『螺旋·燃烧·回旋·弹幕海·扫帚』」 */
    public static String nextFormTitle() {
        return "第 " + format(nextId()) + " 号形态『" + nextName() + "』";
    }

    /** 千分位格式化 */
    public static String format(long value) {
        String raw = String.valueOf(value);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = raw.length() - 1; i >= 0; i--) {
            sb.insert(0, raw.charAt(i));
            count++;
            if (count % 3 == 0 && i > 0) {
                sb.insert(0, ',');
            }
        }
        return sb.toString();
    }

    private static String pick(String[] array) {
        return array[RANDOM.nextInt(array.length)];
    }
}
