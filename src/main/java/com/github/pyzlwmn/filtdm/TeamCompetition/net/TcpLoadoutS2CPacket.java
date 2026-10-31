package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import com.github.pyzlwmn.filtdm.TeamCompetition.client.TcpKeybinds;
import com.github.pyzlwmn.filtdm.TeamCompetition.client.TcpLoadoutData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public class TcpLoadoutS2CPacket {

    public static final int FLAG_ENABLED = 1;
    public static final int FLAG_IN_MATCH = 2;
    public static final int FLAG_CAN_EDIT = 4;

    public static final int FLAG_OPEN = 8;

    public static final int FLAG_REFIT = 16;

    public static final int FLAG_CAN_SWITCH = 32;

    public static final int SLOT_ATTACHMENT = 5;

    public record SlotData(int slot, String label, String current, List<String> options) {
    }

    public record PresetData(String id, String name, boolean active) {
    }

    private final int flags;
    private final List<SlotData> slots;
    private final List<PresetData> presets;
    private final int maxPresets;

    public TcpLoadoutS2CPacket(int flags, List<SlotData> slots, List<PresetData> presets, int maxPresets) {
        this.flags = flags;
        this.slots = slots == null ? List.of() : slots;
        this.presets = presets == null ? List.of() : presets;
        this.maxPresets = maxPresets;
    }

    public static void encode(TcpLoadoutS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.flags);
        buf.writeVarInt(msg.slots.size());
        for (SlotData s : msg.slots) {
            buf.writeVarInt(s.slot());
            buf.writeUtf(s.label(), 64);
            buf.writeUtf(s.current() == null ? "" : s.current(), 128);
            buf.writeVarInt(s.options().size());
            for (String o : s.options()) buf.writeUtf(o, 128);
        }
        buf.writeVarInt(msg.presets.size());
        for (PresetData p : msg.presets) {
            buf.writeUtf(p.id(), 32);
            buf.writeUtf(p.name(), 32);
            buf.writeBoolean(p.active());
        }
        buf.writeVarInt(msg.maxPresets);
    }

    public static TcpLoadoutS2CPacket decode(FriendlyByteBuf buf) {
        int flags = buf.readVarInt();
        int n = buf.readVarInt();
        List<SlotData> slots = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int slot = buf.readVarInt();
            String label = buf.readUtf(64);
            String current = buf.readUtf(128);
            int count = buf.readVarInt();
            List<String> options = new ArrayList<>(count);
            for (int j = 0; j < count; j++) options.add(buf.readUtf(128));
            slots.add(new SlotData(slot, label, current.isEmpty() ? null : current, options));
        }
        int pn = buf.readVarInt();
        List<PresetData> presets = new ArrayList<>(pn);
        for (int i = 0; i < pn; i++) {
            presets.add(new PresetData(buf.readUtf(32), buf.readUtf(32), buf.readBoolean()));
        }
        int max = buf.readVarInt();
        return new TcpLoadoutS2CPacket(flags, slots, presets, max);
    }

    public void handle(NetworkEvent.Context ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TcpLoadoutData.update(flags, slots, presets, maxPresets);
            if ((flags & FLAG_OPEN) != 0) {
                TcpKeybinds.openEditor();
            }
        }
    }
}
