package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout;

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

/**
 * 背包系统配置文件：<游戏目录>/config/bstcsdmplay/loadout.json
 *
 * 结构（槽位分开写）：
 * <pre>
 * {
 *   "enabled": true,
 *   "clearOnStart": true,
 *   "refillOnRespawn": true,
 *   "allowEditInMatch": true,
 *   "skinFolder": "kubejs/config/player_skin",
 *   "showBaseGuns": true,
 *   "skinEntries": true,
 *   "showAllWhenNoSkinFile": true,
 *
 *   "rifle":   ["tacz:ak47"],                       // 槽位1 步枪（原皮）
 *   "pistol":  ["tacz:deagle"],                     // 槽位2 手枪（原皮）
 *   "grenade": ["tacz:ammo_box"],                   // 槽位3 投掷物（占位：全种类弹药盒）
 *   "utility": ["tacz:ammo_box"],                   // 槽位4 道具（占位：全种类弹药盒）
 *   "knife":   ["minecraft:diamond_sword"]          // 槽位5 刀（占位：钻石剑）
 * }
 * </pre>
 *
 * 皮肤系统联动：这里配的是**原皮**（枪本身），kubejs 的 player_skin 里配的是**枪皮**。
 * 玩家某把枪的枪皮为 true → 这把枪进他的显示队列，并且枪皮本身也作为条目出现（gun|skin）。
 *
 * 条目写法：
 *   1) "tacz:ak47"               → TaCZ 枪械 id（原皮，自动包成枪械物品）
 *   2) "tacz:ak47|tacz:ak47_fox" → gun|skin（直接指定皮肤）
 *   3) "minecraft:diamond_sword" → 普通物品 id
 */
public final class LoadoutConfig {

    private static final Logger LOG = LoggerFactory.getLogger("BST-TDM-Loadout");

    /** 槽位索引 = 物品栏 index = 快捷键数字 - 1 */
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

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("bstcsdmplay").resolve("loadout.json");

