package com.github.pyzlwmn.filtdm.TeamCompetition.client.spec;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpDeathCameraS2CPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.ptcrys.fpsmatch.common.camera.CameraEndReason;
import net.ptcrys.fpsmatch.common.client.camera.CameraDirector;
import net.ptcrys.fpsmatch.common.client.camera.CameraLifetime;
import net.ptcrys.fpsmatch.common.client.camera.CameraPolicy;
import net.ptcrys.fpsmatch.common.client.camera.CameraSession;

public final class TcpDeathCameraClient {

    private static CameraSession session;
    private static final DeathCameraTimeline TIMELINE = new DeathCameraTimeline();

    private TcpDeathCameraClient() {
    }

    public static void accept(TcpDeathCameraS2CPacket msg) {
        if (msg.action() == TcpDeathCameraS2CPacket.ACTION_START) {
            start(msg.x(), msg.y(), msg.z(), msg.yaw(), msg.pitch());
        } else {
            stop();
        }
    }

    private static void start(double x, double y, double z, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        stop();

        Vec3 origin = new Vec3(x, y, z);

        double yawRad = Math.toRadians(yaw);
        Vec3 pull = new Vec3(Math.sin(yawRad), 0, -Math.cos(yawRad));

        DeathCameraRig rig = new DeathCameraRig(origin, pull, 0.0, yaw, pitch);

        TIMELINE.start();

        session = CameraDirector.play(
                "filtdm_death_camera",
                CameraDirector.DEATH_PRIORITY,
                rig,
                CameraPolicy.DEATH,
                TIMELINE.clock(),
                TcpDeathCameraClient::stillValid,
                CameraLifetime.SCENE,
                reason -> session = null);

        if (session == null) {

            TIMELINE.reset();
        }
    }

    public static void stop() {
        if (session != null) {
            try {
                session.stop(CameraEndReason.CANCELLED);
            } catch (RuntimeException ignored) {
            }
            session = null;
        }
        TIMELINE.reset();
    }

    private static boolean stillValid() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;

        if (TIMELINE.finished()) return false;
        return true;
    }

    public static void tick() {
        if (session != null) {
            TIMELINE.tick();
        }
    }

    public static float fadeProgress(float partialTick) {
        return session != null ? TIMELINE.fadeProgress(partialTick) : 0.0F;
    }

    public static boolean active() {
        return session != null;
    }
}
