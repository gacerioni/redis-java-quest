package com.emberrealm.quest.core;

/** One runnable lesson for one client (Jedis or Lettuce). */
public interface Lab {
    void run(Ctx ctx) throws Exception;
}
