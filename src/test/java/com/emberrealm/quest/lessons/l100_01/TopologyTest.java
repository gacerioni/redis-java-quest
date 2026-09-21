package com.emberrealm.quest.lessons.l100_01;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import static org.junit.jupiter.api.Assertions.*;

class TopologyTest {
    @Test void onlyAnExplicitDisabledResponseIdentifiesDisabledClusterApi() {
        assertTrue(Topology.clusterDisabled(new JedisDataException("ERR This instance has cluster support disabled")));
        assertFalse(Topology.clusterDisabled(new JedisDataException("NOPERM user has no permissions to run CLUSTER INFO")));
        assertFalse(Topology.clusterDisabled(new JedisConnectionException("Read timed out")));
        assertFalse(Topology.clusterDisabled(new JedisDataException("ERR unknown command CLUSTER")));
    }
}
