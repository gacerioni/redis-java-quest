package com.emberrealm.quest.lessons.l201_01;

import com.emberrealm.quest.core.Console;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Fixed-width tables for the console: headers, a separator line, then rows. ASCII only. */
public final class Table {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private Table() {
    }

    public static void print(Console out, String[] headers, List<String[]> rows) {
        int[] width = new int[headers.length];
        for (int i = 0; i < headers.length; i++) width[i] = headers[i].length();
        for (String[] row : rows) {
            for (int i = 0; i < headers.length && i < row.length; i++) {
                width[i] = Math.max(width[i], str(row[i]).length());
            }
        }
        out.info(line(headers, width));
        StringBuilder sep = new StringBuilder();
        for (int i = 0; i < width.length; i++) {
            if (i > 0) sep.append("  ");
            sep.append("-".repeat(width[i]));
        }
        out.info(sep.toString());
        if (rows.isEmpty()) out.info("(nenhum resultado)");
        for (String[] row : rows) out.info(line(row, width));
    }

    private static String line(String[] cells, int[] width) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width.length; i++) {
            if (i > 0) sb.append("  ");
            String cell = i < cells.length ? str(cells[i]) : "";
            sb.append(cell);
            sb.append(" ".repeat(Math.max(0, width[i] - cell.length())));
        }
        return sb.toString().stripTrailing();
    }

    public static String str(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    /** 18500 -> "18.500" (pt-BR grouping); tolerant to strings and doubles coming back from Redis. */
    public static String money(Object value) {
        if (value == null) return "-";
        try {
            double d = Double.parseDouble(String.valueOf(value));
            return NumberFormat.getIntegerInstance(PT_BR).format(Math.round(d));
        } catch (NumberFormatException e) {
            return String.valueOf(value);
        }
    }

    /** 3498.181818 -> "3.498,18" (pt-BR grouping and decimal comma). */
    public static String decimal(Object value, int places) {
        if (value == null) return "-";
        try {
            double d = Double.parseDouble(String.valueOf(value));
            NumberFormat format = NumberFormat.getNumberInstance(PT_BR);
            format.setMinimumFractionDigits(places);
            format.setMaximumFractionDigits(places);
            return format.format(d);
        } catch (NumberFormatException e) {
            return String.valueOf(value);
        }
    }
}
