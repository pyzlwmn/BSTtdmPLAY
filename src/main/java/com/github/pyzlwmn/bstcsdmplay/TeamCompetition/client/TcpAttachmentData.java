package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

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

    /** 服务端开配件页 */
    public static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof TcpAttachmentScreen) return;
        mc.setScreen(new TcpAttachmentScreen());
    }
}
