package com.github.pyzlwmn.filtdm.TeamCompetition.net;

import com.github.pyzlwmn.filtdm.TeamCompetition.TeamCompetitionMap;
import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.GunItemFactory;
import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.LoadoutConfig;
import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.LoadoutManager;
import com.github.pyzlwmn.filtdm.TeamCompetition.loadout.RefitSession;
import net.minecraft.world.item.ItemStack;
import net.ptcrys.fpsmatch.core.FPSMCore;
import net.ptcrys.fpsmatch.core.map.BaseMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class TcpLoadoutServerHelper {

    private static final Logger LOG = LoggerFactory.getLogger("FIL-TDM-Loadout");

    private TcpLoadoutServerHelper() {
    }

    private static TeamCompetitionMap tdmMap(ServerPlayer player) {
        try {
            return FPSMCore.getInstance().getMapByPlayer(player)
                    .filter(m -> m instanceof TeamCompetitionMap)
                    .map(m -> (TeamCompetitionMap) m)
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void send(ServerPlayer player) {
        TeamCompetitionMap map = tdmMap(player);
        boolean inMatch = map != null && map.isStarted();
        boolean canEdit = map == null ? LoadoutConfig.enabled() : map.canEditLoadout();
        boolean canSwitch = map == null ? LoadoutConfig.enabled() : map.canSwitchPreset();
        TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, inMatch, canEdit, canSwitch, 0));

    }

    public static void open(ServerPlayer player) {
        TeamCompetitionMap map = tdmMap(player);
        boolean inMatch = map != null && map.isStarted();
        boolean canEdit = map == null ? LoadoutConfig.enabled() : map.canEditLoadout();
        boolean canSwitch = map == null ? LoadoutConfig.enabled() : map.canSwitchPreset();
        TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, inMatch, canEdit, canSwitch, TcpLoadoutS2CPacket.FLAG_OPEN));

    }

    public static void refit(ServerPlayer player, String action, String slotName, String snbt) {
        TeamCompetitionMap map = tdmMap(player);
        boolean inMatch = map != null && map.isStarted();

        if ("save".equals(action)) {
            String slot = RefitSession.end(player);
            if (slot == null) return;
            LoadoutManager.saveCustomItem(player, slot, snbt);

            boolean canEdit0 = map == null ? LoadoutConfig.enabled() : map.canEditLoadout();
            boolean canSwitch0 = map == null ? LoadoutConfig.enabled() : map.canSwitchPreset();
            TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, inMatch, canEdit0, canSwitch0, 0));
            return;
        }


        if (inMatch) return;
        if (map != null && !map.canEditLoadout()) return;
        String slot = slotName == null || slotName.isBlank() ? "rifle" : slotName.toLowerCase();
        int idx = slotIndexOf(slot);
        if (idx < 0) return;
        ItemStack gun = LoadoutManager.build(player, idx);
        if (gun.isEmpty() || !GunItemFactory.isGun(gun)) return;

        List<String> allowed = new ArrayList<>();
        RefitSession.begin(player, slot);
        TcpNetwork.sendToPlayer(player, new TcpRefitS2CPacket(slot, GunItemFactory.toSnbt(gun), allowed));
        LOG.info("[TDM·背包] {} 请求改装 {}（可用配件 {} 件）", player.getName().getString(), slot, allowed.size());
    }

    public static void preset(ServerPlayer player, String action, String id, String name) {
        TeamCompetitionMap map = tdmMap(player);

        boolean isSwitch = "switch".equalsIgnoreCase(action == null ? "" : action);
        boolean allowed = map == null ? LoadoutConfig.enabled()
                : (isSwitch ? map.canSwitchPreset() : map.canEditLoadout());
        if (map != null && !allowed) {

            TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, true, false, map.canSwitchPreset(), 0));
            return;
        }

        switch (action == null ? "" : action) {
            case "create" -> LoadoutManager.createPreset(player, name);
            case "rename" -> LoadoutManager.renamePreset(player, id, name);
            case "delete" -> LoadoutManager.deletePreset(player, id);
            case "switch" -> LoadoutManager.switchPreset(player, id);
            default -> { }
        }
        boolean inMatch = map != null && map.isStarted();
        boolean canEdit = map == null ? LoadoutConfig.enabled() : map.canEditLoadout();
        boolean canSwitch = map == null ? LoadoutConfig.enabled() : map.canSwitchPreset();
        TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, inMatch, canEdit, canSwitch, 0));
        if (inMatch) {

            for (int s = 0; s < LoadoutConfig.SLOT_COUNT; s++) {
                if (LoadoutManager.pick(player, s) != null) LoadoutManager.applySlot(player, s);
            }
        }
    }

    public static void sendAttachments(ServerPlayer player, boolean open) {
        TeamCompetitionMap map = tdmMap(player);
        boolean canEdit = map == null ? LoadoutConfig.enabled() : map.canEditLoadout();
        TcpNetwork.sendToPlayer(player, LoadoutManager.buildAttachmentPacket(player, canEdit, open));
    }

    public static void selectAttachment(ServerPlayer player, String slotName, String type, String id) {
        TeamCompetitionMap map = tdmMap(player);
        if (map != null && !map.canEditLoadout()) {

            sendAttachments(player, false);
            return;
        }
        boolean ok = LoadoutManager.selectAttachment(player, slotName, type, id);

        if (ok && map != null && map.isStarted()) {

            int slot = slotIndexOf(slotName);
            if (slot >= 0) LoadoutManager.applySlot(player, slot);
        }
        sendAttachments(player, false);
    }

    private static int slotIndexOf(String slotName) {
        for (int i = 0; i < LoadoutConfig.SLOT_NAMES.length; i++) {
            if (LoadoutConfig.SLOT_NAMES[i].equalsIgnoreCase(slotName)) return i;
        }
        return -1;
    }

    public static void select(ServerPlayer player, int slot, String gunId) {
        TeamCompetitionMap map = tdmMap(player);
        if (map != null && !map.canEditLoadout()) {

            TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, true, false, map.canSwitchPreset(), 0));
            return;
        }

        boolean ok = LoadoutManager.select(player, slot, gunId);

        if (ok && map != null && map.isStarted()) {
            LoadoutManager.applySlot(player, slot);
        }
        boolean inMatch = map != null && map.isStarted();
        boolean canEdit = map == null ? LoadoutConfig.enabled() : map.canEditLoadout();
        TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, inMatch, canEdit));
    }

    static boolean isTdmMap(BaseMap map) {
        return map instanceof TeamCompetitionMap;
    }
}
