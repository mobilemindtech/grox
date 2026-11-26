package org.grox.plugins.springsecurity

import grails.plugin.springsecurity.annotation.Secured
import org.grox.http.GroxRequest
import org.grox.routing.RouteMatch
import org.grox.security.AccessRuleSource
import org.grox.web.ActionMethod
import org.grox.web.ControllerArtefact

/** {@code @Secured} da action, depois do controller, como no spring-security-core. */
class SecuredRules implements AccessRuleSource {

    @Override
    List<String> accessFor(GroxRequest req, RouteMatch route) {
        // só controllers Grails: rotas de outros módulos (ex.: grox-dsl) têm as próprias regras
        if (!(route?.controller instanceof ControllerArtefact)) return null
        ControllerArtefact controller = (ControllerArtefact) route.controller
        ActionMethod action = controller.actions[route.actionName]
        Secured s = action?.method?.getAnnotation(Secured) ?: controller.type.getAnnotation(Secured)
        s != null && (s.httpMethod() == 'ANY' || s.httpMethod().equalsIgnoreCase(req.method)) ? s.value().toList() : null
    }
}
