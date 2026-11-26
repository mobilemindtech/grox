package org.grox.web.interceptor

import org.grox.web.WebRequest

/** Interceptors ordenados por {@code order} (menor primeiro no before; ordem inversa no after). */
class InterceptorRegistry {

    final List<InterceptorApi> interceptors

    InterceptorRegistry(List<InterceptorApi> interceptors) {
        this.interceptors = interceptors.toSorted { a, b -> a.order <=> b.order }
    }

    List<InterceptorApi> matching(WebRequest wr) {
        interceptors ? interceptors.findAll { it.doesMatch(wr) } : []
    }

    boolean isEmpty() {
        interceptors.isEmpty()
    }
}
