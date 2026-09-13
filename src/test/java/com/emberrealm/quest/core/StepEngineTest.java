package com.emberrealm.quest.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepEngineTest {

    @Test
    void lessonWithExerciseGetsLabAndCodeStepsByDefault() {
        Lessons.Lesson lesson = Lessons.byId("101-01").orElseThrow();
        List<Step> steps = StepEngine.stepsFor(lesson);
        assertEquals(List.of("lab", "codigo"), steps.stream().map(s -> s.id).toList());
        assertTrue(steps.get(0).command.contains("./quest run 101-01"));
        assertTrue(steps.get(1).command.contains("./quest exercise 101-01"));
    }

    @Test
    void jsonExportListsEveryLessonWithSteps() throws Exception {
        String json = StepEngine.toJson();
        assertTrue(json.contains("\"101-01\""));
        assertTrue(json.contains("\"instruction\""));
    }

    @Test
    void instructionsRenderTheRealPrefix() {
        Ctx ctx = new Ctx(new Keys("aluno07"), new Console(System.out, false), "101-01", "steps");
        Step step = new CommandStep("mexa", "t", "INCRBY {p}:kills:kaelith 10", "INCRBY {p}:kills:kaelith 10", (c, v) -> { }, c -> { });
        assertEquals("INCRBY aluno07:kills:kaelith 10", step.instructionFor(ctx));
        assertEquals("INCRBY aluno07:kills:kaelith 10", step.commandFor(ctx));
    }
}
