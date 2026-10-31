package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import com.github.pyzlwmn.filtdm.TeamCompetition.client.TcpHudData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

public class TcpKillFeedS2CPacket {

    public static final int TEAM_RED = 0;
    public static final int TEAM_BLUE = 1;
    public static final int TEAM_NONE = 2;

    private final String killer;
    private final int killerTeam;
    private final String victim;
    private final int victimTeam;
    private final ItemStack weapon;

    public TcpKillFeedS2CPacket(String killer, int killerTeam, String victim, int victimTeam, ItemStack weapon) {
        this.killer = killer;
        this.killerTeam = killerTeam;
        this.victim = victim;
        this.victimTeam = victimTeam;
        this.weapon = weapon == null ? ItemStack.EMPTY : weapon;
    }

    public static void encode(TcpKillFeedS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.killer, 32);
        buf.writeVarInt(msg.killerTeam);
        buf.writeUtf(msg.victim, 48);
        buf.writeVarInt(msg.victimTeam);

        buf.writeBoolean(!msg.weapon.isEmpty());
        if (!msg.weapon.isEmpty()) {
            buf.writeItem(msg.weapon);
        }
    }

    public static TcpKillFeedS2CPacket decode(FriendlyByteBuf buf) {
        String killer = buf.readUtf(32);
        int killerTeam = buf.readVarInt();
        String victim = buf.readUtf(48);
        int victimTeam = buf.readVarInt();
        ItemStack weapon = buf.readBoolean() ? buf.readItem() : ItemStack.EMPTY;
        return new TcpKillFeedS2CPacket(killer, killerTeam, victim, victimTeam, weapon);
    }

    public void handle(NetworkEvent.Context ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TcpHudData.addKill(killer, killerTeam, victim, victimTeam, weapon);
        }
    }
}
