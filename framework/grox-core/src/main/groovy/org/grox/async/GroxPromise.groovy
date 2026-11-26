package org.grox.async

import grails.async.Promise

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** {@link Promise} sobre {@link CompletableFuture}. */
class GroxPromise<T> implements Promise<T> {

    final CompletableFuture<T> future

    GroxPromise(CompletableFuture<T> future) {
        this.future = future
    }

    static <T> GroxPromise<T> completed(T value) {
        new GroxPromise<T>(CompletableFuture.completedFuture(value))
    }

    static GroxPromise<List> all(List<GroxPromise> promises) {
        CompletableFuture[] fs = promises.collect { it.future } as CompletableFuture[]
        new GroxPromise<List>(CompletableFuture.allOf(fs).thenApply { promises.collect { it.future.join() } })
    }

    @Override
    T get() {
        try {
            future.get()
        } catch (ExecutionException e) {
            throw e.cause ?: e
        }
    }

    @Override
    T get(long timeout, TimeUnit unit) {
        try {
            future.get(timeout, unit)
        } catch (ExecutionException e) {
            throw e.cause ?: e
        }
    }

    @Override
    boolean isDone() {
        future.done
    }

    /** O callback roda com o mesmo contexto (usuário, tenant, sessão própria) da tarefa. */
    @Override
    Promise then(Closure callable) {
        Map snapshot = AsyncSupport.snapshot()
        new GroxPromise(future.thenApplyAsync({ T v -> AsyncSupport.runWith(snapshot) { callable.call(v) } }, AsyncSupport.executor))
    }

    @Override
    Promise<T> onComplete(Closure callable) {
        future.thenAccept { T v -> callable.call(v) }
        this
    }

    @Override
    Promise<T> onError(Closure callable) {
        future.exceptionally { Throwable e ->
            callable.call(e instanceof CompletionException && e.cause ? e.cause : e)
            null
        }
        this
    }

    /** Promise, CompletionStage, Future do Groovy (Awaitable) ou Future comum → CompletableFuture. */
    static CompletableFuture toFuture(Object value) {
        if (value instanceof GroxPromise) return ((GroxPromise) value).future
        if (value instanceof java.util.concurrent.CompletionStage) return ((java.util.concurrent.CompletionStage) value).toCompletableFuture()
        if (value instanceof groovy.concurrent.Awaitable) return ((groovy.concurrent.Awaitable) value).toCompletableFuture()
        if (value instanceof Promise) return CompletableFuture.supplyAsync({ ((Promise) value).get() }, AsyncSupport.executor)
        null
    }

    static boolean isAsync(Object value) {
        value instanceof Promise || value instanceof java.util.concurrent.CompletionStage || value instanceof groovy.concurrent.Awaitable
    }
}
