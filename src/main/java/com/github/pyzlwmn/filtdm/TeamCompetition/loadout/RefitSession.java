package com.github.pyzlwmn.filtdm.TeamCompetition.loadout;

import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RefitSession {

    private static final Logger LOG = LoggerFactory.getLogger("FIL-TDM-Loadout");

    private static final Map<UUID, String> ACTIVE = new ConcurrentHashMap<>();

    private RefitSession() {
    }

    public static boolean isOpen(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void begin(ServerPlayer player, String slotName) {
        ACTIVE.put(player.getUUID(), slotName);
    }

    public static String end(ServerPlayer player) {
        return ACTIVE.remove(player.getUUID());
    }

    public static void clearAll(String reason) {
        if (!ACTIVE.isEmpty()) {
            LOG.info("[TDM·背包] 清理 {} 个改装会话（{}）", ACTIVE.size(), reason);
            ACTIVE.clear();
        }
    }

    public static void onLogout(ServerPlayer player) {
        if (ACTIVE.remove(player.getUUID()) != null) {
            LOG.info("[TDM·背包] {} 掉线，清理改装会话", player.getName().getString());
        }
    }
}
