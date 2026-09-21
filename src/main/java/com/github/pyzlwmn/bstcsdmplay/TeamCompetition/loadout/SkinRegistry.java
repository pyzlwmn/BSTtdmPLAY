package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout;

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

/**
 * 皮肤系统联动：扫描 &lt;游戏目录&gt;/kubejs/config/player_skin/*.json
 *
 * <p>支持两种写法（混着写也行）：</p>
 *
 * <p><b>① 槽位分组（新版，KJS 皮肤系统现在产出的格式）：</b></p>
 * <pre>
 * {
 *   "player": "pyzlwmn4031",
 *   "uuid": "43446ccb-c100-4477-80d7-f2f0b9b54dbc",
 *   "skins": {
 *     "rifle":  ["ffgspf:ak47_erximofu"],
 *     "pistol": [],
 *     "knife":  []
 *   }
 * }
 * </pre>
 * — 每个槽位（rifle/pistol/grenade/utility/knife，也认 slot1..5 / 1..5 和常见同义词）
 *   底下的 id 只属于这个槽位：步枪的枪皮不会出现在手枪槽里。
 *
 * <p><b>② 扁平写法（旧版，仍然兼容）：</b></p>
 * <pre>
 * {
 *   "player": "Steve",
 *   "skins": { "tacz:ak47_skin_fox": true, "tacz:deagle_skin_gold": false }
 * }
 * </pre>
 * — 这种没有槽位信息的 id 视为「全槽位通用」（和以前行为一致）。
 *
 * <p>宽松解析规则：</p>
 * <ul>
 *   <li>文件名（去掉 .json）= 玩家名 或 UUID；顶层 player / name / playerName / uuid 字段也算身份</li>
 *   <li>任何「键 → true」都算拥有；值 false = 明确没有（false 会盖掉同文件的 true）</li>
 *   <li>数组里的字符串也算拥有；{"gun":"tacz:ak47","skin":"..."} 这类对象取 gun/skin/id 字段</li>
 *   <li>看起来像玩家名（3~16 位字母数字下划线）的字符串不会被误当成枪 id</li>
 * </ul>
 *
 * <p>枪 id 匹配：先精确匹配；若配了 skinPrefixMatch，则 "tacz:ak47" 也能匹配到
 * "tacz:ak47_xxx"（前缀 + 分隔符 _ # | . / -）。</p>
 */
public final class SkinRegistry {

    private static final Logger LOG = LoggerFactory.getLogger("BST-TDM-Loadout");

    /** 玩家键（小写）→ 不带槽位信息的 id（全槽位通用） */
    private static final Map<String, Set<String>> OWNED = new HashMap<>();
    /** 玩家键（小写）→ 槽位索引 → 该槽位的 id */
    private static final Map<String, Map<Integer, Set<String>>> SLOT_OWNED = new HashMap<>();
    /** key（小写）→ 来源文件（日志用） */
    private static final Map<String, String> SOURCE = new HashMap<>();

    private static long lastStamp = -1L;
    private static long lastCheckMs = 0L;
    private static boolean warnedMissing = false;

    private SkinRegistry() {
    }

    // ================== 对外：读取 ==================

    /** 玩家在皮肤系统里登记过的所有 id（不限槽位） */
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

