package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class TcpLoadoutSelectC2SPacket {

    private final int slot;
    private final String gunId;

    public TcpLoadoutSelectC2SPacket(int slot, String gunId) {
        this.slot = slot;
        this.gunId = gunId == null ? "" : gunId;
    }

    public static void encode(TcpLoadoutSelectC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slot);
        buf.writeUtf(msg.gunId, 128);
    }

    public static TcpLoadoutSelectC2SPacket decode(FriendlyByteBuf buf) {
        return new TcpLoadoutSelectC2SPacket(buf.readVarInt(), buf.readUtf(128));
    }

    public void handle(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        TcpLoadoutServerHelper.select(player, slot, gunId);
    }
}
