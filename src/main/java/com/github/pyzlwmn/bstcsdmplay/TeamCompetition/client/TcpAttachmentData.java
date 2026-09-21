package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout.LoadoutConfig;   // ★ v51：补齐 import
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpAttachmentS2CPacket;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端缓存：配件编辑页数据
 */
public final class TcpAttachmentData {

    private static int flags = 0;
    private static List<TcpAttachmentS2CPacket.GunEntry> guns = new ArrayList<>();
    private static int version = 0;
    private static boolean received = false;

    private TcpAttachmentData() {
    }

    public static void update(int newFlags, List<TcpAttachmentS2CPacket.GunEntry> newGuns) {
        flags = newFlags;
        guns = new ArrayList<>(newGuns);
        version++;
        received = true;
    }

    public static void reset() {
        flags = 0;
        guns = new ArrayList<>();
        version++;
        received = false;
    }

    public static boolean received() {
        return received;
    }

    public static int version() {
        return version;
    }

    public static boolean canEdit() {
        return (flags & TcpAttachmentS2CPacket.FLAG_CAN_EDIT) != 0;
    }

    public static List<TcpAttachmentS2CPacket.GunEntry> guns() {
        return guns;
    }

    public static TcpAttachmentS2CPacket.GunEntry gun(String slotName) {
        for (TcpAttachmentS2CPacket.GunEntry g : guns) {
            if (g.slotName().equals(slotName)) return g;
        }
        return null;
    }

    /** 服务端开配件页（★v50：优先 ModernUI 版，拿不到/关掉则退回原版画布版） */
    public static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof TcpAttachmentScreen || isMuiScreen(mc.screen)) return;
        mc.setScreen(createAttachmentScreen());
    }

    /** 当前界面是不是 ModernUI 的界面（反射判断，避免编译期强依赖） */
    private static boolean isMuiScreen(Object screen) {
        try {
            return Class.forName("icyllis.modernui.mc.MuiScreen").isInstance(screen);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * ★ v50：配件编辑用哪个界面
     *   loadout.json 的 "attachmentUi": "MODERN"（默认，真矢量圆角）/ "CLASSIC"（原版画布版）
     *   MODERN 时通过反射调 MuiModApi.createScreen(new TcpAttachmentMuiScreen())，
     *   任何一步不可用（ModernUI 没装 / 类不在 / 报错）都自动退回 CLASSIC，不会崩。
     */
    private static net.minecraft.client.gui.screens.Screen createAttachmentScreen() {
        if (!LoadoutConfig.useModernAttachmentUi()) return new TcpAttachmentScreen();
        try {
            Class<?> fragCls = Class.forName("icyllis.modernui.fragment.Fragment");
            Object fragment = Class.forName(
                            "com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client.TcpAttachmentMuiScreen")
                    .getDeclaredConstructor().newInstance();
            Class<?> apiCls = Class.forName("icyllis.modernui.mc.MuiModApi");
            Object api = apiCls.getMethod("get").invoke(null);
            Object screen = apiCls.getMethod("createScreen", fragCls).invoke(api, fragment);
            if (screen instanceof net.minecraft.client.gui.screens.Screen s) return s;
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().debug("[TDM·背包] ModernUI 配件页不可用，退回原版：{}", t.toString());
        }
        return new TcpAttachmentScreen();
    }
}
