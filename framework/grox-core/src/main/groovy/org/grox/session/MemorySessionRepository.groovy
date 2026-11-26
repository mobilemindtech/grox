package org.grox.session

import org.grox.http.GroxSession

import java.util.concurrent.ConcurrentHashMap

/**
 * Sessões em memória (um nó); expiradas são removidas periodicamente. Com {@code maxActive} sessões, uma nova
 * remove primeiro as expiradas e, se ainda faltar espaço, as 10% acessadas há mais tempo: sob uma enxurrada de
 * requisições anônimas (cada uma cria uma sessão), saem as do robô, tocadas uma vez só, e não as de quem está usando.
 */
class MemorySessionRepository implements SessionRepository {

    /** Padrão de {@code grox.session.maxActive}. */
    static final int DEFAULT_MAX_ACTIVE = 100_000

    final String cookieName
    final int timeoutSeconds
    /** {@code grox.session.maxActive} (0: sem limite). */
    final int maxActive
    private final Map<String, GroxSession> sessions = new ConcurrentHashMap<>()
    private final Thread reaper

    MemorySessionRepository(String cookieName, int timeoutSeconds, int maxActive = DEFAULT_MAX_ACTIVE) {
        this.cookieName = cookieName
        this.timeoutSeconds = timeoutSeconds
        this.maxActive = maxActive
        this.reaper = Thread.ofVirtual().name('grox-session-reaper').start {
            try {
                while (!Thread.currentThread().interrupted) {
                    Thread.sleep(60_000)
                    purge()
                }
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    GroxSession find(String id) {
        if (!id) return null
        GroxSession s = sessions.get(id)
        if (s != null && s.expired) {
            sessions.remove(id)
            return null
        }
        s?.touch()
        s
    }

    @Override
    GroxSession create() {
        new GroxSession(SessionIds.next(), timeoutSeconds, this)
    }

    @Override
    void save(GroxSession session) {
        if (session.invalidated) return
        if (maxActive > 0 && sessions.size() >= maxActive && !sessions.containsKey(session.id)) makeRoom()
        sessions.put(session.id, session)
        session.markClean()
    }

    /** Limite atingido: tira as expiradas; se não bastar, as 10% acessadas há mais tempo. */
    private synchronized void makeRoom() {
        if (sessions.size() < maxActive) return
        purge()
        int excess = sessions.size() - maxActive + Math.max(1, maxActive.intdiv(10) as int)
        if (excess <= 0 || sessions.size() < maxActive) return
        sessions.values().sort(false) { GroxSession s -> s.lastAccessedTime }.take(excess).each { sessions.remove(it.id) }
    }

    @Override
    void delete(String id) {
        sessions.remove(id)
    }

    @Override
    void changeId(GroxSession session) {
        String old = session.id
        session.id = SessionIds.next()
        if (sessions.remove(old) != null) sessions.put(session.id, session)
    }

    @Override
    List<GroxSession> listActive() {
        long now = System.currentTimeMillis()
        sessions.values().findAll { !it.isExpired(now) } as List<GroxSession>
    }

    int size() {
        sessions.size()
    }

    void purge() {
        long now = System.currentTimeMillis()
        sessions.values().removeIf { it.isExpired(now) }
    }

    @Override
    void close() {
        reaper.interrupt()
        sessions.clear()
    }
}
