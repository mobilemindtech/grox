package org.grox.core

/**
 * Captura os {@link ScopedValue} de contexto declarados pelos módulos ({@link ContextProvider}: sessão de
 * dados, autenticação, tenant, requisição) e os vincula na thread do service-ator ou da tarefa.
 */
class ContextPropagation {

    static final List<ContextProvider> PROVIDERS = ServiceLoader.load(ContextProvider, ContextPropagation.classLoader).toList().asImmutable()

    /** Propagados aos services-ator. */
    static final List<ScopedValue> VALUES = PROVIDERS.collectMany { it.actorValues() }.unique().asImmutable()

    /** Propagados às tarefas assíncronas. */
    static final List<ScopedValue> TASK_VALUES = PROVIDERS.collectMany { it.taskValues() }.unique().asImmutable()

    static Map<ScopedValue, Object> capture() {
        snapshotOf(VALUES)
    }

    private static Map<ScopedValue, Object> snapshotOf(List<ScopedValue> values) {
        Map<ScopedValue, Object> snapshot = [:]
        values.each { ScopedValue sv -> if (sv.bound) snapshot[sv] = sv.get() }
        snapshot
    }

    static <T> T bind(Map<ScopedValue, Object> snapshot, Closure<T> body) {
        if (!snapshot) return body.call()
        Scoped.call(carrier(snapshot), body)
    }

    private static ScopedValue.Carrier carrier(Map<ScopedValue, Object> snapshot) {
        ScopedValue.Carrier carrier = null
        snapshot.each { ScopedValue sv, Object v -> carrier = carrier == null ? ScopedValue.where(sv, v) : carrier.where(sv, v) }
        carrier
    }

    /** Retrato para uma tarefa assíncrona. */
    static Map<ScopedValue, Object> captureTask() {
        Map<ScopedValue, Object> snapshot = snapshotOf(TASK_VALUES)
        PROVIDERS.each { it.completeTask(snapshot) }
        snapshot
    }

    /** Roda a tarefa com o retrato e os envoltórios dos módulos. */
    static <T> T runTask(Map<ScopedValue, Object> snapshot, Closure<T> body) {
        Closure<T> wrapped = body
        PROVIDERS.each { wrapped = it.wrapTask(wrapped) }
        snapshot ? Scoped.call(carrier(snapshot), wrapped) : wrapped.call()
    }
}
