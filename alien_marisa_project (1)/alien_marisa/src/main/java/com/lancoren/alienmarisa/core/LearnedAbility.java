package com.lancoren.alienmarisa.core;

import com.lancoren.alienmarisa.AlienConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一条「学会的能力」。
 *
 * <p>她不是播放动画，而是把一次攻击拆成可复现的参数：
 * 类型、弹道、伤害、蓄力、距离、朝向、当时的道具与 buff。
 * 复现端拿着这组参数就能原样打回去 —— 这也是为什么连别的模组的弹幕都能抄
 * （妖归的弹幕只要能拿到 owner 与 deltaMovement 就能入库）。</p>
 *
 * <p>持久化只存「可还原的标量 + 注册名字符串」：
 * 不写 ItemStack / Entity 的完整 NBT，避开 1.21 的 HolderLookup 依赖，
 * 跨版本与跨存档都安全。运行时的 {@link #mainHand} / {@link #offHand} / {@link #entityType}
 * 是真对象引用，从 NBT 读回来时按注册名还原。</p>
 */
public final class LearnedAbility {

    /** 能力来源分类 */
    public enum Kind {
        /** 近战：剑 / 斧 / 拳 */
        MELEE,
        /** 蓄力释放：弓 / 弩 / 三叉戟 */
        RANGED,
        /** 飞行物：箭矢 / 雪球 / 药水 / 妖归弹幕 */
        PROJECTILE,
        /** 效果类：喷溅 / 滞留药水 */
        POTION,
        /** 未分类 */
        UNKNOWN;

        public static Kind byName(String name) {
            for (Kind k : values()) {
                if (k.name().equals(name)) {
                    return k;
                }
            }
            return UNKNOWN;
        }
    }

    /** 一条效果记录（运行时持有 MobEffect 真对象，NBT 里退化成注册名） */
    public static final class EffectEntry {
        public final String id;
        public final MobEffect effect;
        public final int duration;
        public final int amplifier;

        public EffectEntry(String id, MobEffect effect, int duration, int amplifier) {
            this.id = id;
            this.effect = effect;
            this.duration = duration;
            this.amplifier = amplifier;
        }

        public static EffectEntry from(MobEffectInstance instance) {
            MobEffect eff = instance.getEffect().value();
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(eff);
            String id = key == null ? "minecraft:empty" : key.toString();
            return new EffectEntry(id, eff, instance.getDuration(), instance.getAmplifier());
        }

        /** 编码成一行字符串存进 NBT：id|duration|amplifier */
        public String encode() {
            return id + "|" + duration + "|" + amplifier;
        }

        public static EffectEntry decode(String raw) {
            String[] parts = raw.split("\\|");
            if (parts.length < 3) {
                return null;
            }
            ResourceLocation loc = ResourceLocation.tryParse(parts[0]);
            MobEffect eff = loc == null ? null : BuiltInRegistries.MOB_EFFECT.getValue(loc);
            if (eff == null) {
                return null;
            }
            try {
                int duration = Integer.parseInt(parts[1]);
                int amplifier = Integer.parseInt(parts[2]);
                return new EffectEntry(parts[0], eff, duration, amplifier);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    // ===================== 字段 =====================

    /** 去重键：同一招重复出现只刷新保质期，不占新格子 */
    public final String key;
    public final Kind kind;

    /** 播报用的招式名（她会明确告诉你「这招是从你这儿来的」） */
    public final String displayName;

    /** 飞行物类型（PROJECTILE / POTION 用） */
    public final EntityType<?> entityType;
    public final String entityTypeId;

    /**
     * 原实体的一份 NBT 快照 —— 「能抄任意模组弹幕」的关键。
     * 妖归这类带自定义字段的弹幕，光 create 类型是空壳，必须把原始 NBT load 回去。
     * 复现时由 {@link com.lancoren.alienmarisa.replay.AlienReplay} 清掉 Pos / UUID / Owner 再覆盖位置与速度。
     */
    public final CompoundTag entityNbt;

    /** 局部弹道：相对施法者朝向的 x(右) / y(上) / z(前) 速度 */
    public final Vec3 speedLocal;

    /** 伤害（箭矢为 baseDamage，近战为估算值） */
    public final double damage;

    /** 当时主手 / 副手道具（运行时副本，可能为 null） */
    public final ItemStack mainHand;
    public final ItemStack offHand;
    public final String weaponName;

    /** 蓄力 0~1 */
    public final float charge;
    /** 出手时的距离 */
    public final float distance;
    /** 相对角度（正对 = 0，度） */
    public final float yawOffset;
    /** 是否暴击 */
    public final boolean critical;

    /** 当时的增益效果 */
    public final List<EffectEntry> effects;

    /** 被复现过多少次 */
    public int useCount;
    /** 学会的时刻（tick） */
    public final long learnedTick;
    /** 过期时刻（tick），到期遗忘 */
    public long expireTick;

    private LearnedAbility(String key, Kind kind, String displayName,
                           EntityType<?> entityType, String entityTypeId, CompoundTag entityNbt,
                           Vec3 speedLocal,
                           double damage, ItemStack mainHand, ItemStack offHand, String weaponName,
                           float charge, float distance, float yawOffset, boolean critical,
                           List<EffectEntry> effects, long learnedTick, long expireTick) {
        this.key = key;
        this.kind = kind;
        this.displayName = displayName;
        this.entityType = entityType;
        this.entityTypeId = entityTypeId;
        this.entityNbt = entityNbt;
        this.speedLocal = speedLocal;
        this.damage = damage;
        this.mainHand = mainHand;
        this.offHand = offHand;
        this.weaponName = weaponName;
        this.charge = charge;
        this.distance = distance;
        this.yawOffset = yawOffset;
        this.critical = critical;
        this.effects = effects;
        this.learnedTick = learnedTick;
        this.expireTick = expireTick;
    }

    // ===================== 工厂 =====================

    /** 近战：按 蓄力档 × 距离档 × 暴击 × 武器 组合出「招」 */
    public static LearnedAbility melee(String weaponId, String weaponName, float charge,
                                       float distance, float yawOffset, boolean critical,
                                       double damage, ItemStack mainHand, ItemStack offHand,
                                       List<EffectEntry> effects, long now) {
        int chargeBucket = AlienMath.bucket(charge, 0.25D, 4);
        int distBucket = AlienMath.bucket(distance, 3.0D, 6);
        String key = "melee|" + weaponId + "|c" + chargeBucket + "|d" + distBucket + "|" + (critical ? "crit" : "norm");
        String name = critical ? "暴击·" + weaponName : weaponName;
        return new LearnedAbility(key, Kind.MELEE, name, null, null, null, Vec3.ZERO,
                damage, mainHand, offHand, weaponName, charge, distance, yawOffset, critical,
                effects, now, now + AlienConfig.ABILITY_TTL_TICKS);
    }

    /** 蓄力释放：弓 / 弩 / 三叉戟 */
    public static LearnedAbility ranged(String weaponId, String weaponName, float charge,
                                        float distance, double damage, ItemStack mainHand,
                                        ItemStack offHand, List<EffectEntry> effects, long now) {
        int chargeBucket = AlienMath.bucket(charge, 0.25D, 4);
        int distBucket = AlienMath.bucket(distance, 4.0D, 6);
        String key = "ranged|" + weaponId + "|c" + chargeBucket + "|d" + distBucket;
        return new LearnedAbility(key, Kind.RANGED, weaponName, null, null, null, Vec3.ZERO,
                damage, mainHand, offHand, weaponName, charge, distance, 0.0F, false,
                effects, now, now + AlienConfig.ABILITY_TTL_TICKS);
    }

    /**
     * 飞行物 / 弹幕：核心分支。
     * 直接抄 {@code getDeltaMovement()} 的弹道 —— 比 EpicFight 的判定框还直观，
     * 而且对任何模组的弹幕都通用。
     */
    public static LearnedAbility projectile(EntityType<?> type, String typeId, String displayName,
                                            Vec3 speedLocal, double damage, ItemStack mainHand,
                                            ItemStack offHand, float distance,
                                            List<EffectEntry> effects, boolean isPotion, long now) {
        return projectile(type, typeId, displayName, speedLocal, damage, mainHand, offHand,
                distance, effects, isPotion, null, now);
    }

    /**
     * 飞行物 / 弹幕：核心分支（带 NBT 快照）。
     * 直接抄 {@code getDeltaMovement()} 的弹道 —— 比 EpicFight 的判定框还直观；
     * 再附带一份实体 NBT 快照，让妖归这类自定义弹幕也能原样重建。
     */
    public static LearnedAbility projectile(EntityType<?> type, String typeId, String displayName,
                                            Vec3 speedLocal, double damage, ItemStack mainHand,
                                            ItemStack offHand, float distance,
                                            List<EffectEntry> effects, boolean isPotion,
                                            CompoundTag entityNbt, long now) {
        Vec3 v = speedLocal == null ? Vec3.ZERO : speedLocal;
        int speedBucket = AlienMath.bucket(v.length(), 0.35D, 10);
        int dmgBucket = AlienMath.bucket(damage, 2.0D, 8);
        String key = (isPotion ? "potion|" : "proj|") + typeId + "|s" + speedBucket + "|d" + dmgBucket;
        return new LearnedAbility(key, isPotion ? Kind.POTION : Kind.PROJECTILE, displayName,
                type, typeId, sanitizeNbt(entityNbt), v, damage, mainHand, offHand, "", 1.0F,
                distance, 0.0F, false,
                effects, now, now + AlienConfig.ABILITY_TTL_TICKS);
    }

    // ===================== 运行时 =====================

    public boolean expired(long now) {
        return now > expireTick;
    }

    /** 复现一次：计数 +1，刷新保质期（用得多的招记得更久） */
    public void markUsed(long now) {
        useCount++;
        expireTick = now + AlienConfig.ABILITY_TTL_TICKS;
    }

    /** 一行中文描述，用于 /alienmarisa list 与播报 */
    public String describe() {
        if (kind == Kind.MELEE) {
            String chargeText = charge >= 0.9F ? "满蓄" : (charge >= 0.5F ? "重击" : "轻击");
            return chargeText + "·" + displayName + "（" + String.format("%.1f", distance) + "格"
                    + (critical ? "·暴击" : "") + "）";
        }
        if (kind == Kind.RANGED) {
            return "远程·" + displayName + "（" + String.format("%.1f", distance) + "格）";
        }
        return displayName + "（弹速 " + String.format("%.2f", speedLocal.length()) + "）";
    }

    // ===================== 持久化 =====================

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        tag.putString("kind", kind.name());
        tag.putString("display", displayName);
        if (entityTypeId != null) {
            tag.putString("entity", entityTypeId);
        }
        if (entityNbt != null) {
            tag.put("entityNbt", entityNbt);
        }
        tag.putDouble("sx", speedLocal.x);
        tag.putDouble("sy", speedLocal.y);
        tag.putDouble("sz", speedLocal.z);
        tag.putDouble("damage", damage);
        tag.putString("weapon", weaponName == null ? "" : weaponName);
        tag.putFloat("charge", charge);
        tag.putFloat("distance", distance);
        tag.putFloat("yaw", yawOffset);
        tag.putBoolean("crit", critical);
        tag.putInt("useCount", useCount);
        tag.putLong("learnedTick", learnedTick);
        tag.putLong("expireTick", expireTick);

        ListTag effList = new ListTag();
        for (EffectEntry e : effects) {
            effList.add(StringTag.valueOf(e.encode()));
        }
        tag.put("effects", effList);
        return tag;
    }

    public static LearnedAbility fromNbt(CompoundTag tag) {
        String key = tag.getString("key");
        Kind kind = Kind.byName(tag.getString("kind"));
        String display = tag.getString("display");

        String entityId = tag.contains("entity") ? tag.getString("entity") : null;
        EntityType<?> type = null;
        if (entityId != null) {
            ResourceLocation loc = ResourceLocation.tryParse(entityId);
            if (loc != null) {
                type = BuiltInRegistries.ENTITY_TYPE.getValue(loc);
            }
        }

        CompoundTag entityNbt = tag.contains("entityNbt") ? tag.getCompound("entityNbt") : null;

        Vec3 speed = new Vec3(tag.getDouble("sx"), tag.getDouble("sy"), tag.getDouble("sz"));
        double damage = tag.getDouble("damage");

        List<EffectEntry> effects = new ArrayList<EffectEntry>();
        ListTag rawEffects = tag.getList("effects", Tag.TAG_STRING);
        for (int i = 0; i < rawEffects.size(); i++) {
            EffectEntry entry = EffectEntry.decode(rawEffects.getString(i));
            if (entry != null) {
                effects.add(entry);
            }
        }

        LearnedAbility ability = new LearnedAbility(key, kind, display, type, entityId, entityNbt, speed,
                damage, null, null, tag.getString("weapon"),
                tag.getFloat("charge"), tag.getFloat("distance"), tag.getFloat("yaw"),
                tag.getBoolean("crit"), effects, tag.getLong("learnedTick"), tag.getLong("expireTick"));
        ability.useCount = tag.getInt("useCount");
        return ability;
    }

    /** 空效果列表，避免到处 new */
    public static List<EffectEntry> noEffects() {
        return Collections.emptyList();
    }

    /**
     * 把运行时 ItemStack 记录进去（不参与 NBT 持久化，只服务当前会话的「具现同款道具」）。
     * 由于字段是 final，这里走一次浅重建。
     */
    public LearnedAbility withItems(ItemStack main, ItemStack off) {
        LearnedAbility copy = new LearnedAbility(key, kind, displayName, entityType, entityTypeId,
                entityNbt, speedLocal, damage, main, off, weaponName, charge, distance, yawOffset,
                critical, effects, learnedTick, expireTick);
        copy.useCount = useCount;
        copy.expireTick = expireTick;
        return copy;
    }

    /** 清掉会污染复现的字段：位置 / 运动 / 拥有者 / UUID / 维度 */
    private static CompoundTag sanitizeNbt(CompoundTag raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        CompoundTag tag = raw.copy();
        tag.remove("Pos");
        tag.remove("Motion");
        tag.remove("UUID");
        tag.remove("Owner");
        tag.remove("Dimension");
        tag.remove("Fire");
        tag.remove("Air");
        tag.remove("FallDistance");
        return tag;
    }
}
