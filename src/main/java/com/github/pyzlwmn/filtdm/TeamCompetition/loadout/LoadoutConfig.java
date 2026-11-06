package com.github.pyzlwmn.filtdm.TeamCompetition.loadout;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class LoadoutConfig {

    private static final Logger LOG = LoggerFactory.getLogger("FIL-TDM-Loadout");

    public static final int SLOT_RIFLE = 0;
    public static final int SLOT_PISTOL = 1;
    public static final int SLOT_GRENADE = 2;
    public static final int SLOT_UTILITY = 3;
    public static final int SLOT_KNIFE = 4;
    public static final int SLOT_COUNT = 5;

    public static final String[] SLOT_NAMES = {"rifle", "pistol", "grenade", "utility", "knife"};
    public static final String[] SLOT_LABEL = {
            "槽位1 · 步枪", "槽位2 · 手枪", "槽位3 · 投掷物", "槽位4 · 道具", "槽位5 · 近战"
    };

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("filtdm").resolve("loadout.json");

    private static final String DEFAULT_JSON = """
            {
              "_说明": "槽位1=步枪 2=手枪 3/4=投掷物与道具 5=刀；条目可写 tacz 枪械id（原皮）、gun|skin（指定枪皮）、或任意物品id",
              "_皮肤": "kubejs/config/player_skin 里配的是枪皮：某个枪皮为 true → 对应枪进该玩家队列，枪皮本身也会出现在界面里",
              "enabled": true,
              "clearOnStart": true,
              "refillOnRespawn": true,
              "allowEditInMatch": false,
              "skinFolder": "kubejs/config/player_skin",
              "showBaseGuns": true,
              "skinEntries": true,
              "showAllWhenNoSkinFile": true,
              "ammo": { "magazine": -1, "reserve": -1, "fire": "NATIVE" },
              "previewContext": "FIXED",
              "previewScale": 80,
              "attachmentUi": "CLASSIC",
              "attachments": {
                "SCOPE": [],
                "MUZZLE": [],
                "STOCK": [],
                "GRIP": [],
                "LASER": [],
                "EXTENDED_MAG": []
              },
              "autoAttachmentPool": true,
              "autoAttachFirst": true,
              "maxPresets": 5,
              "defaultAttachments": {
                "SCOPE": "",
                "MUZZLE": "",
                "STOCK": "",
                "GRIP": "",
                "LASER": "",
                "EXTENDED_MAG": ""
              },
              "attachmentLimit": 6,

              "rifle":   ["tacz:ak47"],
              "pistol":  ["tacz:deagle"],
              "grenade": ["tacz:ammo_box"],
              "utility": ["tacz:ammo_box"],
              "knife":   ["minecraft:diamond_sword"]
            }
            """;

    @SuppressWarnings("unchecked")
    private static final List<String>[] GUNS = new List[SLOT_COUNT];

    private static boolean enabled = true;
    private static boolean clearOnStart = true;
    private static boolean refillOnRespawn = true;
    private static boolean allowEditInMatch = false;
    private static String skinFolder = "kubejs/config/player_skin";
    private static boolean skinPrefixMatch = true;

    private static boolean skinSlotFilter = true;
    private static boolean showAllWhenNoSkinFile = true;
    private static boolean showBaseGuns = true;
    private static boolean skinEntries = true;



    private static int defaultMagazine = -1;
    private static int defaultReserve = -1;
    private static String defaultFire = "NATIVE";



    private static String previewContext = "FIXED";

    private static float previewScale = 80.0f;

    private static boolean modernAttachmentUi = false;

    private static final java.util.Map<String, int[]> AMMO_BY_GUN = new java.util.HashMap<>();
    private static final java.util.Map<String, String> FIRE_BY_GUN = new java.util.HashMap<>();

    private static final java.util.Map<String, List<String>> ATTACH_BY_TYPE = new java.util.LinkedHashMap<>();

    private static final java.util.Map<String, String> DEFAULT_ATTACHMENTS = new java.util.LinkedHashMap<>();

    private static boolean autoAttachFirst = true;

    private static final List<String> ATTACH_FLAT = new ArrayList<>();
    private static int attachmentLimit = 6;

    private static boolean autoAttachmentPool = true;

    private static long lastStamp = Long.MIN_VALUE;
    private static long lastCheckMs = 0L;

    private LoadoutConfig() {
    }

    public static boolean enabled() {
        reloadIfChanged();
        return enabled;
    }

    public static boolean clearOnStart() {
        reloadIfChanged();
        return clearOnStart;
    }

    public static boolean refillOnRespawn() {
        reloadIfChanged();
        return refillOnRespawn;
    }

    public static boolean allowEditInMatch() {
        reloadIfChanged();
        return allowEditInMatch;
    }

    public static String skinFolder() {
        reloadIfChanged();
        return skinFolder;
    }

    public static boolean skinPrefixMatch() {
        reloadIfChanged();
        return skinPrefixMatch;
    }

    public static boolean showAllWhenNoSkinFile() {
        reloadIfChanged();
        return showAllWhenNoSkinFile;
    }

    public static boolean skinSlotFilter() {
        reloadIfChanged();
        return skinSlotFilter;
    }

    public static boolean showBaseGuns() {
        reloadIfChanged();
        return showBaseGuns;
    }

    public static boolean skinEntries() {
        reloadIfChanged();
        return skinEntries;
    }

    public static int[] ammoFor(String baseGunId) {
        reloadIfChanged();
        int[] custom = AMMO_BY_GUN.get(baseGunId);
        return custom != null ? custom : new int[]{defaultMagazine, defaultReserve};
    }



    public static boolean useModernAttachmentUi() {
        reloadIfChanged();
        return modernAttachmentUi;
    }

    public static float previewScale() {
        reloadIfChanged();
        return previewScale <= 0f ? 80.0f : previewScale;
    }

    public static String previewContext() {
        reloadIfChanged();
        return previewContext == null || previewContext.isBlank() ? "FIXED" : previewContext.trim().toUpperCase();
    }

    public static String fireFor(String baseGunId) {
        reloadIfChanged();
        String v = FIRE_BY_GUN.get(baseGunId);
        return v != null ? v : defaultFire;
    }

    public static List<String> attachments(String type) {
        reloadIfChanged();
        List<String> configured = ATTACH_BY_TYPE.get(type);
        if (configured != null && !configured.isEmpty()) return configured;
        if (autoAttachmentPool) return AttachmentIndex.byType().getOrDefault(type, List.of());
        return List.of();
    }

    public static List<String> attachments() {
        reloadIfChanged();
        if (!ATTACH_FLAT.isEmpty()) return ATTACH_FLAT;
        List<String> out = new ArrayList<>();
        for (List<String> v : ATTACH_BY_TYPE.values()) out.addAll(v);
        if (!out.isEmpty()) return out;
        if (autoAttachmentPool) return AttachmentIndex.all();
        return List.of();
    }

    public static List<String> attachmentTypes() {
        reloadIfChanged();
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : ATTACH_BY_TYPE.entrySet()) {
            if (!e.getValue().isEmpty() && !out.contains(e.getKey())) out.add(e.getKey());
        }
        if (!out.isEmpty()) return out;
        if (!ATTACH_FLAT.isEmpty()) {
            for (String t : AttachmentIndex.TYPE_ORDER) {
                if (!attachments(t).isEmpty()) out.add(t);
            }
            return out;
        }
        if (autoAttachmentPool) {
            for (Map.Entry<String, List<String>> e : AttachmentIndex.byType().entrySet()) {
                if (!e.getValue().isEmpty() && !out.contains(e.getKey())) out.add(e.getKey());
            }
        }
        return out;
    }

    public static String typeLabel(String type) {
        return AttachmentIndex.TYPE_LABEL.getOrDefault(type, type);
    }

    public static Map<String, String> defaultAttachments() {
        reloadIfChanged();
        return DEFAULT_ATTACHMENTS;
    }

    public static boolean autoAttachFirst() {
        reloadIfChanged();
        return autoAttachFirst;
    }

    public static boolean autoAttachmentPool() {
        reloadIfChanged();
        boolean anyConfigured = !ATTACH_FLAT.isEmpty();
        for (List<String> v : ATTACH_BY_TYPE.values()) anyConfigured |= !v.isEmpty();
        return !anyConfigured && autoAttachmentPool;
    }

    public static int attachmentLimit() {
        reloadIfChanged();
        return attachmentLimit;
    }

    private static int maxPresets = 5;

    public static int maxPresets() {
        reloadIfChanged();
        return maxPresets;
    }

    public static List<String> configured(int slot) {
        reloadIfChanged();
        if (slot < 0 || slot >= SLOT_COUNT || GUNS[slot] == null) return Collections.emptyList();
        return GUNS[slot];
    }

    public static Path file() {
        return FILE;
    }

    private static void reloadIfChanged() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < 2000L && lastStamp != Long.MIN_VALUE) return;
        lastCheckMs = now;
        long stamp = fileStamp();
        if (stamp == lastStamp) return;
        lastStamp = stamp;
        load();
    }

    private static long fileStamp() {
        try {
            if (!Files.exists(FILE)) return -1L;
            return Files.getLastModifiedTime(FILE).toMillis() + Files.size(FILE);
        } catch (IOException e) {
            return -1L;
        }
    }

    public static synchronized void load() {
        for (int i = 0; i < SLOT_COUNT; i++) GUNS[i] = new ArrayList<>();

        try {
            if (!Files.exists(FILE)) {
                Files.createDirectories(FILE.getParent());
                Files.writeString(FILE, DEFAULT_JSON, StandardCharsets.UTF_8);
                LOG.info("[TDM·背包] 已生成默认配置：{}", FILE);
            }
            String text = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                LOG.warn("[TDM·背包] 配置文件不是 JSON 对象，已忽略：{}", FILE);
                return;
            }
            JsonObject obj = root.getAsJsonObject();

            enabled = bool(obj, "enabled", true);
            clearOnStart = bool(obj, "clearOnStart", true);
            refillOnRespawn = bool(obj, "refillOnRespawn", true);
            allowEditInMatch = bool(obj, "allowEditInMatch", false);
            skinFolder = str(obj, "skinFolder", "kubejs/config/player_skin");
            skinPrefixMatch = bool(obj, "skinPrefixMatch", true);
            skinSlotFilter = bool(obj, "skinSlotFilter", true);
            showAllWhenNoSkinFile = bool(obj, "showAllWhenNoSkinFile", true);
            showBaseGuns = bool(obj, "showBaseGuns", true);
            skinEntries = bool(obj, "skinEntries", true);

            AMMO_BY_GUN.clear();
            FIRE_BY_GUN.clear();
            ATTACH_BY_TYPE.clear();
            ATTACH_FLAT.clear();
            DEFAULT_ATTACHMENTS.clear();
            autoAttachFirst = bool(obj, "autoAttachFirst", true);
            if (obj.has("defaultAttachments") && obj.get("defaultAttachments").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("defaultAttachments").entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        DEFAULT_ATTACHMENTS.put(e.getKey().toUpperCase(), e.getValue().getAsString());
                    }
                }
            }
            if (obj.has("ammo") && obj.get("ammo").isJsonObject()) {
                JsonObject a = obj.getAsJsonObject("ammo");
                defaultMagazine = intVal(a, "magazine", intVal(a, "mag", -1));
                defaultReserve = intVal(a, "reserve", -1);
                defaultFire = str(a, "fire", str(a, "fireMode", "NATIVE"));
            }
            previewContext = str(obj, "previewContext", "FIXED");
            modernAttachmentUi = "MODERN".equalsIgnoreCase(str(obj, "attachmentUi", "CLASSIC"));
            try {
                if (obj.has("previewScale")) previewScale = obj.get("previewScale").getAsFloat();
            } catch (Throwable ignored) {
            }
            attachmentLimit = Math.max(1, intVal(obj, "attachmentLimit", 6));
            maxPresets = Math.max(1, intVal(obj, "maxPresets", 5));
            autoAttachmentPool = bool(obj, "autoAttachmentPool", true);
            if (obj.has("attachments")) {
                JsonElement ae = obj.get("attachments");
                if (ae.isJsonArray()) {
                    for (JsonElement e : ae.getAsJsonArray()) {
                        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                            String v = e.getAsString().trim();
                            if (!v.isEmpty() && !ATTACH_FLAT.contains(v)) ATTACH_FLAT.add(v);
                        }
                    }
                } else if (ae.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : ae.getAsJsonObject().entrySet()) {
                        List<String> list = new ArrayList<>();
                        if (e.getValue().isJsonArray()) {
                            for (JsonElement v : e.getValue().getAsJsonArray()) {
                                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                                    String s = v.getAsString().trim();
                                    if (!s.isEmpty() && !list.contains(s)) list.add(s);
                                }
                            }
                        }
                        ATTACH_BY_TYPE.put(e.getKey().toUpperCase(), list);
                    }
                }
            }

            if (obj.has("slots") && obj.get("slots").isJsonObject()) {
                readSlotObject(obj.getAsJsonObject("slots"));
            }
            readSlotObject(obj);

            int total = 0;
            for (int i = 0; i < SLOT_COUNT; i++) total += GUNS[i].size();
            LOG.info("[TDM·背包] 配置已载入（{} 条）：步枪{} / 手枪{} / 投掷{} / 道具{} / 刀{} ｜ 原皮{} 枪皮条目{}",
                    total, GUNS[0].size(), GUNS[1].size(), GUNS[2].size(), GUNS[3].size(), GUNS[4].size(),
                    showBaseGuns ? "显示" : "按皮肤解锁", skinEntries ? "显示" : "隐藏");
        } catch (Exception e) {
            LOG.error("[TDM·背包] 读取配置失败（沿用内存里的旧值）：{}", e.toString());
        }
    }

    private static void readSlotObject(JsonObject obj) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            List<String> out = GUNS[i];
            for (String key : keysOf(i)) {
                if (!obj.has(key)) continue;
                JsonElement el = obj.get(key);
                if (el == null || !el.isJsonArray()) continue;
                JsonArray arr = el.getAsJsonArray();
                for (JsonElement item : arr) {
                    if (item == null) continue;
                    if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                        String v = item.getAsString().trim();
                        if (!v.isEmpty() && !out.contains(v)) out.add(v);
                    } else if (item.isJsonObject()) {
                        JsonObject o = item.getAsJsonObject();
                        String gun = firstString(o, "gun", "gunId", "id", "item");
                        String skin = firstString(o, "skin", "skinId");
                        if (gun != null && !gun.isEmpty()) {
                            String v = skin == null || skin.isEmpty() ? gun : gun + "|" + skin;
                            if (!out.contains(v)) out.add(v);

                            int mag = intVal(o, "magazine", intVal(o, "mag", -1));
                            int reserve = intVal(o, "reserve", -1);
                            String fire = firstString(o, "fire", "fireMode");
                            if (mag >= 0 || reserve >= 0) {
                                AMMO_BY_GUN.put(gun, new int[]{mag >= 0 ? mag : defaultMagazine,
                                        reserve >= 0 ? reserve : defaultReserve});
                            }
                            if (fire != null && !fire.isEmpty()) FIRE_BY_GUN.put(gun, fire);
                        }
                    }
                }
            }
        }
    }

    private static String[] keysOf(int slot) {
        return new String[]{SLOT_NAMES[slot], "slot" + (slot + 1), String.valueOf(slot + 1)};
    }

    private static String firstString(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsJsonPrimitive().isString()) {
                return o.get(k).getAsString();
            }
        }
        return null;
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        try {
            if (o.has(key) && o.get(key).isJsonPrimitive()) return o.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
        }
        return def;
    }

    private static int intVal(JsonObject o, String key, int def) {
        try {
            if (o.has(key) && o.get(key).isJsonPrimitive()) return o.get(key).getAsInt();
        } catch (RuntimeException ignored) {
        }
        return def;
    }

    private static String str(JsonObject o, String key, String def) {
        try {
            if (o.has(key) && o.get(key).isJsonPrimitive()) return o.get(key).getAsString();
        } catch (RuntimeException ignored) {
        }
        return def;
    }

    public static String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.addProperty("clearOnStart", clearOnStart);
        o.addProperty("refillOnRespawn", refillOnRespawn);
        o.addProperty("allowEditInMatch", allowEditInMatch);
        o.addProperty("skinFolder", skinFolder);
        o.addProperty("skinSlotFilter", skinSlotFilter);
        o.addProperty("showBaseGuns", showBaseGuns);
        o.addProperty("skinEntries", skinEntries);
        o.addProperty("defaultMagazine", defaultMagazine);
        o.addProperty("defaultReserve", defaultReserve);
        o.addProperty("defaultFireMode", defaultFire);
        o.addProperty("attachmentLimit", attachmentLimit);
        o.addProperty("autoAttachmentPool", autoAttachmentPool);
        for (int i = 0; i < SLOT_COUNT; i++) {
            JsonArray a = new JsonArray();
            for (String s : configured(i)) a.add(s);
            o.add(SLOT_NAMES[i], a);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(o);
    }

    public static void markDirty() {
        lastStamp = Long.MIN_VALUE;
        lastCheckMs = 0L;
    }

    public static boolean exists() {
        return Files.exists(FILE);
    }

    static {

        try {
            load();
        } catch (Throwable t) {
            LOG.error("[TDM·背包] 初始化配置失败：{}", t.toString());
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (GUNS[i] == null) GUNS[i] = new ArrayList<>();
            }
        }
    }
}
