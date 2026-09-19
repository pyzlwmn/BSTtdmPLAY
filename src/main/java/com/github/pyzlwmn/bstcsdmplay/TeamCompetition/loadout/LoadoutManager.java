package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpAttachmentS2CPacket;
import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.net.TcpLoadoutS2CPacket;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 背包系统主逻辑（v24：多背包）
 *
 *  - 每个玩家可以有 N 个「背包」（预设），每个背包有自定义名字，槽位结构一致（槽位1 步枪 … 槽位5 刀）
 *  - 每个背包独立保存：每槽选了什么枪 + 每个枪位各配件槽装了什么
 *  - 对局开始 / 重生 / 中途加入 → 按**当前背包**发枪
 *  - 可选枪列表 = 配置文件槽位内容 ∩ 玩家 player_skin 里为 true 的 id
 */
public final class LoadoutManager {

    private static final Logger LOG = LoggerFactory.getLogger("BST-TDM-Loadout");

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("bstcsdmplay").resolve("player_loadouts.json");

    /** 一个背包预设 */
    public static final class Preset {
        public final String id;
        public String name;
        /** 槽位名 → 枪 id */
        public final Map<String, String> picks = new ConcurrentHashMap<>();
        /** 槽位名 → 配件槽类型 → 配件 id */
        public final Map<String, Map<String, String>> attach = new ConcurrentHashMap<>();
        /** 槽位名 → 改装后的枪的 SNBT（v25：在 TaCZ 改装页改完存下来的成品，开局直接发这把） */
        public final Map<String, String> customNbt = new ConcurrentHashMap<>();

        public Preset(String id, String name) {
            this.id = id == null || id.isBlank() ? newId() : id;
            this.name = name == null || name.isBlank() ? "背包" : name;
        }
    }

    /** uuid → 背包列表 */
    private static final Map<UUID, List<Preset>> PRESETS = new ConcurrentHashMap<>();
    /** uuid → 当前背包 id */
    private static final Map<UUID, String> ACTIVE = new ConcurrentHashMap<>();

    private static final AtomicInteger ID_SEQ = new AtomicInteger(1);
    private static boolean loaded = false;

    private LoadoutManager() {
    }

    private static String newId() {
        return "p" + ID_SEQ.getAndIncrement() + "_" + Integer.toHexString((int) (System.currentTimeMillis() & 0xFFFFFF));
    }

    // ================== 背包管理 ==================

