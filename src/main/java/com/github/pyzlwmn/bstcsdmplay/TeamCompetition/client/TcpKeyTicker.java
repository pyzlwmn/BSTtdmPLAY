package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 键位轮询（FORGE 总线 / 客户端）。
 * 按下 B → 打开装备编辑界面。
 */
@Mod.EventBusSubscriber(modid = "bstcsdmplay", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TcpKeyTicker {

    private static final Logger LOGGER = LoggerFactory.getLogger("BST-TDM-Loadout");

    private TcpKeyTicker() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 改装页被关掉 → 通知服务端存档（v25）
        TcpRefitClient.tick();

        boolean pressed = false;
        while (TcpKeybinds.OPEN.consumeClick()) pressed = true;
        if (!pressed) return;

        if (Minecraft.getInstance().player == null) return;
        LOGGER.info("[TDM·背包] 检测到按键 → 打开装备编辑");
        TcpKeybinds.requestAndOpen();
    }
}
