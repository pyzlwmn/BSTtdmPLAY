package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.TeamCompetitionMap;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.GunItemFactory;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.LoadoutConfig;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.LoadoutManager;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.RefitSession;
import net.minecraft.world.item.ItemStack;
import net.ptcrys.fpsmatch.core.FPSMCore;
import net.ptcrys.fpsmatch.core.map.BaseMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端侧的小助手：C2S 包收到后的落地动作。
 *
 * 规则：
 *  - 玩家必须在一张 tdm 地图里（否则只回一份「不可编辑」的空数据）
 *  - 对局中能不能改，看地图设置 loadout/allowEditInMatch
 */
public final class TcpLoadoutServerHelper {

    private static final Logger LOG = LoggerFactory.getLogger("BST-TDM-Loadout");

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

    /** 把当前可选项 + 现有选择推给客户端 */
    public static void send(ServerPlayer player) {
        TeamCompetitionMap map = tdmMap(player);
        boolean inMatch = map != null && map.isStarted();
        boolean canEdit = map == null ? LoadoutConfig.enabled() : map.canEditLoadout();
        TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, inMatch, canEdit));
        if (map == null) {
            player.displayClientMessage(Component.literal("§7你现在不在对局里，选的装备会存下来、进局自动用"), false);
        } else if (inMatch) {
            player.displayClientMessage(Component.literal("§e对局进行中：背包只读，不能编辑"), true);
        }
    }

    /** 服务端主动让客户端弹开编辑界面（/tdmloadout）——局外也能开 */
    public static void open(ServerPlayer player) {
        TeamCompetitionMap map = tdmMap(player);
        boolean inMatch = map != null && map.isStarted();
        boolean canEdit = map == null ? LoadoutConfig.enabled() : map.canEditLoadout();
        TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, inMatch, canEdit, TcpLoadoutS2CPacket.FLAG_OPEN));
        if (inMatch) {
            player.displayClientMessage(Component.literal("§e对局进行中：背包只读，不能编辑"), true);
        }
    }

    /** 改装台（v26）：start = 把枪 + 它能用的配件发给客户端（客户端虚空打开 TaCZ 改装页）；save = 存成品枪 */
    public static void refit(ServerPlayer player, String action, String slotName, String snbt) {
        TeamCompetitionMap map = tdmMap(player);
        boolean inMatch = map != null && map.isStarted();

        if ("save".equals(action)) {
            String slot = RefitSession.end(player);
            if (slot == null) return;                     // 没开过改装，忽略
            LoadoutManager.saveCustomItem(player, slot, snbt);
            player.displayClientMessage(Component.literal("§a改装完成，这套配置已存到当前背包"), false);
            boolean canEdit0 = map == null ? LoadoutConfig.enabled() : map.canEditLoadout();
            TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, inMatch, canEdit0));
            return;
        }

        // start
        if (inMatch) {
            player.displayClientMessage(Component.literal("§c对局进行中不可改装（本局先这么打）"), false);
            return;
        }
        if (map != null && !map.canEditLoadout()) {
            player.displayClientMessage(Component.literal("§c现在不能改装"), false);
            return;
        }
        String slot = slotName == null || slotName.isBlank() ? "rifle" : slotName.toLowerCase();
        int idx = slotIndexOf(slot);
        if (idx < 0) return;
        ItemStack gun = LoadoutManager.build(player, idx);
        if (gun.isEmpty() || !GunItemFactory.isGun(gun)) {
            player.displayClientMessage(Component.literal("§c这个枪位没有可改的枪"), false);
            return;
        }

        // 这把枪能用的配件（v27：主人说配件自己用其它方式提供，这里不再下发）
        List<String> allowed = new ArrayList<>();
        RefitSession.begin(player, slot);
        TcpNetwork.sendToPlayer(player, new TcpRefitS2CPacket(slot, GunItemFactory.toSnbt(gun), allowed));
        LOG.info("[TDM·背包] {} 请求改装 {}（可用配件 {} 件）", player.getName().getString(), slot, allowed.size());
    }

    /** 背包（预设）管理：create / rename / delete / switch（v24） */
    public static void preset(ServerPlayer player, String action, String id, String name) {
        TeamCompetitionMap map = tdmMap(player);
        if (map != null && !map.canEditLoadout()) {
            player.displayClientMessage(Component.literal("§c对局进行中，背包已锁定"), false);
            TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, true, false));
            return;
        }
        String msg;
        switch (action == null ? "" : action) {
            case "create" -> {
                LoadoutManager.Preset p = LoadoutManager.createPreset(player, name);
                msg = p == null ? "§c背包数量已达上限" : "§a已新建背包：" + p.name;
            }
            case "rename" -> msg = LoadoutManager.renamePreset(player, id, name)
                    ? "§a背包已改名：" + name : "§c改名失败（名字为空？）";
            case "delete" -> msg = LoadoutManager.deletePreset(player, id)
                    ? "§a已删除该背包" : "§c至少保留一个背包";
            case "switch" -> msg = LoadoutManager.switchPreset(player, id)
                    ? "§a已切换到「" + LoadoutManager.active(player).name + "」" : "§c背包不存在";
            default -> msg = "§c未知操作";
        }
        player.displayClientMessage(Component.literal(msg), false);

        boolean inMatch = map != null && map.isStarted();
        boolean canEdit = map == null ? LoadoutConfig.enabled() : map.canEditLoadout();
        TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, inMatch, canEdit));
        if (inMatch) {
            // 对局中切背包 → 立刻整套换装
            for (int s = 0; s < LoadoutConfig.SLOT_COUNT; s++) {
                if (LoadoutManager.pick(player, s) != null) LoadoutManager.applySlot(player, s);
            }
        }
    }

    /** 推一份配件编辑页数据（open=true 时客户端直接跳到配件编辑页） */
    public static void sendAttachments(ServerPlayer player, boolean open) {
        TeamCompetitionMap map = tdmMap(player);
        boolean canEdit = map == null ? LoadoutConfig.enabled() : map.canEditLoadout();
        TcpNetwork.sendToPlayer(player, LoadoutManager.buildAttachmentPacket(player, canEdit, open));
    }

    /** 配件编辑页选/卸配件 */
    public static void selectAttachment(ServerPlayer player, String slotName, String type, String id) {
        TeamCompetitionMap map = tdmMap(player);
        if (map != null && !map.canEditLoadout()) {
            player.displayClientMessage(Component.literal("§c对局进行中，配件已锁定"), false);
            sendAttachments(player, false);
            return;
        }
        boolean ok = LoadoutManager.selectAttachment(player, slotName, type, id);
        if (ok) {
            player.displayClientMessage(Component.literal("§a" + LoadoutConfig.typeLabel(type) + "："
                    + (id == null || id.isBlank() ? "已卸下" : id)), true);
            if (map != null && map.isStarted()) {
                // 对局中：立刻重发一把带配件的枪
                int slot = slotIndexOf(slotName);
                if (slot >= 0) LoadoutManager.applySlot(player, slot);
            }
        } else {
            player.displayClientMessage(Component.literal("§c这个配件不可用（不在配置里 / 枪位不对）"), false);
        }
        sendAttachments(player, false);
    }

    private static int slotIndexOf(String slotName) {
        for (int i = 0; i < LoadoutConfig.SLOT_NAMES.length; i++) {
            if (LoadoutConfig.SLOT_NAMES[i].equalsIgnoreCase(slotName)) return i;
        }
        return -1;
    }

    /** 选了某个槽位（局外也能改：存下来，进局自动用） */
    public static void select(ServerPlayer player, int slot, String gunId) {
        TeamCompetitionMap map = tdmMap(player);
        if (map != null && !map.canEditLoadout()) {
            player.displayClientMessage(Component.literal("§c对局进行中，装备已锁定"), false);
            TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, true, false));
            return;
        }

        boolean ok = LoadoutManager.select(player, slot, gunId);
        if (ok && map != null && map.isStarted()) {
            LoadoutManager.applySlot(player, slot);      // 对局中立刻换枪
            player.displayClientMessage(Component.literal("§a已装备：" + gunId), true);
        } else if (ok) {
            player.displayClientMessage(Component.literal("§a已保存：" + gunId + "（进局自动带上）"), false);
        } else {
            player.displayClientMessage(Component.literal("§c这个装备你不可用（配置里没有，或皮肤未解锁）"), false);
        }
        boolean inMatch = map != null && map.isStarted();
        boolean canEdit = map == null ? LoadoutConfig.enabled() : map.canEditLoadout();
        TcpNetwork.sendToPlayer(player, LoadoutManager.buildPacket(player, inMatch, canEdit));
    }

    /** 给 base 用的（万一别处要 map 类型判断） */
    static boolean isTdmMap(BaseMap map) {
        return map instanceof TeamCompetitionMap;
    }
}
