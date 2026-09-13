package com.emberrealm.quest.core;

/** Thrown by an exercise stub the student has not implemented yet. The CLI turns it into a friendly hint. */
public final class Todo extends RuntimeException {

    public Todo(String whatToImplement) {
        super(whatToImplement);
    }
}
