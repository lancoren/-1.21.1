package com.lancoren.alienmarisa.core;

import com.lancoren.alienmarisa.AlienConfig;
import com.lancoren.alienmarisa.AlienMarisaMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;

/**
 * 实体 NBT 快照的读写桥接。
 *
 * <p>为什么用反射而不是直接调用：1.21.x 这条 API 在小版本间改过名与签名
 * （1.20.5~1.21.1 是 {@code serializeNBT(HolderLookup.Provider)}，1.21.2+ 换成 {@code saveWithoutId(CompoundTag)}），
 * 同一份源码要在两边都能编过，就在运行时探测方法。</p>
 *
 * <p>这是「能抄任意模组弹幕」能否成立的命门：
 * 妖归这类带自定义字段的弹幕，只用 {@code EntityType.create} 造出来是个空壳，
 * 必须把原始 NBT load 回去才有颜色、伤害、存活时间这些字段。</p>
 */
public final class AlienNbt {

    private static final Method CAPTURE_PROVIDER;
    private static final Method CAPTURE_PLAIN;

    static {
        Method capProvider = null;
        Method capPlain = null;

        for (Method m : Entity.class.getMethods()) {
            String name = m.getName();
            if (m.getParameterCount() == 1 && m.getReturnType() == CompoundTag.class) {
                if ("serializeNBT".equals(name)) {
                    capProvider = m;
                } else if ("saveWithoutId".equals(name) || "save".equals(name)) {
                    capPlain = m;
                }
            }
        }
        CAPTURE_PROVIDER = capProvider;
        CAPTURE_PLAIN = capPlain;
        AlienMarisaMod.LOGGER.info("[AlienMarisa] NBT 桥接: capture={}", captureKind());
    }

    private AlienNbt() {
    }

    private static String captureKind() {
        if (CAPTURE_PROVIDER != null) {
            return "serializeNBT(provider)";
        }
        if (CAPTURE_PLAIN != null) {
            return CAPTURE_PLAIN.getName() + "(tag)";
        }
        return "NONE";
    }

    /**
     * 抓一份实体 NBT 快照。
     *
     * @return null = 抓取失败（走「只抄类型 + 弹道」的降级路径）
     */
    public static CompoundTag capture(Entity entity) {
        if (entity == null) {
            return null;
        }
        try {
            CompoundTag tag;
            if (CAPTURE_PROVIDER != null) {
                Object provider = entity.level().registryAccess();
                tag = (CompoundTag) CAPTURE_PROVIDER.invoke(entity, provider);
            } else if (CAPTURE_PLAIN != null) {
                tag = new CompoundTag();
                CAPTURE_PLAIN.invoke(entity, tag);
                if (!tag.contains("id")) {
                    tag.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                            .getKey(entity.getType()).toString());
                }
            } else {
                return null;
            }
            if (tag == null || tag.isEmpty()) {
                return null;
            }
            // 防御：某些模组会把世界级数据塞进弹幕，体积过大直接放弃
            if (estimateSize(tag) > AlienConfig.MAX_NBT_BYTES) {
                if (AlienConfig.DEBUG) {
                    AlienMarisaMod.LOGGER.warn("[AlienMarisa] NBT 快照过大，放弃: {}", tag.getAllKeys());
                }
                return null;
            }
            return tag;
        } catch (Throwable t) {
            if (AlienConfig.DEBUG) {
                AlienMarisaMod.LOGGER.warn("[AlienMarisa] NBT 抓取失败: {}", t.toString());
            }
            return null;
        }
    }

    /** 粗估字节数，避免对超大 NBT 做序列化 */
    private static int estimateSize(CompoundTag tag) {
        int n = 0;
        for (String key : tag.getAllKeys()) {
            n += key.length() * 2 + 16;
            net.minecraft.nbt.Tag value = tag.get(key);
            if (value instanceof net.minecraft.nbt.StringTag) {
                n += ((net.minecraft.nbt.StringTag) value).getAsString().length() * 2;
            }
            if (value instanceof net.minecraft.nbt.ListTag) {
                n += ((net.minecraft.nbt.ListTag) value).size() * 32;
            }
            if (value instanceof net.minecraft.nbt.ByteArrayTag) {
                n += ((net.minecraft.nbt.ByteArrayTag) value).getAsByteArray().length;
            }
        }
        return n;
    }
}
