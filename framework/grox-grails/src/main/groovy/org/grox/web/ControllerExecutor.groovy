package org.grox.web

import org.grox.async.AsyncSupport
import org.grox.async.GroxPromise
import org.grox.core.Autowirer

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.BiFunction
import org.grox.data.Persistence
import org.grox.security.Authentication
import org.grox.web.interceptor.InterceptorApi
import org.grox.security.SecurityContextHolder
import org.grox.http.GroxResponse

/**
 * Executa uma action dentro do ator: binding, invocação e renderização padrão.
 * Se a action devolver uma Promise/CompletionStage/Awaitable, o ator é liberado e a resposta
 * é concluída (view, interceptors after/afterView) quando ela completar. Estático: roda em toda requisição.
 */
@groovy.transform.CompileStatic
class ControllerExecutor {

    final ControllerArtefact artefact
    final WebContext ctx
    final Autowirer autowirer

    ControllerExecutor(ControllerArtefact artefact, WebContext ctx, Autowirer autowirer) {
        this.artefact = artefact
        this.ctx = ctx
        this.autowirer = autowirer
    }

    /** @return GroxResponse ou CompletableFuture&lt;GroxResponse&gt; (action assíncrona) */
    Object execute(Object controller, Invocation inv) {
        def wr = new WebRequest(inv.request(), ctx, artefact, inv.actionName(), inv.routeParams())
        Authentication auth = (Authentication) inv.request().getAttribute(SecurityContextHolder.REQUEST_ATTR)
        Object pending = withAppClassLoader {
            RequestContext.with(wr) {
                SecurityContextHolder.with(auth) { Persistence.withSessionIfAvailable { runAction(controller, wr) } }
            }
        }
        if (pending instanceof List) return continueAsync(wr, auth, (CompletableFuture) pending[0], (List<InterceptorApi>) pending[1])
        wr.finish()
        wr.response
    }

    /** Conclui a resposta quando a promessa completa (fora do ator, com a própria sessão de dados). */
    private CompletableFuture<GroxResponse> continueAsync(WebRequest wr, Authentication auth, CompletableFuture future, List<InterceptorApi> interceptors) {
        int timeout = ctx.asyncTimeoutSeconds
        ((CompletableFuture<Object>) future).orTimeout(timeout, TimeUnit.SECONDS).handleAsync({ Object value, Throwable err ->
            Throwable e = err instanceof CompletionException && err.cause != null ? err.cause : err
            if (e instanceof TimeoutException) {
                wr.response.sendError(503, "A action assíncrona não terminou em ${timeout}s".toString())
                return wr.response
            }
            if (e != null) throw new CompletionException(e)
            withAppClassLoader {
                RequestContext.with(wr) {
                    SecurityContextHolder.with(auth) { Persistence.withSessionIfAvailable { complete(wr, value, interceptors) } }
                }
            }
            wr.finish()
            wr.response
        } as BiFunction<Object, Throwable, GroxResponse>, AsyncSupport.executor)
    }

    /**
     * A action e a view rodam com o classloader da aplicação como context classloader, como no Grails:
     * bibliotecas que o usam (varredura de pacotes, ServiceLoader) enxergam as classes da app. É o
     * classloader do controller: em desenvolvimento, o da geração atual da recarga (a classe Application,
     * estável, fica no do sistema). A thread (virtual, vinda do Netty) herdaria o do sistema.
     */
    private <T> T withAppClassLoader(Closure<T> body) {
        Thread t = Thread.currentThread()
        ClassLoader saved = t.contextClassLoader
        ClassLoader app = artefact.type.classLoader
        if (app == null || app.is(saved)) return body.call()
        t.contextClassLoader = app
        try {
            body.call()
        } finally {
            t.contextClassLoader = saved
        }
    }

    /** @return null (concluída) ou [future, interceptors] (assíncrona) */
    private Object runAction(Object controller, WebRequest wr) {
        ActionMethod action = artefact.actions[wr.actionName]
        List<String> allowed = artefact.allowedMethodsFor(wr.actionName)
        String method = wr.request.method == 'HEAD' ? 'GET' : wr.request.method
        if (allowed && !(method in allowed)) {
            wr.response.setHeader('Allow', allowed.join(', '))
            wr.response.sendError(405)
            return
        }
        List<InterceptorApi> interceptors = ctx.interceptors?.matching(wr) ?: []
        wr.deferViews = true
        for (InterceptorApi i : interceptors) {
            if (!i.before()) {
                ControllerSupport.renderPendingView(wr)
                return null
            }
        }
        Object result = action.invoke(controller, action.bindArguments(wr, autowirer))
        if (GroxPromise.isAsync(result)) return [GroxPromise.toFuture(result), interceptors]
        complete(wr, result, interceptors)
        null
    }

    /** View padrão (Map → modelo), interceptors after, view adiada e afterView. */
    private void complete(WebRequest wr, Object result, List<InterceptorApi> interceptors) {
        // como no Grails: corpo já escrito pela action (response.outputStream, pdfRenderingService...) dispensa a view
        if (!wr.response.committed && !wr.response.hasBody()) {
            Map model = result instanceof Map ? (Map) result : [:]
            ControllerSupport.renderView(wr, "/${artefact.logicalName}/${wr.actionName}", model)
        }
        for (InterceptorApi i : interceptors.reverse()) {
            if (!i.after()) {
                wr.pendingView = null
                break
            }
        }
        ControllerSupport.renderPendingView(wr)
        interceptors.reverse().each { it.afterView() }
    }
}
