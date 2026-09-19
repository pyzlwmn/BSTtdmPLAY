package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 → 服务端：改装台（v26）
 *   action = start（请服务端把「这槽位的枪 + 它能用的配件」发过来）
 *          = save （把改装后的枪的 SNBT 发回来存档）
 *   slotName = rifle/pistol/…
 *   snbt     = 只有 save 用（改装后的枪）
 */
public class TcpRefitC2SPacket {

    private final String action;
    private final String slotName;
    private final String snbt;

    public TcpRefitC2SPacket(String action, String slotName) {
        this(action, slotName, "");
    }

    public TcpRefitC2SPacket(String action, String slotName, String snbt) {
        this.action = action == null ? "" : action;
        this.slotName = slotName == null ? "" : slotName;
        this.snbt = snbt == null ? "" : snbt;
    }

    public static void encode(TcpRefitC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action, 16);
        buf.writeUtf(msg.slotName, 32);
        buf.writeUtf(msg.snbt, 32767);
    }

    public static TcpRefitC2SPacket decode(FriendlyByteBuf buf) {
        return new TcpRefitC2SPacket(buf.readUtf(16), buf.readUtf(32), buf.readUtf(32767));
    }

    public void handle(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        TcpLoadoutServerHelper.refit(player, action, slotName, snbt);
    }
}
