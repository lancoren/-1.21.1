package com.lancoren.alienmarisa.core;

import net.minecraft.world.phys.Vec3;

/**
 * 空间换算工具。
 *
 * <p>录制时把世界坐标系下的速度 / 相对位置转成「相对施法者朝向的局部向量」，
 * 复现时再按施法者当前朝向转回世界坐标 —— 这样她换一个角度站着，也能打出和你一样的弹道。</p>
 *
 * <p>Minecraft 的 yaw：0 = 朝 +Z（南），90 = 朝 -X（西）。
 * 变换矩阵 A = [[-cos, -sin], [-sin, cos]]，A 既对称又正交，所以 A = A⁻¹，
 * 同一个矩阵既能做 toLocal 也能做 toWorld。</p>
 */
public final class AlienMath {

    private AlienMath() {
    }

    /** 水平前向单位向量（忽略俯仰） */
    public static Vec3 front(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }

    /** 水平右向单位向量 */
    public static Vec3 right(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.cos(rad), 0.0D, -Math.sin(rad));
    }

    /** 世界向量 → 局部向量（x = 右，y = 上，z = 前） */
    public static Vec3 toLocal(Vec3 world, float yaw) {
        double rad = Math.toRadians(yaw);
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        double x = world.x * (-c) + world.z * (-s);
        double z = world.x * (-s) + world.z * c;
        return new Vec3(x, world.y, z);
    }

    /** 局部向量 → 世界向量（{@link #toLocal} 的逆运算） */
    public static Vec3 toWorld(Vec3 local, float yaw) {
        double rad = Math.toRadians(yaw);
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        double x = local.x * (-c) + local.z * (-s);
        double z = local.x * (-s) + local.z * c;
        return new Vec3(x, local.y, z);
    }

    /** 把速度限制在上限内，返回新的 Vec3 */
    public static Vec3 clampSpeed(Vec3 v, double cap) {
        double len = v.length();
        if (len <= cap || len < 1.0E-6D) {
            return v;
        }
        return v.scale(cap / len);
    }

    /** 分档：把连续值压成整数档位，用于去重与「招式区分度」 */
    public static int bucket(double value, double step, int maxBucket) {
        int b = (int) Math.floor(value / step);
        return Math.max(0, Math.min(maxBucket, b));
    }
}
