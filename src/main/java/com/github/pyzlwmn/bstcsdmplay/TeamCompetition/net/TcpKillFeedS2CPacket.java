package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client.TcpHudData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 → 客户端：一条击杀提示
 *
 * 显示格式（由 TcpHud 绘制）：
 *   ▌击杀者 [枪包 slot 贴图] 被击杀者
 *   ▐ 竖块 = 击杀者队伍色
 *
 * 队伍代码 TEAM_RED / TEAM_BLUE / TEAM_NONE（生物等中立目标用 NONE）
 */
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
        // 空手/没有图标时也要能发，所以先写一个"有没有"标记
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
