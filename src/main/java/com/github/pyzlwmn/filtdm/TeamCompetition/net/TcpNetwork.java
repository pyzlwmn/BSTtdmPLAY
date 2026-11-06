package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class TcpNetwork {

    private static final String PROTOCOL = "1";
    private static final String MODID = "filtdm";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryBuild(MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private TcpNetwork() {
    }

    public static void register() {
        int id = 0;

        CHANNEL.registerMessage(id++, TcpScoreS2CPacket.class,
                TcpScoreS2CPacket::encode,
                TcpScoreS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, TcpKillFeedS2CPacket.class,
                TcpKillFeedS2CPacket::encode,
                TcpKillFeedS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, TcpMatchEndS2CPacket.class,
                TcpMatchEndS2CPacket::encode,
                TcpMatchEndS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, TcpLoadoutS2CPacket.class,
                TcpLoadoutS2CPacket::encode,
                TcpLoadoutS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, TcpLoadoutRequestC2SPacket.class,
                TcpLoadoutRequestC2SPacket::encode,
                TcpLoadoutRequestC2SPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, TcpLoadoutSelectC2SPacket.class,
                TcpLoadoutSelectC2SPacket::encode,
                TcpLoadoutSelectC2SPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, TcpAttachmentS2CPacket.class,
                TcpAttachmentS2CPacket::encode,
                TcpAttachmentS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, TcpAttachmentRequestC2SPacket.class,
                TcpAttachmentRequestC2SPacket::encode,
                TcpAttachmentRequestC2SPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, TcpAttachmentSelectC2SPacket.class,
                TcpAttachmentSelectC2SPacket::encode,
                TcpAttachmentSelectC2SPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, TcpLoadoutPresetC2SPacket.class,
                TcpLoadoutPresetC2SPacket::encode,
                TcpLoadoutPresetC2SPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, TcpRefitC2SPacket.class,
                TcpRefitC2SPacket::encode,
                TcpRefitC2SPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, TcpRefitS2CPacket.class,
                TcpRefitS2CPacket::encode,
                TcpRefitS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> msg.handle(ctx.get()));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, TcpDeathCameraS2CPacket.class,
                TcpDeathCameraS2CPacket::encode,
                TcpDeathCameraS2CPacket::decode,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> TcpDeathCameraS2CPacket.receive(msg));
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
