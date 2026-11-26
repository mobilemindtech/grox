package groovyx.gpars.group

import groovy.concurrent.Pool

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

/**
 * Compatibilidade mínima com o {@code DefaultPGroup} do GPars sobre o {@code groovy.concurrent.Pool} do
 * Groovy 6: {@code group.task { }} roda o bloco numa virtual thread e devolve um {@code CompletableFuture}
 * ({@code get()}, {@code join()}). Erros não tratados vão para o log. O contexto da requisição não é
 * propagado, como no GPars.
 */
class DefaultPGroup {

    private static final Pool POOL = Pool.virtual()

    DefaultPGroup() {}

    DefaultPGroup(int poolSize) {}

    CompletableFuture task(Closure body) {
        CompletableFuture.supplyAsync({ ->
            try {
                body.call()
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger(DefaultPGroup).error("Erro em task do DefaultPGroup: ${t.message}", t)
                throw t
            }
        } as Supplier, POOL)
    }

    void shutdown() {}
}
