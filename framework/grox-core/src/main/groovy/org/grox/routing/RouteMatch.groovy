package org.grox.routing

class RouteMatch {
    UrlMapping mapping
    /** O destino casado (no módulo Grails, o {@code ControllerArtefact}). */
    RouteTarget controller
    String actionName
    String view
    String redirect
    boolean methodNotAllowed
    Map<String, Object> params = [:]
    /** O roteador que casou a rota (quando há mais de um, no {@link CompositeRouter}). */
    Router router
}
