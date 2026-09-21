package com.emberrealm.quest.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Everything a lab or check needs: namespaced keys, console, and the progress marker. */
public final class Ctx {

    public final Keys keys;
    public final Console out;
    public final String lesson;
    public final String client;
    private boolean unavailable;

    public Ctx(Keys keys, Console out, String lesson, String client) {
        this.keys = keys;
        this.out = out;
        this.lesson = lesson;
        this.client = client;
    }

    /** Shortcut for keys.of(...). */
    public String k(String... parts) {
        return keys.of(parts);
    }

    public String progressKey() {
        return keys.of("progress", lesson);
    }

    /** Marker written by the student's own exercise code ("Sua vez"); the check requires it. */
    public String exerciseKey() {
        return keys.of("exercise", lesson);
    }

    /** Invalidate evidence from a previous successful attempt before a new connection is tried. */
    public void begin() {
        try (redis.clients.jedis.RedisClient marker = Clients.jedis()) {
            marker.hset(progressKey(), Map.of("status", "running", "client", client, "at", Instant.now().toString()));
        }
    }

    /** Records that the exercise ran, with a few stats the check can read back. Pairs: field, value... */
    public void exerciseDone(String... fieldValuePairs) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("client", client);
        fields.put("at", Instant.now().toString());
        fields.put("ran_" + client, "1");
        for (int i = 0; i + 1 < fieldValuePairs.length; i += 2) {
            fields.put(fieldValuePairs[i], fieldValuePairs[i + 1]);
        }
        try (redis.clients.jedis.RedisClient marker = Clients.jedis()) {
            marker.hset(exerciseKey(), fields);
        }
        out.blank();
        out.ok("Sua vez registrada na lição " + lesson + " com " + client + ". Marcador: " + exerciseKey());
        out.info("Confira com: ./quest check " + lesson);
    }

    /** Records that this lesson ran, with a few stats the check can read back. Pairs: field, value, field, value... */
    public void done(String... fieldValuePairs) {
        record(false, fieldValuePairs);
    }

    /** Records an attempted lab whose required environment or observation is missing. */
    public void unavailable(String... fieldValuePairs) {
        record(true, fieldValuePairs);
    }

    public boolean isUnavailable() {
        return unavailable;
    }

    private void record(boolean unavailable, String... fieldValuePairs) {
        this.unavailable = unavailable;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("status", unavailable ? "unavailable" : "ran");
        fields.put("client", client);
        fields.put("ran_" + client, "1");
        fields.put("at", Instant.now().toString());
        for (int i = 0; i + 1 < fieldValuePairs.length; i += 2) {
            fields.put(fieldValuePairs[i], fieldValuePairs[i + 1]);
        }
        try (redis.clients.jedis.RedisClient marker = Clients.jedis()) {
            marker.hset(progressKey(), fields);
        }
        out.blank();
        if (unavailable) out.warn("Lição " + lesson + " parcial/indisponível com " + client + ". Confira os requisitos e rode de novo.");
        else out.ok("Lab " + lesson + " executado com " + client + ". Valide os resultados com verify.");
        out.info("Confira com: ./quest check " + lesson);
    }
}
