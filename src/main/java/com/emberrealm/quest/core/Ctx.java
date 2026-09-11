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

    /** Records that this lesson ran, with a few stats the check can read back. Pairs: field, value, field, value... */
    public void done(String... fieldValuePairs) {
        Map<String, String> fields = new LinkedHashMap<>();
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
        out.ok("Lição " + lesson + " concluída com " + client + ". Marcador: " + progressKey());
        out.info("Confira com: ./quest check " + lesson);
    }
}
