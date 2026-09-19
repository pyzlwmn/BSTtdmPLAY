package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout;

import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 改装会话（v26：纯客户端虚拟改装，服务端**完全不碰玩家背包**）
 *
 * 服务端只记住「谁在改装哪个枪位」，用来：
 *   - save 时校验（必须真的开过）
 *   - 对局开始时取消（局内不可改装）
 */
public final class RefitSession {

    private static final Logger LOG = LoggerFactory.getLogger("BST-TDM-Loadout");

    private static final Map<UUID, String> ACTIVE = new ConcurrentHashMap<>();

    private RefitSession() {
    }

    public static boolean isOpen(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** 记下开始改装的枪位 */
    public static void begin(ServerPlayer player, String slotName) {
        ACTIVE.put(player.getUUID(), slotName);
    }

    /** 结束（返回枪位名；没开过返回 null） */
    public static String end(ServerPlayer player) {
        return ACTIVE.remove(player.getUUID());
    }

    /** 对局开始时清掉所有会话（局内不可改装） */
    public static void clearAll(String reason) {
        if (!ACTIVE.isEmpty()) {
            LOG.info("[TDM·背包] 清理 {} 个改装会话（{}）", ACTIVE.size(), reason);
            ACTIVE.clear();
        }
    }

    /** 掉线清理 */
    public static void onLogout(ServerPlayer player) {
        if (ACTIVE.remove(player.getUUID()) != null) {
            LOG.info("[TDM·背包] {} 掉线，清理改装会话", player.getName().getString());
        }
    }
}
