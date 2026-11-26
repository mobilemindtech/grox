package org.grox.web

import groovy.concurrent.Awaitable
import org.grox.async.ActorPool

import java.util.function.Function

/**
 * N atores por controller, cada um com sua própria instância do controller ({@link ActorPool}:
 * o ator com menos mensagens pendentes, a partir de um índice round-robin).
 */
class PooledControllerDispatcher implements ControllerDispatcher {

    private final ActorPool<Invocation> pool

    PooledControllerDispatcher(int size, Closure<Object> factory, ControllerExecutor executor) {
        pool = new ActorPool<Invocation>(size, { ->
            Object instance = factory.call()
            ({ Invocation inv -> executor.execute(instance, inv) } as Function<Invocation, Object>)
        } as java.util.function.Supplier<Function<Invocation, Object>>)
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
