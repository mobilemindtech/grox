package org.grox.security.gorm

import org.grox.data.Gorm
import org.grox.data.Persistence
import org.grox.security.GroxUser
import org.grox.security.SecurityConfig
import org.grox.security.TokenStore
import org.grox.security.UserDetailsService

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Tokens do login REST guardados numa domain class, como o {@code GormTokenStorageService} do
 * spring-security-rest ({@code rest.token.storage.useGorm: true}). O token é opaco e vale até o
 * logout. O usuário do token fica em cache por {@code rest.token.storage.gorm.cacheSeconds}
 * (extensão Grox, padrão 30; 0 desliga): sem ele, cada requisição faria três consultas. Roles e
 * desativação valem depois desse prazo; o logout no mesmo nó vale na hora.
 */
class GormTokenStorage implements TokenStore {

    final Class<?> tokenClass
    final String valueProperty
    final String usernameProperty
    private final SecureRandom random = new SecureRandom()
    static final int MAX_CACHED = 10_000
    final long cacheMillis
    private final Map<String, Cached> cache = new ConcurrentHashMap<>()

    private static class Cached {
        final GroxUser user
        final long expiresAt

        Cached(GroxUser user, long expiresAt) {
            this.user = user
            this.expiresAt = expiresAt
        }
    }

    GormTokenStorage(SecurityConfig cfg, ClassLoader loader) {
        if (!cfg.tokenDomainClassName) {
            throw new IllegalStateException("${SecurityConfig.PREFIX}.rest.token.storage.useGorm exige rest.token.storage.gorm.tokenDomainClassName")
        }
        this.tokenClass = Class.forName(cfg.tokenDomainClassName, true, loader)
        this.valueProperty = cfg.tokenValuePropertyName
        this.usernameProperty = cfg.tokenUsernamePropertyName
        this.cacheMillis = cfg.tokenCacheSeconds * 1000L
    }

    /** Usuário do token em cache, ou null se ausente ou vencido. */
    GroxUser cached(String value) {
        Cached c = cacheMillis > 0 ? cache.get(value) : null
        if (c == null) return null
        if (c.expiresAt < System.currentTimeMillis()) {
            cache.remove(value, c)
            return null
        }
        c.user
    }

    void cache(String value, GroxUser user) {
        if (cacheMillis <= 0) return
        long now = System.currentTimeMillis()
        if (cache.size() >= MAX_CACHED) {
            cache.values().removeIf { it.expiresAt < now }
            if (cache.size() >= MAX_CACHED) cache.clear()
        }
        cache.put(value, new Cached(user, now + cacheMillis))
    }

    /** Usuário do token: do cache ou recarregado (token e usuário na mesma sessão de dados); null se inválido. */
    @Override
    GroxUser load(String value, UserDetailsService users) {
        GroxUser principal = cached(value)
        if (principal != null) return principal
        GroxUser user = Persistence.withSessionIfAvailable {
            String username = findUsername(value)
            username ? users.loadUserByUsername(username) : null
        }
        if (user == null || !user.enabled || user.accountLocked || user.accountExpired) return null
        principal = user.withoutPassword()
        cache(value, principal)
        principal
    }

    /** Gera e grava um token para o usuário (mesmo formato do SecureRandomTokenGenerator do plugin). */
    @Override
    String store(String username) {
        String value = new BigInteger(160, random).toString(32)
        Persistence.withTransaction {
            Object token = tokenClass.getDeclaredConstructor().newInstance()
            token[valueProperty] = value
            token[usernameProperty] = username
            token.save(flush: true, failOnError: true)
        }
        value
    }

    /** @return o username dono do token, ou null se ele não existir */
    String findUsername(String value) {
        Persistence.withSession {
            Gorm.api(tokenClass).findWhere([(valueProperty): value])?.getAt(usernameProperty)?.toString()
        }
    }

    /** @return false se o token não existia */
    @Override
    boolean remove(String value) {
        cache.remove(value)
        Persistence.withTransaction {
            Object token = Gorm.api(tokenClass).findWhere([(valueProperty): value])
            if (token == null) return false
            token.delete(flush: true)
            true
        }
    }
}
