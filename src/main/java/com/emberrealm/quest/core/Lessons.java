package com.emberrealm.quest.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Lesson catalog (lessons.csv) plus reflective lookup of the classes that implement each lesson. */
public final class Lessons {

    public record Lesson(String id, String course, String title) {
        public String packageName() {
            return "com.emberrealm.quest.lessons.l" + id.replace('-', '_');
        }
    }

    private static final List<Lesson> ALL = load();

    private Lessons() {
    }

    public static List<Lesson> all() {
        return ALL;
    }

    public static Optional<Lesson> byId(String id) {
        return ALL.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    public static Optional<Lab> lab(Lesson lesson, String client) {
        String simple = switch (client) {
            case "jedis" -> "JedisLab";
            case "lettuce" -> "LettuceLab";
            default -> throw new IllegalArgumentException("client must be jedis or lettuce, got: " + client);
        };
        return instantiate(lesson.packageName() + "." + simple, Lab.class);
    }

    public static Optional<Check> check(Lesson lesson) {
        return instantiate(lesson.packageName() + ".LessonCheck", Check.class);
    }

    private static <T> Optional<T> instantiate(String className, Class<T> type) {
        try {
            Class<?> c = Class.forName(className);
            return Optional.of(type.cast(c.getDeclaredConstructor().newInstance()));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate " + className, e);
        }
    }

    private static List<Lesson> load() {
        List<Lesson> list = new ArrayList<>();
        try (InputStream in = Lessons.class.getResourceAsStream("/lessons.csv")) {
            if (in == null) throw new IllegalStateException("lessons.csv missing from classpath");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                if (line.isBlank()) continue;
                String[] parts = line.split(";", 3);
                list.add(new Lesson(parts[0].trim(), parts[1].trim(), parts[2].trim()));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return List.copyOf(list);
    }
}