    /** 该玩家的全部背包（至少一个） */
    public static List<Preset> presets(ServerPlayer player) {
        ensureLoaded();
        List<Preset> list = PRESETS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>(List.of(new Preset(null, "背包1"))));
        if (list.isEmpty()) list.add(new Preset(null, "背包1"));
        return list;
    }

    /** 当前背包 */
    public static Preset active(ServerPlayer player) {
        List<Preset> list = presets(player);
        String id = ACTIVE.get(player.getUUID());
        if (id != null) {
            for (Preset p : list) {
                if (p.id.equals(id)) return p;
            }
        }
        Preset first = list.get(0);
        ACTIVE.put(player.getUUID(), first.id);
        return first;
    }

    public static Preset byId(ServerPlayer player, String id) {
        for (Preset p : presets(player)) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    public static int maxPresets() {
        return LoadoutConfig.maxPresets();
    }

    /** 新建背包（名字可空 → 自动「背包N」） */
    public static Preset createPreset(ServerPlayer player, String name) {
        List<Preset> list = presets(player);
        if (list.size() >= maxPresets()) return null;
        String n = name == null || name.isBlank() ? ("背包" + (list.size() + 1)) : name.trim();
        Preset p = new Preset(null, n.length() > 16 ? n.substring(0, 16) : n);
        list.add(p);
        ACTIVE.put(player.getUUID(), p.id);
        save();
        LOG.info("[TDM·背包] {} 新建背包「{}」（共 {} 个）", player.getName().getString(), p.name, list.size());
        return p;
    }

    public static boolean renamePreset(ServerPlayer player, String id, String name) {
        Preset p = byId(player, id);
        if (p == null || name == null || name.isBlank()) return false;
        String n = name.trim();
        p.name = n.length() > 16 ? n.substring(0, 16) : n;
        save();
        LOG.info("[TDM·背包] {} 背包改名 →「{}」", player.getName().getString(), p.name);
        return true;
    }

    public static boolean deletePreset(ServerPlayer player, String id) {
        List<Preset> list = presets(player);
        if (list.size() <= 1) return false;
        Preset p = byId(player, id);
        if (p == null) return false;
        list.remove(p);
        if (p.id.equals(ACTIVE.get(player.getUUID()))) {
            ACTIVE.put(player.getUUID(), list.get(0).id);
        }
        save();
        LOG.info("[TDM·背包] {} 删除背包「{}」（剩 {} 个）", player.getName().getString(), p.name, list.size());
        return true;
    }

    public static boolean switchPreset(ServerPlayer player, String id) {
        if (byId(player, id) == null) return false;
        ACTIVE.put(player.getUUID(), id);
        save();
        Preset p = active(player);
        LOG.info("[TDM·背包] {} 切到背包「{}」", player.getName().getString(), p.name);
        return true;
    }

    // ================== 选择 ==================

    /** 玩家某个槽位可以选的 id 列表（配置 ∩ 皮肤） */
    public static List<String> available(ServerPlayer player, int slot) {
        List<String> all = LoadoutConfig.configured(slot);
        if (all.isEmpty()) return List.of();

        boolean hasSkinData = SkinRegistry.hasFile(player);
        java.util.Set<String> owned = hasSkinData ? SkinRegistry.ownedIds(player) : java.util.Set.of();

        List<String> out = new ArrayList<>();
        for (String raw : all) {
            String base = baseGun(raw);
            boolean skinUnlocked = hasSkinData && SkinRegistry.matchId(player, base) != null;
            if (LoadoutConfig.showBaseGuns() || skinUnlocked
                    || (!hasSkinData && LoadoutConfig.showAllWhenNoSkinFile())) {
                if (!out.contains(raw)) out.add(raw);
            }
            if (LoadoutConfig.skinEntries() && hasSkinData) {
                for (String skin : skinsFor(base, owned)) {
                    String entry = base + "|" + skin;
                    if (!out.contains(entry)) out.add(entry);
                }
            }
        }
        return out;
    }

    private static String baseGun(String entry) {
        int cut = entry.indexOf('|');
        if (cut < 0) cut = entry.indexOf('#');
        return cut > 0 ? entry.substring(0, cut).trim() : entry.trim();
    }

    private static List<String> skinsFor(String gunId, java.util.Set<String> owned) {
        List<String> out = new ArrayList<>();
        String want = gunId.toLowerCase();
        for (String s : owned) {
            String n = s.toLowerCase();
            if (n.equals(want) || !n.startsWith(want) || n.length() == want.length()) continue;
            char sep = n.charAt(want.length());
            if (sep == '_' || sep == '#' || sep == '|' || sep == '.' || sep == '/' || sep == '-') out.add(s);
        }
        return out;
    }

    /** 当前背包里某槽位选中的枪（无效则回退第一个可选项） */
    public static String pick(ServerPlayer player, int slot) {
        Preset p = active(player);
        List<String> options = available(player, slot);
        if (options.isEmpty()) return null;
        String cur = p.picks.get(LoadoutConfig.SLOT_NAMES[slot]);
        if (cur != null && options.contains(cur)) return cur;
        String fallback = options.get(0);
        p.picks.put(LoadoutConfig.SLOT_NAMES[slot], fallback);
        return fallback;
    }

    /** UI 点击选择：校验合法性 → 保存到当前背包 → 对局中立刻换枪 */
    public static boolean select(ServerPlayer player, int slot, String gunId) {
        if (slot < 0 || slot >= LoadoutConfig.SLOT_COUNT) return false;
        List<String> options = available(player, slot);
        if (!options.contains(gunId)) {
            LOG.info("[TDM·背包] {} 想选 {}（槽位{}）但不在可选项里（{}）",
                    player.getName().getString(), gunId, slot + 1, options.size());
            return false;
        }
        active(player).picks.put(LoadoutConfig.SLOT_NAMES[slot], gunId);
        save();
        LOG.info("[TDM·背包] {} [{}] 槽位{} 选择 {}", player.getName().getString(), active(player).name, slot + 1, gunId);
        return true;
    }

    // ================== 发枪 ==================

    public static void giveLoadout(ServerPlayer player, boolean clearInventory) {
        if (!LoadoutConfig.enabled()) return;
        if (clearInventory) clearInventory(player);

        int given = 0;
        for (int slot = 0; slot < LoadoutConfig.SLOT_COUNT; slot++) {
            ItemStack stack = build(player, slot);
            if (stack.isEmpty()) continue;
            player.getInventory().setItem(slot, stack);
            given++;
        }
        for (int i = LoadoutConfig.SLOT_COUNT; i < 9; i++) {
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        try {
            player.getInventory().selected = 0;
            player.connection.send(new ClientboundSetCarriedItemPacket(0));
        } catch (RuntimeException ignored) {
        }
        sync(player);
        LOG.info("[TDM·背包] {} 已发放背包「{}」装备：{} 件（槽位 {}）",
                player.getName().getString(), active(player).name, given, describePicks(player));
    }

    /** 某个槽位该发的物品（优先用改装存下的成品 SNBT，否则按配置 id 重建） */
    public static ItemStack build(ServerPlayer player, int slot) {
        String slotName = LoadoutConfig.SLOT_NAMES[slot];
        String custom = active(player).customNbt.get(slotName);
        if (custom != null && !custom.isBlank()) {
            ItemStack saved = GunItemFactory.fromSnbt(custom);
            if (!saved.isEmpty()) {
                // 成品枪：补满弹药就行（配件/皮肤都在 NBT 里）
                int[] ammo = LoadoutConfig.ammoFor(GunItemFactory.gunIdOf(saved) == null ? "" : GunItemFactory.gunIdOf(saved));
                GunItemFactory.fillAmmo(saved, ammo[0], ammo[1]);
                return saved;
            }
            LOG.warn("[TDM·背包] 槽位{} 存下的成品枪解析失败，回退按配置重建", slot + 1);
        }
        String id = pick(player, slot);
        if (id == null) return ItemStack.EMPTY;
        String base = baseGun(id);
        int[] ammo = LoadoutConfig.ammoFor(base);
        Map<String, String> atts = effectiveAttachments(player, slotName);
        ItemStack stack = GunItemFactory.create(id, ammo[0], ammo[1], LoadoutConfig.fireFor(base), atts);
        if (stack.isEmpty()) {
            LOG.warn("[TDM·背包] 槽位{} 的 {} 生成失败（id 写错 / 模组没装）", slot + 1, id);
        }
        return stack;
    }

    // ================== 改装成品（v25） ==================

    /** 存下改装后的成品枪 */
    public static boolean saveCustomItem(ServerPlayer player, String slotName, String snbt) {
        Preset p = active(player);
        if (snbt == null || snbt.isBlank()) {
            p.customNbt.remove(slotName);
        } else {
            p.customNbt.put(slotName, snbt);
        }
        save();
        LOG.info("[TDM·背包] {} [{}] 枪位 {} 成品枪已{}", player.getName().getString(), p.name, slotName,
                snbt == null || snbt.isBlank() ? "清除" : "保存");
        return true;
    }

    /** 是否已有改装成品 */
    public static boolean hasCustomItem(ServerPlayer player, String slotName) {
        String s = active(player).customNbt.get(slotName);
        return s != null && !s.isBlank();
    }

    public static void applySlot(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= LoadoutConfig.SLOT_COUNT) return;
        player.getInventory().setItem(slot, build(player, slot));
        sync(player);
    }

    /** 补满弹药 */
    public static void refillAmmo(ServerPlayer player) {
        if (!LoadoutConfig.enabled()) return;
        int n = 0;
        for (int slot = 0; slot < LoadoutConfig.SLOT_COUNT; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            String id = pick(player, slot);
            String base = id == null ? GunItemFactory.gunIdOf(stack) : baseGun(id);
            int[] ammo = base == null ? new int[]{-1, -1} : LoadoutConfig.ammoFor(base);
            GunItemFactory.fillAmmo(stack, ammo[0], ammo[1]);
            n++;
        }
        if (n > 0) sync(player);
    }

    public static boolean emptyLoadout(ServerPlayer player) {
        for (int slot = 0; slot < LoadoutConfig.SLOT_COUNT; slot++) {
            if (!player.getInventory().getItem(slot).isEmpty()) return false;
        }
        return true;
    }

    private static void clearInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        player.getInventory().offhand.set(0, ItemStack.EMPTY);
    }

    private static void sync(ServerPlayer player) {
        try {
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
        } catch (RuntimeException ignored) {
        }
    }

    private static String describePicks(ServerPlayer player) {
        StringBuilder sb = new StringBuilder();
        for (int slot = 0; slot < LoadoutConfig.SLOT_COUNT; slot++) {
            String id = pick(player, slot);
            sb.append(slot + 1).append('=').append(id == null ? "空" : id).append(' ');
        }
        return sb.toString().trim();
    }

    // ================== 配件 ==================

    public static Map<String, String> attachmentsOf(ServerPlayer player, String slotName) {
        return active(player).attach.getOrDefault(slotName, Map.of());
    }

    public static String attachmentFor(ServerPlayer player, String slotName, String type) {
        return attachmentsOf(player, slotName).get(type);
    }

    public static List<String> attachmentIdsForSlot(ServerPlayer player, int slot) {
        return new ArrayList<>(effectiveAttachments(player, LoadoutConfig.SLOT_NAMES[slot]).values());
    }

    public static Map<String, String> effectiveAttachments(ServerPlayer player, String slotName) {
        Map<String, String> sel = new LinkedHashMap<>(attachmentsOf(player, slotName));
        Map<String, String> defaults = LoadoutConfig.defaultAttachments();
        for (String type : LoadoutConfig.attachmentTypes()) {
            if (sel.containsKey(type)) continue;
            String def = defaults.get(type);
            if (def == null || def.isBlank()) {
                if (!LoadoutConfig.autoAttachFirst()) continue;
                List<String> opts = LoadoutConfig.attachments(type);
                if (opts.isEmpty()) continue;
                def = opts.get(0);
            }
            sel.put(type, def);
        }
        return sel;
    }

    public static boolean selectAttachment(ServerPlayer player, String slotName, String type, String id) {
        if (slotName == null || type == null) return false;
        slotName = slotName.toLowerCase();
        type = type.toUpperCase();

        List<String> allowed = LoadoutConfig.attachments(type);
        Preset p = active(player);
        Map<String, String> byType = new ConcurrentHashMap<>(p.attach.getOrDefault(slotName, Map.of()));

        if (id == null || id.isBlank()) {
            byType.remove(type);
        } else {
            if (!allowed.contains(id)) {
                LOG.info("[TDM·背包] {} 想装 {} 到 {}（{}），但不在可选列表里",
                        player.getName().getString(), id, slotName, type);
                return false;
            }
            if (byType.size() >= LoadoutConfig.attachmentLimit() && !byType.containsKey(type)) return false;
            byType.put(type, id);
        }
        p.attach.put(slotName, byType);
        save();
        LOG.info("[TDM·背包] {} [{}] 枪位 {} 配件 {} = {}", player.getName().getString(), p.name, slotName, type,
                id == null || id.isBlank() ? "（卸下）" : id);
        return true;
    }

    // ================== 网络包 ==================

    public static TcpLoadoutS2CPacket buildPacket(ServerPlayer player, boolean inMatch, boolean canEdit) {
        return buildPacket(player, inMatch, canEdit, 0);
    }

    /** extraFlags：额外的 FLAG（如 FLAG_OPEN / FLAG_REFIT）；传 0 表示不带 */
    public static TcpLoadoutS2CPacket buildPacket(ServerPlayer player, boolean inMatch, boolean canEdit, int extraFlags) {
        List<TcpLoadoutS2CPacket.SlotData> slots = new ArrayList<>();
        for (int slot = 0; slot < LoadoutConfig.SLOT_COUNT; slot++) {
            slots.add(new TcpLoadoutS2CPacket.SlotData(
                    slot, LoadoutConfig.SLOT_LABEL[slot], pick(player, slot), available(player, slot)));
        }
        List<TcpLoadoutS2CPacket.PresetData> presets = new ArrayList<>();
        Preset act = active(player);
        for (Preset p : presets(player)) {
            presets.add(new TcpLoadoutS2CPacket.PresetData(p.id, p.name, p.id.equals(act.id)));
        }
        int flags = 0;
        if (LoadoutConfig.enabled()) flags |= TcpLoadoutS2CPacket.FLAG_ENABLED;
        if (inMatch) flags |= TcpLoadoutS2CPacket.FLAG_IN_MATCH;
        if (canEdit) flags |= TcpLoadoutS2CPacket.FLAG_CAN_EDIT;
        flags |= extraFlags;
        return new TcpLoadoutS2CPacket(flags, slots, presets, maxPresets());
    }

    public static TcpAttachmentS2CPacket buildAttachmentPacket(ServerPlayer player, boolean canEdit, boolean open) {
        List<TcpAttachmentS2CPacket.GunEntry> guns = new ArrayList<>();
        for (int slot = 0; slot < LoadoutConfig.SLOT_COUNT; slot++) {
            String gunId = pick(player, slot);
            if (gunId == null) continue;
            String slotName = LoadoutConfig.SLOT_NAMES[slot];
            String label = slot < LoadoutConfig.SLOT_LABEL.length
                    ? LoadoutConfig.SLOT_LABEL[slot] + "（" + gunId + "）" : gunId;
            // ★ v28：只列「这把枪真能装」的配件（装不上的不显示）
            ItemStack gunStack = build(player, slot);
            List<TcpAttachmentS2CPacket.TypeEntry> types = new ArrayList<>();
            if (!gunStack.isEmpty()) {
                Map<String, String> eff = effectiveAttachments(player, slotName);
                for (String type : LoadoutConfig.attachmentTypes()) {
                    if (!GunItemFactory.allowsType(gunStack, type)) continue;
                    List<String> options = new ArrayList<>();
                    for (String id : LoadoutConfig.attachments(type)) {
                        if (GunItemFactory.allowsAttachment(gunStack, id)) options.add(id);
                    }
                    // 已装的配件一定要在列表里（否则看不到当前值）
                    String cur = eff.get(type);
                    if (cur != null && !cur.isBlank() && !options.contains(cur)) options.add(cur);
                    if (options.isEmpty()) continue;
                    types.add(new TcpAttachmentS2CPacket.TypeEntry(type, cur, options));
                }
            }
            if (types.isEmpty()) continue;
            guns.add(new TcpAttachmentS2CPacket.GunEntry(slotName, label, gunId, types));
        }
        int flags = 0;
        if (canEdit) flags |= TcpAttachmentS2CPacket.FLAG_CAN_EDIT;
        if (open) flags |= TcpAttachmentS2CPacket.FLAG_OPEN;
        return new TcpAttachmentS2CPacket(flags, guns);
    }

    public static void reloadAll() {
        LoadoutConfig.markDirty();
        LoadoutConfig.load();
        SkinRegistry.invalidate();
        LOG.info("[TDM·背包] 已重读配置与皮肤目录");
    }

    // ================== 持久化 ==================

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            JsonElement root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return;
            int players = 0;
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(e.getKey());
                } catch (RuntimeException ex) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                List<Preset> list = new ArrayList<>();

                if (o.has("presets") && o.get("presets").isJsonArray()) {
                    for (JsonElement pe : o.getAsJsonArray("presets")) {
                        if (!pe.isJsonObject()) continue;
                        JsonObject po = pe.getAsJsonObject();
                        Preset p = new Preset(getString(po, "id"), getString(po, "name"));
                        if (po.has("picks") && po.get("picks").isJsonObject()) {
                            for (Map.Entry<String, JsonElement> s : po.getAsJsonObject("picks").entrySet()) {
                                if (s.getValue().isJsonPrimitive()) p.picks.put(s.getKey(), s.getValue().getAsString());
                            }
                        }
                        if (po.has("attachments") && po.get("attachments").isJsonObject()) {
                            for (Map.Entry<String, JsonElement> s : po.getAsJsonObject("attachments").entrySet()) {
                                if (!s.getValue().isJsonObject()) continue;
                                Map<String, String> t = new ConcurrentHashMap<>();
                                for (Map.Entry<String, JsonElement> t2 : s.getValue().getAsJsonObject().entrySet()) {
                                    if (t2.getValue().isJsonPrimitive()) t.put(t2.getKey(), t2.getValue().getAsString());
                                }
                                p.attach.put(s.getKey(), t);
                            }
                        }
                        if (po.has("custom") && po.get("custom").isJsonObject()) {
                            for (Map.Entry<String, JsonElement> s : po.getAsJsonObject("custom").entrySet()) {
                                if (s.getValue().isJsonPrimitive()) p.customNbt.put(s.getKey(), s.getValue().getAsString());
                            }
                        }
                        list.add(p);
                    }
                } else {
                    // 旧格式（v17~v23）：顶层直接是 picks，attachments 在同一个对象里
                    Preset p = new Preset(null, "背包1");
                    for (Map.Entry<String, JsonElement> s : o.entrySet()) {
                        if ("presets".equals(s.getKey()) || "active".equals(s.getKey())) continue;
                        if ("attachments".equals(s.getKey()) && s.getValue().isJsonObject()) {
                            for (Map.Entry<String, JsonElement> g : s.getValue().getAsJsonObject().entrySet()) {
                                if (!g.getValue().isJsonObject()) continue;
                                Map<String, String> t = new ConcurrentHashMap<>();
                                for (Map.Entry<String, JsonElement> t2 : g.getValue().getAsJsonObject().entrySet()) {
                                    if (t2.getValue().isJsonPrimitive()) t.put(t2.getKey(), t2.getValue().getAsString());
                                }
                                p.attach.put(g.getKey(), t);
                            }
                            continue;
                        }
                        if (s.getValue().isJsonPrimitive()) p.picks.put(s.getKey(), s.getValue().getAsString());
                    }
                    list.add(p);
                }
                if (list.isEmpty()) list.add(new Preset(null, "背包1"));
                PRESETS.put(uuid, list);
                String act = getString(o, "active");
                ACTIVE.put(uuid, act != null && list.stream().anyMatch(p -> p.id.equals(act)) ? act : list.get(0).id);
                players++;
            }
            LOG.info("[TDM·背包] 载入 {} 名玩家的背包数据：{}", players, FILE);
        } catch (Exception e) {
            LOG.error("[TDM·背包] 读取玩家背包失败：{}", e.toString());
        }
    }

    private static String getString(JsonObject o, String key) {
        try {
            if (o.has(key) && o.get(key).isJsonPrimitive()) return o.get(key).getAsString();
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    public static synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, List<Preset>> e : new LinkedHashMap<>(PRESETS).entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("active", ACTIVE.getOrDefault(e.getKey(), e.getValue().get(0).id));
                JsonArray arr = new JsonArray();
                for (Preset p : e.getValue()) {
                    JsonObject po = new JsonObject();
                    po.addProperty("id", p.id);
                    po.addProperty("name", p.name);
                    JsonObject picks = new JsonObject();
                    for (Map.Entry<String, String> s : p.picks.entrySet()) picks.addProperty(s.getKey(), s.getValue());
                    po.add("picks", picks);
                    JsonObject att = new JsonObject();
                    for (Map.Entry<String, Map<String, String>> s : p.attach.entrySet()) {
                        JsonObject t = new JsonObject();
                        for (Map.Entry<String, String> t2 : s.getValue().entrySet()) t.addProperty(t2.getKey(), t2.getValue());
                        att.add(s.getKey(), t);
                    }
                    po.add("attachments", att);
                    JsonObject cust = new JsonObject();
                    for (Map.Entry<String, String> s : p.customNbt.entrySet()) cust.addProperty(s.getKey(), s.getValue());
                    po.add("custom", cust);
                    arr.add(po);
                }
                o.add("presets", arr);
                root.add(e.getKey().toString(), o);
            }
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.error("[TDM·背包] 保存玩家背包失败：{}", e.toString());
        }
    }

    // ================== 调试 ==================

    public static String debug(ServerPlayer player) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6【背包诊断】").append(player.getName().getString()).append('\n');
        sb.append("配置文件：").append(LoadoutConfig.exists() ? LoadoutConfig.file().toString() : "§c不存在！").append('\n');
        sb.append("皮肤目录：").append(LoadoutConfig.skinFolder())
                .append(" ｜ 你的皮肤文件：").append(SkinRegistry.sourceOf(player) == null ? "§e（没有）" : SkinRegistry.sourceOf(player))
                .append('\n');
        sb.append("你拥有的枪皮/物品 id：").append(SkinRegistry.debugDump(player)).append('\n');
        Preset act = active(player);
        sb.append("背包 ").append(presets(player).size()).append('/').append(maxPresets())
                .append(" 个 ｜ 当前：§a").append(act.name).append("§r\n");
        for (int slot = 0; slot < LoadoutConfig.SLOT_COUNT; slot++) {
            List<String> opts = available(player, slot);
            String cur = pick(player, slot);
            sb.append(LoadoutConfig.SLOT_LABEL[slot]).append(" → 可选 ")
                    .append(opts.isEmpty() ? "§c（空）" : String.join(", ", opts))
                    .append(" ｜ 当前 ").append(cur == null ? "§e（未选择）" : cur).append('\n');
        }
        return sb.toString();
    }

    public static String attachmentDebug(ServerPlayer player, int slot) {
        String gunId = pick(player, slot);
        String slotName = LoadoutConfig.SLOT_NAMES[slot];
        StringBuilder sb = new StringBuilder();
        sb.append("§6【配件诊断】背包「").append(active(player).name).append("」槽位").append(slot + 1)
                .append(' ').append(slotName).append(" 枪=").append(gunId == null ? "§c（没选）" : gunId).append('\n');
        if (gunId != null) {
            Map<String, String> eff = effectiveAttachments(player, slotName);
            sb.append("会装的配件：").append(eff.isEmpty() ? "§e（无）" : eff.toString()).append('\n');
            sb.append("配件类型：").append(String.join(", ", LoadoutConfig.attachmentTypes())).append('\n');
            sb.append("自动扫描=").append(LoadoutConfig.autoAttachmentPool())
                    .append(" ｜ autoAttachFirst=").append(LoadoutConfig.autoAttachFirst()).append('\n');
            ItemStack stack = build(player, slot);
            sb.append("实测发枪 → ").append(stack.isEmpty() ? "§c生成失败" : stack.getDescriptionId()).append('\n');
            if (!stack.isEmpty()) sb.append(GunItemFactory.dumpAttachmentNbt(stack)).append('\n');
        }
        sb.append("TaCZ：").append(GunItemFactory.taczLoaded() ? "已装" : "§c未装")
                .append(" ｜ 发现的配件数=").append(AttachmentIndex.all().size());
        return sb.toString();
    }
}
