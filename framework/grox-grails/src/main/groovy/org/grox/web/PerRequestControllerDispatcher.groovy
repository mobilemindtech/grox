package org.grox.web

import groovy.concurrent.Actor
import groovy.concurrent.Awaitable
import org.grox.http.GroxResponse

import java.util.function.Function

/** Um ator (e uma instância do controller) por requisição, descartado ao final. */
class PerRequestControllerDispatcher implements ControllerDispatcher {

    private final Closure<Object> factory
    private final ControllerExecutor executor

    PerRequestControllerDispatcher(Closure<Object> factory, ControllerExecutor executor) {
        this.factory = factory
        this.executor = executor
    }

    @Override
    Awaitable<Object> submit(Invocation invocation) {
        Object instance = factory.call()
        Actor<Invocation> actor = Actor.reactor({ Invocation inv -> executor.execute(instance, inv) } as Function<Invocation, Object>)
        Awaitable<Object> result = actor.sendAndGet(invocation)
        result.whenComplete { r, e -> actor.stop() }
    }

    @Override
    void close() {
    }
}
