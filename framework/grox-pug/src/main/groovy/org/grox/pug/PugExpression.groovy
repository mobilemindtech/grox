package org.grox.pug

import org.grox.view.gsp.GspRenderContext

/** Classe base das expressões do Pug: tags do {@code g} (e de namespaces sem prefixo) chamadas como método, como na GSP. */
abstract class PugExpression extends Script {

    Object methodMissing(String name, Object args) {
        GspRenderContext rc = GspRenderContext.currentOrNull()
        String ns = rc?.tags?.bareTagNamespace(name)
        if (ns != null) return rc.tags.namespace(ns).invokeMethod(name, args)
        throw new org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack(name, getClass(), (Object[]) args, false)
    }
}
