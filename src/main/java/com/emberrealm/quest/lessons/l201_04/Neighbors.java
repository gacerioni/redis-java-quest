package com.emberrealm.quest.lessons.l201_04;

import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.lessons.l201_01.Table;
import com.emberrealm.quest.world.World;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared bits of lesson 201-04: the element attributes, and a table of VSIM results with names. */
final class Neighbors {

    static final String SELF = "espada-de-brasa";
    static final String EPIC_FILTER = ".rarity == \"epico\"";

    private Neighbors() {
    }

    static Map<String, World.Item> byId() {
        Map<String, World.Item> map = new LinkedHashMap<>();
        for (World.Item item : World.items()) map.put(item.id(), item);
        return map;
    }

    /** The JSON attributes stored next to each vector: what FILTER expressions can see. */
    static String attributes(World.Item item) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("type", item.type());
        attrs.put("rarity", item.rarity());
        attrs.put("level", item.level());
        attrs.put("price", item.price());
        try {
            return World.JSON.writeValueAsString(attrs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize attributes of " + item.id(), e);
        }
    }

    /** VSIM WITHSCORES comes back as element -> similarity (1.0 = identical). Highest first. */
    static void table(Ctx ctx, Map<String, Double> scores, Map<String, World.Item> items) {
        List<String[]> rows = new ArrayList<>();
        scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    World.Item item = items.get(e.getKey());
                    rows.add(new String[]{
                            e.getKey(),
                            item == null ? "-" : item.name(),
                            item == null ? "-" : item.type(),
                            item == null ? "-" : item.rarity(),
                            Table.decimal(e.getValue(), 3)});
                });
        Table.print(ctx.out, new String[]{"elemento", "name", "type", "rarity", "score"}, rows);
    }

    /** The closest element that is not the query element itself. */
    static String nearest(Map<String, Double> scores, String self) {
        return scores.entrySet().stream()
                .filter(e -> !e.getKey().equals(self))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("-");
    }
}
