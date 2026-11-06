package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class TcpDeathCameraS2CPacket {

    public static final int ACTION_STOP = 0;
    public static final int ACTION_START = 1;

    private final int action;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public TcpDeathCameraS2CPacket(int action, double x, double y, double z, float yaw, float pitch) {
        this.action = action;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static TcpDeathCameraS2CPacket start(double x, double y, double z, float yaw, float pitch) {
        return new TcpDeathCameraS2CPacket(ACTION_START, x, y, z, yaw, pitch);
    }

    public static TcpDeathCameraS2CPacket stop() {
        return new TcpDeathCameraS2CPacket(ACTION_STOP, 0, 0, 0, 0, 0);
    }

    public int action() {
        return action;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public static void encode(TcpDeathCameraS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        if (msg.action == ACTION_START) {
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
            buf.writeFloat(msg.yaw);
            buf.writeFloat(msg.pitch);
        }
    }

    public static TcpDeathCameraS2CPacket decode(FriendlyByteBuf buf) {
        int action = buf.readVarInt();
        if (action == ACTION_START) {
            return new TcpDeathCameraS2CPacket(action, buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat());
        }
        return new TcpDeathCameraS2CPacket(action, 0, 0, 0, 0, 0);
    }

    public static void handle(TcpDeathCameraS2CPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DeathCameraReceiver.receive(msg));
        context.setPacketHandled(true);
    }

    public static void receive(TcpDeathCameraS2CPacket msg) {
        DeathCameraReceiver.receive(msg);
    }

    private static final class DeathCameraReceiver {
        static void receive(TcpDeathCameraS2CPacket msg) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.github.pyzlwmn.filtdm.TeamCompetition.client.spec.TcpDeathCameraClient.accept(msg));
        }
    }
}
