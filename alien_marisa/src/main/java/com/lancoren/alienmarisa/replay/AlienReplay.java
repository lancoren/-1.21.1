package com.lancoren.alienmarisa.replay;

import com.lancoren.alienmarisa.AlienConfig;
import com.lancoren.alienmarisa.AlienMarisaMod;
import com.lancoren.alienmarisa.core.AlienFormNamer;
import com.lancoren.alienmarisa.core.AlienLibrary;
import com.lancoren.alienmarisa.core.AlienMath;
import com.lancoren.alienmarisa.core.LearnedAbility;
import com.lancoren.alienmarisa.observe.AlienObservation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 复现端 —— 用你的招打你。
 *
 * <p>她不播放动画，而是按学到的参数重建飞行物 / 演出近战。
 * 因为是「施法者无关」的（只接收 caster 参数），
 * 等 Boss 实体做好后把 caster 换成她即可，逻辑一行不用改。</p>
 *
 * <p>{@link #canHit} 是压迫感的核心：
 * 出手前先算一遍「这一招此刻能不能打中你」，只放有效的 ——
 * 玩家观感上就是「她在针对我」。</p>
 */
public final class AlienReplay {

    private AlienReplay() {
    }

    // ===================== 命中预判 =====================

    /**
     * 这一招此刻能不能打中目标？
     *
     * <p>近战：距离 + 夹角判定。
     * 弹幕：逐 tick 推进弹道（含重力与阻力），取最接近目标时的距离。</p>
     */
    public static boolean canHit(Entity caster, Entity target, LearnedAbility ability) {
        if (caster == null || target == null || ability == null) {
            return false;
        }
        if (ability.kind == LearnedAbility.Kind.MELEE || ability.kind == LearnedAbility.Kind.RANGED) {
            double dist = caster.distanceTo(target);
            if (dist > 4.5D) {
                return false;
            }
            Vec3 local = AlienMath.toLocal(target.position().subtract(caster.position()), caster.getYRot());
            double angle = Math.abs(Math.toDegrees(Math.atan2(local.x, local.z)));
            return angle <= 75.0D;
        }

        Vec3 vel = AlienMath.toWorld(ability.speedLocal, caster.getYRot());
        if (vel.lengthSqr() < 1.0E-6D) {
            return false;
        }
        vel = AlienMath.clampSpeed(vel, AlienConfig.PROJECTILE_SPEED_CAP);

        Vec3 pos = muzzle(caster);
        Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        double best = Double.MAX_VALUE;

        for (int i = 0; i < AlienConfig.PREDICT_STEPS; i++) {
            pos = pos.add(vel);
            vel = vel.add(0.0D, -0.05D, 0.0D).scale(0.99D);
            double d = pos.distanceTo(aim);
            if (d < best) {
                best = d;
            }
            if (d > 64.0D) {
                break;
            }
        }
        return best <= AlienConfig.HIT_TOLERANCE;
    }

    // ===================== 复现 =====================

    /**
     * 复现一条能力。
     *
     * @param caster 施法者（测试期可以是玩家，正式期传入她自己）
     * @param target 目标
     * @return true = 真的打出来了
     */
    public static boolean replay(Entity caster, LearnedAbility ability, Entity target) {
        if (!AlienConfig.ENABLED || caster == null || ability == null) {
            return false;
        }
        Level level = caster.level();
        if (level.isClientSide) {
            return false;
        }

        ability.markUsed(AlienObservation.now());

        if (ability.kind == LearnedAbility.Kind.MELEE || ability.kind == LearnedAbility.Kind.RANGED) {
            replayMelee(caster, target, ability);
            announce(caster, ability);
            return true;
        }
        boolean ok = replayProjectile(caster, target, ability);
        if (ok) {
            announce(caster, ability);
        }
        return ok;
    }

    /** 近战 / 远程的演出：粒子 + 可选伤害 */
    private static void replayMelee(Entity caster, Entity target, LearnedAbility ability) {
        Level level = caster.level();
        if (level instanceof ServerLevel) {
            ServerLevel server = (ServerLevel) level;
            Vec3 hit = target == null
                    ? caster.position().add(AlienMath.front(caster.getYRot()).scale(1.5D))
                    : target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    hit.x, hit.y, hit.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            server.sendParticles(ParticleTypes.CRIT,
                    hit.x, hit.y, hit.z, 8, 0.3D, 0.3D, 0.3D, 0.05D);
        }
        if (AlienConfig.REPLAY_DAMAGE && target instanceof LivingEntity) {
            float amount = (float) Math.max(1.0D, ability.damage);
            target.hurt(level.damageSources().source(DamageTypes.MOB_ATTACK, caster), amount);
        }
    }

    /** 按学到的弹道重建飞行物 */
    private static boolean replayProjectile(Entity caster, Entity target, LearnedAbility ability) {
        if (ability.entityType == null) {
            return false;
        }
        Level level = caster.level();
        Entity spawn = ability.entityType.create(level);
        if (spawn == null) {
            return false;
        }

        Vec3 front = AlienMath.front(caster.getYRot());
        Vec3 origin = muzzle(caster).add(front.scale(0.6D));
        spawn.setPos(origin.x, origin.y, origin.z);

        Vec3 vel = AlienMath.toWorld(ability.speedLocal, caster.getYRot());
        vel = AlienMath.clampSpeed(vel, AlienConfig.PROJECTILE_SPEED_CAP);
        spawn.setDeltaMovement(vel);

        if (spawn instanceof Projectile) {
            ((Projectile) spawn).setOwner(caster);
        }
        if (spawn instanceof AbstractArrow) {
            AbstractArrow arrow = (AbstractArrow) spawn;
            double damage = AlienConfig.REPLAY_DAMAGE ? Math.max(1.0D, ability.damage) : 0.01D;
            arrow.setBaseDamage(damage);
            for (LearnedAbility.EffectEntry entry : ability.effects) {
                if (entry.effect != null && AlienConfig.COPY_EFFECTS) {
                    arrow.addEffect(new MobEffectInstance(
                            BuiltInRegistries.MOB_EFFECT.wrapAsHolder(entry.effect),
                            entry.duration, entry.amplifier));
                }
            }
        }

        // 打标记：防止她学着学着自己递归
        spawn.getPersistentData().putBoolean(AlienMarisaMod.TAG_REPLICA, true);

        level.addFreshEntity(spawn);
        return true;
    }

    private static Vec3 muzzle(Entity caster) {
        return caster.position().add(0.0D, caster.getEyeHeight() * 0.9D, 0.0D);
    }

    // ===================== 调度（给后续 AI / 指令用） =====================

    /** 随机抽一招打出去 */
    public static boolean replayRandom(Entity caster, Entity target) {
        LearnedAbility ability = AlienLibrary.random();
        return replay(caster, ability, target);
    }

    /** 只挑此刻真能打中你的招 —— 「她在针对你」的观感来源 */
    public static boolean replayCanHit(Entity caster, Entity target) {
        List<LearnedAbility> all = AlienLibrary.all();
        if (all.isEmpty()) {
            return false;
        }
        LearnedAbility best = null;
        double bestDist = Double.MAX_VALUE;
        for (LearnedAbility ability : all) {
            if (!canHit(caster, target, ability)) {
                continue;
            }
            double d = ability.kind == LearnedAbility.Kind.MELEE
                    ? Math.abs(ability.distance - caster.distanceTo(target))
                    : 0.0D;
            if (d < bestDist) {
                bestDist = d;
                best = ability;
            }
        }
        if (best == null) {
            return false;
        }
        return replay(caster, best, target);
    }

    /** 连续复现 n 次（弹幕海演出） */
    public static int replayBurst(Entity caster, Entity target, int count) {
        int done = 0;
        for (int i = 0; i < count; i++) {
            if (replayRandom(caster, target)) {
                done++;
            }
        }
        return done;
    }

    // ===================== 播报 =====================

    /** 台词：先报形态编号，再点名这招是从你那儿来的 */
    private static void announce(Entity caster, LearnedAbility ability) {
        if (!AlienConfig.REPLAY_BROADCAST) {
            return;
        }
        Component message = Component.literal("§5【异形魔理沙】「用" + AlienFormNamer.nextFormTitle() + "——")
                .append(Component.literal("§5『" + ability.describe() + "』da☆ze"));
        if (caster.level() instanceof ServerLevel) {
            ServerLevel level = (ServerLevel) caster.level();
            for (ServerPlayer player : level.players()) {
                if (player.distanceTo(caster) <= AlienConfig.BROADCAST_RADIUS) {
                    player.sendSystemMessage(message);
                }
            }
        }
        if (AlienConfig.DEBUG) {
            AlienMarisaMod.LOGGER.info("[AlienMarisa] 复现: {}", ability.describe());
        }
    }
}
