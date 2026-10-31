package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.LoadoutManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class TcpLoadoutRequestC2SPacket {

    public TcpLoadoutRequestC2SPacket() {
    }

    public static void encode(TcpLoadoutRequestC2SPacket msg, FriendlyByteBuf buf) {

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
