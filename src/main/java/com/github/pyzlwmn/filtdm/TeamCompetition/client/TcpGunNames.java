package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class TcpGunNames {

    private static final java.util.Map<String, String> ZH_GUN = new java.util.HashMap<>();

    static {
        ZH_GUN.put("ak47", "AK-47");
        ZH_GUN.put("m4a1", "M4A1");
        ZH_GUN.put("hk416d", "HK416D");
        ZH_GUN.put("hk416", "HK416");
        ZH_GUN.put("scar_h", "SCAR-H");
        ZH_GUN.put("scar_l", "SCAR-L");
        ZH_GUN.put("awp", "AWP");
        ZH_GUN.put("svd", "SVD");
        ZH_GUN.put("mk14", "MK14");
        ZH_GUN.put("m14", "M14");
        ZH_GUN.put("vector45", "Vector .45");
        ZH_GUN.put("vector", "Vector");
        ZH_GUN.put("mp5a5", "MP5A5");
        ZH_GUN.put("mp5", "MP5");
        ZH_GUN.put("as_val", "AS Val");
        ZH_GUN.put("m870", "M870");
        ZH_GUN.put("aa12", "AA-12");
        ZH_GUN.put("deagle", "沙漠之鹰");
        ZH_GUN.put("desert_eagle", "沙漠之鹰");
        ZH_GUN.put("glock_17", "格洛克 17");
        ZH_GUN.put("glock17", "格洛克 17");
        ZH_GUN.put("m249", "M249");
        ZH_GUN.put("m1911", "M1911");
        ZH_GUN.put("p90", "P90");
        ZH_GUN.put("ump45", "UMP45");
        ZH_GUN.put("m95", "M95");
        ZH_GUN.put("m107", "M107");
        ZH_GUN.put("qbu201", "QBU-201");
        ZH_GUN.put("qbu10", "QBU-10");
        ZH_GUN.put("qjz171", "QJZ-171");
        ZH_GUN.put("spas_12", "SPAS-12");
        ZH_GUN.put("m1014", "M1014");
        ZH_GUN.put("rpg7", "RPG-7");
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

        String byKey = taczTranslation(gun);
        if (byKey != null) return byKey;

        String byOwn = ownTranslation(gun);
        if (byOwn != null) return byOwn;

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

        String zh = ZH_GUN.get(pathOf(gun));
        return zh != null ? zh : prettify(gun);
    }

    private static String ownTranslation(String id) {
        String path = pathOf(id);
        String key = "filtdm.gun." + path;
        try {
            String s = net.minecraft.network.chat.Component.translatable(key).getString();
            if (s != null && !s.isBlank() && !s.equals(key)) return s;
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        String p = colon >= 0 ? id.substring(colon + 1) : id;
        return p.toLowerCase().replace('-', '_');
    }

    private static String taczTranslation(String id) {
        String path = pathOf(id);
        for (String key : new String[]{"tacz.gun." + path + ".name", "tacz." + path + ".name"}) {
            try {
                String s = net.minecraft.network.chat.Component.translatable(key).getString();
                if (s != null && !s.isBlank() && !s.equals(key) && !s.startsWith("tacz.")) return s;
            } catch (RuntimeException ignored) {
            }
        }
        return null;
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
