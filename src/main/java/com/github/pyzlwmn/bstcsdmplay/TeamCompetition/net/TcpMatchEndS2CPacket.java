package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client.TcpHudData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 → 客户端：对局结束面板（MVP / SVP）
 *
 * 收到后客户端显示 N 秒（holdSeconds），期间可以开枪但打不出伤害，
 * 到点服务端把所有人传送到准备点并清场。
 */
public class TcpMatchEndS2CPacket {

    public static final int TEAM_RED = 0;
    public static final int TEAM_BLUE = 1;
    public static final int TEAM_DRAW = 2;

    public record PlayerLine(String name, int team, int kills, int deaths, int assists, int damage) {
    }

    private final int winnerTeam;
    private final int redScore;
    private final int blueScore;
    private final PlayerLine mvp;
    private final PlayerLine svp;
    private final int holdSeconds;

    public TcpMatchEndS2CPacket(int winnerTeam, int redScore, int blueScore,
                                PlayerLine mvp, PlayerLine svp, int holdSeconds) {
        this.winnerTeam = winnerTeam;
        this.redScore = redScore;
        this.blueScore = blueScore;
        this.mvp = mvp;
        this.svp = svp;
        this.holdSeconds = holdSeconds;
    }

    public static void encode(TcpMatchEndS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.winnerTeam);
        buf.writeVarInt(msg.redScore);
        buf.writeVarInt(msg.blueScore);
        buf.writeVarInt(msg.holdSeconds);
        writeLine(buf, msg.mvp);
        writeLine(buf, msg.svp);
    }

    private static void writeLine(FriendlyByteBuf buf, PlayerLine line) {
        boolean has = line != null;
        buf.writeBoolean(has);
        if (!has) return;
        buf.writeUtf(line.name(), 24);
        buf.writeVarInt(line.team());
        buf.writeVarInt(line.kills());
        buf.writeVarInt(line.deaths());
        buf.writeVarInt(line.assists());
        buf.writeVarInt(line.damage());
    }

    private static PlayerLine readLine(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        return new PlayerLine(buf.readUtf(24), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static TcpMatchEndS2CPacket decode(FriendlyByteBuf buf) {
        int winnerTeam = buf.readVarInt();
        int redScore = buf.readVarInt();
        int blueScore = buf.readVarInt();
        int holdSeconds = buf.readVarInt();
        PlayerLine mvp = readLine(buf);
        PlayerLine svp = readLine(buf);
        return new TcpMatchEndS2CPacket(winnerTeam, redScore, blueScore, mvp, svp, holdSeconds);
    }

    public void handle(NetworkEvent.Context ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TcpHudData.setMatchEnd(winnerTeam, redScore, blueScore, mvp, svp, holdSeconds);
        }
    }
}
