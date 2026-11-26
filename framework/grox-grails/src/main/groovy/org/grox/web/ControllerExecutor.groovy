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
@groovy.util.logging.Slf4j
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
        if (pending instanceof List) {
            List p = (List) pending
            return continueAsync(wr, auth, (CompletableFuture) p[0], (List<InterceptorApi>) p[1], (List<InterceptorApi>) p[2])
        }
        wr.finish()
        wr.response
    }

    /** Conclui a resposta quando a promessa completa (fora do ator, com a própria sessão de dados). */
    private CompletableFuture<GroxResponse> continueAsync(WebRequest wr, Authentication auth, CompletableFuture future,
                                                          List<InterceptorApi> interceptors, List<InterceptorApi> passed) {
        int timeout = ctx.asyncTimeoutSeconds
        ((CompletableFuture<Object>) future).orTimeout(timeout, TimeUnit.SECONDS).handleAsync({ Object value, Throwable err ->
            Throwable e = err instanceof CompletionException && err.cause != null ? err.cause : err
            Throwable failure = e instanceof TimeoutException ? null : e
            try {
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
                return wr.response
            } catch (Throwable t) {
                if (failure == null) failure = t instanceof CompletionException && t.cause != null ? t.cause : t
                throw t
            } finally {
                Throwable f = failure
                withAppClassLoader { RequestContext.with(wr) { SecurityContextHolder.with(auth) { afterView(wr, passed, f) } } }
            }
        } as BiFunction<Object, Throwable, GroxResponse>, AsyncSupport.executor)
    }

    /**
     * {@code afterView()} dos interceptors cujo {@code before()} passou, em ordem inversa, como o
     * {@code afterCompletion} do Spring: também quando a action ou a view lançou exceção (em {@code throwable}).
     * Uma exceção num {@code afterView} vai para o log e não esconde a da requisição.
     */
    private static void afterView(WebRequest wr, List<InterceptorApi> passed, Throwable error) {
        if (passed.isEmpty()) return
        if (error != null && wr.request.getAttribute('exception') == null) wr.request.setAttribute('exception', error)
        for (InterceptorApi i : passed.reverse()) {
            try {
                i.afterView()
            } catch (Throwable t) {
                log.error("afterView de ${i.getClass().name} falhou", t)
            }
        }
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
        List<InterceptorApi> passed = []
        boolean async = false
        Throwable error = null
        try {
            wr.deferViews = true
            for (InterceptorApi i : interceptors) {
                if (!i.before()) {
                    ControllerSupport.renderPendingView(wr)
                    return null
                }
                passed << i
            }
            Object result = action.invoke(controller, action.bindArguments(wr, autowirer))
            if (GroxPromise.isAsync(result)) {
                async = true   // o afterView roda quando a promessa completar
                return [GroxPromise.toFuture(result), interceptors, passed]
            }
            complete(wr, result, interceptors)
            return null
        } catch (Throwable t) {
            error = t
            throw t
        } finally {
            if (!async) afterView(wr, passed, error)
        }
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
    }
}
