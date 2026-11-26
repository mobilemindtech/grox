package org.grox.pug

import de.neuland.pug4j.model.PugModel
import org.grox.view.gsp.GspBinding
import org.grox.view.gsp.GspRenderContext

/**
 * As variáveis de uma expressão do Pug: o modelo do pug4j (com os escopos de {@code each}, mixins e blocos),
 * depois os objetos implícitos da GSP ({@code params}, {@code session}, {@code flash}, {@code request}...)
 * e os namespaces de tags ({@code g}, {@code sec}, os da aplicação). Atribuições vão ao modelo.
 */
class PugBinding extends Binding {

    private final PugModel model
    private GspBinding implicit

    PugBinding(PugModel model) {
        this.model = model
    }

    @Override
    Object getVariable(String name) {
        if (model.containsKey(name)) return model.get(name)
        GspRenderContext rc = GspRenderContext.currentOrNull()
        if (rc != null) {
            if (name in GspBinding.IMPLICIT) {
                if (implicit == null) implicit = new GspBinding([:], rc)
                return implicit.getVariable(name)
            }
            if (rc.tags.hasNamespace(name)) return rc.tags.namespace(name)
        }
        null   // variável ausente é null, como em JavaScript (undefined) e na GSP
    }

    @Override
    void setVariable(String name, Object value) {
        model.put(name, value)
    }

    @Override
    boolean hasVariable(String name) {
        true
    }

    @Override
    Map getVariables() {
        model
    }
}
