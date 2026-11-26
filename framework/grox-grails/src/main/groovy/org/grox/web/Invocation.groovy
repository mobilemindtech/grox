package org.grox.web

import org.grox.http.GroxRequest

/** Mensagem enviada ao ator do controller. */
record Invocation(GroxRequest request, String actionName, Map<String, Object> routeParams) {}
