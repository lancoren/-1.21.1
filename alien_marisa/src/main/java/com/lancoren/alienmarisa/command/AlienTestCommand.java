package com.lancoren.alienmarisa.command;

import com.lancoren.alienmarisa.AlienConfig;
import com.lancoren.alienmarisa.core.AlienFormNamer;
import com.lancoren.alienmarisa.core.AlienLibrary;
import com.lancoren.alienmarisa.core.LearnedAbility;
import com.lancoren.alienmarisa.replay.AlienReplay;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * 测试指令：{@code /alienmarisa ...}
 *
 * <p>原型阶段用它验证「你用什么、她回敬什么」，
 * 等 Boss 实体做出来后这套指令可以保留当调试后门。</p>
 */
public final class AlienTestCommand {

    private AlienTestCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("alienmarisa")
                .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())));
        root.then(Commands.literal("size").executes(ctx -> size(ctx.getSource())));

        root.then(Commands.literal("replay")
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .executes(ctx -> replay(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index")))));

        root.then(Commands.literal("last").executes(ctx -> replayLast(ctx.getSource())));
        root.then(Commands.literal("random").executes(ctx -> replayRandom(ctx.getSource())));
        root.then(Commands.literal("canhit").executes(ctx -> replayCanHit(ctx.getSource())));
        root.then(Commands.literal("burst")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> burst(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))));

        root.then(Commands.literal("item")
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .executes(ctx -> giveItem(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index")))));

        root.then(Commands.literal("form").executes(ctx -> form(ctx.getSource())));
        root.then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())));
        root.then(Commands.literal("config").executes(ctx -> config(ctx.getSource())));

        event.getDispatcher().register(root);
    }

    // ===================== 子命令 =====================

    private static int list(CommandSourceStack source) {
        List<LearnedAbility> all = AlienLibrary.all();
        if (all.isEmpty()) {
            source.sendSystemMessage(Component.literal("§7她还没看过任何招式。去打几下怪、射几箭、丢几个雪球。"));
            return 0;
        }
        source.sendSystemMessage(Component.literal("§5【异形魔理沙】已学会 §d" + all.size()
                + " §5招（上限 " + AlienConfig.MAX_ABILITIES + "）："));
        int limit = Math.min(all.size(), 30);
        for (int i = 0; i < limit; i++) {
            LearnedAbility ability = all.get(i);
            source.sendSystemMessage(Component.literal("§7 #" + (i + 1) + " §f"
                    + ability.describe() + " §8x" + ability.useCount + " §7[" + ability.kind.name() + "]"));
        }
        if (all.size() > limit) {
            source.sendSystemMessage(Component.literal("§7 ……另有 " + (all.size() - limit) + " 条未显示"));
        }
        return 1;
    }

    private static int size(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("§5已学会 §d" + AlienLibrary.size() + " §5招"));
        return 1;
    }

    private static int replay(CommandSourceStack source, int index) {
        LearnedAbility ability = AlienLibrary.get(index);
        if (ability == null) {
            source.sendSystemMessage(Component.literal("§c没有第 " + index + " 条记录。用 /alienmarisa list 看看。"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        AlienReplay.replay(player, ability, player);
        source.sendSystemMessage(Component.literal("§5复现：§f" + ability.describe()));
        return 1;
    }

    private static int replayLast(CommandSourceStack source) {
        LearnedAbility ability = AlienLibrary.last();
        if (ability == null) {
            source.sendSystemMessage(Component.literal("§c还没有记录。"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        AlienReplay.replay(player, ability, player);
        return 1;
    }

    private static int replayRandom(CommandSourceStack source) {
        ServerPlayer player = source.getPlayerOrException();
        if (!AlienReplay.replayRandom(player, player)) {
            source.sendSystemMessage(Component.literal("§c没有可复现的招式。"));
            return 0;
        }
        return 1;
    }

    private static int replayCanHit(CommandSourceStack source) {
        ServerPlayer player = source.getPlayerOrException();
        if (!AlienReplay.replayCanHit(player, player)) {
            source.sendSystemMessage(Component.literal("§7此刻没有一招能打中你 —— 换个站位试试。"));
            return 0;
        }
        return 1;
    }

    private static int burst(CommandSourceStack source, int count) {
        ServerPlayer player = source.getPlayerOrException();
        int done = AlienReplay.replayBurst(player, player, count);
        source.sendSystemMessage(Component.literal("§5弹幕海：放出 §d" + done + " §5发"));
        return done;
    }

    /** 她把你当时用的道具具现出来 —— 原作里她连冈格尼尔都能掏出来 */
    private static int giveItem(CommandSourceStack source, int index) {
        LearnedAbility ability = AlienLibrary.get(index);
        if (ability == null) {
            source.sendSystemMessage(Component.literal("§c没有第 " + index + " 条记录。"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ItemStack main = ability.mainHand;
        if (main == null || main.isEmpty()) {
            source.sendSystemMessage(Component.literal("§7这条记录没记下道具（可能是空手，或已跨存档丢失运行时引用）。"));
            return 0;
        }
        player.getInventory().add(main.copy());
        source.sendSystemMessage(Component.literal("§5【异形魔理沙】「你的道具，我也拿来用用吧da☆ze」-> "
                + main.getHoverName().getString()));
        return 1;
    }

    private static int form(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("§5【异形魔理沙】「用"
                + AlienFormNamer.nextFormTitle() + "——」"));
        source.sendSystemMessage(Component.literal("§8（理论组合数 " + AlienFormNamer.format(AlienFormNamer.combinatorics())
                + "，对外宣称 " + AlienFormNamer.format(AlienFormNamer.CLAIMED_TOTAL) + " 种形态）"));
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        AlienLibrary.clear();
        source.sendSystemMessage(Component.literal("§5她把学过的全忘了。"));
        return 1;
    }

    private static int config(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("§5--- 异形魔理沙 配置 ---"));
        source.sendSystemMessage(Component.literal("§7 ENABLED=" + AlienConfig.ENABLED
                + " / OBSERVE_MELEE=" + AlienConfig.OBSERVE_MELEE
                + " / OBSERVE_RANGED=" + AlienConfig.OBSERVE_RANGED
                + " / OBSERVE_PROJECTILE=" + AlienConfig.OBSERVE_PROJECTILE));
        source.sendSystemMessage(Component.literal("§7 COPY_ITEMS=" + AlienConfig.COPY_ITEMS
                + " / COPY_EFFECTS=" + AlienConfig.COPY_EFFECTS
                + " / ONLY_PLAYER=" + AlienConfig.OBSERVE_ONLY_PLAYER
                + " / RADIUS=" + AlienConfig.OBSERVE_RADIUS));
        source.sendSystemMessage(Component.literal("§7 MAX_ABILITIES=" + AlienConfig.MAX_ABILITIES
                + " / TTL=" + AlienConfig.ABILITY_TTL_TICKS + "tick"
                + " / REPLAY_DAMAGE=" + AlienConfig.REPLAY_DAMAGE
                + " / SPEED_CAP=" + AlienConfig.PROJECTILE_SPEED_CAP));
        return 1;
    }
}