    /** 玩家某个槽位可用的 id（通用 id + 该槽位分组里的 id） */
    public static Set<String> ownedIds(ServerPlayer player, int slot) {
        // 关掉槽位过滤 → 退回老行为（所有 id 全槽位通用）
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

    /** 玩家在某个槽位分组里**显式列出来**的 id（不含通用 id，调试/统计用） */
    public static Set<String> slotOnlyIds(ServerPlayer player, int slot) {
        reloadIfChanged();
        Set<String> out = new TreeSet<>();
        for (String key : keysOf(player)) {
            Set<String> grouped = slotIds(key, slot);
            if (grouped != null) out.addAll(grouped);
        }
        return out;
    }

    /** 该玩家的皮肤文件里有没有槽位分组信息 */
    public static boolean hasSlotData(ServerPlayer player) {
        reloadIfChanged();
        for (String key : keysOf(player)) {
            if (SLOT_OWNED.containsKey(key)) return true;
        }
        return false;
    }

    /** 该玩家在 player_skin 里有没有对应文件 */
    public static boolean hasFile(ServerPlayer player) {
        reloadIfChanged();
        for (String key : keysOf(player)) {
            if (OWNED.containsKey(key) || SLOT_OWNED.containsKey(key)) return true;
        }
        return false;
    }

    /** 玩家是否拥有这个枪 id（返回命中的具体 id，没命中返回 null） */
    public static String matchId(ServerPlayer player, String gunId) {
        return match(gunId, ownedIds(player));
    }

    /** 玩家是否拥有这个枪 id（限定槽位版本） */
    public static String matchId(ServerPlayer player, String gunId, int slot) {
        return match(gunId, ownedIds(player, slot));
    }

    private static String match(String gunId, Set<String> owned) {
        if (gunId == null || gunId.isBlank() || owned.isEmpty()) return null;
        String want = normalize(gunId);
        // ① 精确（含 gun|skin / gun#skin 写法：只要枪匹配就算拥有，皮肤另说）
        for (String id : owned) {
            if (normalize(id).equals(want)) return id;
        }
        // ② 前缀（"tacz:ak47" vs "tacz:ak47_skin_fox"）
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

    // ================== 扫描 ==================

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
                if (f.getFileName().toString().startsWith("_")) continue; // _catalog.json 之类跳过
                try {
                    String text = Files.readString(f, StandardCharsets.UTF_8);
                    JsonElement root = JsonParser.parseString(text);

                    // ① 不带槽位的 id（全槽位通用）
                    Set<String> up = new TreeSet<>();
                    Set<String> down = new TreeSet<>();
                    collect(root, up, down);
                    Set<String> owned = new TreeSet<>(up);
                    owned.removeAll(down);

                    // ② 槽位分组（skins.rifle = [...]）
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

    /**
     * 收集不带槽位信息的 id（递归到底；遇到「槽位分组对象」不下钻，交给 collectSlots 处理）
     */
    private static void collect(JsonElement el, Set<String> up, Set<String> down) {
        if (el == null) return;
        if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                String key = e.getKey();
                JsonElement v = e.getValue();
                if (isSlotGroup(key, v)) continue; // 槽位分组，跳过
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

    /**
     * 收集槽位分组：任何「键 = 槽位名」且值是数组/对象的节点，值里的 id 归到该槽位。
     * 支持 skins.rifle = ["id"]、skins.rifle = {"id": true}、甚至 {"rifle": {"id": true}} 嵌套写法。
     */
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
                walkSlots(v, out); // 继续往里找（比如 skins 本身、或 {"slots": {...}}）
            } else if (v.isJsonArray()) {
                for (JsonElement child : v.getAsJsonArray()) walkSlots(child, out);
            }
        }
    }

    /** 这个键看起来是槽位分组吗（值是数组/对象，且键是槽位名） */
    private static boolean isSlotGroup(String key, JsonElement value) {
        if (key == null || value == null) return false;
        if (!value.isJsonArray() && !value.isJsonObject()) return false;
        return slotIndexOf(key) >= 0;
    }

    /** 槽位名 → 槽位索引，认不出来返回 -1（= 通用 id） */
    public static int slotIndexOf(String key) {
        if (key == null) return -1;
        String k = key.trim().toLowerCase();
        for (int i = 0; i < LoadoutConfig.SLOT_NAMES.length; i++) {
            String n = LoadoutConfig.SLOT_NAMES[i].toLowerCase();
            if (k.equals(n) || k.equals("slot" + (i + 1)) || k.equals(String.valueOf(i + 1))) return i;
        }
        switch (k) {
            // 步枪类
            case "ar": case "assault": case "assaultrifle": case "assault_rifle": case "rifles":
            case "smg": case "shotgun": case "sniper": case "lmg": case "machinegun": case "primary":
                return 0;
            // 手枪类
            case "handgun": case "pistols": case "secondary": case "sidearm":
                return 1;
            // 投掷物
            case "grenades": case "throwable": case "throwables": case "explosive": case "bomb":
                return 2;
            // 道具
            case "items": case "gear": case "misc": case "props": case "tool": case "tools":
            case "utilities":
                return 3;
            // 近战
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

    /** 像资源 id（含 : 且无空格）或者纯英文数字下划线且不太像玩家名 */
    private static boolean looksLikeId(String s) {
        if (s.isEmpty() || s.length() > 120 || s.contains(" ")) return false;
        if (s.indexOf(':') > 0) return true;
        return s.matches("[a-zA-Z0-9_./\\-]+") && !s.matches("[A-Za-z0-9_]{3,16}");
    }

    /** 顶层身份字段（player / name / playerName / uuid） */
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

    // ================== 调试 ==================

    /** 调试：某个玩家拥有的全部 id（含槽位分组统计） */
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
