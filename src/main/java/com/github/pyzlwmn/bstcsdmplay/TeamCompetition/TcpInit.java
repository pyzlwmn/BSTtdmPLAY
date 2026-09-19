package com.github.pyzlwmn.bstcsdmplay.TeamCompetition;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpNetwork;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 公共初始化：注册网络通道（服务端/客户端都要跑）。
 * ⚠️ modid 改成你自己的
 */
@Mod.EventBusSubscriber(modid = "bstcsdmplay", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TcpInit {

    private TcpInit() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TcpNetwork::register);
    }
}
