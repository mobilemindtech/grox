package org.grox.dsl

import groovy.util.logging.Slf4j
import org.grox.async.ActorPool
import org.grox.data.Persistence
import org.grox.http.CurrentRequest
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.routing.RouteMatch
import org.grox.routing.Router
import org.grox.routing.UrlMapping
import org.grox.routing.UrlMappingsHolder
import org.grox.security.Authentication
import org.grox.security.SecurityContextHolder

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Function

/**
 * Roteador das rotas do {@code grox-dsl} no pipeline HTTP do core. O caminho casa como no UrlMappings
 * ({@code {id}} vira variável; rota mais específica primeiro; método errado dá 405). O handler roda numa
 * virtual thread ({@code execution: 'virtual'}) ou no pool de atores da rota ({@code 'actor'}), com a
 * requisição, a autenticação e uma sessão de dados vinculadas.
 */
@Slf4j
class DslRouter implements Router, AutoCloseable {

    final Map<String, RouteGroup> groups
    final UrlMappingsHolder holder
    final DslEnvironment env
    private final ExecutorService blocking = Executors.newVirtualThreadPerTaskExecutor()

    DslRouter(Map<String, RouteGroup> groups, DslEnvironment env) {
        this.groups = groups
        this.env = env
        int order = 0
        List<UrlMapping> mappings = []
        groups.values().each { RouteGroup g ->
            g.routes.values().each { Route r ->
                UrlMapping m = UrlMapping.parse(toPattern(r.path))
                m.attrs = [controller: g.name, action: r.action]
                m.httpMethod = r.httpMethod
                m.order = order++
                mappings << m
            }
        }
        this.holder = new UrlMappingsHolder(mappings)
    }

    /** {@code /livros/{id}} → o padrão do UrlMappings ({@code /livros/$id}, com o marcador de variável). */
    static String toPattern(String path) {
        path.replaceAll(/\{(\w+)\}/) { all, String name -> UrlMapping.MARK + name + UrlMapping.MARK }
    }

    int getRouteCount() {
        groups.values().sum(0) { RouteGroup g -> g.routes.size() } as int
    }

    @Override
    RouteMatch match(GroxRequest req) {
        holder.match(req.path, req.method, groups)
    }

    @Override
    CompletableFuture<GroxResponse> handle(GroxRequest req, RouteMatch m) {
        Route route = ((RouteGroup) m.controller).routes[m.actionName]
        def ctx = new RouteContext(req, route, m.params, env)
        if (route.actor) return route.pool.submit(ctx).toCompletableFuture().thenApply { Object r -> (GroxResponse) r }
        // já numa virtual thread (a do filtro de segurança): roda aqui mesmo, sem outro salto de thread
        if (Thread.currentThread().virtual) {
            try {
                return CompletableFuture.completedFuture(run(route, route.instance, ctx))
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t)
            }
        }
        CompletableFuture.supplyAsync({ -> run(route, route.instance, ctx) }, blocking)
    }

    /** Executa o handler com a requisição, a autenticação e a sessão de dados (open session in view). */
    static GroxResponse run(Route route, Object instance, RouteContext ctx) {
        ctx.advanceFlash()
        Authentication auth = (Authentication) ctx.request.getAttribute(SecurityContextHolder.REQUEST_ATTR)
        CurrentRequest.with(ctx) {
            SecurityContextHolder.with(auth) {
                Persistence.withSessionIfAvailable { ctx.complete(route.execute(instance, ctx)) }
            }
        }
    }

    /** Pool de atores da rota; com handler anotado, cada ator tem a sua instância da classe. */
    @groovy.transform.CompileStatic
    static ActorPool<RouteContext> pool(Route route, int size, Closure<Object> newInstance) {
        new ActorPool<RouteContext>(size, () -> handler(route, route.closure != null ? route.instance : newInstance.call()))
    }

    /** Lambda nativa: uma closure com {@code as Function} vira um proxy do JDK, chamado a cada mensagem. */
    @groovy.transform.CompileStatic
    private static Function<RouteContext, Object> handler(Route route, Object instance) {
        (RouteContext ctx) -> (Object) DslRouter.run(route, instance, ctx)
    }

    @Override
    CompletableFuture<GroxResponse> errorPage(GroxRequest req, int status) {
        null
    }

    @Override
    String routeKey(RouteMatch m) {
        m.controller instanceof RouteGroup ? "${((RouteGroup) m.controller).name}.${m.actionName}" : null
    }

    @Override
    boolean hasTarget(String name) {
        groups.containsKey(name)
    }

    @Override
    void close() {
        blocking.shutdown()
        groups.values().each { g -> g.routes.values().each { it.pool?.close() } }
    }
}
