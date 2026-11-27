package org.grox.web.interceptor

import org.grox.web.WebRequest

/** Interceptors ordenados por {@code order} (menor primeiro no before; ordem inversa no after). */
@groovy.transform.CompileStatic
class InterceptorRegistry {

    final List<InterceptorApi> interceptors

    InterceptorRegistry(List<InterceptorApi> interceptors) {
        this.interceptors = interceptors.toSorted { InterceptorApi a, InterceptorApi b -> a.order <=> b.order }
    }

    List<InterceptorApi> matching(WebRequest wr) {
        if (interceptors.isEmpty()) return Collections.emptyList()
        List<InterceptorApi> r = new ArrayList<>(interceptors.size())
        for (InterceptorApi i : interceptors) {
            if (i.doesMatch(wr)) r.add(i)
        }
        r
    }

    boolean isEmpty() {
        interceptors.isEmpty()
    }
}
