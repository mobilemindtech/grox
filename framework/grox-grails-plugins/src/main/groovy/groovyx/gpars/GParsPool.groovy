package groovyx.gpars

import groovy.concurrent.AsyncScope
import groovy.concurrent.ParallelScope

import java.util.function.Function

/**
 * Compatibilidade com o {@code GParsPool} do GPars (o jar do GPars não roda no Groovy 6) sobre o
 * {@code groovy.concurrent} do Groovy 6: {@code GParsPool.withPool(n) { lista.collectParallel { } }} é o
 * {@code ParallelScope.withPool(n)}, e {@code collectParallel}, {@code eachParallel}, {@code findAllParallel},
 * {@code anyParallel}, {@code everyParallel}, {@code groupByParallel}... são as extensões nativas do Groovy 6,
 * que usam o pool corrente. Como no GPars, o contexto da requisição (sessão, tenant, usuário) não é propagado.
 */
class GParsPool {

    static <T> T withPool(Closure<T> body) {
        withPool(Runtime.runtime.availableProcessors() + 1, body)
    }

    static <T> T withPool(int size, Closure<T> body) {
        ParallelScope.withPool(Math.max(1, size), { AsyncScope scope -> body.call() } as Function<AsyncScope, T>)
    }
}
