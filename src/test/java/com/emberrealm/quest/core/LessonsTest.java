package com.emberrealm.quest.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonsTest {

    @Test
    void catalogHasTwentyFourUniqueLessonsInFiveCourses() {
        assertEquals(24, Lessons.all().size());
        Set<String> ids = new HashSet<>();
        Set<String> courses = new HashSet<>();
        for (Lessons.Lesson l : Lessons.all()) {
            assertTrue(ids.add(l.id()), "duplicate id " + l.id());
            courses.add(l.course());
            assertTrue(l.id().matches("\\d{3}-\\d{2}"), "bad id " + l.id());
        }
        assertEquals(5, courses.size());
    }

    @Test
    void everyLessonWithAJedisLabAlsoHasLettuceAndCheck() {
        for (Lessons.Lesson l : Lessons.all()) {
            boolean jedis = Lessons.lab(l, "jedis").isPresent();
            boolean lettuce = Lessons.lab(l, "lettuce").isPresent();
            boolean check = Lessons.check(l).isPresent();
            assertEquals(jedis, lettuce, l.id() + ": both clients must be implemented together");
            assertEquals(jedis, check, l.id() + ": a lesson with code needs a check");
        }
    }

    @Test
    void firstTwoLessonsAreImplemented() {
        assertTrue(Lessons.lab(Lessons.byId("100-01").orElseThrow(), "jedis").isPresent());
        assertTrue(Lessons.lab(Lessons.byId("100-02").orElseThrow(), "lettuce").isPresent());
    }
}
