package com.github.pyzlwmn.filtdm.TeamCompetition.client.spec;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpDeathCameraS2CPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.ptcrys.fpsmatch.common.camera.CameraEndReason;
import net.ptcrys.fpsmatch.common.client.camera.CameraDirector;
import net.ptcrys.fpsmatch.common.client.camera.CameraLifetime;
import net.ptcrys.fpsmatch.common.client.camera.CameraPolicy;
import net.ptcrys.fpsmatch.common.client.camera.CameraSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TcpDeathCameraClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("FIL-TDM-DeathCam");

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
            // ★ v69 诊断：CameraDirector.play 返回 null 时原来静默失败，动画完全看不到
            //   这里把原因打出来（FPSMatch 相机被占用 / 优先级被拦 / 客户端环境不满足）
            LOGGER.warn("[TDM·死亡相机] CameraDirector.play 返回 null → 死亡动画不生效 "
                            + "(origin={},{},{}, yaw={}, pitch={}, policy=DEATH, priority={})",
                    x, y, z, yaw, pitch, CameraDirector.DEATH_PRIORITY);
            TIMELINE.reset();
        } else {
            LOGGER.info("[TDM·死亡相机] 已启动：origin={},{},{}, yaw={}, pitch={}", x, y, z, yaw, pitch);
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
