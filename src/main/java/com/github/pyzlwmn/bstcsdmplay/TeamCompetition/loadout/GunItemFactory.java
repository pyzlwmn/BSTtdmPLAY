package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 把配置里的 id 变成 ItemStack（枪械 / 弹药 / 配件）。
 *
 * 全部走反射：TaCZ 是可选依赖，编译期不需要它。
 *
 * 优先级：
 *   ① 普通物品（原版/其它模组）→ new ItemStack(item)
 *   ② TaCZ 官方构造器 GunItemBuilder.of(枪id)…build()（如果这个版本的 TaCZ 有）
 *   ③ 手搓：tacz:modern_kinetic_gun + IGun#setGunId / setCurrentAmmoCount / setDummyAmmoAmount / setFireMode
 *   ④ 再不行就纯 NBT 兜底（GunId / AmmoCount / HasBulletInBarrel / FireMode / SkinId）
 *
 * ⚠️ v19：以前没写 FireMode → 客户端显示「未知开火模式」且不能开枪（主人实测）。
 */
public final class GunItemFactory {

    private static final Logger LOG = LoggerFactory.getLogger("BST-TDM-Loadout");

    private static final String MODID_TACZ = "tacz";
    private static final String TACZ_GUN_ITEM = "modern_kinetic_gun";
    private static final String TACZ_ATTACHMENT_ITEM = "attachment";
    private static final String IGUN_CLASS = "com.tacz.guns.api.item.IGun";

    private static final String[] FIRE_MODE_CLASSES = {
            "com.tacz.guns.api.item.gun.FireMode",     // 1.1.7 实际位置（javap 确认）
            "com.tacz.guns.api.item.FireMode",
            "com.tacz.guns.resource.pojo.data.gun.FireMode",
    };

    /** TaCZ 配件槽类型（javap 确认：SCOPE/MUZZLE/STOCK/GRIP/LASER/EXTENDED_MAG/NONE） */
    private static final String[] ATTACHMENT_TYPES = {
            "SCOPE", "MUZZLE", "STOCK", "GRIP", "LASER", "EXTENDED_MAG"
    };

    private static boolean warnedTacz = false;

    private GunItemFactory() {
    }

