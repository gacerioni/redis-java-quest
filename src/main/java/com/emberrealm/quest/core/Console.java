package com.emberrealm.quest.core;

import java.io.PrintStream;

/** Student-facing console output. Plain ASCII markers so it reads well on any terminal. */
public final class Console {

    private static final String ESC = "\u001b[";

    private final PrintStream out;
    private final boolean color;

    public Console() {
        this(System.out, System.getenv("NO_COLOR") == null && System.console() != null);
    }

    public Console(PrintStream out, boolean color) {
        this.out = out;
        this.color = color;
    }

    public void h1(String text) {
        out.println();
        out.println(paint("1;35", "== " + text + " =="));
    }

    public void step(String text) {
        out.println(paint("1;36", "-> " + text));
    }

    public void info(String text) {
        out.println("   " + text);
    }

    /** Shows the command the client sent, the way redis-cli would show it. */
    public void cmd(String text) {
        out.println(paint("90", "   > " + text));
    }

    public void kv(String key, Object value) {
        out.println("   " + paint("1", key + ": ") + value);
    }

    public void ok(String text) {
        out.println(paint("1;32", "[OK] ") + text);
    }

    public void warn(String text) {
        out.println(paint("1;33", "[!!] ") + text);
    }

    public void fail(String text) {
        out.println(paint("1;31", "[FAIL] ") + text);
    }

    public void hint(String text) {
        out.println(paint("33", "   dica: " + text));
    }

    public void blank() {
        out.println();
    }

    private String paint(String code, String text) {
        return color ? ESC + code + "m" + text + ESC + "0m" : text;
    }
}
