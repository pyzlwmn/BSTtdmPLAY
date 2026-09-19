package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.loadout;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * id 查询工具（指令 /tdmloadout ids）
 *
 * 作用：主人不知道枪械/弹药盒的准确 id 时，这里把「TaCZ 注册的枪 id」和
 * 「所有名字里带 ammo / 属于 tacz 命名空间的物品 id」打出来，照着填 loadout.json 就行。
 */
public final class IdDump {

    private IdDump() {
    }

    public static List<String> dump() {
        List<String> out = new ArrayList<>();
        out.add("§6===== TaCZ 枪械 id =====");
        List<String> guns = taczGunIds();
        if (guns.isEmpty()) {
            out.add("§c（读不到：没装 TaCZ 或 API 变了，可以看日志）");
        } else {
            out.add("§7共 " + guns.size() + " 把，下面是前 80 个：");
            for (int i = 0; i < Math.min(80, guns.size()); i++) out.add("§f" + guns.get(i));
        }

        out.add("§6===== TaCZ 配件 id =====");
        List<String> atts = AttachmentIndex.all();
        if (atts.isEmpty()) {
            out.add("§c（没发现配件：没装 TaCZ / 枪包里没有配件）");
        } else {
            out.add("§7共 " + atts.size() + " 个（界面里 attachments 留空就自动用这些）：");
            for (String s : atts) out.add("§f" + s);
        }

        out.add("§6===== 名字带 ammo 的物品 id =====");
        List<String> ammo = itemIds(id -> id.contains("ammo"));
        if (ammo.isEmpty()) {
            out.add("§c（没有找到名字带 ammo 的物品，弹药盒可能叫别的名字）");
        } else {
            for (String s : ammo) out.add("§f" + s);
        }

        out.add("§6===== tacz 命名空间物品（前 40 个）=====");
        List<String> taczItems = itemIds(id -> id.startsWith("tacz:"));
        for (int i = 0; i < Math.min(40, taczItems.size()); i++) out.add("§f" + taczItems.get(i));

        out.add("§6===== 当前 loadout.json 内容 =====");
        out.add("§7" + LoadoutConfig.toJson().replace("\n", " ").replaceAll("\\s+", " "));
        return out;
    }

    /** 反射拿 TaCZ 的全部枪械 id（不同版本方法名不一样，逐个试） */
    private static List<String> taczGunIds() {
        TreeSet<String> ids = new TreeSet<>();
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            for (String name : new String[]{"getAllCommonGunIndex", "getAllClientGunIndex", "getCommonGunIndexMap", "getAllGunIndex"}) {
                try {
                    Method m = api.getMethod(name);
                    Object r = m.invoke(null);
                    collectKeys(r, ids);
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(ids);
    }

    private static void collectKeys(Object result, TreeSet<String> ids) {
        if (result instanceof Map<?, ?> map) {
            for (Object k : map.keySet()) {
                if (k != null) ids.add(k.toString());
            }
        } else if (result instanceof Iterable<?> it) {
            // TaCZ 1.1.7 返回 Set<Map.Entry<ResourceLocation, CommonGunIndex>>
            for (Object o : it) {
                if (o instanceof Map.Entry<?, ?> entry && entry.getKey() != null) {
                    ids.add(entry.getKey().toString());
                }
            }
        }
    }

    private static List<String> itemIds(java.util.function.Predicate<String> filter) {
        TreeSet<String> ids = new TreeSet<>();
        try {
            for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
                String s = rl.toString();
                if (filter.test(s)) ids.add(s);
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(ids);
    }

    public static List<String> empty() {
        return Collections.emptyList();
    }
}
