package com.emberrealm.quest.core;

import com.emberrealm.quest.world.Seed;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
                return solve(out, args[1], args.length >= 3 && args[2].equals("--yes"));
            }
            case "check" -> {
                if (args.length < 2) return usage(out);
                if (args[1].equals("all")) {
                    int worst = 0;
                    for (Lessons.Lesson l : Lessons.all()) worst = Math.max(worst, check(out, keys, l.id()));
                    return worst;
                }
                return check(out, keys, args[1]);
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
        out.info("quest run <id> <client>    roda o lab pronto da lição: client = jedis | lettuce | both");
        out.info("quest exercise <id> <client>  roda o SEU código da lição (JedisExercise / LettuceExercise)");
        out.info("quest solve <id> --yes     copia a solução de referência por cima do seu exercício");
        out.info("quest check <id|all>       confere o estado da lição no Redis (lab e sua vez)");
        out.info("quest reset --yes          apaga todas as chaves do seu prefixo");
        out.info("quest doctor               relatório de conexão");
        out.blank();
        out.info("Redis: " + Env.redacted(Env.redisUrl()) + "   prefixo: " + Env.prefix());
        return 2;
    }

    private static int list(Console out, Keys keys) {
        Map<String, Boolean> done = doneMarkers(keys, "progress");
        Map<String, Boolean> exercised = doneMarkers(keys, "exercise");
        String course = "";
        out.h1("Lições");
        for (Lessons.Lesson l : Lessons.all()) {
            if (!l.course().equals(course)) {
                course = l.course();
                out.blank();
                out.step(course);
            }
            boolean hasCode = Lessons.lab(l, "jedis").isPresent();
            boolean hasExercise = Lessons.exercise(l, "jedis").isPresent();
            boolean labDone = done.getOrDefault(l.id(), false);
            boolean yourTurnDone = exercised.getOrDefault(l.id(), false);
            String mark;
            if (!hasCode) mark = "[ ] (em breve)";
            else if (labDone && (!hasExercise || yourTurnDone)) mark = "[x]";
            else if (labDone) mark = "[~]";
            else mark = "[ ]";
            out.info(mark + " " + l.id() + "  " + l.title() + (hasExercise && !yourTurnDone && labDone ? "   (falta a sua vez)" : ""));
        }
        out.blank();
        out.info("[x] lab e sua vez feitos   [~] lab visto, falta a sua vez   [ ] não começou");
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

    private static int solve(Console out, String id, boolean confirmed) throws IOException {
        Path from = Paths.get("solutions", "l" + id.replace('-', '_'));
        Path to = Paths.get("src", "main", "java", "com", "emberrealm", "quest", "lessons", "l" + id.replace('-', '_'));
        if (!Files.isDirectory(from)) {
            out.warn("Não há solução de referência para " + id + " (pasta " + from + " não existe). Rode a partir da raiz do repositório.");
            return 3;
        }
        List<Path> files;
        try (var stream = Files.list(from)) {
            files = stream.filter(f -> f.toString().endsWith(".java")).sorted().toList();
        }
        if (!confirmed) {
            out.warn("Isso sobrescreve o SEU código em " + to + ":");
            for (Path f : files) out.info("  " + to.resolve(f.getFileName()));
            out.hint("Confirme com: ./quest solve " + id + " --yes");
            return 0;
        }
        for (Path f : files) {
            Files.copy(f, to.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            out.ok("copiado " + f + " -> " + to.resolve(f.getFileName()));
        }
        out.info("Agora rode: ./quest exercise " + id + " jedis (o quest recompila sozinho) e depois ./quest check " + id);
        return 0;
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

    private static int check(Console out, Keys keys, String id) throws Exception {
        Optional<Lessons.Lesson> lesson = Lessons.byId(id);
        if (lesson.isEmpty()) {
            out.fail("Lição desconhecida: " + id);
            return 2;
        }
        Optional<Check> check = Lessons.check(lesson.get());
        if (check.isEmpty()) {
            out.warn("Lição " + id + " ainda não tem check (em breve).");
            return 3;
        }
        out.h1("check " + id + " " + lesson.get().title());
        Ctx ctx = new Ctx(keys, out, id, "check");
        Verdict verdict = new Verdict();
        check.get().run(ctx, verdict);
        verdict.print(out);
        out.blank();
        if (verdict.ok()) out.ok("Lição " + id + " completa.");
        else out.fail("Ainda falta coisa na lição " + id + ". Rode a lição de novo e confira as dicas acima.");
        return verdict.ok() ? 0 : 1;
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
