package org.grox.core

import groovy.transform.CompileStatic

/**
 * Executa uma closure com {@code ScopedValue}s vinculados. Substitui
 * {@code carrier.call(body as ScopedValue.CallableOp)}: a coerção cria um
 * {@code java.lang.reflect.Proxy} por chamada, e cada chamada passa pelo {@code ConvertedClosure}
 * (de 3 a 6 aninhados por requisição, cerca de 16% da CPU de uma página GSP no JFR).
 */
@CompileStatic
final class Scoped {

    private Scoped() {
    }

    private static final class ClosureOp<T> implements ScopedValue.CallableOp<T, Throwable> {
        final Closure<T> body

        ClosureOp(Closure<T> body) {
            this.body = body
        }

        @Override
        T call() {
            body.call()
        }
    }

    static <T> T call(ScopedValue.Carrier carrier, Closure<T> body) {
        carrier.call(new ClosureOp<T>(body))
    }

    static <T, V> T where(ScopedValue<V> key, V value, Closure<T> body) {
        ScopedValue.where(key, value).call(new ClosureOp<T>(body))
    }
}
