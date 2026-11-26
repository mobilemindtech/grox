package org.grox.web

import groovy.util.logging.Slf4j
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.routing.RouteMatch
import org.grox.routing.Router
import org.grox.routing.UrlMapping
import org.grox.data.Persistence
import org.grox.security.Authentication
import org.grox.security.SecurityContextHolder

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Roteador do Grails no pipeline HTTP do core: casa o UrlMappings com os controllers (função pura, no
 * event loop) e envia a invocação ao ator do controller. Atende também redirects e views avulsas do
 * UrlMappings, e as páginas de erro mapeadas ({@code "404"}/{@code "500"}).
 */
@Slf4j
class ControllerRouter implements Router, AutoCloseable {

    final WebContext ctx
    private final ExecutorService blocking = Executors.newVirtualThreadPerTaskExecutor()

    ControllerRouter(WebContext ctx) {
        this.ctx = ctx
    }

    @Override
    RouteMatch match(GroxRequest req) {
        ctx.urlMappings.match(req.path, req.method, ctx.controllers)
    }

    @Override
    CompletableFuture<GroxResponse> handle(GroxRequest req, RouteMatch m) {
        if (m.redirect) {
            def resp = new GroxResponse()
            resp.sendRedirect(m.redirect, m.mapping.attrs.permanent ? 301 : 302)
            return CompletableFuture.completedFuture(resp)
        }
        if (m.view) return renderStandaloneView(req, m.view, m.params, 200)
        ControllerArtefact c = (ControllerArtefact) m.controller
        invoke(req, c, actionSubmitted(req, c) ?: m.actionName, m.params)
    }

    @Override
    CompletableFuture<GroxResponse> errorPage(GroxRequest req, int status) {
        UrlMapping em = ctx.urlMappings.errorMapping(status)
        ControllerArtefact target = em?.attrs?.controller ? ctx.controllers[em.attrs.controller.toString()] : null
        if (target != null) {
            String action = em.attrs.action?.toString() ?: target.defaultAction
            return invoke(req, target, action, [status: status]).thenApply { GroxResponse r ->
                if (r.status == 200) r.status = status
                r
            }
        }
        em?.attrs?.view ? renderStandaloneView(req, em.attrs.view.toString(), [status: status], status) : null
    }

    @Override
    String routeKey(RouteMatch m) {
        m.controller instanceof ControllerArtefact ? "${((ControllerArtefact) m.controller).logicalName}.${m.actionName}" : null
    }

    @Override
    boolean hasTarget(String name) {
        ctx.controllers.containsKey(name)
    }

    /** {@code <g:actionSubmit action="delete">} envia {@code _action_delete}: escolhe essa action do mesmo controller. */
    private static String actionSubmitted(GroxRequest req, ControllerArtefact c) {
        for (String k : req.formParameters.keySet()) {
            if (k.startsWith('_action_')) {
                String a = k.substring(8)
                if (c.actions.containsKey(a)) return a
            }
        }
        null
    }

    CompletableFuture<GroxResponse> invoke(GroxRequest req, ControllerArtefact c, String action, Map<String, Object> params) {
        c.dispatcher.submit(new Invocation(req, action, params)).toCompletableFuture()
                .thenCompose { Object r -> r instanceof CompletableFuture ? (CompletableFuture<GroxResponse>) r : CompletableFuture.completedFuture((GroxResponse) r) }
    }

    private CompletableFuture<GroxResponse> renderStandaloneView(GroxRequest req, String view, Map params, int status) {
        CompletableFuture.supplyAsync({
            def wr = new WebRequest(req, ctx, null, null, params)
            wr.response.status = status
            RequestContext.with(wr) {
                inView(req) { ctx.viewRenderer.render(view, [:], wr) }
            }
            wr.finish()
            wr.response
        }, blocking)
    }

    /** Autenticação da requisição e sessão de dados (open session in view) para renderizar fora de uma action. */
    static Object inView(GroxRequest req, Closure body) {
        Authentication auth = (Authentication) req.getAttribute(SecurityContextHolder.REQUEST_ATTR)
        SecurityContextHolder.with(auth) { Persistence.withSessionIfAvailable(body) }
    }

    @Override
    void close() {
        blocking.shutdown()
    }
}
