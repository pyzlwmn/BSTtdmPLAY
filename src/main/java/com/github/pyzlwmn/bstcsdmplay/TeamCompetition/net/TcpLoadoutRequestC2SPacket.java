package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.LoadoutManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 → 服务端：请求一份背包数据（按 B 打开 UI 时发）
 */
public class TcpLoadoutRequestC2SPacket {

    public TcpLoadoutRequestC2SPacket() {
    }

    public static void encode(TcpLoadoutRequestC2SPacket msg, FriendlyByteBuf buf) {
        // 无字段
    }

    public static TcpLoadoutRequestC2SPacket decode(FriendlyByteBuf buf) {
        return new TcpLoadoutRequestC2SPacket();
    }

    public void handle(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        TcpLoadoutServerHelper.send(player);
    }
}
