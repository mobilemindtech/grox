package org.grox.view.gsp

import org.grox.view.ViewRequest

/** Binding com o modelo e os objetos implícitos da GSP (resolvidos sob demanda). */
@groovy.transform.CompileStatic
class GspBinding extends Binding {

    static final Set<String> IMPLICIT = ['params', 'request', 'response', 'session', 'flash', 'controllerName',
                                         'actionName', 'grailsApplication', 'webRequest', 'pageScope', 'locale'] as Set

    private final GspRenderContext rc
    /** Binding da página que renderizou este template: como no Grails, o template enxerga o modelo dela. */
    private final GspBinding parent

    GspBinding(Map model, GspRenderContext rc, GspBinding parent = null) {
        super(new LinkedHashMap(model ?: [:]))
        this.rc = rc
        this.parent = parent
    }

    @Override
    boolean hasVariable(String name) {
        super.hasVariable(name) || name in IMPLICIT || parent?.hasVariable(name)
    }

    @Override
    Object getVariable(String name) {
        if (super.hasVariable(name)) return super.getVariable(name)
        if (parent != null && !(name in IMPLICIT) && parent.hasVariable(name)) return parent.getVariable(name)
        ViewRequest wr = rc.webRequest
        // if/else: um switch sobre String passa por isCase (despacho dinâmico) em cada case
        if (name == 'params') return wr != null ? wr.params : new org.grox.http.GroxParams()   // sem Elvis: GroxParams vazio é falso
        if (name == 'request') return wr?.request
        if (name == 'session') return wr?.session
        if (name == 'flash') return wr?.flash
        if (name == 'controllerName') return wr?.controllerName
        if (name == 'actionName') return wr?.actionName
        if (name == 'pageScope') return rc.pageScope
        if (name == 'webRequest') return wr
        if (name == 'response') return wr?.response
        if (name == 'grailsApplication') return wr?.application
        if (name == 'locale') return wr?.locale
        super.getVariable(name)
    }
}
