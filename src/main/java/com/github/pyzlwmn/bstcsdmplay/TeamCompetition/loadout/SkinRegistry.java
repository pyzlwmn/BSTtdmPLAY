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
 * 皮肤系统联动：扫描 <游戏目录>/kubejs/config/player_skin/*.json
 *
 * 解析规则（宽松，什么都吃得下）：
 *  - 文件名（去掉 .json）= 玩家名 或 UUID
 *  - 顶层若有 player / name / playerName / uuid 字段，也当成这个玩家的身份
 *  - JSON 里任何「键 → true」都算拥有（"tacz:ak47": true）
 *  - 数组里的字符串（["tacz:ak47", ...]）也算拥有
 *  - {"gun":"tacz:ak47","skin":"tacz:ak47_fox","use":false} 这类对象：收集 gun/skin/id 字段
 *  - 值为 false 的键会被排除（false 覆盖 true）
 *  - 看起来像玩家名/uuid 的字符串不会被当成枪 id（避免误判）
 *
 * 枪 id 匹配：先精确匹配；若配了 skinPrefixMatch，则 "tacz:ak47" 也能匹配到
 * "tacz:ak47_xxx"（前缀 + 分隔符 _ # | . / -）——用于「枪 id 与皮肤 id 同前缀」的存档。
 */
public final class SkinRegistry {

    private static final Logger LOG = LoggerFactory.getLogger("BST-TDM-Loadout");

    /** key（小写）→ 拥有的 id 集合 */
    private static final Map<String, Set<String>> OWNED = new HashMap<>();
    /** key（小写）→ 来源文件（日志用） */
    private static final Map<String, String> SOURCE = new HashMap<>();

    private static long lastStamp = -1L;
    private static long lastCheckMs = 0L;
    private static boolean warnedMissing = false;

    private SkinRegistry() {
    }

    // ================== 对外 ==================

    /** 玩家在皮肤系统里登记过的所有 id */
    public static Set<String> ownedIds(ServerPlayer player) {
        reloadIfChanged();
        Set<String> out = new TreeSet<>();
        for (String key : keysOf(player)) {
            Set<String> s = OWNED.get(key);
            if (s != null) out.addAll(s);
        }
        return out;
    }

    /** 该玩家在 player_skin 里有没有对应文件 */
    public static boolean hasFile(ServerPlayer player) {
        reloadIfChanged();
        for (String key : keysOf(player)) {
            if (OWNED.containsKey(key)) return true;
        }
        return false;
    }

    /** 玩家是否拥有这个枪 id（返回命中的具体 id，没命中返回 null） */
    public static String matchId(ServerPlayer player, String gunId) {
        if (gunId == null || gunId.isBlank()) return null;
        String want = normalize(gunId);
        Set<String> owned = ownedIds(player);
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
        SOURCE.clear();

        if (!Files.isDirectory(dir)) {
            if (!warnedMissing) {
                warnedMissing = true;
                LOG.warn("[TDM·背包] 皮肤目录不存在：{}（UI 里将按配置显示全部枪；可在 loadout.json 改 skinFolder）", dir);
            }
            return;
        }

        int files = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path f : stream) {
                try {
                    String text = Files.readString(f, StandardCharsets.UTF_8);
                    JsonElement root = JsonParser.parseString(text);
                    Set<String> up = new TreeSet<>();
                    Set<String> down = new TreeSet<>();
                    collect(root, up, down);

                    Set<String> owned = new TreeSet<>(up);
                    owned.removeAll(down);

                    Set<String> keys = new HashSet<>();
                    String stem = f.getFileName().toString();
                    if (stem.toLowerCase().endsWith(".json")) stem = stem.substring(0, stem.length() - 5);
                    keys.add(stem.toLowerCase());
                    for (String k : identityFields(root)) keys.add(k.toLowerCase());

                    for (String key : keys) {
                        OWNED.put(key, owned);
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
        LOG.info("[TDM·背包] 皮肤目录扫描完成：{} 个文件 / {} 个玩家键（{}）", files, OWNED.size(), dir);
    }

    /** 收集 true / false 的 id */
    private static void collect(JsonElement el, Set<String> up, Set<String> down) {
        if (el == null) return;
        if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                String key = e.getKey();
                JsonElement v = e.getValue();
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

    /** 调试：某个玩家拥有的全部 id */
    public static String debugDump(ServerPlayer player) {
        Set<String> ids = ownedIds(player);
        if (ids.isEmpty()) return "（无：未找到该玩家的皮肤文件）";
        return String.join(", ", ids);
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
