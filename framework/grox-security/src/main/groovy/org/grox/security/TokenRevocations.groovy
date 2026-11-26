package org.grox.security

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

import java.util.concurrent.ConcurrentHashMap

/** Tokens JWT revogados (jti) até a expiração deles: memória ou Redis. */
interface TokenRevocations extends AutoCloseable {

    void revoke(String jti, long expiresAtEpochSeconds)

    boolean isRevoked(String jti)

    static class Memory implements TokenRevocations {
        private final Map<String, Long> revoked = new ConcurrentHashMap<>()

        @Override
        void revoke(String jti, long exp) {
            long now = System.currentTimeMillis().intdiv(1000)
            revoked.values().removeIf { it < now }
            revoked.put(jti, exp)
        }

        @Override
        boolean isRevoked(String jti) {
            Long exp = revoked.get(jti)
            exp != null && exp >= System.currentTimeMillis().intdiv(1000)
        }

        @Override
        void close() {
        }
    }

    /** Compartilhada entre nós: chave {@code grox:jwt:revoked:<jti>} com EXPIREAT. */
    static class Redis implements TokenRevocations {
        private final RedisClient client
        private final StatefulRedisConnection<String, String> connection

        Redis(String url) {
            client = RedisClient.create(url)
            connection = client.connect()
        }

        @Override
        void revoke(String jti, long exp) {
            String key = 'grox:jwt:revoked:' + jti
            connection.sync().set(key, '1')
            connection.sync().expireat(key, exp)
        }

        @Override
        boolean isRevoked(String jti) {
            connection.sync().exists('grox:jwt:revoked:' + jti) > 0
        }

        @Override
        void close() {
            connection?.close()
            client?.shutdown()
        }
    }
}
