package com.lancoren.alienmarisa.observe;

import com.lancoren.alienmarisa.AlienConfig;
import com.lancoren.alienmarisa.AlienMarisaMod;
import com.lancoren.alienmarisa.core.AlienLibrary;
import com.lancoren.alienmarisa.core.AlienMath;
import com.lancoren.alienmarisa.core.AlienNbt;
import com.lancoren.alienmarisa.core.LearnedAbility;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 观测端 —— 「看一眼就学会」。
 *
 * <p>三条捕获路径：</p>
 * <ul>
 *   <li>{@link AttackEntityEvent}：近战（蓄力档 / 距离 / 武器 / 暴击）</li>
 *   <li>{@link LivingEntityUseItemEvent.Stop}：弓弩 / 三叉戟等蓄力释放</li>
 *   <li>{@link EntityJoinLevelEvent}：任何飞行物与弹幕，直接抄 {@code getDeltaMovement()} 弹道</li>
 * </ul>
 *
 * <p>弹幕这条路最关键：妖归的弹幕只要能追溯到 owner 与速度向量，
 * 她就能原样回敬 —— 弹道参数比 EpicFight 的判定框更适合直接复制。</p>
 *
 * <p>防自噬：复现出来的实体都带 {@link AlienMarisaMod#TAG_REPLICA} 标记，这里遇到就跳过。</p>
 *
 * <p><b>注意类上的 {@code @EventBusSubscriber}：这是 NeoForge 的顶层注解，不是 {@code @Mod.EventBusSubscriber}。
 * 没有它，下面四个方法一次都不会被调用 —— 她永远学不到任何招。</b></p>
 */
@EventBusSubscriber(modid = AlienMarisaMod.MODID)
public final class AlienObservation {

    /** 服务端 tick 计数（她的「当前时间」） */
    private static long serverTick = 0L;

    private AlienObservation() {
    }

    public static long now() {
        return serverTick;
    }

    // ===================== 时间轴 =====================

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!AlienConfig.ENABLED) {
            return;
        }
        serverTick++;
        AlienLibrary.tick(serverTick);
    }

    // ===================== 近战 =====================

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!AlienConfig.ENABLED || !AlienConfig.OBSERVE_MELEE) {
            return;
        }
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity)) {
            return;
        }

        float charge = player.getAttackStrengthScale(0.5F);
        float distance = (float) player.distanceTo(target);

        // 目标相对玩家的局部坐标 → 相对角度
        Vec3 local = AlienMath.toLocal(target.position().subtract(player.position()), player.getYRot());
        float yawOffset = (float) Math.toDegrees(Math.atan2(local.x, local.z));

        // 暴击近似：不在地面 + 下落中 + 满蓄
        boolean critical = !player.onGround() && player.fallDistance > 0.0F && charge > 0.9F;

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        String weaponId = itemId(main);
        String weaponName = nameOf(main);

        double damage = estimateMeleeDamage(player, charge, critical);

        LearnedAbility ability = LearnedAbility.melee(weaponId, weaponName, charge, distance,
                yawOffset, critical, damage, copyOrNull(main), copyOrNull(off),
                collectEffects(player), serverTick);

        if (AlienLibrary.learn(ability)) {
            announce(player, "§5【异形魔理沙】「" + ability.describe() + "……我收下了，da☆ze」");
        }
    }

    // ===================== 蓄力释放（弓 / 弩 / 三叉戟） =====================

    @SubscribeEvent
    public static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        if (!AlienConfig.ENABLED || !AlienConfig.OBSERVE_RANGED) {
            return;
        }
        LivingEntity user = event.getEntity();
        if (user.level().isClientSide || !(user instanceof Player)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!(stack.getItem() instanceof ProjectileWeaponItem)) {
            return;
        }

        Player player = (Player) user;
        int used = event.getDuration();
        int total = stack.getUseDuration(player);
        float charge = total <= 0 ? 1.0F : (float) (total - used) / (float) total;
        charge = Math.max(0.0F, Math.min(1.0F, charge));

        // 蓄力释放这一刻还看不到弹道，距离记 0；真正的弹速会由 EntityJoinLevelEvent 那条更准的记录补全
        float distance = 0.0F;

        LearnedAbility ability = LearnedAbility.ranged(itemId(stack), nameOf(stack), charge, distance,
                0.0D, copyOrNull(player.getMainHandItem()), copyOrNull(player.getOffhandItem()),
                collectEffects(player), serverTick);

        if (AlienLibrary.learn(ability)) {
            announce(player, "§5【异形魔理沙】「远程的招式也不错呢——我记下了，da☆ze」");
        }
    }

    // ===================== 飞行物 / 弹幕 =====================

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!AlienConfig.ENABLED || !AlienConfig.OBSERVE_PROJECTILE) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        // 防自噬：她自己的复制品不学
        if (entity.getPersistentData().getBoolean(AlienMarisaMod.TAG_REPLICA)) {
            return;
        }
        if (!(entity instanceof Projectile)) {
            return;
        }

        Projectile projectile = (Projectile) entity;
        Entity owner = projectile.getOwner();
        if (owner == null) {
            return;
        }
        if (AlienConfig.OBSERVE_ONLY_PLAYER && !(owner instanceof Player)) {
            return;
        }
        if (!(owner instanceof LivingEntity)) {
            return;
        }

        LivingEntity caster = (LivingEntity) owner;
        Vec3 speed = entity.getDeltaMovement();
        if (speed.lengthSqr() < 1.0E-8D) {
            return; // 静止实体没有弹道可抄
        }

        // 世界速度 → 相对施法者朝向的局部弹道
        Vec3 localSpeed = AlienMath.toLocal(speed, caster.getYRot());

        EntityType<?> type = entity.getType();
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        String typeId = key == null ? "minecraft:unknown" : key.toString();

        double damage = 0.0D;
        if (entity instanceof AbstractArrow) {
            damage = ((AbstractArrow) entity).getBaseDamage();
        }

        boolean isPotion = typeId.contains("potion");
        String displayName = entity.getName().getString();

        ItemStack main = caster.getMainHandItem();
        ItemStack off = caster.getOffhandItem();

        // 抓一份实体 NBT 快照：妖归这类自定义弹幕光靠 create 是空壳，必须能 load 回原始字段
        net.minecraft.nbt.CompoundTag snapshot = AlienNbt.capture(entity);

        LearnedAbility ability = LearnedAbility.projectile(type, typeId, displayName, localSpeed,
                damage, copyOrNull(main), copyOrNull(off), (float) caster.distanceTo(entity),
                collectEffects(caster), isPotion, snapshot, serverTick);

        if (AlienLibrary.learn(ability)) {
            announce(caster, "§5【异形魔理沙】「这个弹道……很有意思da☆ze」");
        }
    }

    // ===================== 工具 =====================

    private static double estimateMeleeDamage(Player player, float charge, boolean critical) {
        // 必须用 getAttributeValue（含武器与附魔加成）；getAttributeBaseValue 恒为 1.0，
        // 会导致她回敬的近战永远只有 1 点伤害 —— 「用你的招打你」就没有杀伤说服力了。
        double base = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double scaled = base * (0.2D + charge * charge * 0.8D);
        return critical ? scaled * 1.5D : scaled;
    }

    /** 只录增益类效果：她的「适应」应该抄你的强，而不是抄你的虚弱 */
    private static List<LearnedAbility.EffectEntry> collectEffects(LivingEntity entity) {
        if (!AlienConfig.COPY_EFFECTS) {
            return LearnedAbility.noEffects();
        }
        List<LearnedAbility.EffectEntry> list = new ArrayList<LearnedAbility.EffectEntry>();
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
                list.add(LearnedAbility.EffectEntry.from(instance));
            }
        }
        return list;
    }

    private static ItemStack copyOrNull(ItemStack stack) {
        if (!AlienConfig.COPY_ITEMS || stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.copy();
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        ResourceLocation loc = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return loc == null ? "minecraft:air" : loc.toString();
    }

    private static String nameOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "空手";
        }
        return stack.getHoverName().getString();
    }

    /** 只对附近的玩家播报，避免全服刷屏 */
    private static void announce(LivingEntity near, String text) {
        Component message = Component.literal(text);
        if (near.level() instanceof ServerLevel) {
            ServerLevel level = (ServerLevel) near.level();
            for (ServerPlayer player : level.players()) {
                if (player.distanceTo(near) <= AlienConfig.BROADCAST_RADIUS) {
                    player.sendSystemMessage(message);
                }
            }
        }
        if (AlienConfig.DEBUG) {
            AlienMarisaMod.LOGGER.info("[AlienMarisa] {}", text);
        }
    }
}
