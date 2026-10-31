package com.github.pyzlwmn.filtdm.TeamCompetition.loadout;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

public final class GunItemFactory {

    private static final Logger LOG = LoggerFactory.getLogger("FIL-TDM-Loadout");

    private static final String MODID_TACZ = "tacz";
    private static final String TACZ_GUN_ITEM = "modern_kinetic_gun";
    private static final String TACZ_ATTACHMENT_ITEM = "attachment";
    private static final String IGUN_CLASS = "com.tacz.guns.api.item.IGun";

    private static final String[] FIRE_MODE_CLASSES = {
            "com.tacz.guns.api.item.gun.FireMode",
            "com.tacz.guns.api.item.FireMode",
            "com.tacz.guns.resource.pojo.data.gun.FireMode",
    };

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

    public static ItemStack create(String spec, int mag, int reserve, String fireMode) {
        return create(spec, mag, reserve, fireMode, java.util.Map.of());
    }

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

        if (fireMode == null || fireMode.isBlank() || "NATIVE".equalsIgnoreCase(fireMode)) {
            fireMode = nativeFireMode(rl.toString());
        }

        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item != Items.AIR) {
            return new ItemStack(item);
        }

        if (taczLoaded()) {
            ItemStack stack = viaBuilder(rl, skin, mag, reserve, fireMode, attachments);
            if (stack == null || stack.isEmpty()) stack = byHand(rl, skin, mag, reserve, fireMode, attachments);
            if (stack != null && !stack.isEmpty()) {

                if (attachments != null && !attachments.isEmpty()) ensureAttachments(stack, attachments);


                fillAmmo(stack, mag, reserve);
                return stack;
            }
        } else if (!warnedTacz) {
            warnedTacz = true;
            LOG.warn("[TDM·背包] 找不到物品 {}，且未安装 TaCZ → 该条目无效", gun);
        }
        return ItemStack.EMPTY;
    }

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

    public static int[] nativeAmmo(String gunId) {
        int mag = nativeMagazine(gunId);
        return mag > 0 ? new int[]{mag, mag * 3} : new int[]{-1, -1};
    }

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

    public static ItemStack createAttachment(String id) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null) return ItemStack.EMPTY;

        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item != Items.AIR) return new ItemStack(item);

        if (!taczLoaded()) return ItemStack.EMPTY;

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

    public static void fillAmmo(ItemStack stack) {
        fillAmmo(stack, -1, -1);
    }

    public static final int RESERVE_MULTIPLIER = 5;

    public static int magazineCapacity(ItemStack stack) {
        return magazineCapacityDebug(stack, null);
    }

    public static int magazineCapacityDebug(ItemStack stack, StringBuilder tracer) {
        if (stack == null || stack.isEmpty()) {
            if (tracer != null) tracer.append("空栈");
            return -1;
        }
        Object igun = igunOf(stack);
        if (igun == null) {
            if (tracer != null) tracer.append("igun=NULL");
            return -1;
        }

        Object gunData = gunDataOf(stack, igun);
        if (gunData != null) {

            Object withAtt = callStatic("com.tacz.guns.util.AttachmentDataUtils",
                    "getAmmoCountWithAttachment", stack, gunData);
            if (tracer != null) tracer.append("官方=").append(withAtt == null ? "NULL(调用失败)" : withAtt);
            if (withAtt instanceof Number n && n.intValue() > 0) return n.intValue();

            Object base = call(gunData, "getAmmoAmount");
            if (tracer != null) tracer.append(" 基础=").append(base == null ? "NULL" : base);
            if (base instanceof Number n && n.intValue() > 0) return n.intValue();



            Object ext = call(gunData, "getExtendedMagAmmoAmount");
            if (tracer != null) {
                if (ext instanceof int[] arr) {
                    tracer.append(" 扩容表=[");
                    for (int i = 0; i < arr.length; i++) {
                        if (i > 0) tracer.append(',');
                        tracer.append(arr[i]);
                    }
                    tracer.append("]");
                } else {
                    tracer.append(" 扩容表=").append(ext == null ? "NULL" : ext);
                }
            }
            if (ext instanceof int[] arr && arr.length > 0) {
                int lvl = magExtendLevel(stack, igun);
                if (tracer != null) tracer.append(" 扩容等级=").append(lvl);
                if (lvl > 0) {
                    int idx = Math.min(lvl, arr.length) - 1;
                    if (arr[idx] > 0) return arr[idx];
                }
            }
        } else {
            if (tracer != null) tracer.append("gunData=NULL");
        }

        if (stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("GunCurrentAmmoCount")) {
                int cur = tag.getInt("GunCurrentAmmoCount");
                if (tracer != null) tracer.append(" NBT当前=").append(cur);
                if (cur > 0) return cur;
            } else if (tracer != null) {
                tracer.append(" NBT当前=无");
            }
        }
        if (tracer != null) tracer.append(" → 读不出，返回-1");
        return -1;
    }

    public static int magExtendLevel(ItemStack stack, Object igun) {
        try {

            Class<?> typeCls = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
            Object type = Enum.valueOf(typeCls.asSubclass(Enum.class), "EXTENDED_MAG");
            Object att = call(igun, "getAttachment", stack, type);
            if (!(att instanceof ItemStack attStack) || attStack.isEmpty()) return 0;

            Object attId = call(igun, "getAttachmentId", stack, type);
            if (attId == null) return 0;

            Object attData = attachmentDataOf(attId);
            if (attData == null) return fallbackLevelByName(attId);
            for (String m : new String[]{"getExtendedMagLevel", "getMagExtendLevel", "getLevel"}) {
                Object v = call(attData, m);
                if (v instanceof Number n && n.intValue() > 0) return n.intValue();
            }
            return fallbackLevelByName(attId);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Object attachmentDataOf(Object attId) {
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            for (String m : new String[]{"getCommonAttachmentIndex", "getAttachmentIndex"}) {
                try {
                    Object opt = api.getMethod(m, ResourceLocation.class).invoke(null, attId);
                    if (opt instanceof java.util.Optional<?> o) {
                        Object idx = o.orElse(null);
                        if (idx != null) return idx.getClass().getMethod("getAttachmentData").invoke(idx);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int fallbackLevelByName(Object attId) {
        String s = String.valueOf(attId).toLowerCase();
        for (int lv = 3; lv >= 1; lv--) {
            if (s.endsWith("_" + lv) || s.endsWith("mag" + lv) || s.contains("extended_mag_" + lv)
                    || s.contains("_lv" + lv) || s.contains("_t" + lv)) return lv;
        }
        return 0;
    }

    private static Object gunDataOf(ItemStack stack, Object igun) {
        try {
            Object gunId = call(igun, "getGunId", stack);
            if (gunId == null) return null;
            String idStr = gunId instanceof ResourceLocation rl ? rl.toString() : String.valueOf(gunId);
            return gunDataOf(idStr);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void fillAmmo(ItemStack stack, int mag, int reserve) {
        if (stack == null || stack.isEmpty()) return;
        Object igun = igunOf(stack);
        if (igun == null) return;

        StringBuilder trace = new StringBuilder();
        int capacity = magazineCapacityDebug(stack, trace);
        int raw = capacity;
        if (capacity <= 0) capacity = 30;

        int magCount = mag >= 0 ? mag : capacity;

        int reserveCount = reserve >= 0 ? reserve : capacity * RESERVE_MULTIPLIER;

        Object gunIdObj = call(igun, "getGunId", stack);
        LOG.info("[TDM·弹药] gun={} | {} | 原始读取={} → 弹匣={} 备弹={} (配置mag={} reserve={})",
                gunIdObj, trace, raw, magCount, reserveCount, mag, reserve);

        call(igun, "setCurrentAmmoCount", stack, magCount);
        call(igun, "setDummyAmmoAmount", stack, reserveCount);
        call(igun, "setBulletInBarrel", stack, true);

        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("GunCurrentAmmoCount", magCount);
        tag.putInt("GunDummyAmmoAmount", reserveCount);
        tag.putInt("DummyAmmo", reserveCount);
        tag.putBoolean("HasBulletInBarrel", true);
    }

    public static boolean isGun(ItemStack stack) {
        return !stack.isEmpty() && igunOf(stack) != null;
    }

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

    public static boolean allowsType(ItemStack gun, String type) {
        if (gun == null || gun.isEmpty() || type == null || type.isBlank() || "MISC".equalsIgnoreCase(type)) return true;
        Object igun = igunOf(gun);
        if (igun == null) return false;
        Object typeEnum = attachmentTypeEnum(type);
        if (typeEnum == null) return true;
        Object ok = call(igun, "allowAttachmentType", gun, typeEnum);
        return !(ok instanceof Boolean b) || b;
    }

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

    public static String toSnbt(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        try {
            return stack.save(new net.minecraft.nbt.CompoundTag()).toString();
        } catch (Throwable t) {
            LOG.warn("[TDM·背包] 物品转 SNBT 失败：{}", t.toString());
            return "";
        }
    }

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
                r = call(igun, "setAttachment", gun, att);
            }
            LOG.info("[TDM·背包] 装配件 {} → {}（allowAttachment={}，调用返回 {}，回读 {}）",
                    id, gunIdOf(gun), allowed, r, readAttachments(gun, igun));
        }
    }

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


    private static ItemStack viaBuilder(ResourceLocation gunId, String skin, int mag, int reserve, String fireMode,
                                        java.util.Map<String, String> attachments) {
        try {
            Object builder = callStatic("com.tacz.guns.api.item.builder.GunItemBuilder", "create");
            if (builder == null) return null;
            call(builder, "setId", gunId);

            if (reserve >= 0) call(builder, "setMaxDummyAmmoAmount", reserve);
            call(builder, "setAmmoInBarrel", true);

            if (attachments != null) {
                for (java.util.Map.Entry<String, String> e : attachments.entrySet()) {
                    Object type = attachmentTypeEnum(e.getKey());
                    ResourceLocation attId = ResourceLocation.tryParse(e.getValue());
                    if (type == null || attId == null) continue;
                    Object r = call(builder, "putAttachment", type, attId);

                    LOG.debug("[TDM·背包] 请求挂配件 {} → {}（已调用，返回非空={}）", e.getValue(), gunId, r != null);
                }
            }
            Object stack = call(builder, "build");
            if (stack instanceof ItemStack is && !is.isEmpty()) {
                if (skin != null && !skin.isEmpty()) {
                    is.getOrCreateTag().putString("SkinId", skin);
                }

                applyFireMode(is, fireMode);
                finishNbt(is, gunId, skin, -1, -1, fireMode);
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
                applyFireMode(stack, fireMode);

                if (attachments != null && !attachments.isEmpty()) {
                    installAttachments(stack, new java.util.ArrayList<>(attachments.values()));
                }

                fillAmmo(stack, mag, reserve);
            }
            finishNbt(stack, gunId, skin, mag, reserve, fireMode);
            return stack;
        } catch (Throwable t) {
            LOG.warn("[TDM·背包] 构造枪械失败 {}：{}", gunId, t.toString());
            return null;
        }
    }

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
                return null;
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

            }
        }
        return null;
    }
}
