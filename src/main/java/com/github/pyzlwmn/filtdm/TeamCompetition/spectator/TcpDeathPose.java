package com.github.pyzlwmn.filtdm.TeamCompetition.spectator;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TcpDeathPose {

    public record Pose(double x, double y, double z, float yaw, float pitch) {
    }

    private static final Map<UUID, Pose> POSES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> STAMPS = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LAST_HURT_EYE = new ConcurrentHashMap<>();

    private static final long TTL_NS = 10_000_000_000L;

    private TcpDeathPose() {
    }

    public static void record(ServerPlayer player) {
        if (player == null) return;
        Vec3 eye = player.getEyePosition(1.0F);
        POSES.put(player.getUUID(), new Pose(eye.x, eye.y, eye.z, player.getYRot(), player.getXRot()));
        STAMPS.put(player.getUUID(), System.nanoTime());
    }

    public static Optional<Pose> get(UUID id) {
        if (id == null) return Optional.empty();
        Long stamp = STAMPS.get(id);
        if (stamp == null) return Optional.empty();
        if (System.nanoTime() - stamp > TTL_NS) {
            clear(id);
            return Optional.empty();
        }
        return Optional.ofNullable(POSES.get(id));
    }

    public static void clear(UUID id) {
        if (id == null) return;
        POSES.remove(id);
        STAMPS.remove(id);
        LAST_HURT_EYE.remove(id);
    }

    public static void clearAll() {
        POSES.clear();
        STAMPS.clear();
        LAST_HURT_EYE.clear();
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (victim.level().isClientSide) return;
        LAST_HURT_EYE.put(victim.getUUID(), victim.getEyePosition(1.0F));
    }

    public static Optional<Vec3> lastHurtEye(UUID id) {
        return Optional.ofNullable(LAST_HURT_EYE.get(id));
    }
}
