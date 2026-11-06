package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.TeamCompetition.TeamCompetitionMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ★ v70：客户端存活状态纠正器。
 *
 * <p>问题（主人 11-05 报）：pair / solo 的 Tab 面板一死就永远显示「已阵亡」+ 灰头像，不会再改回来；
 * 严重时那一格直接变成「空位」。
 *
 * <p>根因（FPSMatch 上游）：
 * <ol>
 *   <li>{@code BaseMap.handleDeath} 里 {@code data.setLiving(false)} → {@code markDirty()}</li>
 *   <li>{@code MapTeams.sync(...)} 每个 mapTick 把 dirty 数据推给客户端，客户端
 *       {@code PlayerData.setLiving(false)} 同样 {@code markDirty()} —— 这次是<b>客户端侧的 dirty</b></li>
 *   <li>客户端 dirty 没人清（只有服务端发新包才覆盖），所以本地一直是 false 也不会再被清</li>
 *   <li>复活时服务端确实把 isLiving 置回 true，但 {@code PlayerData.isOnline()} 是
 *       {@code FPSMCore.getPlayerByUUID} —— 纯服务端方法，客户端一调就抛
 *       {@code RuntimeException("isOnline() only available on server side")}，所以服务端永远发不出 true，
 *       只有 false（玩家掉线时才发）</li>
 * </ol>
 * → 客户端拿不到「活过来了」的信号。
 *
 * <p>本类的做法：只在 Tab 面板打开时，用客户端本地事实（玩家实体在、没死、不是旁观者）覆盖显示状态。
 * 不碰 FPSMatch 内部数据，不改上游 jar，纯显示层兜底。
 */
@OnlyIn(Dist.CLIENT)
public final class TcpLivingFixer {

    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    private TcpLivingFixer() {
    }

    /** 判定结果三态：TRUE 活 / FALSE 死 / null 不知道（实体还没加载，交回上游数据） */
    public static Boolean isAliveLocal(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var player = mc.level.getPlayerByUUID(id);
        if (player == null) {
            // 跨维度拿不到实体时退回流控里还在列表中的 PlayerInfo
            return null;
        }
        // getPlayerByUUID 返回 Player，本身就是 LivingEntity；
        // 再写 instanceof LivingEntity 模式会因「模式类型是表达式类型的子类型」编译报错
        if (player.isDeadOrDying()) return false;
        if (player.getHealth() <= 0.0f) return false;
        if (player.isSpectator()) return false;
        return true;
    }

    /** 带 5 tick 缓存的版本，避免一帧内反复查实体 */
    public static Boolean isAliveCached(UUID id) {
        long now = tickNow();
        Cached c = CACHE.get(id);
        if (c != null && now - c.tick < 5) return c.alive;
        Boolean v = isAliveLocal(id);
        if (v != null) CACHE.put(id, new Cached(v, now));
        return v;
    }

    /** 玩家退出 / 换局时清缓存 */
    public static void clear() {
        CACHE.clear();
    }

    public static void clear(UUID id) {
        CACHE.remove(id);
    }

    private static long tickNow() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }

    private record Cached(boolean alive, long tick) {
    }

    /** 便捷方法：本渲染器当前玩的模式是不是 solo */
    public static boolean isSolo(String gameType) {
        return TeamCompetitionMap.TYPE_SOLO.equals(gameType);
    }

    /** 判断某个 PlayerInfo 是不是「其实还活着」——只在 Tab 渲染时用 */
    public static boolean overrideAlive(PlayerInfo info) {
        Boolean v = isAliveCached(info.getProfile().getId());
        return v != null && v;
    }
}
