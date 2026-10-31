package com.github.pyzlwmn.filtdm.TeamCompetition;

import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.IdDump;
import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.LoadoutManager;
import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.RefitSession;
import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpLoadoutServerHelper;
import net.ptcrys.fpsmatch.common.event.register.RegisterFPSMapEvent;
import net.ptcrys.fpsmatch.common.event.register.RegisterFPSMSaveDataEvent;
import net.ptcrys.fpsmatch.core.persistence.SaveHolder;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeamCompetitionRegister {

    private TeamCompetitionRegister() {
    }

    @SubscribeEvent
    public static void onRegisterGameType(RegisterFPSMapEvent event) {

        event.registerGameType(TeamCompetitionMap.TYPE, TeamCompetitionMap::new);
    }

    @SubscribeEvent
    public static void onRegisterSaveData(RegisterFPSMSaveDataEvent event) {
        event.registerData(
                TeamCompetitionMap.class,
                "TeamCompetitionMaps",
                new SaveHolder.Builder<>(TeamCompetitionMap.CODEC)
                        .withLoadHandler(TeamCompetitionMap::load)
                        .withSaveHandler(TeamCompetitionMap::save)
                        .build());
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            RefitSession.onLogout(sp);
        }
    }

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
