package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * 把配置里的 id 显示成人看着舒服的名字。
 *
 *  1) 普通物品 → 用物品自己的名字（走语言文件，中文客户端就是中文）
 *  2) TaCZ 枪 id（不是物品）→ 美化 id：tacz:ak47 → AK47，tacz:glock_17 → Glock 17
 *  3) 带了皮肤（gun|skin）→ 后面跟 [皮肤名]
 */
public final class TcpGunNames {

    private TcpGunNames() {
    }

    /**
     * 配件名（中文优先）：TaCZ 配件索引的 name 是翻译键（tacz.attachment.xxx.name），
     * 客户端能直接取到中文；取不到就回退通用美化。
     */
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
        return skin == null || skin.isEmpty() ? name : name + " [" + prettify(skin) + "]";
    }

    private static String resolve(String gun) {
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
        return prettify(gun);
    }

    /** "tacz:glock_17" → "Glock 17" */
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