    private static final String DEFAULT_JSON = """
            {
              "_说明": "槽位1=步枪 2=手枪 3/4=投掷物与道具 5=刀；条目可写 tacz 枪械id（原皮）、gun|skin（指定枪皮）、或任意物品id",
              "_皮肤": "kubejs/config/player_skin 里配的是枪皮：某个枪皮为 true → 对应枪进该玩家队列，枪皮本身也会出现在界面里",
              "enabled": true,
              "clearOnStart": true,
              "refillOnRespawn": true,
              "allowEditInMatch": true,
              "skinFolder": "kubejs/config/player_skin",
              "showBaseGuns": true,
              "skinEntries": true,
              "showAllWhenNoSkinFile": true,
              "ammo": { "magazine": 30, "reserve": 300, "fire": "AUTO" },
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
    private static boolean allowEditInMatch = true;
    private static String skinFolder = "kubejs/config/player_skin";
    private static boolean skinPrefixMatch = true;
    private static boolean showAllWhenNoSkinFile = true;
    private static boolean showBaseGuns = true;
    private static boolean skinEntries = true;

    /** 全局弹药默认值：弹夹 / 备弹 / 开火模式 */
    private static int defaultMagazine = 30;
    private static int defaultReserve = 300;
    private static String defaultFire = "AUTO";   // v22：默认连发（枪不支持连发会自动回退单发）

    /** 单把枪的弹药覆盖：枪 id → [弹夹, 备弹] */
    private static final java.util.Map<String, int[]> AMMO_BY_GUN = new java.util.HashMap<>();
    private static final java.util.Map<String, String> FIRE_BY_GUN = new java.util.HashMap<>();

    /** 可选配件池（按槽位类型：SCOPE/MUZZLE/…；每个类型留空则自动扫描 TaCZ 该类型的全部配件） */
    private static final java.util.Map<String, List<String>> ATTACH_BY_TYPE = new java.util.LinkedHashMap<>();
    /** 默认配件（玩家没选就发这套；键 = 槽位类型 SCOPE/MUZZLE/…） */
    private static final java.util.Map<String, String> DEFAULT_ATTACHMENTS = new java.util.LinkedHashMap<>();
    /** 玩家没选、默认也没配 → 自动用该类型的第一个配件（保证发枪带配件） */
    private static boolean autoAttachFirst = true;
    /** 旧的扁平写法（兼容） */
    private static final List<String> ATTACH_FLAT = new ArrayList<>();
    private static int attachmentLimit = 6;
    /** 配件池留空时自动用 TaCZ 的全部配件（省得手填 id / 界面不显示配件页） */
    private static boolean autoAttachmentPool = true;

    /** ⚠️ 不能是 -1（文件不存在时 stamp 也是 -1，会导致永远不加载、配置全空） */
    private static long lastStamp = Long.MIN_VALUE;
    private static long lastCheckMs = 0L;

    private LoadoutConfig() {
    }

    // ================== 对外读取 ==================

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

    /** 原皮条目是否直接给所有玩家（false = 只有拥有该枪任一枪皮的玩家才能用） */
    public static boolean showBaseGuns() {
        reloadIfChanged();
        return showBaseGuns;
    }

    /** 玩家拥有的枪皮是否作为独立条目出现（gun|skin） */
    public static boolean skinEntries() {
        reloadIfChanged();
        return skinEntries;
    }

    /** 某把枪的弹药：[0]=弹夹 [1]=备弹（配置里没写就用全局默认） */
    public static int[] ammoFor(String baseGunId) {
        reloadIfChanged();
        int[] custom = AMMO_BY_GUN.get(baseGunId);
        return custom != null ? custom : new int[]{defaultMagazine, defaultReserve};
    }

    /** 某把枪的开火模式（SEMI/AUTO/BURST） */
    public static String fireFor(String baseGunId) {
        reloadIfChanged();
        String v = FIRE_BY_GUN.get(baseGunId);
        return v != null ? v : defaultFire;
    }

    /** 某个槽位类型可选的配件 id（配置为空且 autoAttachmentPool 时 = TaCZ 自动扫描） */
    public static List<String> attachments(String type) {
        reloadIfChanged();
        List<String> configured = ATTACH_BY_TYPE.get(type);
        if (configured != null && !configured.isEmpty()) return configured;
        if (autoAttachmentPool) return AttachmentIndex.byType().getOrDefault(type, List.of());
        return List.of();
    }

    /** 全部配件（扁平，给旧接口/调试用） */
    public static List<String> attachments() {
        reloadIfChanged();
        if (!ATTACH_FLAT.isEmpty()) return ATTACH_FLAT;
        List<String> out = new ArrayList<>();
        for (List<String> v : ATTACH_BY_TYPE.values()) out.addAll(v);
        if (!out.isEmpty()) return out;
        if (autoAttachmentPool) return AttachmentIndex.all();
        return List.of();
    }

    /** 界面上要显示的配件槽类型（配置写了哪些就显示哪些；都没写 = TaCZ 里有配件的类型） */
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

    /** 某个类型的显示名（瞄具/枪口/…） */
    public static String typeLabel(String type) {
        return AttachmentIndex.TYPE_LABEL.getOrDefault(type, type);
    }

    /** 默认配件：类型 → 配件 id */
    public static Map<String, String> defaultAttachments() {
        reloadIfChanged();
        return DEFAULT_ATTACHMENTS;
    }

    /** 没选也没默认时，是否自动上该类型的第一个配件 */
    public static boolean autoAttachFirst() {
        reloadIfChanged();
        return autoAttachFirst;
    }

    /** 配件池是不是「自动扫描」模式 */
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

    /** 玩家背包数量上限（预设） */
    private static int maxPresets = 5;

    public static int maxPresets() {
        reloadIfChanged();
        return maxPresets;
    }

    /** 配置里某个槽位写了哪些枪（未做皮肤过滤） */
    public static List<String> configured(int slot) {
        reloadIfChanged();
        if (slot < 0 || slot >= SLOT_COUNT || GUNS[slot] == null) return Collections.emptyList();
        return GUNS[slot];
    }

    public static Path file() {
        return FILE;
    }

    // ================== 加载 ==================

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
            allowEditInMatch = bool(obj, "allowEditInMatch", true);
            skinFolder = str(obj, "skinFolder", "kubejs/config/player_skin");
            skinPrefixMatch = bool(obj, "skinPrefixMatch", true);
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
                defaultMagazine = intVal(a, "magazine", intVal(a, "mag", 30));
                defaultReserve = intVal(a, "reserve", 300);
                defaultFire = str(a, "fire", str(a, "fireMode", "AUTO"));
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

    /** 支持 rifle/pistol/grenade/utility/knife、slot1..slot5、以及 "1".."5" */
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
                            // 单把枪的弹药 / 开火模式覆盖
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

    /** 调试/导出用 */
    public static String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.addProperty("clearOnStart", clearOnStart);
        o.addProperty("refillOnRespawn", refillOnRespawn);
        o.addProperty("allowEditInMatch", allowEditInMatch);
        o.addProperty("skinFolder", skinFolder);
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

    /** 强制标脏（/tdmloadout reload 用） */
    public static void markDirty() {
        lastStamp = Long.MIN_VALUE;
        lastCheckMs = 0L;
    }

    /** 配置文件是否已存在（诊断用） */
    public static boolean exists() {
        return Files.exists(FILE);
    }

    static {
        // 首次加载（顺便生成默认配置）
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
