package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 键位注册（MOD 总线 / 客户端）。
 * 单独顶层类，避免「嵌套类不被扫描」这类玄学问题（2026-09-19）。
 */
@Mod.EventBusSubscriber(modid = "bstcsdmplay", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TcpKeyRegister {

    private static final Logger LOGGER = LoggerFactory.getLogger("BST-TDM-Loadout");

    private TcpKeyRegister() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TcpKeybinds.OPEN);
        LOGGER.info("[TDM·背包] 客户端键位注册完成：{} → 打开装备编辑（按键设置里可改）",
                TcpKeybinds.OPEN.getTranslatedKeyMessage().getString());
    }
}
