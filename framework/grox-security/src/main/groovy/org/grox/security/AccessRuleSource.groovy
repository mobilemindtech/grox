package org.grox.security

import org.grox.http.GroxRequest
import org.grox.routing.RouteMatch

/**
 * Regras de acesso declaradas no código (o {@code @Secured} do plugin spring-security), com
 * precedência sobre o {@code interceptUrlMap}. Devolve null quando a rota não declara nada.
 */
interface AccessRuleSource {
    List<String> accessFor(GroxRequest req, RouteMatch route)
}
