package com.emberrealm.quest.lessons.l100_03;

import com.emberrealm.quest.core.Ctx;

import java.util.Map;
import java.util.TreeMap;

/**
 * Groups the keys found by SCAN the way the Redis Insight Browser tree does: by the segment right after
 * the prefix (item, player, zone...) and then by type. Shared by both labs so they print the same table.
 */
final class Tour {

    private static final class Entry {
        int count;
        String example;
    }

    /** entity -> TYPE reply -> count and first key seen. */
    private final Map<String, Map<String, Entry>> groups = new TreeMap<>();
    private final Map<String, Integer> byType = new TreeMap<>();
    private final String prefix;
    private int total;

    Tour(String prefix) {
        this.prefix = prefix + ":";
    }

    void add(String key, String type) {
        total++;
        String rest = key.startsWith(prefix) ? key.substring(prefix.length()) : key;
        int colon = rest.indexOf(':');
        String entity = colon < 0 ? rest : rest.substring(0, colon);
        Entry entry = groups.computeIfAbsent(entity, e -> new TreeMap<>()).computeIfAbsent(type, t -> new Entry());
        if (entry.count == 0) entry.example = key;
        entry.count++;
        byType.merge(type, 1, Integer::sum);
    }

    int total() {
        return total;
    }

    int count(String entity, String type) {
        Map<String, Entry> types = groups.get(entity);
        if (types == null || !types.containsKey(type)) return 0;
        return types.get(type).count;
    }

    int distinctTypes() {
        return byType.size();
    }

    void print(Ctx ctx) {
        ctx.out.info(String.format("%-10s %-11s %5s   %s", "entidade", "tipo", "qtd", "exemplo"));
        for (Map.Entry<String, Map<String, Entry>> group : groups.entrySet()) {
            for (Map.Entry<String, Entry> byKind : group.getValue().entrySet()) {
                Entry entry = byKind.getValue();
                ctx.out.info(String.format("%-10s %-11s %5d   %s", group.getKey(), label(byKind.getKey()), entry.count, entry.example));
            }
        }
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, Integer> t : byType.entrySet()) {
            if (summary.length() > 0) summary.append(", ");
            summary.append(label(t.getKey())).append(": ").append(t.getValue());
        }
        ctx.out.kv("por tipo", summary);
    }

    /** TYPE answers with internal names; show the name a Java developer recognises. */
    static String label(String type) {
        return switch (type) {
            case "ReJSON-RL" -> "JSON";
            case "zset" -> "sorted set";
            case "MBbloom--" -> "bloom";
            case "TSDB-TYPE" -> "timeseries";
            default -> type;
        };
    }
}
