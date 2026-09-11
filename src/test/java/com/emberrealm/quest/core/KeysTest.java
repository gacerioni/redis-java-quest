package com.emberrealm.quest.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeysTest {

    @Test
    void joinsPartsWithColons() {
        Keys keys = new Keys("quest");
        assertEquals("quest:player:kaelith", keys.of("player", "kaelith"));
        assertEquals("quest:rank:xp", keys.of("rank", "xp"));
        assertEquals("quest:*", keys.pattern());
    }
}
