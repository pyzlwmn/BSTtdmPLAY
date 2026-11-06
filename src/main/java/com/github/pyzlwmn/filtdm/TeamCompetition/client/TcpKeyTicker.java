package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TcpKeyTicker {

    private static final Logger LOGGER = LoggerFactory.getLogger("FIL-TDM-Loadout");

    private TcpKeyTicker() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        TcpRefitClient.tick();

        com.github.pyzlwmn.filtdm.TeamCompetition.client.spec.TcpDeathCameraClient.tick();

        boolean pressed = false;
        while (TcpKeybinds.OPEN.consumeClick()) pressed = true;
        if (!pressed) return;

        if (Minecraft.getInstance().player == null) return;
        LOGGER.info("[TDM·背包] 检测到按键 → 打开装备编辑");
        TcpKeybinds.requestAndOpen();
    }
}
