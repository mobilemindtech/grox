package org.grox.core

/**
 * Contexto de um módulo que acompanha o trabalho em outras threads ({@code ScopedValue}): os
 * services-ator recebem o contexto de quem chama, e as tarefas assíncronas o de quem as criou.
 * Descoberto por {@link ServiceLoader} ({@code META-INF/services/org.grox.core.ContextProvider}).
 */
interface ContextProvider {

    /** Propagados às chamadas de services-ator (inclusive a sessão de dados, que o ator usa no lugar de quem chama). */
    default List<ScopedValue> actorValues() { [] }

    /** Propagados às tarefas assíncronas (sem a sessão de dados: cada tarefa abre a sua). */
    default List<ScopedValue> taskValues() { [] }

    /** Completa o retrato de uma tarefa com valores ainda não vinculados (ex.: a autenticação da requisição). */
    default void completeTask(Map<ScopedValue, Object> snapshot) {}

    /** Envolve o corpo de uma tarefa (ex.: sessão de dados sob demanda). */
    default <T> Closure<T> wrapTask(Closure<T> body) { body }
}
