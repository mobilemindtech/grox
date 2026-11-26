package org.springframework.web.context.request

import groovy.transform.CompileStatic
import org.grox.web.RequestContext
import org.grox.web.WebRequest

@CompileStatic
class RequestContextHolder {
    static RequestAttributes getRequestAttributes() {
        WebRequest ctx = RequestContext.currentOrNull()
        ctx == null ? null : new ServletWebRequest(ctx)
    }

    static RequestAttributes currentRequestAttributes() {
        RequestAttributes attrs = getRequestAttributes()
        if (attrs == null) throw new IllegalStateException('Nenhuma requisição em andamento nesta thread')
        attrs
    }
}
