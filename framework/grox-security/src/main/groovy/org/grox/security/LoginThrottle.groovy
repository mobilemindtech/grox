package org.grox.security

import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentHashMap

/**
 * Limite de tentativas de login (form, REST e Basic), em memória por nó. Conta falhas por
 * (IP, usuário) e por usuário numa janela fixa; ao passar do limite, novas tentativas são
 * recusadas sem verificar a senha (o bcrypt também é caro: sem isto, força bruta vira DoS de CPU).
 * Não há limite só por IP: atrás de proxy todos chegam com o mesmo endereço.
 */
@CompileStatic
class LoginThrottle {

    static final int MAX_KEYS = 100_000

    final int maxPerIpAndUser
    final int maxPerUser
    final long windowMillis
    private final Map<String, Window> failures = new ConcurrentHashMap<>()

    private static class Window {
        final long start
        int count

        Window(long start) {
            this.start = start
        }
    }

    LoginThrottle(int maxPerIpAndUser, int maxPerUser, int windowSeconds) {
        this.maxPerIpAndUser = maxPerIpAndUser
        this.maxPerUser = maxPerUser
        this.windowMillis = windowSeconds * 1000L
    }

    /** @return segundos até poder tentar de novo, ou 0 se liberado */
    long retryAfter(String ip, String username) {
        long now = System.currentTimeMillis()
        long a = wait(pairKey(ip, username), maxPerIpAndUser, now)
        long b = wait(userKey(username), maxPerUser, now)
        Math.max(a, b)
    }

    void failed(String ip, String username) {
        long now = System.currentTimeMillis()
        if (failures.size() > MAX_KEYS) failures.values().removeIf { Window w -> now - w.start > windowMillis }
        [pairKey(ip, username), userKey(username)].each { String k ->
            failures.compute(k) { String key, Window w ->
                Window cur = w == null || now - w.start > windowMillis ? new Window(now) : w
                cur.count++
                cur
            }
        }
    }

    /** Login certo zera as falhas daquele IP para o usuário (as de outros IPs continuam). */
    void succeeded(String ip, String username) {
        failures.remove(pairKey(ip, username))
    }

    private long wait(String key, int max, long now) {
        Window w = failures.get(key)
        if (w == null || max <= 0 || w.count < max) return 0
        long left = w.start + windowMillis - now
        left <= 0 ? 0 : Math.max(1L, left.intdiv(1000L) as long)
    }

    private static String userKey(String username) {
        'u:' + (username ?: '').toLowerCase()
    }

    private static String pairKey(String ip, String username) {
        'p:' + (ip ?: '?') + '|' + (username ?: '').toLowerCase()
    }
}
