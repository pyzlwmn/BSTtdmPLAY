package com.github.pyzlwmn.filtdm.TeamCompetition.loadout;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class SkinRegistry {

    private static final Logger LOG = LoggerFactory.getLogger("FIL-TDM-Loadout");

    private static final Map<String, Set<String>> OWNED = new HashMap<>();

    private static final Map<String, Map<Integer, Set<String>>> SLOT_OWNED = new HashMap<>();

    private static final Map<String, String> SOURCE = new HashMap<>();

    private static long lastStamp = -1L;
    private static long lastCheckMs = 0L;
    private static boolean warnedMissing = false;

    private SkinRegistry() {
    }


    public static Set<String> ownedIds(ServerPlayer player) {
        reloadIfChanged();
        Set<String> out = new TreeSet<>();
        for (String key : keysOf(player)) {
            Set<String> s = OWNED.get(key);
            if (s != null) out.addAll(s);
            Map<Integer, Set<String>> slots = SLOT_OWNED.get(key);
            if (slots != null) {
                for (Set<String> sub : slots.values()) out.addAll(sub);
            }
        }
        return out;
    }

    public static Set<String> ownedIds(ServerPlayer player, int slot) {

        if (!LoadoutConfig.skinSlotFilter()) return ownedIds(player);
        reloadIfChanged();
        Set<String> out = new TreeSet<>();
        for (String key : keysOf(player)) {
            Set<String> flat = OWNED.get(key);
            if (flat != null) out.addAll(flat);
            Set<String> grouped = slotIds(key, slot);
            if (grouped != null) out.addAll(grouped);
        }
        return out;
    }

    public static Set<String> slotOnlyIds(ServerPlayer player, int slot) {
        reloadIfChanged();
        Set<String> out = new TreeSet<>();
        for (String key : keysOf(player)) {
            Set<String> grouped = slotIds(key, slot);
            if (grouped != null) out.addAll(grouped);
        }
        return out;
    }

    public static boolean hasSlotData(ServerPlayer player) {
        reloadIfChanged();
        for (String key : keysOf(player)) {
            if (SLOT_OWNED.containsKey(key)) return true;
        }
        return false;
    }

    public static boolean hasFile(ServerPlayer player) {
        reloadIfChanged();
        for (String key : keysOf(player)) {
            if (OWNED.containsKey(key) || SLOT_OWNED.containsKey(key)) return true;
        }
        return false;
    }

    public static String matchId(ServerPlayer player, String gunId) {
        return match(gunId, ownedIds(player));
    }

    public static String matchId(ServerPlayer player, String gunId, int slot) {
        return match(gunId, ownedIds(player, slot));
    }

    private static String match(String gunId, Set<String> owned) {
        if (gunId == null || gunId.isBlank() || owned.isEmpty()) return null;
        String want = normalize(gunId);

        for (String id : owned) {
            if (normalize(id).equals(want)) return id;
        }

        if (LoadoutConfig.skinPrefixMatch()) {
            for (String id : owned) {
                String n = normalize(id);
                if (!n.startsWith(want) || n.length() == want.length()) continue;
                char sep = n.charAt(want.length());
                if (sep == '_' || sep == '#' || sep == '|' || sep == '.' || sep == '/' || sep == '-') {
                    return id;
                }
            }
        }
        return null;
    }

    public static void invalidate() {
        lastStamp = -1L;
        lastCheckMs = 0L;
    }

    private static void reloadIfChanged() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < 2000L && lastStamp >= 0L) return;
        lastCheckMs = now;
        Path dir = skinDir();
        long stamp = dirStamp(dir);
        if (stamp == lastStamp) return;
        lastStamp = stamp;
        scan(dir);
    }

    private static Path skinDir() {
        String folder = LoadoutConfig.skinFolder();
        if (folder == null || folder.isBlank()) folder = "kubejs/config/player_skin";
        Path p = Path.of(folder);
        return p.isAbsolute() ? p : FMLPaths.GAMEDIR.get().resolve(folder);
    }

    private static long dirStamp(Path dir) {
        try {
            if (!Files.isDirectory(dir)) return -2L;
            long stamp = 7L;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path f : stream) {
                    stamp += Files.getLastModifiedTime(f).toMillis() + Files.size(f);
                }
            }
            return stamp;
        } catch (Exception e) {
            return -3L;
        }
    }

    private static synchronized void scan(Path dir) {
        OWNED.clear();
        SLOT_OWNED.clear();
        SOURCE.clear();

        if (!Files.isDirectory(dir)) {
            if (!warnedMissing) {
                warnedMissing = true;
                LOG.warn("[TDM·背包] 皮肤目录不存在：{}（UI 里将按配置显示全部枪；可在 loadout.json 改 skinFolder）", dir);
            }
            return;
        }

        int files = 0;
        int groupedFiles = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path f : stream) {
                if (f.getFileName().toString().startsWith("_")) continue;
                try {
                    String text = Files.readString(f, StandardCharsets.UTF_8);
                    JsonElement root = JsonParser.parseString(text);

                    Set<String> up = new TreeSet<>();
                    Set<String> down = new TreeSet<>();
                    collect(root, up, down);
                    Set<String> owned = new TreeSet<>(up);
                    owned.removeAll(down);

                    Map<Integer, Set<String>> slots = collectSlots(root);
                    if (!slots.isEmpty()) groupedFiles++;

                    Set<String> keys = new HashSet<>();
                    String stem = f.getFileName().toString();
                    if (stem.toLowerCase().endsWith(".json")) stem = stem.substring(0, stem.length() - 5);
                    keys.add(stem.toLowerCase());
                    for (String k : identityFields(root)) keys.add(k.toLowerCase());

                    for (String key : keys) {
                        OWNED.put(key, owned);
                        if (!slots.isEmpty()) SLOT_OWNED.put(key, slots);
                        SOURCE.put(key, f.getFileName().toString());
                    }
                    files++;
                } catch (Exception e) {
                    LOG.warn("[TDM·背包] 皮肤文件解析失败 {}：{}", f.getFileName(), e.toString());
                }
            }
        } catch (Exception e) {
            LOG.error("[TDM·背包] 扫描皮肤目录失败：{}", e.toString());
            return;
        }
        LOG.info("[TDM·背包] 皮肤目录扫描完成：{} 个文件（其中 {} 个带槽位分组）/ {} 个玩家键（{}）",
                files, groupedFiles, OWNED.size(), dir);
    }

    private static Set<String> slotIds(String playerKey, int slot) {
        Map<Integer, Set<String>> slots = SLOT_OWNED.get(playerKey);
        return slots == null ? null : slots.get(slot);
    }

    private static void collect(JsonElement el, Set<String> up, Set<String> down) {
        if (el == null) return;
        if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                String key = e.getKey();
                JsonElement v = e.getValue();
                if (isSlotGroup(key, v)) continue;
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
                    (v.getAsBoolean() ? up : down).add(key);
                } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                    if (isIdKey(key)) {
                        String s = v.getAsString().trim();
                        if (!s.isEmpty()) up.add(s);
                    }
                } else {
                    collect(v, up, down);
                }
            }
        } else if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    String s = child.getAsString().trim();
                    if (looksLikeId(s)) up.add(s);
                } else {
                    collect(child, up, down);
                }
            }
        }
    }

    private static Map<Integer, Set<String>> collectSlots(JsonElement el) {
        Map<Integer, Set<String>> out = new HashMap<>();
        walkSlots(el, out);
        return out;
    }

    private static void walkSlots(JsonElement el, Map<Integer, Set<String>> out) {
        if (el == null || !el.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            String key = e.getKey();
            JsonElement v = e.getValue();
            int slot = isSlotGroup(key, v) ? slotIndexOf(key) : -1;
            if (slot >= 0) {
                Set<String> up = new TreeSet<>();
                Set<String> down = new TreeSet<>();
                collect(v, up, down);
                up.removeAll(down);
                if (!up.isEmpty()) {
                    out.computeIfAbsent(slot, k -> new TreeSet<>()).addAll(up);
                }
            } else if (v.isJsonObject()) {
                walkSlots(v, out);
            } else if (v.isJsonArray()) {
                for (JsonElement child : v.getAsJsonArray()) walkSlots(child, out);
            }
        }
    }

    private static boolean isSlotGroup(String key, JsonElement value) {
        if (key == null || value == null) return false;
        if (!value.isJsonArray() && !value.isJsonObject()) return false;
        return slotIndexOf(key) >= 0;
    }

    public static int slotIndexOf(String key) {
        if (key == null) return -1;
        String k = key.trim().toLowerCase();
        for (int i = 0; i < LoadoutConfig.SLOT_NAMES.length; i++) {
            String n = LoadoutConfig.SLOT_NAMES[i].toLowerCase();
            if (k.equals(n) || k.equals("slot" + (i + 1)) || k.equals(String.valueOf(i + 1))) return i;
        }
        switch (k) {

            case "ar": case "assault": case "assaultrifle": case "assault_rifle": case "rifles":
            case "smg": case "shotgun": case "sniper": case "lmg": case "machinegun": case "primary":
                return 0;

            case "handgun": case "pistols": case "secondary": case "sidearm":
                return 1;

            case "grenades": case "throwable": case "throwables": case "explosive": case "bomb":
                return 2;

            case "items": case "gear": case "misc": case "props": case "tool": case "tools":
            case "utilities":
                return 3;

            case "melee": case "knifes": case "knives": case "blade": case "sword": case "swords":
                return 4;
            default:
                return -1;
        }
    }

    private static boolean isIdKey(String key) {
        String k = key.toLowerCase();
        return k.equals("gun") || k.equals("gunid") || k.equals("id") || k.equals("item") || k.equals("skin")
                || k.equals("skinid") || k.equals("weapon");
    }

    private static boolean looksLikeId(String s) {
        if (s.isEmpty() || s.length() > 120 || s.contains(" ")) return false;
        if (s.indexOf(':') > 0) return true;
        return s.matches("[a-zA-Z0-9_./\\-]+") && !s.matches("[A-Za-z0-9_]{3,16}");
    }

    private static Set<String> identityFields(JsonElement root) {
        Set<String> out = new HashSet<>();
        if (root == null || !root.isJsonObject()) return out;
        JsonObject o = root.getAsJsonObject();
        for (String k : new String[]{"player", "name", "playerName", "player_name", "uuid", "玩家"}) {
            if (o.has(k) && o.get(k).isJsonPrimitive()) {
                try {
                    String v = o.get(k).getAsString().trim();
                    if (!v.isEmpty()) out.add(v);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return out;
    }

    private static String[] keysOf(ServerPlayer player) {
        String name = player.getGameProfile().getName().toLowerCase();
        String uuid = player.getUUID().toString().toLowerCase();
        return new String[]{name, uuid, uuid.replace("-", "")};
    }

    private static String normalize(String id) {
        String s = id.trim().toLowerCase();
        int cut = s.indexOf('|');
        if (cut < 0) cut = s.indexOf('#');
        return cut > 0 ? s.substring(0, cut) : s;
    }


    public static String debugDump(ServerPlayer player) {
        Set<String> ids = ownedIds(player);
        if (ids.isEmpty()) return "（无：未找到该玩家的皮肤文件）";
        StringBuilder sb = new StringBuilder(String.join(", ", ids));
        StringBuilder per = new StringBuilder();
        for (int i = 0; i < LoadoutConfig.SLOT_COUNT; i++) {
            int n = slotOnlyIds(player, i).size();
            if (n > 0) {
                if (per.length() > 0) per.append(" ｜ ");
                per.append(LoadoutConfig.SLOT_NAMES[i]).append('=').append(n);
            }
        }
        if (per.length() > 0) sb.append("\n§7槽位分组：§f").append(per);
        return sb.toString();
    }

    public static Map<String, Set<String>> snapshot() {
        reloadIfChanged();
        return Collections.unmodifiableMap(OWNED);
    }

    public static String sourceOf(ServerPlayer player) {
        reloadIfChanged();
        for (String key : keysOf(player)) {
            String s = SOURCE.get(key);
            if (s != null) return s;
        }
        return null;
    }
}
