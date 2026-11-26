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
        // a da action só vale para o seu httpMethod; nos demais métodos vale a do controller
        List<String> access = rules(action?.method?.getAnnotation(Secured), req.method)
        access != null ? access : rules(controller.type.getAnnotation(Secured), req.method)
    }

    private static List<String> rules(Secured s, String method) {
        s != null && (s.httpMethod() == 'ANY' || s.httpMethod().equalsIgnoreCase(method)) ? s.value().toList() : null
    }
}
