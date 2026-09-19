package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.GunItemFactory;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpNetwork;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpRefitC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 改装台客户端侧（v26：虚空打开）
 *
 * 服务端把「当前这槽位的枪（SNBT）+ 这把枪能用的全部配件 id」发过来，这里：
 *   1) **只在本地**把主手枪和配件摆进本地背包视图（真实背包不受影响，服务端也不知道）；
 *   2) 反射打开 TaCZ 原版改装页 `com.tacz.guns.client.gui.GunRefitScreen`；
 *   3) 关掉面板时，把（本地）改装后的枪转成 SNBT 发回服务端存档，然后还原本地背包视图。
 *
 * TaCZ 没装 / 打不开 → 直接收尾，不会把玩家卡住。
 */
public final class TcpRefitClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("BST-TDM-Loadout");

    /** 本地背包快照（虚空打开的临时视图） */
    private static List<ItemStack> snapItems = null;
    private static List<ItemStack> snapArmor = null;
    private static ItemStack snapOffhand = ItemStack.EMPTY;
    private static int snapSelected = 0;

    private static Screen refitScreen = null;
    private static boolean watching = false;
    private static String slotName = "rifle";

    private TcpRefitClient() {
    }

    /** 服务端发来「开始改装」 */
    public static void beginVirtual(String slot, String gunSnbt, List<String> attachmentIds) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        restoreSnapshotOnly();          // 清掉可能残留的旧快照
        slotName = slot == null || slot.isBlank() ? "rifle" : slot;

        Inventory inv = player.getInventory();
        snapItems = new ArrayList<>();
        for (ItemStack s : inv.items) snapItems.add(s.copy());
        snapArmor = new ArrayList<>();
        for (ItemStack s : inv.armor) snapArmor.add(s.copy());
        snapOffhand = inv.offhand.get(0).copy();
        snapSelected = inv.selected;

        // 本地视图：主手 = 枪，其余格 = 可用配件
        for (int i = 0; i < inv.items.size(); i++) inv.items.set(i, ItemStack.EMPTY);
        for (int i = 0; i < inv.armor.size(); i++) inv.armor.set(i, ItemStack.EMPTY);
        inv.offhand.set(0, ItemStack.EMPTY);

        ItemStack gun = GunItemFactory.fromSnbt(gunSnbt);
        inv.items.set(0, gun.isEmpty() ? ItemStack.EMPTY : gun);
        inv.selected = 0;
        LOGGER.info("[TDM·背包] 虚空打开改装台：槽位={}", slotName);

        // 打开 TaCZ 改装页
        try {
            Class<?> cls = Class.forName("com.tacz.guns.client.gui.GunRefitScreen");
            Object o = cls.getConstructor().newInstance();
            if (o instanceof Screen screen) {
                mc.setScreen(screen);
                refitScreen = screen;
                watching = true;
                player.displayClientMessage(Component.literal("§a改装面板已打开（本地虚拟，不会动你的背包）"), true);
                return;
            }
        } catch (Throwable t) {
            LOGGER.warn("[TDM·背包] 打不开 TaCZ 改装页：{}", t.toString());
        }
        player.displayClientMessage(Component.literal("§c没能打开改装界面（TaCZ 版本不同？）"), false);
        finishVirtual();
    }

    /** 客户端 tick 里调：改装页一关就存档 + 还原本地视图 */
    public static void tick() {
        if (!watching) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == refitScreen) return;      // 还开着
        watching = false;
        refitScreen = null;
        finishVirtual();
    }

    private static void finishVirtual() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        String snbt = "";
        if (player != null) {
            ItemStack gun = findGun(player.getInventory());
            snbt = GunItemFactory.toSnbt(gun);
            if (snbt.isEmpty()) {
                player.displayClientMessage(Component.literal("§c没找到改装后的枪，保留原配置"), false);
            }
        }
        TcpNetwork.CHANNEL.sendToServer(new TcpRefitC2SPacket("save", slotName, snbt));
        restoreSnapshotOnly();
    }

    private static ItemStack findGun(Inventory inv) {
        for (ItemStack s : inv.items) {
            if (!s.isEmpty() && GunItemFactory.isGun(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    /** 还原本地背包视图 */
    private static void restoreSnapshotOnly() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || snapItems == null) {
            snapItems = null;
            snapArmor = null;
            return;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < snapItems.size() && i < inv.items.size(); i++) inv.items.set(i, snapItems.get(i));
        if (snapArmor != null) {
            for (int i = 0; i < snapArmor.size() && i < inv.armor.size(); i++) inv.armor.set(i, snapArmor.get(i));
        }
        inv.offhand.set(0, snapOffhand == null ? ItemStack.EMPTY : snapOffhand);
        inv.selected = Math.max(0, Math.min(8, snapSelected));
        snapItems = null;
        snapArmor = null;
        snapOffhand = ItemStack.EMPTY;
    }
}
