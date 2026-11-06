package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import com.github.pyzlwmn.filtdm.TeamCompetition.client.TcpAttachmentData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public class TcpAttachmentS2CPacket {

    public static final int FLAG_OPEN = 1;
    public static final int FLAG_CAN_EDIT = 2;

    public record TypeEntry(String type, String current, List<String> options) {
    }

    public record GunEntry(String slotName, String label, String gunId, List<TypeEntry> types) {
    }

    private final int flags;
    private final List<GunEntry> guns;

    public TcpAttachmentS2CPacket(int flags, List<GunEntry> guns) {
        this.flags = flags;
        this.guns = guns == null ? List.of() : guns;
    }

    public static void encode(TcpAttachmentS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.flags);
        buf.writeVarInt(msg.guns.size());
        for (GunEntry g : msg.guns) {
            buf.writeUtf(g.slotName(), 32);
            buf.writeUtf(g.label(), 64);
            buf.writeUtf(g.gunId() == null ? "" : g.gunId(), 128);
            buf.writeVarInt(g.types().size());
            for (TypeEntry t : g.types()) {
                buf.writeUtf(t.type(), 32);
                buf.writeUtf(t.current() == null ? "" : t.current(), 128);
                buf.writeVarInt(t.options().size());
                for (String o : t.options()) buf.writeUtf(o, 128);
            }
        }
    }

    public static TcpAttachmentS2CPacket decode(FriendlyByteBuf buf) {
        int flags = buf.readVarInt();
        int gn = buf.readVarInt();
        List<GunEntry> guns = new ArrayList<>(gn);
        for (int i = 0; i < gn; i++) {
            String slotName = buf.readUtf(32);
            String label = buf.readUtf(64);
            String gunId = buf.readUtf(128);
            int tn = buf.readVarInt();
            List<TypeEntry> types = new ArrayList<>(tn);
            for (int j = 0; j < tn; j++) {
                String type = buf.readUtf(32);
                String current = buf.readUtf(128);
                int on = buf.readVarInt();
                List<String> options = new ArrayList<>(on);
                for (int k = 0; k < on; k++) options.add(buf.readUtf(128));
                types.add(new TypeEntry(type, current.isEmpty() ? null : current, options));
            }
            guns.add(new GunEntry(slotName, label, gunId.isEmpty() ? null : gunId, types));
        }
        return new TcpAttachmentS2CPacket(flags, guns);
    }

    public void handle(NetworkEvent.Context ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TcpAttachmentData.update(flags, guns);
            if ((flags & FLAG_OPEN) != 0) {
                TcpAttachmentData.openEditor();
            }
        }
    }
}
