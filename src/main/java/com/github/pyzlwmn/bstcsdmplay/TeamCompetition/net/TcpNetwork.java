package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * 自己的网络通道（Forge 1.20.1 SimpleChannel 模板）
 *
 * 用法：
 *   1. 在 common setup 里调一次 TcpNetwork.register()
 *   2. 发包：TcpNetwork.sendToPlayer(player, msg) / sendToAll(msg)
 */
public final class TcpNetwork {

    private static final String PROTOCOL = "1";
    private static final String MODID = "bstcsdmplay";   // ⚠️ 改成你的 modid

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryBuild(MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private TcpNetwork() {
    }

    public static void register() {
        int id = 0;
        // 服务端 → 客户端：比分同步
        CHANNEL.registerMessage(id++, TcpScoreS2CPacket.class,
                TcpScoreS2CPacket::encode,
                TcpScoreS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // 服务端 → 客户端：单条击杀提示（击杀者 / 枪包图标 / 被击杀者）
        CHANNEL.registerMessage(id++, TcpKillFeedS2CPacket.class,
                TcpKillFeedS2CPacket::encode,
                TcpKillFeedS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // 服务端 → 客户端：对局结束面板（MVP / SVP）
        CHANNEL.registerMessage(id++, TcpMatchEndS2CPacket.class,
                TcpMatchEndS2CPacket::encode,
                TcpMatchEndS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // 服务端 → 客户端：背包/装备编辑数据（槽位 / 可选项 / 当前选择）
        CHANNEL.registerMessage(id++, TcpLoadoutS2CPacket.class,
                TcpLoadoutS2CPacket::encode,
                TcpLoadoutS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // 客户端 → 服务端：请求一份背包数据
        CHANNEL.registerMessage(id++, TcpLoadoutRequestC2SPacket.class,
                TcpLoadoutRequestC2SPacket::encode,
                TcpLoadoutRequestC2SPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // 客户端 → 服务端：选择某个槽位的装备
        CHANNEL.registerMessage(id++, TcpLoadoutSelectC2SPacket.class,
                TcpLoadoutSelectC2SPacket::encode,
                TcpLoadoutSelectC2SPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // 服务端 → 客户端：配件编辑页数据（v22）
        CHANNEL.registerMessage(id++, TcpAttachmentS2CPacket.class,
                TcpAttachmentS2CPacket::encode,
                TcpAttachmentS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // 客户端 → 服务端：请求配件编辑页（并打开）
        CHANNEL.registerMessage(id++, TcpAttachmentRequestC2SPacket.class,
                TcpAttachmentRequestC2SPacket::encode,
                TcpAttachmentRequestC2SPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // 客户端 → 服务端：选/卸配件
        CHANNEL.registerMessage(id++, TcpAttachmentSelectC2SPacket.class,
                TcpAttachmentSelectC2SPacket::encode,
                TcpAttachmentSelectC2SPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // 客户端 → 服务端：背包（预设）管理：create/rename/delete/switch
        CHANNEL.registerMessage(id++, TcpLoadoutPresetC2SPacket.class,
                TcpLoadoutPresetC2SPacket::encode,
                TcpLoadoutPresetC2SPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // 客户端 → 服务端：改装台（v26）start/save
        CHANNEL.registerMessage(id++, TcpRefitC2SPacket.class,
                TcpRefitC2SPacket::encode,
                TcpRefitC2SPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // 服务端 → 客户端：开始改装（枪 + 可用配件，客户端虚空打开 TaCZ 改装页）
        CHANNEL.registerMessage(id++, TcpRefitS2CPacket.class,
                TcpRefitS2CPacket::encode,
                TcpRefitS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToAll(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }
}
