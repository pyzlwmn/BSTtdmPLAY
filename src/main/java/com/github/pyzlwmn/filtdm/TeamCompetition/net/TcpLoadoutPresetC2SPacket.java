package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class TcpLoadoutPresetC2SPacket {

    private final String action;
    private final String id;
    private final String name;

    public TcpLoadoutPresetC2SPacket(String action, String id, String name) {
        this.action = action == null ? "" : action;
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
    }

    public static void encode(TcpLoadoutPresetC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action, 16);
        buf.writeUtf(msg.id, 32);
        buf.writeUtf(msg.name, 64);
    }

    public static TcpLoadoutPresetC2SPacket decode(FriendlyByteBuf buf) {
        return new TcpLoadoutPresetC2SPacket(buf.readUtf(16), buf.readUtf(32), buf.readUtf(64));
    }

    public void handle(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        TcpLoadoutServerHelper.preset(player, action, id, name);
    }
}
