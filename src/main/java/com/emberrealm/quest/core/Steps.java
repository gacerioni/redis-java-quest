package com.emberrealm.quest.core;

import java.util.List;

/** Declares the ordered steps of one lesson. Class name per lesson: com.emberrealm.quest.lessons.l<id>.LessonSteps */
public interface Steps {
    List<Step> steps();
}
