package com.emberrealm.quest.core;

import com.emberrealm.quest.world.Seed;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CLI entry point.
 *   quest list                  lessons and which ones you already completed
 *   quest seed                  load the Ember Realm world into your Redis
 *   quest run 101-02 jedis      run the guided lab of a lesson with one client (jedis | lettuce | both)
 *   quest exercise 101-02 jedis run YOUR code for the lesson (JedisExercise / LettuceExercise)
 *   quest solve 101-02 --yes    copy the reference solution over your exercise files
 *   quest check 101-02          verify the lesson state in Redis (or: check all)
 *   quest reset --yes           delete every key under your prefix
 *   quest doctor                connectivity report (same as run 100-01 jedis)
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Console out = new Console();
        int code;
        try {
            code = dispatch(args, out);
        } catch (Exception e) {
            out.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
            out.hint("URL em uso: " + Env.redacted(Env.redisUrl()) + ". Veja .env.example e a lição 100-02.");
            code = 1;
        } finally {
            Clients.shutdownLettuce();
        }
        System.exit(code);
    }

    private static int dispatch(String[] args, Console out) throws Exception {
        if (args.length == 0) return usage(out);
        Keys keys = new Keys(Env.prefix());
        switch (args[0]) {
            case "list" -> { return list(out, keys); }
            case "seed" -> { return seed(out, keys); }
            case "doctor" -> { return run(out, keys, "100-01", "jedis"); }
            case "run" -> {
                if (args.length < 2) return usage(out);
                String client = args.length >= 3 ? args[2] : "jedis";
                if (client.equals("both")) {
                    int a = run(out, keys, args[1], "jedis");
                    int b = run(out, keys, args[1], "lettuce");
                    return a == 0 && b == 0 ? 0 : 1;
                }
                return run(out, keys, args[1], client);
            }
            case "start" -> {
                if (args.length < 2) return usage(out);
                return withLesson(out, args[1], l -> StepEngine.start(new Ctx(keys, out, l.id(), "steps"), l));
            }
            case "steps" -> {
                if (args.length >= 2 && (args[1].equals("--json") || args[1].equals("--js"))) {
                    String json = StepEngine.toJson();
                    System.out.println(args[1].equals("--js") ? "window.QUEST = window.QUEST || {};\nwindow.QUEST.steps = " + json + ";" : json);
                    return 0;
                }
                if (args.length < 2) return usage(out);
                return withLesson(out, args[1], l -> StepEngine.show(new Ctx(keys, out, l.id(), "steps"), l, StepEngine.stepsFor(l), "Passos:"));
            }
            case "verify", "check" -> {
                if (args.length < 2) return usage(out);
                if (args[1].equals("all")) {
                    int worst = 0;
                    for (Lessons.Lesson l : Lessons.all()) {
                        if (StepEngine.stepsFor(l).isEmpty()) continue;
                        out.h1("verify " + l.id() + " " + l.title());
                        worst = Math.max(worst, StepEngine.verify(new Ctx(keys, out, l.id(), "steps"), l));
                    }
                    return worst;
                }
                return withLesson(out, args[1], l -> {
                    out.h1("verify " + l.id() + " " + l.title());
                    return StepEngine.verify(new Ctx(keys, out, l.id(), "steps"), l);
                });
            }
            case "skip" -> {
                if (args.length < 2) return usage(out);
                String selector = args.length >= 3 ? args[2] : null;
                return withLesson(out, args[1], l -> StepEngine.solve(new Ctx(keys, out, l.id(), "steps"), l, selector, true));
            }
            case "next" -> {
                Optional<Lessons.Lesson> next = StepEngine.nextLesson(keys);
                if (next.isEmpty()) {
                    out.ok("Você concluiu todos os passos de todas as lições. Parabéns, aventureiro.");
                    return 0;
                }
                out.h1(next.get().id() + " " + next.get().title());
                return StepEngine.start(new Ctx(keys, out, next.get().id(), "steps"), next.get());
            }
            case "exercise" -> {
                if (args.length < 2) return usage(out);
                String client = args.length >= 3 ? args[2] : "jedis";
                if (client.equals("both")) {
                    int a = exercise(out, keys, args[1], "jedis");
                    int b = exercise(out, keys, args[1], "lettuce");
                    return a == 0 && b == 0 ? 0 : Math.max(a, b);
                }
                return exercise(out, keys, args[1], client);
            }
            case "solve" -> {
                if (args.length < 2) return usage(out);
                String selector = args.length >= 3 && !args[2].startsWith("--") ? args[2] : null;
                return withLesson(out, args[1], l -> StepEngine.solve(new Ctx(keys, out, l.id(), "steps"), l, selector, false));
            }
            case "reset" -> { return reset(out, keys, args.length >= 2 && args[1].equals("--yes")); }
            case "progress" -> { return progress(out, keys); }
            default -> { return usage(out); }
        }
    }

    private static int usage(Console out) {
        out.h1("Redis Java Quest");
        out.info("quest list                 lições e progresso");
        out.info("quest seed                 carrega o mundo Ember Realm no seu Redis");
        out.info("quest start <id>           prepara a lição e mostra os passos (setup)");
        out.info("quest verify <id|all>      confere cada passo no Redis e marca os concluídos");
        out.info("quest solve <id> [passo]   faz o próximo passo (ou o passo n) por você");
        out.info("quest skip <id> [passo]    igual ao solve, mas marca o passo como pulado");
        out.info("quest next                 vai para a primeira lição com passo pendente");
        out.info("quest run <id> <client>    roda o lab pronto: client = jedis | lettuce | both");
        out.info("quest exercise <id> <client>  roda o SEU código (JedisExercise / LettuceExercise)");
        out.info("quest reset --yes          apaga todas as chaves do seu prefixo");
        out.info("quest doctor               relatório de conexão");
        out.blank();
        out.info("Redis: " + Env.redacted(Env.redisUrl()) + "   prefixo: " + Env.prefix());
        return 2;
    }

    private static int list(Console out, Keys keys) {
        Map<String, Map<String, String>> stepProgress = new HashMap<>();
        try (redis.clients.jedis.RedisClient jedis = Clients.jedis()) {
            for (Lessons.Lesson l : Lessons.all()) stepProgress.put(l.id(), jedis.hgetAll(StepEngine.stepsKey(keys, l.id())));
        } catch (Exception ignored) {
            // no Redis reachable: list still works, just without progress
        }
        String course = "";
        out.h1("Lições");
        for (Lessons.Lesson l : Lessons.all()) {
            if (!l.course().equals(course)) {
                course = l.course();
                out.blank();
                out.step(course);
            }
            int[] c = StepEngine.counts(keys, l, stepProgress.getOrDefault(l.id(), Map.of()));
            String mark;
            if (c[1] == 0) mark = "[ ] (em breve)   ";
            else if (c[0] == c[1]) mark = "[x] " + c[0] + "/" + c[1] + " passos";
            else if (c[0] > 0) mark = "[~] " + c[0] + "/" + c[1] + " passos";
            else mark = "[ ] " + c[0] + "/" + c[1] + " passos";
            out.info(String.format("%-18s %s  %s", mark, l.id(), l.title()));
        }
        out.blank();
        out.info("[x] todos os passos   [~] em andamento   [ ] não começou.   ./quest next leva ao próximo passo pendente.");
        out.info("Redis: " + Env.redacted(Env.redisUrl()) + "   prefixo: " + keys.prefix());
        return 0;
    }

    private static Map<String, Boolean> doneMarkers(Keys keys, String kind) {
        Map<String, Boolean> done = new HashMap<>();
        try (redis.clients.jedis.RedisClient jedis = Clients.jedis()) {
            for (Lessons.Lesson l : Lessons.all()) {
                done.put(l.id(), jedis.exists(keys.of(kind, l.id())));
            }
        } catch (Exception ignored) {
            // no Redis reachable: list still works, just without progress
        }
        return done;
    }

    private static int exercise(Console out, Keys keys, String id, String client) throws Exception {
        Optional<Lessons.Lesson> lesson = Lessons.byId(id);
        if (lesson.isEmpty()) {
            out.fail("Lição desconhecida: " + id + ". Use: quest list");
            return 2;
        }
        Optional<Lab> exercise = Lessons.exercise(lesson.get(), client);
        if (exercise.isEmpty()) {
            out.warn("Lição " + id + " ainda não tem exercício para " + client + ".");
            return 3;
        }
        String file = "src/main/java/com/emberrealm/quest/lessons/l" + id.replace('-', '_') + "/"
                + (client.equals("jedis") ? "JedisExercise" : "LettuceExercise") + ".java";
        out.h1(id + " Sua vez: " + lesson.get().title() + "  [" + client + "]");
        out.info("Seu código: " + file);
        Ctx ctx = new Ctx(keys, out, id, client);
        try {
            exercise.get().run(ctx);
            return 0;
        } catch (Todo todo) {
            out.blank();
            out.warn("Sua vez: " + todo.getMessage());
            out.hint("Edite " + file + " e rode de novo: ./quest exercise " + id + " " + client);
            out.hint("Travou? ./quest solve " + id + " --yes copia a solução de referência por cima do seu arquivo.");
            return 4;
        }
    }

    @FunctionalInterface
    interface LessonCommand {
        int run(Lessons.Lesson lesson) throws Exception;
    }

    private static int withLesson(Console out, String id, LessonCommand command) throws Exception {
        Optional<Lessons.Lesson> lesson = Lessons.byId(id);
        if (lesson.isEmpty()) {
            out.fail("Lição desconhecida: " + id + ". Use: quest list");
            return 2;
        }
        return command.run(lesson.get());
    }

    private static int seed(Console out, Keys keys) throws Exception {
        Ctx ctx = new Ctx(keys, out, "seed", "jedis");
        new Seed().run(ctx);
        return 0;
    }

    private static int run(Console out, Keys keys, String id, String client) throws Exception {
        Optional<Lessons.Lesson> lesson = Lessons.byId(id);
        if (lesson.isEmpty()) {
            out.fail("Lição desconhecida: " + id + ". Use: quest list");
            return 2;
        }
        Optional<Lab> lab = Lessons.lab(lesson.get(), client);
        if (lab.isEmpty()) {
            out.warn("Lição " + id + " ainda não tem código para " + client + " (em breve).");
            return 3;
        }
        out.h1(id + " " + lesson.get().title() + "  [" + client + "]");
        out.info("Redis: " + Env.redacted(Env.redisUrl()) + "   prefixo: " + keys.prefix());
        Ctx ctx = new Ctx(keys, out, id, client);
        lab.get().run(ctx);
        return 0;
    }

    private static int reset(Console out, Keys keys, boolean confirmed) {
        List<String> found = new ArrayList<>();
        try (redis.clients.jedis.RedisClient jedis = Clients.jedis()) {
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match(keys.pattern()).count(500);
            do {
                ScanResult<String> page = jedis.scan(cursor, params);
                found.addAll(page.getResult());
                cursor = page.getCursor();
            } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
            if (!confirmed) {
                out.warn(found.size() + " chaves com prefixo '" + keys.prefix() + ":' seriam apagadas. Confirme com: quest reset --yes");
                return 0;
            }
            for (int i = 0; i < found.size(); i += 200) {
                List<String> batch = found.subList(i, Math.min(i + 200, found.size()));
                jedis.unlink(batch.toArray(new String[0]));
            }
        }
        out.ok(found.size() + " chaves apagadas do prefixo '" + keys.prefix() + ":'. Rode quest seed para recarregar o mundo.");
        return 0;
    }

    private static int progress(Console out, Keys keys) {
        Map<String, Boolean> done = doneMarkers(keys, "progress");
        long count = done.values().stream().filter(Boolean::booleanValue).count();
        out.h1("Progresso: " + count + "/" + Lessons.all().size() + " lições");
        for (Lessons.Lesson l : Lessons.all()) {
            if (done.getOrDefault(l.id(), false)) out.ok(l.id() + "  " + l.title());
        }
        return 0;
    }
}
