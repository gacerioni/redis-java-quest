package com.emberrealm.quest.core;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves configuration from (in order): JVM system properties, environment variables, a .env file.
 * The .env file is looked up in the working directory and next to the running jar.
 */
public final class Env {

    public static final String DEFAULT_REDIS_URL = "redis://localhost:6379";

    private static final Map<String, String> DOTENV = loadDotEnv();

    private Env() {
    }

    public static String get(String name, String defaultValue) {
        String v = System.getProperty(name);
        if (isBlank(v)) v = System.getenv(name);
        if (isBlank(v)) v = DOTENV.get(name);
        return isBlank(v) ? defaultValue : v.trim();
    }

    public static Optional<String> optional(String name) {
        return Optional.ofNullable(get(name, null));
    }

    /** Redis URL used by every lesson: redis://user:password@host:port, or rediss:// for TLS. */
    public static String redisUrl() {
        return get("REDIS_URL", DEFAULT_REDIS_URL);
    }

    /** Optional TLS URL of a paid database (the free plan has no TLS). Used by lesson 301-03 only. */
    public static Optional<String> redisTlsUrl() {
        return optional("REDIS_TLS_URL");
    }

    /** Ollama base URL, used only to embed new questions in lesson 201-03. */
    public static String ollamaUrl() {
        return get("OLLAMA_URL", "http://localhost:11434");
    }

    /**
     * Key prefix for everything a student creates. Defaults to the username in the URL
     * (handy when one shared database has one ACL user per student), or "quest".
     */
    public static String prefix() {
        String explicit = get("QUEST_PREFIX", null);
        if (explicit != null) return sanitize(explicit);
        String user = userFromUrl(redisUrl());
        if (user == null || user.isBlank() || user.equals("default")) return "quest";
        return sanitize(user);
    }

    static String sanitize(String raw) {
        String s = raw.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        return s.isBlank() ? "quest" : s;
    }

    static String userFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            String userInfo = uri.getUserInfo();
            if (userInfo == null) return null;
            int colon = userInfo.indexOf(':');
            return colon < 0 ? userInfo : userInfo.substring(0, colon);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Same URL with the password replaced by ****, safe to print. */
    public static String redacted(String url) {
        return url.replaceAll("(://[^:/@]*:)[^@]*@", "$1****@");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        for (Path candidate : List.of(Paths.get(".env"), jarDir().resolve(".env"), jarDir().resolve("..").resolve(".env"))) {
            if (!Files.isRegularFile(candidate)) continue;
            try {
                for (String line : Files.readAllLines(candidate, StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) continue;
                    if (t.startsWith("export ")) t = t.substring(7).trim();
                    int eq = t.indexOf('=');
                    if (eq <= 0) continue;
                    String key = t.substring(0, eq).trim();
                    String value = t.substring(eq + 1).trim();
                    if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    map.putIfAbsent(key, value);
                }
            } catch (IOException ignored) {
                // a broken .env must never stop a lesson; environment variables still work
            }
            break;
        }
        return map;
    }

    private static Path jarDir() {
        try {
            Path p = Paths.get(Env.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isDirectory(p) ? p : p.getParent();
        } catch (Exception e) {
            return Paths.get(".");
        }
    }
}
