package com.emberrealm.quest.world;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** The Ember Realm dataset shipped in src/main/resources/world. Fictional MMORPG data, safe to share. */
public final class World {

    public record Stats(int attack, int defense, int magic, int healing, int speed) {
    }

    public record Item(String id, String name, String type, String slot, String rarity, int level, int price,
                       List<String> classes, Stats stats, String description, float[] embedding) {
    }

    public record Player(String id, String name, String playerClass, int level, int hp, int mana, int gold, long xp,
                         String zone, String guild, List<String> achievements) {
    }

    public record Zone(String id, String name, int levelMin, int levelMax, String danger, double lat, double lon) {
    }

    public record Query(String id, String text, float[] embedding) {
    }

    public static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private World() {
    }

    public static List<Item> items() {
        return read("/world/items.json", Item.class);
    }

    public static List<Player> players() {
        return read("/world/players.json", Player.class);
    }

    public static List<Zone> zones() {
        return read("/world/zones.json", Zone.class);
    }

    public static List<Query> queries() {
        return read("/world/queries.json", Query.class);
    }

    /** Raw JSON documents of the items, exactly as stored with JSON.SET (embedding included). */
    public static List<Map<String, Object>> itemDocuments() {
        return readMaps("/world/items.json");
    }

    private static <T> List<T> read(String resource, Class<T> type) {
        try (InputStream in = World.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("missing resource " + resource);
            return JSON.readValue(in, JSON.getTypeFactory().constructCollectionType(List.class, type));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + resource, e);
        }
    }

    private static List<Map<String, Object>> readMaps(String resource) {
        try (InputStream in = World.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("missing resource " + resource);
            return JSON.readValue(in, JSON.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + resource, e);
        }
    }
}
