package org.grox.web

import groovy.concurrent.Awaitable
import org.grox.async.ActorPool

import java.util.function.Function

/**
 * N atores por controller, cada um com sua própria instância do controller ({@link ActorPool}:
 * o ator com menos mensagens pendentes, a partir de um índice round-robin).
 */
@groovy.transform.CompileStatic
class PooledControllerDispatcher implements ControllerDispatcher {

    private final ActorPool<Invocation> pool

    /** Lambdas nativas: uma closure com {@code as Function} vira um proxy do JDK, chamado a cada mensagem. */
    PooledControllerDispatcher(int size, Closure<Object> factory, ControllerExecutor executor) {
        pool = new ActorPool<Invocation>(size, () -> handler(factory.call(), executor))
    }

    private static Function<Invocation, Object> handler(Object instance, ControllerExecutor executor) {
        (Invocation inv) -> executor.execute(instance, inv)
    }

    int getSize() {
        pool.size
    }

    @Override
    Awaitable<Object> submit(Invocation invocation) {
        pool.submit(invocation)
    }

    @Override
    void close() {
        pool.close()
    }
}
