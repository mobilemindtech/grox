package org.grox.view.gsp

import groovy.transform.CompileStatic
import org.grox.http.Html
import org.grox.http.SafeHtml

/**
 * Classe base das GSPs compiladas. A classe é dinâmica ({@code getProperty}/{@code methodMissing}
 * resolvem as variáveis e as tags das expressões do usuário), mas os métodos chamados pelo código
 * gerado são estáticos: no JFR, o despacho dinâmico deles (com o corpo da tag às vezes null)
 * religava call sites a cada requisição.
 */
abstract class GspPage extends Script {

    String[] texts
    String viewPath
    GspRenderContext rc

    @CompileStatic
    Writer getOut() {
        rc.out
    }

    @CompileStatic
    void printText(int index) {
        rc.out.write(texts[index])
    }

    @CompileStatic
    void printEncoded(Object value) {
        if (value != null) rc.out.write(Html.escape(value))
    }

    @CompileStatic
    SafeHtml raw(Object value) {
        new SafeHtml(value?.toString())
    }

    @CompileStatic
    void setPageVariable(String name, Object value) {
        rc.pageScope.setVariable(name, value)
    }

    @CompileStatic
    SafeHtml captureBody(Closure body) {
        rc.capture(body)
    }

    @CompileStatic
    void invokeTag(String namespace, String name, Map attrs, Closure body) {
        rc.tags.invoke(namespace, name, attrs, body == null ? null : bodyFunction(body), rc)
    }

    /**
     * Corpo da tag: {@code body()} / {@code body(item)} / {@code body([var: valor])}.
     * Não privado: a página gerada é subclasse, e métodos privados chamados de closures
     * passam por MissingMethodException antes de serem achados.
     */
    @CompileStatic
    protected Closure bodyFunction(Closure body) {
        return { Object arg = null -> rc.capture { withVariables(arg) { body.call() } } }
    }

    @CompileStatic
    protected Object withVariables(Object arg, Closure c) {
        if (arg == null) return c.call()
        Map vars = arg instanceof Map ? (Map) arg : [it: arg]
        Binding b = getBinding()
        Map variables = b.variables
        Map<String, Object> saved = [:]
        for (Object k : vars.keySet()) {
            String n = k.toString()
            if (variables.containsKey(n)) saved.put(n, variables.get(n))
            b.setVariable(n, vars.get(k))
        }
        try {
            c.call()
        } finally {
            for (Object k : vars.keySet()) {
                String n = k.toString()
                if (saved.containsKey(n)) b.setVariable(n, saved.get(n)) else variables.remove(n)
            }
        }
    }

    @Override
    @CompileStatic
    Object getProperty(String name) {
        if (name == 'out') return rc.out
        Binding b = getBinding()
        if (b.hasVariable(name)) return b.getVariable(name)
        if (rc.pageScope.hasVariable(name)) return rc.pageScope.getVariable(name)
        if (rc.tags.hasNamespace(name)) return rc.tags.namespace(name)
        // variável ausente é null, como no Grails; sem exceção por acesso (templates com variáveis opcionais)
        if (getMetaClass().hasProperty(this, name) == null) return null
        try {
            return getMetaClass().getProperty(this, name)
        } catch (MissingPropertyException ignored) {
            return null
        }
    }

    /** Tags do namespace {@code g} chamadas como método: {@code ${createLink(action: 'x')}}. */
    Object methodMissing(String name, Object args) {
        String ns = rc.tags.bareTagNamespace(name)
        if (ns != null) return rc.tags.namespace(ns).invokeMethod(name, args)
        throw new org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack(name, getClass(), (Object[]) args, false)
    }
}
