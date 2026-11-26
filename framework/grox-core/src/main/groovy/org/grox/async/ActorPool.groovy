package org.grox.async

import groovy.concurrent.Actor
import groovy.concurrent.Awaitable

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * Pool fixo de atores, cada um com a sua instância (estado não compartilhado). A mensagem vai para o ator
 * menos ocupado, começando de uma posição que gira (sem preferir sempre o primeiro). Usado pelos
 * controllers do Grails ({@code concurrency: 'pool'}) e pelas rotas do {@code grox-dsl} em {@code execution: 'actor'}.
 */
class ActorPool<T> implements AutoCloseable {

    private final List<Actor<T>> actors = []
    private final AtomicInteger[] inFlight
    private final AtomicInteger next = new AtomicInteger()

    /** @param handler para cada ator, a função que atende a mensagem (criada uma vez por ator) */
    ActorPool(int size, java.util.function.Supplier<Function<T, Object>> handler) {
        inFlight = new AtomicInteger[size]
        for (int i = 0; i < size; i++) {
            inFlight[i] = new AtomicInteger()
            actors << Actor.reactor(handler.get())
        }
    }

    int getSize() {
        actors.size()
    }

    @groovy.transform.CompileStatic
    Awaitable<Object> submit(T message) {
        int n = actors.size()
        int start = Math.floorMod(next.getAndIncrement(), n)
        int chosen = start
        int best = Integer.MAX_VALUE
        for (int k = 0; k < n; k++) {
            int i = (start + k) % n
            int load = inFlight[i].get()
            if (load < best) {
                best = load
                chosen = i
                if (load == 0) break
            }
        }
        AtomicInteger counter = inFlight[chosen]
        counter.incrementAndGet()
        Awaitable<Object> result = actors[chosen].sendAndGet(message)
        result.whenComplete { r, e -> counter.decrementAndGet() }
    }

    @Override
    void close() {
        actors*.stop()
    }
}
