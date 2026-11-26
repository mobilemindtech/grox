package org.grox.view.taglib

import org.grox.http.SafeHtml
import org.grox.view.gsp.GspRenderContext
import org.grox.http.CurrentRequest
import org.grox.view.ViewRequest

/** {@code g.link(action: 'x')}, {@code fmt.moeda(valor: v)}: tags chamadas como métodos. */
class TagNamespace {

    final String namespace
    final TagLibRegistry registry

    TagNamespace(String namespace, TagLibRegistry registry) {
        this.namespace = namespace
        this.registry = registry
    }

    def methodMissing(String name, Object argsObj) {
        if (!registry.has(namespace, name)) throw new org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack(name, TagNamespace, (Object[]) argsObj, false)
        Object[] args = (Object[]) argsObj
        Map attrs = [:]
        Closure body = null
        for (Object a : args) {
            if (a instanceof Map) attrs = new LinkedHashMap((Map) a)
            else if (a instanceof Closure) body = wrapBody((Closure) a)
            else if (a instanceof CharSequence) body = { Object ignored = null -> new SafeHtml(a.toString()) }
        }
        GspRenderContext rc = GspRenderContext.currentOrNull()
        if (rc != null) return registry.invokeAsMethod(namespace, name, attrs, body, rc)
        // fora de uma GSP (ex.: controller): cria um contexto temporário
        def scope = CurrentRequest.orNull()
        ViewRequest wr = scope instanceof ViewRequest ? (ViewRequest) scope : null
        def temp = new GspRenderContext(wr, wr?.views, new org.apache.groovy.io.StringBuilderWriter())
        temp.bind { registry.invokeAsMethod(namespace, name, attrs, body, temp) }
    }

    private static Closure wrapBody(Closure user) {
        return { Object arg = null ->
            Object r = user.maximumNumberOfParameters == 0 ? user.call() : user.call(arg)
            new SafeHtml(r?.toString())
        }
    }
}
