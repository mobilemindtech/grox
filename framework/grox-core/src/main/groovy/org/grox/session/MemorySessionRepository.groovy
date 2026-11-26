package org.grox.session

import org.grox.http.GroxSession

import java.util.concurrent.ConcurrentHashMap

/** Sessões em memória (um nó); expiradas são removidas periodicamente. */
class MemorySessionRepository implements SessionRepository {

    final String cookieName
    final int timeoutSeconds
    private final Map<String, GroxSession> sessions = new ConcurrentHashMap<>()
    private final Thread reaper

    MemorySessionRepository(String cookieName, int timeoutSeconds) {
        this.cookieName = cookieName
        this.timeoutSeconds = timeoutSeconds
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
        sessions.put(session.id, session)
        session.markClean()
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
