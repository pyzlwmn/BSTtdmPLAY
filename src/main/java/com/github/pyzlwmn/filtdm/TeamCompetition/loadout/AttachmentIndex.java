package com.github.pyzlwmn.filtdm.TeamCompetition.loadout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class AttachmentIndex {

    private static final Logger LOG = LoggerFactory.getLogger("FIL-TDM-Loadout");

    private static final String[] ALL_METHODS = {
            "getAllCommonAttachmentIndex",
            "getAllClientAttachmentIndex",
            "getCommonAttachmentIndexMap",
            "getAttachmentIndexMap",
    };

    public static final List<String> TYPE_ORDER = List.of(
            "SCOPE", "MUZZLE", "STOCK", "GRIP", "LASER", "EXTENDED_MAG");
    public static final Map<String, String> TYPE_LABEL = Map.of(
            "SCOPE", "瞄具", "MUZZLE", "枪口", "STOCK", "枪托",
            "GRIP", "握把", "LASER", "激光", "EXTENDED_MAG", "弹匣", "MISC", "其它");

    private static List<String> cache = null;
    private static Map<String, String> typeCache = null;

    private AttachmentIndex() {
    }

    public static List<String> all() {
        if (cache != null) return cache;

        TreeSet<String> ids = new TreeSet<>();
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            for (String name : ALL_METHODS) {
                try {
                    Method m = api.getMethod(name);
                    Object r = m.invoke(null);

                    if (r instanceof Map<?, ?> map) {
                        for (Object k : map.keySet()) {
                            if (k != null) ids.add(k.toString());
                        }
                    } else if (r instanceof Iterable<?> it) {
                        for (Object o : it) {
                            if (o instanceof Map.Entry<?, ?> entry && entry.getKey() != null) {
                                ids.add(entry.getKey().toString());
                            } else if (o != null) {
                                ids.add(o.toString());
                            }
                        }
                    }
                } catch (NoSuchMethodException ignored) {

                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            LOG.debug("[TDM·背包] 读不到 TaCZ 配件索引：{}", t.toString());
        }

        cache = new ArrayList<>(ids);
        LOG.info("[TDM·背包] 自动发现 TaCZ 配件 {} 个{}", cache.size(),
                cache.isEmpty() ? "（没装 TaCZ / 枪包里没有配件 → 不显示配件页）" : "");
        return cache;
    }

    public static String typeOf(String attachmentId) {
        Map<String, String> types = types();
        return types.getOrDefault(attachmentId, "MISC");
    }

    private static Map<String, String> types() {
        if (typeCache != null) return typeCache;
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Method get = api.getMethod("getCommonAttachmentIndex", net.minecraft.resources.ResourceLocation.class);
            for (String id : all()) {
                String type = "MISC";
                try {
                    net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
                    if (rl != null) {
                        Object opt = get.invoke(null, rl);
                        Object idx = unwrapOptional(opt);
                        if (idx != null) {
                            Object t = null;
                            for (String m : new String[]{"getType", "type"}) {
                                try {
                                    t = idx.getClass().getMethod(m).invoke(idx);
                                    break;
                                } catch (NoSuchMethodException ignored) {
                                }
                            }
                            if (t != null) type = t.toString().toUpperCase();
                        }
                    }
                } catch (Throwable ignored) {
                }
                out.put(id, type);
            }
        } catch (Throwable t) {
            LOG.debug("[TDM·背包] 配件类型识别不可用（全部归到 MISC）：{}", t.toString());
        }
        typeCache = out;
        return out;
    }

    private static Object unwrapOptional(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof java.util.Optional<?> opt) return opt.orElse(null);
        } catch (Throwable ignored) {
        }
        return o;
    }

    public static Map<String, List<String>> byType() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String type : TYPE_ORDER) out.put(type, new ArrayList<>());
        out.put("MISC", new ArrayList<>());
        for (String id : all()) {
            String type = typeOf(id);
            out.computeIfAbsent(type, k -> new ArrayList<>()).add(id);
        }
        return out;
    }

    public static void invalidate() {
        cache = null;
        typeCache = null;
    }
}
