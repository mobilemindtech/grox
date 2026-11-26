package org.grox.async

import org.grox.core.ContextPropagation

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Execução de tarefas: virtual threads, com a requisição, a autenticação e o tenant de quem
 * criou a tarefa. A sessão de dados NÃO é compartilhada (Session do Hibernate não é thread-safe):
 * cada tarefa abre a sua, sob demanda.
 */
class AsyncSupport {

    static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()

    /** Valores de contexto propagados (sem a sessão de dados), declarados pelos módulos. */
    static Map<ScopedValue, Object> snapshot() {
        ContextPropagation.captureTask()
    }

    static <T> T runWith(Map<ScopedValue, Object> snapshot, Closure<T> body) {
        ContextPropagation.runTask(snapshot, body)
    }

    static <T> GroxPromise<T> task(Closure<T> body) {
        Map<ScopedValue, Object> s = snapshot()
        new GroxPromise<T>(CompletableFuture.supplyAsync({ runWith(s, body) }, executor))
    }
}
