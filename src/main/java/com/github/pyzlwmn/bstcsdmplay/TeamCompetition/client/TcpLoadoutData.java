package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpLoadoutS2CPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端缓存：装备编辑 UI 的数据（含多背包，v24）
 */
public final class TcpLoadoutData {

    private static int flags = 0;
    private static List<TcpLoadoutS2CPacket.SlotData> slots = new ArrayList<>();
    private static List<TcpLoadoutS2CPacket.PresetData> presets = new ArrayList<>();
    private static int maxPresets = 5;
    private static int version = 0;
    private static boolean received = false;

    private TcpLoadoutData() {
    }

    public static void update(int newFlags, List<TcpLoadoutS2CPacket.SlotData> newSlots,
                             List<TcpLoadoutS2CPacket.PresetData> newPresets, int newMaxPresets) {
        flags = newFlags;
        slots = new ArrayList<>(newSlots);
        presets = new ArrayList<>(newPresets);
        maxPresets = newMaxPresets;
        version++;
        received = true;
    }

    /** 打开 UI 前清一下，避免看到上一局的旧数据 */
    public static void reset() {
        flags = 0;
        slots = new ArrayList<>();
        presets = new ArrayList<>();
        version++;
        received = false;
    }

    public static boolean received() {
        return received;
    }

    public static int version() {
        return version;
    }

    public static int flags() {
        return flags;
    }

    public static boolean enabled() {
        return (flags & TcpLoadoutS2CPacket.FLAG_ENABLED) != 0;
    }

    public static boolean inMatch() {
        return (flags & TcpLoadoutS2CPacket.FLAG_IN_MATCH) != 0;
    }

    public static boolean canEdit() {
        return (flags & TcpLoadoutS2CPacket.FLAG_CAN_EDIT) != 0;
    }

    public static List<TcpLoadoutS2CPacket.SlotData> slots() {
        return slots;
    }

    public static TcpLoadoutS2CPacket.SlotData slot(int index) {
        for (TcpLoadoutS2CPacket.SlotData s : slots) {
            if (s.slot() == index) return s;
        }
        return null;
    }

    public static List<TcpLoadoutS2CPacket.PresetData> presets() {
        return presets;
    }

    public static TcpLoadoutS2CPacket.PresetData activePreset() {
        for (TcpLoadoutS2CPacket.PresetData p : presets) {
            if (p.active()) return p;
        }
        return presets.isEmpty() ? null : presets.get(0);
    }

    public static int maxPresets() {
        return maxPresets;
    }
}
