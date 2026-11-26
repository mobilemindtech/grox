package org.grox.core.reload

import groovy.transform.CompileStatic
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.RequestDispatcher

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * Entrada do Netty durante a recarga: repassa à geração atual da aplicação. Na recarga completa as
 * requisições novas ficam em espera ({@link #hold}) até a próxima geração subir; cada geração conta
 * as suas requisições em andamento, para ser fechada só quando terminarem.
 */
@CompileStatic
class SwitchableDispatcher implements RequestDispatcher {

    private volatile CompletableFuture<Target> next = new CompletableFuture<>()
    private Target active

    @Override
    CompletableFuture<GroxResponse> dispatch(GroxRequest request) {
        next.thenCompose { Target t -> t.dispatch(request) }
    }

    /** Segura as requisições novas até o próximo {@link #switchTo}; devolve a geração que atendia. */
    synchronized Target hold() {
        if (next.done) next = new CompletableFuture<>()
        active
    }

    /** Passa a atender com {@code dispatcher} (liberando as que esperavam); devolve a geração anterior. */
    synchronized Target switchTo(RequestDispatcher dispatcher) {
        Target previous = active
        active = new Target(dispatcher)
        if (next.done) next = CompletableFuture.completedFuture(active)
        else next.complete(active)
        previous
    }

    static class Target {
        final RequestDispatcher dispatcher
        final AtomicInteger inFlight = new AtomicInteger()

        Target(RequestDispatcher dispatcher) { this.dispatcher = dispatcher }

        CompletableFuture<GroxResponse> dispatch(GroxRequest request) {
            inFlight.incrementAndGet()
            try {
                return dispatcher.dispatch(request).whenComplete { GroxResponse r, Throwable e -> inFlight.decrementAndGet() }
            } catch (Throwable t) {
                inFlight.decrementAndGet()
                throw t
            }
        }

        /** Espera as requisições desta geração terminarem (até {@code timeoutMs}); false se estourou. */
        boolean awaitIdle(long timeoutMs) {
            long deadline = System.currentTimeMillis() + timeoutMs
            while (inFlight.get() > 0) {
                if (System.currentTimeMillis() > deadline) return false
                Thread.sleep(20)
            }
            true
        }
    }

    /** Responde 503 com o erro da última recarga, até a próxima dar certo. */
    static class Failure implements RequestDispatcher {
        final Throwable error

        Failure(Throwable error) { this.error = error }

        @Override
        CompletableFuture<GroxResponse> dispatch(GroxRequest request) {
            def response = new GroxResponse(status: 503, contentType: 'text/plain;charset=UTF-8')
            response.setHeader('Cache-Control', 'no-store')
            def out = response.writer
            out.println('Grox: a aplicação não subiu após a última mudança. Corrija o erro e salve de novo.')
            out.println()
            error.printStackTrace(out)
            out.flush()
            response.committed = true
            CompletableFuture.completedFuture(response)
        }
    }
}
