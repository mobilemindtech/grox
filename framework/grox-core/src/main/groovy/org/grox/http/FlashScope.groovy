package org.grox.http

/**
 * Escopo flash: valores definidos numa requisição ficam disponíveis também na
 * seguinte ({@code flash.message = '...'} antes de um redirect).
 */
class FlashScope implements Serializable, org.grox.session.SessionValue {

    /** Atributo da sessão com o flash. */
    static final String KEY = 'grox.flash'

    private static final long serialVersionUID = 1L

    private Map<String, Object> now = [:]
    private Map<String, Object> next = [:]
    /** Mudou desde a carga (não serializado): um flash vazio que só avança não é regravado. */
    private transient boolean modified
    /** Flash de uma requisição sem sessão: grava-se na sessão (criando-a) só na primeira escrita. */
    private transient Runnable attach

    private static final String DETACHED = 'grox.flash.detached'

    /**
     * O flash da requisição: o da sessão, se ela existe. Sem sessão, um flash avulso (o mesmo durante a requisição)
     * que só cria a sessão na primeira escrita: ler {@code flash.message} numa página não cria uma sessão por
     * visitante anônimo.
     * @param session dá a sessão da requisição, criando-a com {@code true}
     */
    @groovy.transform.CompileStatic
    static FlashScope of(GroxRequest req, java.util.function.Function<Boolean, GroxSession> session) {
        FlashScope avulso = (FlashScope) req.getAttribute(DETACHED)
        GroxSession s = session.apply(false)
        if (s != null) {
            FlashScope f = (FlashScope) s.getAttribute(KEY)
            if (f == null) {
                f = avulso ?: new FlashScope()
                f.attach = null
                s.setAttribute(KEY, f)
            }
            return f
        }
        if (avulso == null) {
            FlashScope novo = new FlashScope()
            novo.attach = (Runnable) { -> session.apply(true).setAttribute(KEY, novo) }
            req.setAttribute(DETACHED, novo)
            avulso = novo
        }
        avulso
    }

    synchronized void advance() {
        if (now.isEmpty() && next.isEmpty()) return
        now = next
        next = [:]
        modified = true
    }

    @Override
    synchronized boolean isModified() {
        modified
    }

    synchronized Object get(String key) {
        next.containsKey(key) ? next[key] : now[key]
    }

    void put(String key, Object value) {
        Runnable a = attach
        if (a != null) {
            attach = null
            a.run()
        }
        synchronized (this) {
            next[key] = value
            modified = true
        }
    }

    synchronized boolean containsKey(String key) {
        next.containsKey(key) || now.containsKey(key)
    }

    synchronized boolean isEmpty() {
        next.isEmpty() && now.isEmpty()
    }

    synchronized Map<String, Object> asMap() {
        now + next
    }

    Object getAt(String key) { get(key) }

    void putAt(String key, Object value) { put(key, value) }

    Object propertyMissing(String name) { get(name) }

    void propertyMissing(String name, Object value) { put(name, value) }
}
