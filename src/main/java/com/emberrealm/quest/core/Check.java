package com.emberrealm.quest.core;

/** Inspects the database after a lesson and tells the student what is done and what is missing. */
public interface Check {
    void run(Ctx ctx, Verdict verdict) throws Exception;
}
