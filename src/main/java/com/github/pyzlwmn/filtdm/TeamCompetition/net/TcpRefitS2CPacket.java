package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import com.github.pyzlwmn.filtdm.TeamCompetition.client.TcpRefitClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public class TcpRefitS2CPacket {

    private final String slotName;
    private final String gunSnbt;
    private final List<String> attachments;

    public TcpRefitS2CPacket(String slotName, String gunSnbt, List<String> attachments) {
        this.slotName = slotName == null ? "" : slotName;
        this.gunSnbt = gunSnbt == null ? "" : gunSnbt;
        this.attachments = attachments == null ? List.of() : attachments;
    }

    public static void encode(TcpRefitS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.slotName, 32);
        buf.writeUtf(msg.gunSnbt, 32767);
        buf.writeVarInt(msg.attachments.size());
        for (String s : msg.attachments) buf.writeUtf(s, 128);
    }

    public static TcpRefitS2CPacket decode(FriendlyByteBuf buf) {
        String slotName = buf.readUtf(32);
        String snbt = buf.readUtf(32767);
        int n = buf.readVarInt();
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readUtf(128));
        return new TcpRefitS2CPacket(slotName, snbt, list);
    }

    public void handle(NetworkEvent.Context ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TcpRefitClient.beginVirtual(slotName, gunSnbt, attachments);
        }
    }
}
