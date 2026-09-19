package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端 → 服务端：配件编辑页里选了某个配件槽的配件
 *   slotName = rifle/pistol/...（枪位）
 *   type     = SCOPE/MUZZLE/STOCK/GRIP/LASER/EXTENDED_MAG
 *   id       = 配件 id（空字符串 = 卸下）
 */
public class TcpAttachmentSelectC2SPacket {

    private final String slotName;
    private final String type;
    private final String id;

    public TcpAttachmentSelectC2SPacket(String slotName, String type, String id) {
        this.slotName = slotName == null ? "" : slotName;
        this.type = type == null ? "" : type;
        this.id = id == null ? "" : id;
    }

    public static void encode(TcpAttachmentSelectC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.slotName, 32);
        buf.writeUtf(msg.type, 32);
        buf.writeUtf(msg.id, 128);
    }

    public static TcpAttachmentSelectC2SPacket decode(FriendlyByteBuf buf) {
        return new TcpAttachmentSelectC2SPacket(buf.readUtf(32), buf.readUtf(32), buf.readUtf(128));
    }

    public void handle(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        TcpLoadoutServerHelper.selectAttachment(player, slotName, type, id);
    }
}
