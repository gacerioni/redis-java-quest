package com.emberrealm.quest.lessons.l201_03;

import com.emberrealm.quest.core.Ctx;
import com.emberrealm.quest.core.Env;
import com.emberrealm.quest.lessons.l201_01.ItemsIndex;
import com.emberrealm.quest.lessons.l201_01.Table;
import com.emberrealm.quest.world.Ollama;
import com.emberrealm.quest.world.World;

import java.util.List;
import java.util.Optional;

/**
 * Which question drives the lesson. Default: a precomputed vector from World.queries() (q1 to q4,
 * pick with QUEST_QUERY). With a local Ollama running all-minilm, QUEST_QUESTION embeds any text.
 */
final class Questions {

    static final String TEXT_QUERY = "@type:{arma}";

    private Questions() {
    }

    static World.Query pick(Ctx ctx) {
        List<World.Query> all = World.queries();
        String custom = Env.get("QUEST_QUESTION", null);
        if (custom != null && !custom.isBlank()) {
            if (Ollama.available()) {
                ctx.out.step("Pergunta própria via Ollama (" + Ollama.MODEL + "): \"" + custom + "\"");
                Optional<float[]> vector = Ollama.embed(custom);
                if (vector.isPresent() && vector.get().length == ItemsIndex.DIM) {
                    ctx.out.info("Embedding gerado localmente com " + ItemsIndex.DIM + " dimensões, o mesmo modelo dos itens do seed.");
                    return new World.Query("custom", custom, vector.get());
                }
                ctx.out.warn("O Ollama não devolveu um embedding de " + ItemsIndex.DIM + " dimensões; usando uma pergunta pré-computada.");
            } else {
                ctx.out.warn("QUEST_QUESTION definido, mas o Ollama não responde em " + Env.ollamaUrl() + "; usando uma pergunta pré-computada.");
                ctx.out.hint("ollama pull all-minilm e ollama serve, ou remova QUEST_QUESTION.");
            }
        }
        String wanted = Env.get("QUEST_QUERY", "q1");
        World.Query chosen = all.stream().filter(q -> q.id().equals(wanted)).findFirst().orElse(all.get(0));
        ctx.out.step("Pergunta " + chosen.id() + ": \"" + chosen.text() + "\" (vetor pré-computado, "
                + chosen.embedding().length + " dimensões)");
        for (World.Query q : all) ctx.out.info("  " + q.id() + ": " + q.text());
        ctx.out.hint("Troque com QUEST_QUERY=q3, ou faça a sua pergunta com QUEST_QUESTION=\"...\" e um Ollama local.");
        return chosen;
    }

    /** Servers older than 8.4 answer "unknown command" to FT.HYBRID. */
    static boolean isUnknownCommand(Exception e) {
        String message = String.valueOf(e.getMessage()).toLowerCase();
        return message.contains("unknown command") || message.contains("unknown subcommand");
    }

    /** Cosine distance from the KNN result shown as similarity (1 - distance), 3 decimals. */
    static String similarity(Object distance) {
        try {
            return Table.decimal(1.0 - Double.parseDouble(String.valueOf(distance)), 3);
        } catch (NumberFormatException e) {
            return "-";
        }
    }
}
