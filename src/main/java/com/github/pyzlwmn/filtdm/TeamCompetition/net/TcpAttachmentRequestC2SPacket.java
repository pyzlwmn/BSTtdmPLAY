package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class TcpAttachmentRequestC2SPacket {

    public TcpAttachmentRequestC2SPacket() {
    }

    public static void encode(TcpAttachmentRequestC2SPacket msg, FriendlyByteBuf buf) {

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
