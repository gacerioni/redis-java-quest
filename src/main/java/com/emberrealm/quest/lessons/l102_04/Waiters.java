package com.emberrealm.quest.lessons.l102_04;

import com.emberrealm.quest.core.Console;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.world.World;

import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Shared helpers of lesson 102-04: QUEST_WAITERS, INFO clients parsing, CLIENT LIST counting, polling. */
final class Waiters {

    static final int DEFAULT = 5;
    static final int BRPOP_TIMEOUT_S = 3;
    static final String SENTINEL_GROUP = "sentinelas";
    static final String CHAT_ZONE = "floresta-de-cinzas";

    private Waiters() {
    }

    /** The relevant lines of INFO clients (Redis 7+ also reports maxclients there, no CONFIG needed). */
    record ClientsInfo(long connected, long blocked, long pubsub, long maxclients) {

        static ClientsInfo parse(String info) {
            return new ClientsInfo(field(info, "connected_clients"), field(info, "blocked_clients"),
                    field(info, "pubsub_clients"), field(info, "maxclients"));
        }

        String summary() {
            return "connected_clients=" + connected + "  blocked_clients=" + blocked
                    + "  pubsub_clients=" + pubsub + "  maxclients=" + (maxclients < 0 ? "?" : maxclients);
        }
    }

    /** What happened to one waiter: a player popped, nothing (timeout) or an error such as maxclients. */
    record Outcome(int number, String player, String error, long elapsedMs) {

        boolean rejected() {
            return error != null && error.toLowerCase().contains("max number of clients");
        }

        String describe() {
            String who = "jogador " + number + ": ";
            if (error != null) return who + "erro após " + elapsedMs + " ms: " + error;
            if (player == null) return who + "ninguém chegou em " + BRPOP_TIMEOUT_S + " s (BRPOP devolveu nil)";
            return who + "acordou com a vaga de " + player + " após " + elapsedMs + " ms";
        }
    }

    /** QUEST_WAITERS, default 5. Above 20 we warn: only do that against the local Docker Redis. */
    static int count(Console out) {
        String raw = Env.get("QUEST_WAITERS", String.valueOf(DEFAULT));
        int n;
        try {
            n = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            out.warn("QUEST_WAITERS inválido (" + raw + "), usando " + DEFAULT);
            return DEFAULT;
        }
        n = Math.max(1, Math.min(n, 200));
        if (n > 20) {
            out.warn(n + " conexões de uma vez: faça isso só contra o Redis local do docker compose. No Redis Cloud free o limite é 30.");
        }
        return n;
    }

    /** One dungeon slot per waiter, named after the seeded players (repeating when there are more waiters). */
    static String[] slots(int waiters) {
        List<World.Player> players = World.players();
        String[] slots = new String[waiters];
        for (int i = 0; i < waiters; i++) slots[i] = players.get(i % players.size()).id();
        return slots;
    }

    static long field(String info, String name) {
        return info.lines()
                .filter(line -> line.startsWith(name + ":"))
                .map(line -> Long.parseLong(line.substring(name.length() + 1).trim()))
                .findFirst()
                .orElse(-1L);
    }

    static long countLines(String clientList, String token) {
        return clientList.lines().filter(line -> line.contains(token)).count();
    }

    /** id, name, flags and cmd of the first CLIENT LIST line that mentions the token, for the console. */
    static String sampleLine(String clientList, String token) {
        return clientList.lines()
                .filter(line -> line.contains(token))
                .findFirst()
                .map(line -> {
                    StringBuilder sb = new StringBuilder();
                    for (String part : line.split(" ")) {
                        if (part.startsWith("id=") || part.startsWith("name=") || part.startsWith("flags=") || part.startsWith("cmd=")) {
                            sb.append(part).append(' ');
                        }
                    }
                    return sb.toString().trim();
                })
                .orElse("(nenhuma linha com " + token + ")");
    }

    /** The message that matters: the deepest cause, which is where the server error text ends up. */
    static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String root = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        String top = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        return top.equals(root) ? root : top + " (" + root + ")";
    }

    /** Polls every 50 ms until the condition holds or the timeout passes; returns whether it held. */
    static boolean poll(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** Daemon threads: even if something hangs, the JVM still exits when the lab ends. */
    static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
