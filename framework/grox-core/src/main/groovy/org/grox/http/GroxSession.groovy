package org.grox.http

import org.grox.session.SessionRepository

import java.util.concurrent.ConcurrentHashMap

/**
 * Sessão HTTP; atributos acessíveis como propriedades ({@code session.user}). Registra os
 * atributos alterados, removidos e lidos para que repositórios remotos (Redis) gravem só
 * o necessário ao fim da requisição.
 */
class GroxSession implements jakarta.servlet.http.HttpSession {

    String id
    long creationTime = System.currentTimeMillis()
    volatile long lastAccessedTime = creationTime
    int maxInactiveInterval
    /** Ainda não gravada no repositório ({@link #isNew}). */
    private boolean fresh = true
    boolean invalidated
    /** Id trocado nesta requisição (proteção contra session fixation). */
    boolean idChanged
    private final SessionRepository repository
    private final Map<String, Object> attributes = new ConcurrentHashMap<>()
    final Set<String> changed = ConcurrentHashMap.newKeySet()
    final Set<String> removed = ConcurrentHashMap.newKeySet()
    final Set<String> accessed = ConcurrentHashMap.newKeySet()

    GroxSession(String id, int maxInactiveInterval, SessionRepository repository) {
        this.id = id
        this.maxInactiveInterval = maxInactiveInterval
        this.repository = repository
    }

    Object getAttribute(String name) {
        Object v = attributes.get(name)
        if (v != null) accessed << name
        v
    }

    void setAttribute(String name, Object value) {
        if (value == null) {
            removeAttribute(name)
            return
        }
        attributes.put(name, value)
        changed << name
        removed.remove(name)
    }

    void removeAttribute(String name) {
        if (attributes.remove(name) != null || !fresh) removed << name
        changed.remove(name)
    }

    /** Ainda não gravada no repositório (o cliente ainda não recebeu o cookie). */
    @Override
    boolean isNew() {
        fresh
    }

    @Override
    Enumeration<String> getAttributeNames() {
        Collections.enumeration(new ArrayList<>(attributes.keySet()))
    }

    /** Sem container: não há {@code ServletContext}. */
    @Override
    jakarta.servlet.ServletContext getServletContext() {
        throw new UnsupportedOperationException('Grox não tem ServletContext')
    }

    /** Acesso sem marcar leitura (usado pelos repositórios). */
    Map<String, Object> getAttributeMap() {
        attributes
    }

    boolean isExpired(long now = System.currentTimeMillis()) {
        maxInactiveInterval > 0 && now - lastAccessedTime > maxInactiveInterval * 1000L
    }

    void touch() {
        lastAccessedTime = System.currentTimeMillis()
    }

    void invalidate() {
        invalidated = true
        attributes.clear()
        repository.delete(id)
    }

    /** Novo id mantendo os atributos (chamado no login). */
    String changeSessionId() {
        repository.changeId(this)
        idChanged = true
        id
    }

    /** Limpa o controle de alterações (depois de carregar ou gravar). */
    void markClean() {
        fresh = false
        idChanged = false
        changed.clear()
        removed.clear()
        accessed.clear()
    }

    Object getAt(String name) { getAttribute(name) }

    void putAt(String name, Object value) { setAttribute(name, value) }

    Object propertyMissing(String name) { getAttribute(name) }

    void propertyMissing(String name, Object value) { setAttribute(name, value) }
}