    public static boolean taczLoaded() {
        try {
            return ModList.get().isLoaded(MODID_TACZ);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static ItemStack create(String spec) {
        return create(spec, 30, 300, "SEMI");
    }

    /**
     * 配置条目 → 物品
     *
     * @param spec    "tacz:ak47" / "tacz:ak47|tacz:ak47_fox" / "minecraft:diamond_sword"
     * @param mag     弹夹子弹数（-1 = 不覆盖）
     * @param reserve 备弹数（dummy ammo，-1 = 不覆盖）
     * @param fire    开火模式：SEMI / AUTO / BURST
     */
    public static ItemStack create(String spec, int mag, int reserve, String fireMode) {
        return create(spec, mag, reserve, fireMode, java.util.Map.of());
    }

    /**
     * 配置条目 → 物品（带配件版）
     *
     * @param attachments 槽位类型 → 配件 id（走官方 GunItemBuilder.putAttachment，发枪直接带上）
     */
    public static ItemStack create(String spec, int mag, int reserve, String fireMode,
                                   java.util.Map<String, String> attachments) {
        if (spec == null || spec.isBlank()) return ItemStack.EMPTY;

        String gun = spec.trim();
        String skin = null;
        int cut = indexOfSeparator(gun);
        if (cut > 0) {
            skin = gun.substring(cut + 1).trim();
            gun = gun.substring(0, cut).trim();
        }

        ResourceLocation rl = ResourceLocation.tryParse(gun);
        if (rl == null) {
            LOG.warn("[TDM·背包] 非法 id：{}", spec);
            return ItemStack.EMPTY;
        }

        // ★ v43：开火模式传空/NATIVE → 用这把枪原版支持的（有全自动用全自动，没有用单发）
        if (fireMode == null || fireMode.isBlank() || "NATIVE".equalsIgnoreCase(fireMode)) {
            fireMode = nativeFireMode(rl.toString());
        }

        // ① 普通物品
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item != Items.AIR) {
            return new ItemStack(item);
        }

        // ②③ 枪械
        if (taczLoaded()) {
            ItemStack stack = viaBuilder(rl, skin, mag, reserve, fireMode, attachments);
            if (stack == null || stack.isEmpty()) stack = byHand(rl, skin, mag, reserve, fireMode, attachments);
            if (stack != null && !stack.isEmpty()) {
                // ★ v33：builder.putAttachment 对部分槽位会静默失败（主人日志：6 个只挂上 3 个）
                //    → 回读确认，缺的用官方 installAttachment 补挂，保证「预览 = 实枪」一致
                if (attachments != null && !attachments.isEmpty()) ensureAttachments(stack, attachments);
                return stack;
            }
        } else if (!warnedTacz) {
            warnedTacz = true;
            LOG.warn("[TDM·背包] 找不到物品 {}，且未安装 TaCZ → 该条目无效", gun);
        }
        return ItemStack.EMPTY;
    }

    /**
     * ★ v33：补挂配件——回读当前已挂槽位，缺的用官方 installAttachment 再挂一遍。
     * 背景：主人日志里 6 个类型「builder 返回 true」但回读只剩 3 个（SCOPE/GRIP/LASER 丢了），
     *       导致预览/实枪看不到这些部件。这里做一次校验补挂。
     */
    private static void ensureAttachments(ItemStack stack, java.util.Map<String, String> attachments) {
        try {
            java.util.Set<String> have = installedTypes(stack);
            boolean anyMissing = false;
            for (String t : attachments.keySet()) {
                if (!have.contains(t.toUpperCase())) {
                    anyMissing = true;
                    break;
                }
            }
            if (!anyMissing) return;

            Object igun = igunOf(stack);
            if (igun == null) return;
            for (java.util.Map.Entry<String, String> e : attachments.entrySet()) {
                if (e.getValue() == null || e.getValue().isBlank()) continue;
                if (have.contains(e.getKey().toUpperCase())) continue;
                ItemStack att = createAttachment(e.getValue());
                if (att.isEmpty()) continue;
                Object r = call(igun, "installAttachment", stack, att);
                LOG.info("[TDM·背包] 补挂配件 {}（{}）→ {}（installAttachment 返回 {}，回读 {}）",
                        e.getValue(), e.getKey(), gunIdOf(stack), r, readAttachments(stack));
            }
        } catch (Throwable t) {
            LOG.debug("[TDM·背包] 补挂配件异常：{}", t.toString());
        }
    }

    /** 回读：当前已经挂上了哪些槽位类型（在 ATTACHMENT_TYPES 里能查到的） */
    public static java.util.Set<String> installedTypes(ItemStack gun) {
        java.util.Set<String> out = new java.util.HashSet<>();
        Object igun = igunOf(gun);
        if (igun == null) return out;
        try {
            Class<?> typeCls = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
            for (String name : ATTACHMENT_TYPES) {
                Object type;
                try {
                    type = Enum.valueOf(typeCls.asSubclass(Enum.class), name);
                } catch (Throwable t) {
                    continue;
                }
                Object r = call(igun, "getAttachment", gun, type);
                if (r instanceof ItemStack is && !is.isEmpty()) out.add(name);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /**
     * ★ v35：实测「这把枪到底能不能装这个配件」。
     *
     * 背景（主人 2026-09-21 日志实锤）：
     *   TaCZ 的 `allowAttachment` 会**乐观地返回 true**，但 `installAttachment` 才说真话：
     *   - ak47 回读：SCOPE/MUZZLE/STOCK/EXTENDED_MAG 挂上了，GRIP/LASER 怎么都挂不上
     *   - deagle 回读：MUZZLE/LASER/EXTENDED_MAG 挂上了，SCOPE/STOCK/GRIP 挂不上
     *   → 界面里那些「装不上的槽位」就是主人说的「预览不了其它的部件」
     *
     * 做法：拿一份枪的副本真装一次，回读确认真的上了才算数（副本不影响原枪）。
     */
    // ================== v43：读 TaCZ 原版枪械数据（弹匣量 / 射击模式）==================

    /** 原版弹匣容量（TaCZ gun data）；拿不到返回 -1 */
    public static int nativeMagazine(String gunId) {
        try {
            Object gunData = gunDataOf(gunId);
            if (gunData == null) return -1;
            Object v = gunData.getClass().getMethod("getAmmoAmount").invoke(gunData);
            return v instanceof Number n ? n.intValue() : -1;
        } catch (Throwable t) {
            LOG.debug("[TDM·背包] 读原版弹匣量失败 {}：{}", gunId, t.toString());
            return -1;
        }
    }

    /** 原版射击模式：fireModeSet 里有 AUTO → AUTO，否则 SEMI */
    public static String nativeFireMode(String gunId) {
        try {
            Object gunData = gunDataOf(gunId);
            if (gunData == null) return "SEMI";
            Object set = gunData.getClass().getMethod("getFireModeSet").invoke(gunData);
            if (set instanceof Iterable<?> it) {
                for (Object m : it) {
                    if (m != null && "AUTO".equalsIgnoreCase(m.toString())) return "AUTO";
                }
            }
            return "SEMI";
        } catch (Throwable t) {
            return "SEMI";
        }
    }

    /** 原版弹药：{弹匣, 弹匣×3}；读不到返回 {-1,-1} */
    public static int[] nativeAmmo(String gunId) {
        int mag = nativeMagazine(gunId);
        return mag > 0 ? new int[]{mag, mag * 3} : new int[]{-1, -1};
    }

    /** 拿 TaCZ 的 GunData（走反射，TaCZ 没装就返回 null） */
    private static Object gunDataOf(String gunId) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(gunId == null ? "" : gunId);
            if (rl == null) return null;
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object opt = api.getMethod("getCommonGunIndex", ResourceLocation.class).invoke(null, rl);
            Object idx = opt;
            if (opt instanceof java.util.Optional<?> o) idx = o.orElse(null);
            if (idx == null) return null;
            return idx.getClass().getMethod("getGunData").invoke(idx);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean canReallyInstall(ItemStack gun, String attachmentId) {
        if (gun == null || gun.isEmpty() || attachmentId == null || attachmentId.isBlank()) return false;
        try {
            ItemStack copy = gun.copy();
            Object igun = igunOf(copy);
            if (igun == null) return false;
            ItemStack att = createAttachment(attachmentId);
            if (att.isEmpty()) return false;
            String type = AttachmentIndex.typeOf(attachmentId).toUpperCase();
            call(igun, "installAttachment", copy, att);
            return installedTypes(copy).contains(type);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 配件物品（v28.1 修正）
     *
     * ⚠️ 之前先调了通用 create()，配件 id 会被当成枪造出一把“枪”来，
     * 于是 allowAttachment 全判 false → 配件被全部过滤（只剩一个）。
     * 现在直接按：已注册物品 → AttachmentItemBuilder → tacz:attachment+NBT 走。
     */
    public static ItemStack createAttachment(String id) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null) return ItemStack.EMPTY;

        // ① 本来就是注册过的物品
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item != Items.AIR) return new ItemStack(item);

        if (!taczLoaded()) return ItemStack.EMPTY;

        // ② TaCZ 配件：AttachmentItemBuilder.create().setId(rl).build()
        try {
            Object builder = callStatic("com.tacz.guns.api.item.builder.AttachmentItemBuilder", "create");
            if (builder != null) {
                call(builder, "setId", rl);
                Object stack = call(builder, "build");
                if (stack instanceof ItemStack is && !is.isEmpty()) return is;
            }
        } catch (Throwable t) {
            LOG.debug("[TDM·背包] AttachmentItemBuilder 不可用：{}", t.toString());
        }

        // ③ 兜底：tacz:attachment + NBT AttachmentId
        try {
            ResourceLocation itemId = ResourceLocation.tryBuild(MODID_TACZ, TACZ_ATTACHMENT_ITEM);
            Item attItem = itemId == null ? Items.AIR : BuiltInRegistries.ITEM.get(itemId);
            if (attItem == Items.AIR) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(attItem);
            stack.getOrCreateTag().putString("AttachmentId", rl.toString());
            return stack;
        } catch (Throwable t) {
            LOG.warn("[TDM·背包] 构造配件失败 {}：{}", id, t.toString());
            return ItemStack.EMPTY;
        }
    }

    // ================== 弹药 ==================

    public static void fillAmmo(ItemStack stack) {
        fillAmmo(stack, -1, -1);
    }

    /** 补弹药；mag/reserve < 0 表示只把弹夹填满到最大、备弹给个默认 */
    public static void fillAmmo(ItemStack stack, int mag, int reserve) {
        if (stack == null || stack.isEmpty()) return;
        Object igun = igunOf(stack);
        if (igun == null) return;

        int magCount = mag;
        if (magCount < 0) {
            Object max = call(igun, "getMaxDummyAmmoAmount", stack);
            magCount = max instanceof Number n && n.intValue() > 0 ? n.intValue() : 30;
        }
        int reserveCount = reserve;

        call(igun, "setCurrentAmmoCount", stack, magCount);
        if (reserveCount >= 0) {
            call(igun, "setDummyAmmoAmount", stack, reserveCount);
        }
        call(igun, "setBulletInBarrel", stack, true);
        // TaCZ 1.1.7 的真实 NBT 名（javap GunItemDataAccessor 确认）
        stack.getOrCreateTag().putInt("GunCurrentAmmoCount", magCount);
        stack.getOrCreateTag().putBoolean("HasBulletInBarrel", true);
    }

    public static boolean isGun(ItemStack stack) {
        return !stack.isEmpty() && igunOf(stack) != null;
    }

    /**
     * 设置开火模式（v22）：默认连发；枪本身不支持连发（比如半自动手枪/栓狙）就自动回退单发。
     * 返回最终生效的模式字符串。
     */
    public static String applyFireMode(ItemStack stack, String requested) {
        if (stack == null || stack.isEmpty()) return null;
        String want = requested == null || requested.isBlank() ? "AUTO" : requested.toUpperCase();
        Object igun = igunOf(stack);
        if (igun == null) {
            stack.getOrCreateTag().putString("GunFireMode", want);
            return want;
        }
        setFireMode(igun, stack, want);
        String actual = readFireMode(igun, stack);
        if (actual != null && !actual.equalsIgnoreCase(want) && !"SEMI".equals(want)) {
            // 枪不支持这个模式（比如自动武器才有连发）→ 退到单发
            setFireMode(igun, stack, "SEMI");
            String fallback = readFireMode(igun, stack);
            LOG.info("[TDM·背包] {} 不支持 {} → 已回退 {}（回读 {}）", gunIdOf(stack), want, "SEMI", fallback);
            return fallback != null ? fallback : "SEMI";
        }
        return actual != null ? actual : want;
    }

    private static void setFireMode(Object igun, ItemStack stack, String mode) {
        Object fm = fireModeEnum(mode);
        if (fm != null) call(igun, "setFireMode", stack, fm);
        stack.getOrCreateTag().putString("GunFireMode", mode);
    }

    private static String readFireMode(Object igun, ItemStack stack) {
        Object r = call(igun, "getFireMode", stack);
        return r == null ? null : r.toString();
    }

    /** 卸掉某个配件槽的配件 */
    public static void uninstall(ItemStack gun, String type) {
        if (gun == null || gun.isEmpty() || type == null || type.isBlank()) return;
        Object igun = igunOf(gun);
        if (igun == null) return;
        Object typeEnum = attachmentTypeEnum(type);
        if (typeEnum == null) return;
        Object r = call(igun, "unloadAttachment", gun, typeEnum);
        if (r == null) {
            call(igun, "uninstallAttachment", gun, typeEnum);
        }
        LOG.info("[TDM·背包] 卸配件 {} ← {}（回读 {}）", type, gunIdOf(gun), readAttachments(gun, igun));
    }

    private static Object attachmentTypeEnum(String type) {
        try {
            Class<?> c = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
            for (Object o : c.getEnumConstants()) {
                if (o.toString().equalsIgnoreCase(type)) return o;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static String gunIdOf(ItemStack stack) {
        Object igun = igunOf(stack);
        if (igun == null) return null;
        Object r = call(igun, "getGunId", stack);
        return r == null ? null : r.toString();
    }

    /** 是不是这把枪允许的配件类型（Snap：allowAttachmentType） */
    public static boolean allowsType(ItemStack gun, String type) {
        if (gun == null || gun.isEmpty() || type == null || type.isBlank() || "MISC".equalsIgnoreCase(type)) return true;
        Object igun = igunOf(gun);
        if (igun == null) return false;
        Object typeEnum = attachmentTypeEnum(type);
        if (typeEnum == null) return true;
        Object ok = call(igun, "allowAttachmentType", gun, typeEnum);
        return !(ok instanceof Boolean b) || b;
    }

    /** 是不是这把枪允许的配件（TaCZ：allowAttachment 优先，再退 allowAttachmentType） */
    public static boolean allowsAttachment(ItemStack gun, String attachmentId) {
        if (gun == null || gun.isEmpty() || attachmentId == null || attachmentId.isBlank()) return false;
        Object igun = igunOf(gun);
        if (igun == null) return false;
        ItemStack att = createAttachment(attachmentId);
        if (att.isEmpty()) return false;
        Object ok = call(igun, "allowAttachment", gun, att);
        if (ok instanceof Boolean b) return b;
        Object type = attachmentTypeEnum(AttachmentIndex.typeOf(attachmentId));
        if (type == null) return true;
        Object ok2 = call(igun, "allowAttachmentType", gun, type);
        return !(ok2 instanceof Boolean b2) || b2;
    }

    /** 物品 → SNBT 字符串（长存用） */
    public static String toSnbt(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        try {
            return stack.save(new net.minecraft.nbt.CompoundTag()).toString();
        } catch (Throwable t) {
            LOG.warn("[TDM·背包] 物品转 SNBT 失败：{}", t.toString());
            return "";
        }
    }

    /** SNBT 字符串 → 物品（解不开就返回空） */
    public static ItemStack fromSnbt(String snbt) {
        if (snbt == null || snbt.isBlank()) return ItemStack.EMPTY;
        try {
            var tag = net.minecraft.nbt.TagParser.parseTag(snbt);
            return ItemStack.of(tag);
        } catch (Throwable t) {
            LOG.warn("[TDM·背包] SNBT 解析失败：{}", t.toString());
            return ItemStack.EMPTY;
        }
    }

    /** 给枪装配件（TaCZ 官方 installAttachment；失败就写日志，并回读验证 + 报告 allowAttachment） */
    public static void installAttachments(ItemStack gun, List<String> attachmentIds) {
        if (gun == null || gun.isEmpty() || attachmentIds == null || attachmentIds.isEmpty()) return;
        Object igun = igunOf(gun);
        if (igun == null) return;
        for (String id : attachmentIds) {
            ItemStack att = createAttachment(id);
            if (att.isEmpty()) {
                LOG.warn("[TDM·背包] 配件生成失败：{}", id);
                continue;
            }
            Object allowed = call(igun, "allowAttachment", gun, att);
            Object r = call(igun, "installAttachment", gun, att);
            if (r == null) {
                r = call(igun, "setAttachment", gun, att);   // 换个可能的名字
            }
            LOG.info("[TDM·背包] 装配件 {} → {}（allowAttachment={}，调用返回 {}，回读 {}）",
                    id, gunIdOf(gun), allowed, r, readAttachments(gun, igun));
        }
    }

    /** 诊断输出：枪 + 配件槽类型 + NBT 里 Attachment 段（给 /tdmloadout attdebug） */
    public static String dumpAttachmentNbt(ItemStack gun) {
        if (gun == null || gun.isEmpty()) return "（空手 / 不是枪）";
        Object igun = igunOf(gun);
        StringBuilder sb = new StringBuilder();
        sb.append("item=").append(BuiltInRegistries.ITEM.getKey(gun.getItem())).append(" gunId=").append(gunIdOf(gun));
        try {
            var tag = gun.getTag();
            sb.append(" ｜ Attachment 段=").append(tag == null ? "无 NBT" : String.valueOf(tag.get("Attachment")));
        } catch (RuntimeException e) {
            sb.append(" ｜ 读 NBT 失败").append(e);
        }
        sb.append(" ｜ 回读 ").append(readAttachments(gun));
        return sb.toString();
    }

    /** 回读枪上已有配件（TaCZ 1.1.7：getAttachment(stack, AttachmentType) 按槽位查） */
    public static String readAttachments(ItemStack gun) {
        Object igun = igunOf(gun);
        return igun == null ? "非枪械" : readAttachments(gun, igun);
    }

    private static String readAttachments(ItemStack gun, Object igun) {
        try {
            Class<?> typeCls = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
            StringBuilder sb = new StringBuilder();
            for (String name : ATTACHMENT_TYPES) {
                Object type;
                try {
                    type = Enum.valueOf(typeCls.asSubclass(Enum.class), name);
                } catch (Throwable t) {
                    continue;
                }
                Object r = call(igun, "getAttachment", gun, type);
                if (r instanceof ItemStack is && !is.isEmpty()) {
                    Object aid = call(igun, "getAttachmentId", gun, type);
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(name).append('=').append(aid == null ? "?" : aid)
                            .append('(').append(is.getItem()).append(')');
                }
            }
            return sb.length() == 0 ? "（空）" : sb.toString();
        } catch (Throwable t) {
            return "回读失败：" + t;
        }
    }

    // ================== 内部 ==================

    /** TaCZ 官方构造器：GunItemBuilder.create().setId(枪id).setAmmoCount(n).setAmmoInBarrel(true).setFireMode(fm).build() */
    private static ItemStack viaBuilder(ResourceLocation gunId, String skin, int mag, int reserve, String fireMode,
                                        java.util.Map<String, String> attachments) {
        try {
            Object builder = callStatic("com.tacz.guns.api.item.builder.GunItemBuilder", "create");
            if (builder == null) return null;
            call(builder, "setId", gunId);
            if (mag >= 0) call(builder, "setAmmoCount", mag);
            if (reserve >= 0) call(builder, "setMaxDummyAmmoAmount", reserve);
            call(builder, "setAmmoInBarrel", true);
            // ★ 配件：官方 putAttachment(AttachmentType, ResourceLocation) —— 发枪直接带上
            if (attachments != null) {
                for (java.util.Map.Entry<String, String> e : attachments.entrySet()) {
                    Object type = attachmentTypeEnum(e.getKey());
                    ResourceLocation attId = ResourceLocation.tryParse(e.getValue());
                    if (type == null || attId == null) continue;
                    Object r = call(builder, "putAttachment", type, attId);
                    // ⚠️ putAttachment 返回的是 builder 本身，永远非 null → 这行只能当“请求了”看，不能当成功
                    LOG.debug("[TDM·背包] 请求挂配件 {} → {}（已调用，返回非空={}）", e.getValue(), gunId, r != null);
                }
            }
            Object stack = call(builder, "build");
            if (stack instanceof ItemStack is && !is.isEmpty()) {
                if (skin != null && !skin.isEmpty()) {
                    is.getOrCreateTag().putString("SkinId", skin);
                }
                if (reserve >= 0) call(igunOf(is), "setDummyAmmoAmount", is, reserve);
                applyFireMode(is, fireMode);
                finishNbt(is, gunId, skin, mag, reserve, fireMode);
                if (attachments != null && !attachments.isEmpty()) {
                    java.util.Set<String> have = installedTypes(is);
                    StringBuilder miss = new StringBuilder();
                    for (String t : attachments.keySet()) {
                        if (!have.contains(t.toUpperCase())) {
                            if (miss.length() > 0) miss.append(',');
                            miss.append(t);
                        }
                    }
                    LOG.info("[TDM·背包] {} 发枪带上配件，回读：{} ｜ 缺失：{}", gunId, readAttachments(is),
                            miss.length() == 0 ? "无" : miss.toString());
                }
                return is;
            }
        } catch (Throwable t) {
            LOG.debug("[TDM·背包] GunItemBuilder 不可用：{}", t.toString());
        }
        return null;
    }

    private static ItemStack byHand(ResourceLocation gunId, String skin, int mag, int reserve, String fireMode,
                                    java.util.Map<String, String> attachments) {
        try {
            ResourceLocation itemId = ResourceLocation.tryBuild(MODID_TACZ, TACZ_GUN_ITEM);
            Item item = itemId == null ? Items.AIR : BuiltInRegistries.ITEM.get(itemId);
            if (item == Items.AIR) {
                if (!warnedTacz) {
                    warnedTacz = true;
                    LOG.warn("[TDM·背包] 没找到 {}:{}（TaCZ 版本不同？）→ 枪械条目全部无效",
                            MODID_TACZ, TACZ_GUN_ITEM);
                }
                return null;
            }
            ItemStack stack = new ItemStack(item);
            Object igun = igunOf(stack);
            if (igun != null) {
                call(igun, "setGunId", stack, gunId);
                if (skin != null && !skin.isEmpty()) call(igun, "setSkinId", stack, skin);
                fillAmmo(stack, mag, reserve);
                applyFireMode(stack, fireMode);
                // 手搓路径没法 putAttachment → 用 installAttachment
                if (attachments != null && !attachments.isEmpty()) {
                    installAttachments(stack, new java.util.ArrayList<>(attachments.values()));
                }
            }
            finishNbt(stack, gunId, skin, mag, reserve, fireMode);
            return stack;
        } catch (Throwable t) {
            LOG.warn("[TDM·背包] 构造枪械失败 {}：{}", gunId, t.toString());
            return null;
        }
    }

    /** NBT 兜底：TaCZ 有些字段缺失时会显示「未知开火模式」并且开不了枪 */
    private static void finishNbt(ItemStack stack, ResourceLocation gunId, String skin,
                                 int mag, int reserve, String fireMode) {
        try {
            var tag = stack.getOrCreateTag();
            tag.putString("GunId", gunId.toString());
            if (mag >= 0) tag.putInt("GunCurrentAmmoCount", mag);
            tag.putBoolean("HasBulletInBarrel", true);
            if (fireMode != null && !fireMode.isBlank()) tag.putString("GunFireMode", fireMode.toUpperCase());
            if (reserve >= 0) tag.putInt("MaxDummyAmmo", reserve);
            if (skin != null && !skin.isEmpty()) tag.putString("SkinId", skin);
        } catch (RuntimeException ignored) {
        }
    }

    private static Object fireModeEnum(String mode) {
        if (mode == null || mode.isBlank()) return null;
        for (String cls : FIRE_MODE_CLASSES) {
            try {
                Class<?> c = Class.forName(cls);
                if (!c.isEnum()) continue;
                for (Object o : c.getEnumConstants()) {
                    if (o.toString().equalsIgnoreCase(mode)) return o;
                }
                return null;   // 找到枚举类但没这个值：不猜
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean isTaczItem(ItemStack stack, String path) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return MODID_TACZ.equals(id.getNamespace()) && path.equals(id.getPath());
    }

    private static Object igunOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !taczLoaded()) return null;
        try {
            Class<?> cls = Class.forName(IGUN_CLASS);
            Method m = cls.getMethod("getIGunOrNull", ItemStack.class);
            return m.invoke(null, stack);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int indexOfSeparator(String s) {
        int a = s.indexOf('|');
        int b = s.indexOf('#');
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    private static Object callStatic(String className, String method, Object... args) {
        try {
            Class<?> cls = Class.forName(className);
            return callOn(cls, null, method, args);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 宽松反射调用：按名字 + 参数个数找方法，自动做基础类型转换 */
    private static Object call(Object target, String method, Object... args) {
        if (target == null) return null;
        return callOn(target.getClass(), target, method, args);
    }

    private static Object callOn(Class<?> cls, Object target, String method, Object... args) {
        if (cls == null) return null;
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(method) || m.getParameterCount() != args.length) continue;
            try {
                Class<?>[] types = m.getParameterTypes();
                Object[] converted = new Object[args.length];
                boolean ok = true;
                for (int i = 0; i < args.length; i++) {
                    Object a = args[i];
                    if (a == null) {
                        converted[i] = null;
                        continue;
                    }
                    Class<?> t = types[i];
                    if (t.isInstance(a)) {
                        converted[i] = a;
                    } else if (t == int.class || t == Integer.class) {
                        if (!(a instanceof Number)) { ok = false; break; }
                        converted[i] = ((Number) a).intValue();
                    } else if (t == long.class || t == Long.class) {
                        if (!(a instanceof Number)) { ok = false; break; }
                        converted[i] = ((Number) a).longValue();
                    } else if (t == boolean.class || t == Boolean.class) {
                        if (!(a instanceof Boolean)) { ok = false; break; }
                        converted[i] = a;
                    } else if (t == String.class) {
                        converted[i] = String.valueOf(a);
                    } else if (t == ResourceLocation.class && a instanceof String s) {
                        converted[i] = ResourceLocation.tryParse(s);
                    } else {
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;
                m.setAccessible(true);
                return m.invoke(target, converted);
            } catch (Throwable ignored) {
                // 换下一个重载
            }
        }
        return null;
    }
}
