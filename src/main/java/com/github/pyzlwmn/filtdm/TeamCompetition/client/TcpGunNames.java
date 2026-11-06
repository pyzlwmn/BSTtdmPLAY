package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class TcpGunNames {

    private static final java.util.Map<String, String> ZH_GUN = new java.util.HashMap<>();

    static {
        // ★ 枪名不再硬编码：统一读枪包索引的 i18n key（动态跟随枪包更新）
        //   这里只保留「非 TACZ 枪包物品」的兜底名
        ZH_GUN.put("ammo_box", "弹药盒");
        ZH_GUN.put("timeless_ammo", "弹药");
        ZH_GUN.put("grenade", "手雷");
        ZH_GUN.put("c4", "C4 炸药");

    }

    private static final java.util.Map<String, String> ZH_SKIN = new java.util.HashMap<>();

    static {
        ZH_SKIN.put("fox", "狐");
        ZH_SKIN.put("dragon", "龙");
        ZH_SKIN.put("tiger", "虎");
        ZH_SKIN.put("gold", "黄金");
        ZH_SKIN.put("silver", "白银");
        ZH_SKIN.put("camo", "迷彩");
        ZH_SKIN.put("neon", "霓虹");
        ZH_SKIN.put("erximofu", "二次元");
        ZH_SKIN.put("default", "原皮");
    }

    private TcpGunNames() {
    }

    public static String attachmentName(String id) {
        if (id == null || id.isBlank()) return "（空）";
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                Object opt = api.getMethod("getCommonAttachmentIndex", ResourceLocation.class).invoke(null, rl);
                Object idx = opt instanceof java.util.Optional<?> o ? o.orElse(null) : opt;
                if (idx != null) {
                    Object pojo = idx.getClass().getMethod("getPojo").invoke(idx);
                    if (pojo != null) {
                        Object nm = pojo.getClass().getMethod("getName").invoke(pojo);
                        if (nm instanceof String key && !key.isBlank()) {
                            if (key.indexOf('.') > 0) {
                                String translated = net.minecraft.network.chat.Component.translatable(key).getString();
                                if (!translated.equals(key)) return translated;
                            }
                            return prettify(key);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return display(id);
    }

    public static String display(String id) {
        if (id == null || id.isBlank()) return "（空）";

        String gun = id;
        String skin = null;
        int cut = gun.indexOf('|');
        if (cut < 0) cut = gun.indexOf('#');
        if (cut > 0) {
            skin = gun.substring(cut + 1).trim();
            gun = gun.substring(0, cut).trim();
        }

        String name = resolve(gun);

        return skin == null || skin.isEmpty() ? name : name + " [" + skinName(skin) + "]";
    }

    private static String resolve(String gun) {

        // ★ 第一优先：读枪包索引里的 i18n key，交给游戏翻译。
        //   TACZ 的 index/guns/<id>.json 里 "name": "tacz.gun.<id>.name"，
        //   ClientGunIndex.getName() 拿到的就是这个 key，枪包更新中文名会自动跟着变，
        //   不用 mod 再维护一份映射表。
        String byPack = gunPackName(gun);
        if (byPack != null) return byPack;

        // 第二：物品自身的本地化描述（非 TACZ 物品，如弹药盒、手雷等）
        ResourceLocation rl = ResourceLocation.tryParse(gun);
        if (rl != null) {
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != Items.AIR) {
                try {
                    String s = item.getDescription().getString();
                    if (s != null && !s.isBlank()) return s;
                } catch (RuntimeException ignored) {
                }
            }
        }

        // 最后：mod 自己的表 / 美化 id
        String zh = ZH_GUN.get(pathOf(gun));
        return zh != null ? zh : prettify(gun);
    }

    /**
     * 从枪包的枪械索引里取显示名。
     * <p>ClientGunIndex.getName() 返回的是 i18n key（如 {@code tacz.gun.ak47.name}），
     * 用 Component.translatable 翻译成当前语言；翻译不到就返回 null 走下一级兜底。
     */
    private static String gunPackName(String gun) {
        ResourceLocation rl = ResourceLocation.tryParse(gun);
        if (rl == null) return null;
        String key = null;
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object opt = api.getMethod("getClientGunIndex", ResourceLocation.class).invoke(null, rl);
            Object idx = opt instanceof java.util.Optional<?> o ? o.orElse(null) : opt;
            if (idx == null) {
                // 客户端索引没加载时回落通用索引
                Object opt2 = api.getMethod("getCommonGunIndex", ResourceLocation.class).invoke(null, rl);
                Object common = opt2 instanceof java.util.Optional<?> o2 ? o2.orElse(null) : opt2;
                if (common != null) {
                    Object pojo = common.getClass().getMethod("getPojo").invoke(common);
                    if (pojo != null) {
                        Object nm = pojo.getClass().getMethod("getName").invoke(pojo);
                        if (nm instanceof String s2 && !s2.isBlank()) key = s2;
                    }
                }
            } else {
                Object nm = idx.getClass().getMethod("getName").invoke(idx);
                if (nm instanceof String s && !s.isBlank()) key = s;
            }
        } catch (Throwable ignored) {
            return null;
        }
        if (key == null) return null;
        if (key.indexOf('.') < 0) return key;   // 已经是明文名字
        try {
            String translated = net.minecraft.network.chat.Component.translatable(key).getString();
            if (translated != null && !translated.isBlank() && !translated.equals(key)) return translated;
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        String p = colon >= 0 ? id.substring(colon + 1) : id;
        return p.toLowerCase().replace('-', '_');
    }

    private static String skinName(String skin) {
        String path = pathOf(skin);
        String direct = ZH_SKIN.get(path);
        if (direct != null) return direct;
        for (java.util.Map.Entry<String, String> e : ZH_SKIN.entrySet()) {
            if (path.endsWith("_" + e.getKey())) {

                String key = "filtdm.skin." + e.getKey();
                try {
                    String s = net.minecraft.network.chat.Component.translatable(key).getString();
                    if (s != null && !s.isBlank() && !s.equals(key)) return s;
                } catch (RuntimeException ignored) {
                }
                return e.getValue();
            }
        }
        return prettify(skin);
    }

    private static String prettify(String id) {
        String path = id;
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        path = path.replace('/', ' ').replace('-', ' ');
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            if (p.length() <= 4 && p.matches("[a-zA-Z0-9]+")) {
                sb.append(p.toUpperCase());
            } else {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return sb.length() == 0 ? id : sb.toString();
    }
}
