package com.emberrealm.quest.world;

import com.emberrealm.quest.core.Env;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Optional: embeds free text with a local Ollama (model all-minilm, 384 dimensions), the same model used for the dataset. */
public final class Ollama {

    public static final String MODEL = "all-minilm";

    private Ollama() {
    }

    public static boolean available() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(Env.ollamaUrl() + "/api/tags"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            return client().send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public static Optional<float[]> embed(String text) {
        try {
            String body = World.JSON.writeValueAsString(Map.of("model", MODEL, "prompt", text));
            HttpRequest req = HttpRequest.newBuilder(URI.create(Env.ollamaUrl() + "/api/embeddings"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = client().send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return Optional.empty();
            JsonNode arr = World.JSON.readTree(res.body()).get("embedding");
            float[] v = new float[arr.size()];
            for (int i = 0; i < v.length; i++) v[i] = (float) arr.get(i).asDouble();
            return Optional.of(v);
        } catch (IOException | InterruptedException e) {
            return Optional.empty();
        }
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }
}
