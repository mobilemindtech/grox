package org.grox.dsl

import org.grox.routing.RouteTarget

/** As rotas de uma classe ({@link Routes} ou {@link Handler}): o "controller" no roteamento e nos links. */
class RouteGroup implements RouteTarget {

    /** Nome lógico: {@code autores} para {@code Autores}, {@code livro} para {@code LivroRoutes}. */
    final String name
    final Class<?> type
    /** Ação → rota, na ordem de declaração. */
    final Map<String, Route> routes = new LinkedHashMap<>()

    RouteGroup(String name, Class<?> type) {
        this.name = name
        this.type = type
    }

    @Override
    String getDefaultAction() { null }

    @Override
    boolean hasAction(String action) { routes.containsKey(action) }
}
