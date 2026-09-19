package com.github.pyzlwmn.bstcsdmplay.TeamCompetition;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.IdDump;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.LoadoutManager;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.RefitSession;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpLoadoutServerHelper;
import net.ptcrys.fpsmatch.common.event.register.RegisterFPSMapEvent;
import net.ptcrys.fpsmatch.common.event.register.RegisterFPSMSaveDataEvent;
import net.ptcrys.fpsmatch.core.persistence.SaveHolder;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 注册游戏类型 tdm + 地图存档（v6 新增存档）。
 *
 * - RegisterFPSMapEvent：把玩法登记进去，之后 /fpsm map create 才能选到 tcp
 * - RegisterFPSMSaveDataEvent：登记地图数据（队伍/能力/出生点），FPSMatch 保存时自动落盘
 *   地图设置（Setting）走 BaseMap.saveConfig()，也在 save() 里一起存
 *
 * ⚠️ modid 改成你自己的（下面按 bstcsdmplay 写的）
 */
@Mod.EventBusSubscriber(modid = "bstcsdmplay", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeamCompetitionRegister {

    private TeamCompetitionRegister() {
    }

    @SubscribeEvent
    public static void onRegisterGameType(RegisterFPSMapEvent event) {
        // 工厂签名：(ServerLevel, mapName, areaData) -> BaseMap
        event.registerGameType(TeamCompetitionMap.TYPE, TeamCompetitionMap::new);
    }

    @SubscribeEvent
    public static void onRegisterSaveData(RegisterFPSMSaveDataEvent event) {
        event.registerData(
                TeamCompetitionMap.class,
                "TeamCompetitionMaps",                     // 存档文件夹名（fpsmatch/TeamCompetitionMaps/）
                new SaveHolder.Builder<>(TeamCompetitionMap.CODEC)
                        .withLoadHandler(TeamCompetitionMap::load)    // 继承自 BaseMap 的实例方法
                        .withSaveHandler(TeamCompetitionMap::save)    // 本类静态 save
                        .build());
    }

    /**
     * v25：玩家离线时如果还卡在改装台（背包被换仓），把原背包还回去，避免丢东西。
     */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            RefitSession.onLogout(sp);
        }
    }

    /**
     * 背包系统聊天指令（v17.1 新增，按键失灵时的兜底入口）：
     *   /tdmloadout          打开装备编辑界面
     *   /tdmloadout debug    打印「你的皮肤文件 / 拥有什么 / 各槽位可选项」
     *   /tdmloadout reload   重读 loadout.json 与 player_skin 目录
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tdmloadout")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    TcpLoadoutServerHelper.open(player);
                    return 1;
                })
                .then(Commands.literal("debug").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    for (String line : LoadoutManager.debug(player).split("\n")) {
                        player.sendSystemMessage(Component.literal(line));
                    }
                    return 1;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    LoadoutManager.reloadAll();
                    ctx.getSource().sendSuccess(() -> Component.literal("§a装备配置与皮肤目录已重读"), false);
                    return 1;
                }))
                .then(Commands.literal("attdebug").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    for (String line : LoadoutManager.attachmentDebug(player, 0).split("\n")) {
                        player.sendSystemMessage(Component.literal(line));
                    }
                    return 1;
                }))
                .then(Commands.literal("ids").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    for (String line : IdDump.dump()) {
                        player.sendSystemMessage(Component.literal(line));
                    }
                    return 1;
                })));
    }
}
