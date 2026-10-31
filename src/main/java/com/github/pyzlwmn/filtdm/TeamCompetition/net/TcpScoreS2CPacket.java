package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import com.github.pyzlwmn.filtdm.TeamCompetition.client.TcpHudData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TcpScoreS2CPacket {

    public static final int FLAG_KILL_FEED = 1;
    public static final int FLAG_AVATARS = 2;

    public static final int FLAG_PLAYING = 4;

    public static final int FLAG_POST_MATCH = 8;

    public record Roster(UUID id, String name, boolean red) {
    }

    private final int red;
    private final int blue;
    private final int target;
    private final int seconds;
    private final int timeLimit;
    private final int countdown;
    private final int flags;
    private final List<Roster> roster;

    public TcpScoreS2CPacket(int red, int blue, int target, int seconds, int timeLimit,
                             int countdown, int flags, List<Roster> roster) {
        this.red = red;
        this.blue = blue;
        this.target = target;
        this.seconds = seconds;
        this.timeLimit = timeLimit;
        this.countdown = countdown;
        this.flags = flags;
        this.roster = roster == null ? List.of() : roster;
    }

    public static void encode(TcpScoreS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.red);
        buf.writeVarInt(msg.blue);
        buf.writeVarInt(msg.target);
        buf.writeVarInt(msg.seconds);
        buf.writeVarInt(msg.timeLimit);
        buf.writeVarInt(msg.countdown);
        buf.writeVarInt(msg.flags);
        buf.writeVarInt(msg.roster.size());
        for (Roster r : msg.roster) {
            buf.writeUUID(r.id());
            buf.writeUtf(r.name(), 16);
            buf.writeBoolean(r.red());
        }
    }

    public static TcpScoreS2CPacket decode(FriendlyByteBuf buf) {
        int red = buf.readVarInt();
        int blue = buf.readVarInt();
        int target = buf.readVarInt();
        int seconds = buf.readVarInt();
        int timeLimit = buf.readVarInt();
        int countdown = buf.readVarInt();
        int flags = buf.readVarInt();
        int n = buf.readVarInt();
        List<Roster> roster = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            roster.add(new Roster(buf.readUUID(), buf.readUtf(16), buf.readBoolean()));
        }
        return new TcpScoreS2CPacket(red, blue, target, seconds, timeLimit, countdown, flags, roster);
    }

    public void handle(NetworkEvent.Context ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TcpHudData.update(red, blue, target, seconds, timeLimit, countdown, flags, roster);
        }
    }
}
