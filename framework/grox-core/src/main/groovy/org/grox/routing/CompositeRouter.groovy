package org.grox.routing

import org.grox.http.GroxRequest
import org.grox.http.GroxResponse

import java.util.concurrent.CompletableFuture

/**
 * Vários roteadores na mesma aplicação (controllers do Grails e rotas do {@code grox-dsl}), consultados
 * na ordem em que entraram: vale o primeiro que casar a rota. Um 405 de um roteador só vale se nenhum
 * outro casar o caminho.
 */
class CompositeRouter implements Router {

    final List<Router> routers

    CompositeRouter(List<Router> routers) {
        this.routers = routers
    }

    @Override
    RouteMatch match(GroxRequest req) {
        RouteMatch notAllowed = null
        for (Router r : routers) {
            RouteMatch m = r.match(req)
            if (m == null) continue
            m.router = r
            if (!m.methodNotAllowed) return m
            notAllowed = notAllowed ?: m
        }
        notAllowed
    }

    @Override
    CompletableFuture<GroxResponse> handle(GroxRequest req, RouteMatch match) {
        (match.router ?: routers[0]).handle(req, match)
    }

    @Override
    CompletableFuture<GroxResponse> errorPage(GroxRequest req, int status) {
        for (Router r : routers) {
            CompletableFuture<GroxResponse> page = r.errorPage(req, status)
            if (page != null) return page
        }
        null
    }

    @Override
    String routeKey(RouteMatch match) {
        match.router?.routeKey(match)
    }

    @Override
    boolean hasTarget(String name) {
        routers.any { it.hasTarget(name) }
    }
}
