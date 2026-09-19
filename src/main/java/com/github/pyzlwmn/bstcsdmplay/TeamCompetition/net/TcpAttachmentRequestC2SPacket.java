package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 → 服务端：请求配件编辑数据（并让客户端打开配件页）
 */
public class TcpAttachmentRequestC2SPacket {

    public TcpAttachmentRequestC2SPacket() {
    }

    public static void encode(TcpAttachmentRequestC2SPacket msg, FriendlyByteBuf buf) {
        // 无字段
    }

    public static TcpAttachmentRequestC2SPacket decode(FriendlyByteBuf buf) {
        return new TcpAttachmentRequestC2SPacket();
    }

    public void handle(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        TcpLoadoutServerHelper.sendAttachments(player, true);
    }
}
