package com.emberrealm.quest.lessons.l102_02;

import com.emberrealm.quest.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The combat log entries shared by 102-02 (Streams), 102-03 (consumer groups) and 102-04.
 * Deterministic: the same 20 blows every run, only the stream ids change (the server mints them).
 */
public final class CombatEvents {

    public static final int COUNT = 20;

    static final String[] MONSTERS = {
            "lobo-de-cinzas", "esqueleto-rubro", "troll-do-pantano",
            "harpia-gelida", "aranha-de-brasa", "golem-de-ashenmoor"};

    private CombatEvents() {
    }

    /** Twenty events with the fields attacker, target, damage and zone (all strings, tiny payload). */
    public static List<Map<String, String>> events() {
        List<World.Player> players = World.players();
        List<Map<String, String>> events = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            World.Player attacker = players.get(i % players.size());
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("attacker", attacker.id());
            fields.put("target", MONSTERS[i % MONSTERS.length]);
            fields.put("damage", String.valueOf(40 + (i * 37) % 300));
            fields.put("zone", attacker.zone());
            events.add(fields);
        }
        return events;
    }

    /** One line per event for the console: "kaelith acerta lobo-de-cinzas: 40 de dano (pico-gelido)". */
    public static String describe(String id, Map<String, String> fields) {
        return id + "  " + fields.get("attacker") + " acerta " + fields.get("target") + ": "
                + fields.get("damage") + " de dano (" + fields.get("zone") + ")";
    }
}
