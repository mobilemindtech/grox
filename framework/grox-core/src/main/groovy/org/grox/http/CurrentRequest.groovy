package org.grox.http

import groovy.transform.CompileStatic

/** A {@link RequestScope} da thread, vinculada via {@link ScopedValue} (propagada às tarefas assíncronas). */
@CompileStatic
final class CurrentRequest {

    static final ScopedValue<RequestScope> VALUE = ScopedValue.newInstance()

    private CurrentRequest() {}

    static RequestScope orNull() {
        VALUE.bound ? VALUE.get() : null
    }

    static <T> T with(RequestScope scope, Closure<T> body) {
        org.grox.core.Scoped.where(VALUE, scope, body)
    }
}
