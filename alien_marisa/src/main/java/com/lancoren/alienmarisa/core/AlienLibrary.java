package com.lancoren.alienmarisa.core;

import com.lancoren.alienmarisa.AlienConfig;
import com.lancoren.alienmarisa.AlienMarisaMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力库 —— 她脑子里那 427 万种里的「已观测采样」。
 *
 * <p>当前是进程内静态库（原型阶段够用，也方便用指令测试）。
 * 等 Boss 实体做出来后，把 {@link #saveAll()} / {@link #loadAll(CompoundTag)}
 * 接到实体的持久化数据上即可，其余逻辑一行不用改。</p>
 *
 * <p>容量满时淘汰「最久没用过」的一条（LRU），
 * 保证她永远记得玩家最近在用的招 —— 这正是压迫感的来源。</p>
 */
public final class AlienLibrary {

    private static final Map<String, LearnedAbility> ABILITIES = new LinkedHashMap<String, LearnedAbility>();
    private static final RandomSource RANDOM = RandomSource.create();

    private AlienLibrary() {
    }

    /**
     * 录入一条能力。
     *
     * @return true = 这是新招（可以播报「我记下了，da☆ze」）；false = 库里已经有了，只刷新保质期
     */
    public static boolean learn(LearnedAbility ability) {
        if (ability == null) {
            return false;
        }
        LearnedAbility exist = ABILITIES.get(ability.key);
        if (exist != null) {
            exist.expireTick = Math.max(exist.expireTick, ability.expireTick);
            if (exist.mainHand == null && ability.mainHand != null) {
                // 之前没记下道具，这次补上
                ABILITIES.put(exist.key, ability);
            }
            return false;
        }
        if (ABILITIES.size() >= AlienConfig.MAX_ABILITIES) {
            evictOldest();
        }
        ABILITIES.put(ability.key, ability);
        if (AlienConfig.DEBUG) {
            AlienMarisaMod.LOGGER.info("[AlienMarisa] 学会新招: {} ({})", ability.key, ability.describe());
        }
        return true;
    }

    private static void evictOldest() {
        String oldestKey = null;
        long oldestExpire = Long.MAX_VALUE;
        for (Map.Entry<String, LearnedAbility> entry : ABILITIES.entrySet()) {
            if (entry.getValue().expireTick < oldestExpire) {
                oldestExpire = entry.getValue().expireTick;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            ABILITIES.remove(oldestKey);
        }
    }

    public static int size() {
        return ABILITIES.size();
    }

    public static boolean isEmpty() {
        return ABILITIES.isEmpty();
    }

    /** 按录入顺序返回快照 */
    public static List<LearnedAbility> all() {
        return new ArrayList<LearnedAbility>(ABILITIES.values());
    }

    /** 1-based 取第 n 条，越界返回 null */
    public static LearnedAbility get(int index) {
        List<LearnedAbility> list = all();
        if (index < 1 || index > list.size()) {
            return null;
        }
        return list.get(index - 1);
    }

    /** 最近学会的一条 */
    public static LearnedAbility last() {
        List<LearnedAbility> list = all();
        if (list.isEmpty()) {
            return null;
        }
        return list.get(list.size() - 1);
    }

    public static LearnedAbility random() {
        List<LearnedAbility> list = all();
        if (list.isEmpty()) {
            return null;
        }
        return list.get(RANDOM.nextInt(list.size()));
    }

    /** 每 tick 清理过期条目 */
    public static void tick(long nowTick) {
        if (ABILITIES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, LearnedAbility>> it = ABILITIES.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            if (it.next().getValue().expired(nowTick)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0 && AlienConfig.DEBUG) {
            AlienMarisaMod.LOGGER.info("[AlienMarisa] 遗忘了 {} 条过期招式，剩余 {}", removed, ABILITIES.size());
        }
    }

    public static void clear() {
        ABILITIES.clear();
    }

    // ===================== 持久化 =====================

    public static CompoundTag saveAll() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (LearnedAbility ability : ABILITIES.values()) {
            list.add(ability.toNbt());
        }
        root.put("abilities", list);
        root.putInt("size", ABILITIES.size());
        return root;
    }

    public static void loadAll(CompoundTag root) {
        ABILITIES.clear();
        if (root == null || !root.contains("abilities")) {
            return;
        }
        ListTag list = root.getList("abilities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            LearnedAbility ability = LearnedAbility.fromNbt(list.getCompound(i));
            ABILITIES.put(ability.key, ability);
        }
    }

    /** 只读视图，给指令与 AI 调度用 */
    public static List<LearnedAbility> unmodifiable() {
        return Collections.unmodifiableList(all());
    }
}
