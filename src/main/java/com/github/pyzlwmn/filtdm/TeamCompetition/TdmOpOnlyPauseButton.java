package com.github.pyzlwmn.filtdm.TeamCompetition;

import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.packet.mapselect.MapSelectionAccessS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TdmOpOnlyPauseButton {

    private static final int PUSH_INTERVAL_TICKS = 60;

    private TdmOpOnlyPauseButton() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            hideForNonOp(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            hideForNonOp(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % PUSH_INTERVAL_TICKS != 0) return;
        hideForNonOp(player);
    }

    private static void hideForNonOp(ServerPlayer player) {
        if (player.hasPermissions(2)) return;
        FPSMatch.sendToPlayer(player, new MapSelectionAccessS2CPacket(false));
    }
}
