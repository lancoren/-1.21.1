package com.lancoren.alienmarisa;

import com.lancoren.alienmarisa.command.AlienTestCommand;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 异形魔理沙（Alien Marisa）战斗机制原型 —— 主类。
 *
 * <p>本阶段（Step 1+2）只做两件事：</p>
 * <ul>
 *   <li>观测录制：玩家/生物的攻击一出现就被参数化入库（招式 + 道具）</li>
 *   <li>复现回敬：按学到的参数原样打回去，并播报形态名</li>
 * </ul>
 *
 * <p>刻意不注册实体 / 模型 / 渲染器：先把「看一眼就学会」这条最独特的机制跑通、确认手感，
 * 再给她配身体，避免返工。复现端只接收一个 {@code caster} 参数，
 * 等 Boss 实体做好后把 caster 换成她即可，逻辑一行不用改。</p>
 */
@Mod(AlienMarisaMod.MODID)
public class AlienMarisaMod {

    public static final String MODID = "alien_marisa";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 复制品标记。
     * <p>她自己复现出来的弹幕会被打上这个标记，观测端遇到带标记的实体直接跳过 ——
     * 否则她会学着学着自己递归（学自己的复制品 → 再复现 → 再学……）。</p>
     */
    public static final String TAG_REPLICA = MODID + ":replica";

    public AlienMarisaMod(IEventBus modBus) {
        modBus.addListener(AlienTestCommand::onRegisterCommands);
    }
}
