package com.emberrealm.quest.lessons.l100_01;

import java.util.Locale;

/** A command permission or network error is not evidence about the server topology. */
final class Topology {
    private Topology() { }

    static boolean clusterDisabled(Throwable error) {
        String message = error.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("cluster support disabled") || lower.contains("cluster is disabled");
    }

    static String failureHint(Throwable error) {
        if (clusterDisabled(error)) {
            return "Cluster API desabilitada neste endpoint: use client de endpoint único. Pode ser standalone ou proxy do Redis Cloud/Software.";
        }
        return "Não foi possível determinar a topologia: este erro pode ser de ACL, rede ou suporte ao comando. Confira a configuração do banco; não escolha o client por este erro.";
    }
}
