package org.grox.http

import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.grox.management.AccessLog
import org.grox.management.Management
import org.grox.routing.RouteMatch
import org.grox.routing.Router
import org.grox.session.RequestSession
import org.grox.session.SessionRepository

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Atendimento HTTP comum a qualquer tipo de aplicação: {@code _method}, caminho seguro, filtro de
 * segurança ({@link RequestFilter}) fora do event loop, recursos ({@link ResourceHandler}), endpoints
 * especiais ({@link Endpoint}), as rotas do {@link Router}, páginas de erro, gravação da sessão,
 * cabeçalhos de segurança, access log e métricas. Montado pelo {@link HttpModule}; os outros módulos
 * plugam o roteador, o filtro e os recursos.
 */
@Slf4j
@groovy.transform.CompileStatic
class HttpPipeline implements RequestDispatcher, AutoCloseable {

    static final String ERROR_DISPATCH = 'grox.errorDispatch'
    static final String ROUTE_ATTR = 'grox.route'
    /** Métodos roteados até as rotas; TRACE/CONNECT dão 405 e os desconhecidos, 501. */
    static final Set<String> KNOWN_METHODS = ['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'] as Set

    SessionRepository sessions
    /** Estáticos e assets, na ordem em que são consultados. */
    final List<ResourceHandler> resources = []
    /** Endpoints especiais (management, console); a segurança usa a mesma lista. */
    final List<Endpoint> endpoints = []
    Management management
    AccessLog accessLog
    /** Cabeçalhos acrescentados às respostas que não os definem ({@code grox.http.securityHeaders}). */
    Map<String, String> securityHeaders = [:]
    RequestFilter filter
    Router router
    /** Exceções com status próprio ({@code GroxModule.exceptionStatuses}): aviso no log, sem stack. */
    Map<Class<? extends Throwable>, Integer> exceptionStatuses = [:]
    /** Em development, a página de erro padrão mostra a mensagem e o stack da exceção. */
    boolean development
    final ExecutorService blocking = Executors.newVirtualThreadPerTaskExecutor()

    @Override
    CompletableFuture<GroxResponse> dispatch(GroxRequest req) {
        long start = management?.metrics?.begin() ?: System.nanoTime()
        CompletableFuture<GroxResponse> routed
        try {
            routed = route(req)
        } catch (Throwable t) {
            // exceção síncrona (fora do future): ainda passa pelo end das métricas e pelo access log
            routed = CompletableFuture.failedFuture(t)
        }
        // lambdas nativas: uma closure passada a uma interface funcional vira um proxy do JDK (ConversionHandler)
        routed.thenCompose((GroxResponse r) -> commitSession(req, r)).thenApply((GroxResponse r) -> withSecurityHeaders(r)).whenComplete((GroxResponse r, Throwable e) -> {
            String key = routeKey(req)
            management?.metrics?.end(req, r, e, start, key)
            accessLog?.log(req, r, e, start, key)
        })
    }

    /** Pluga um roteador; com mais de um, eles são consultados na ordem em que entraram. */
    void addRouter(Router r) {
        router = router == null ? r : new org.grox.routing.CompositeRouter((router instanceof org.grox.routing.CompositeRouter ?
                ((org.grox.routing.CompositeRouter) router).routers : [router]) + [r])
    }

    /** O endpoint especial do caminho, ou null. */
    @groovy.transform.CompileStatic
    Endpoint endpointFor(String path) {
        for (Endpoint e : endpoints) if (e.handles(path)) return e
        null
    }

    /** O recurso (estático ou asset) que atende a requisição, ou null. */
    @groovy.transform.CompileStatic
    ResourceHandler resourceFor(GroxRequest req) {
        for (ResourceHandler r : resources) if (r.handles(req)) return r
        null
    }

    /** Cabeçalhos de segurança padrão, sem sobrescrever os definidos pela aplicação. */
    @groovy.transform.CompileStatic
    private GroxResponse withSecurityHeaders(GroxResponse r) {
        for (Map.Entry<String, String> h : securityHeaders.entrySet()) if (!r.containsHeader(h.key)) r.setHeader(h.key, h.value)
        r
    }

    /** Rota para métricas: a do roteador ({@code livro.show}), endpoint, recurso, security ou other. */
    String routeKey(GroxRequest req) {
        RouteMatch m = (RouteMatch) req.getAttribute(ROUTE_ATTR)
        String key = m != null ? router?.routeKey(m) : null
        if (key != null) return key
        Endpoint endpoint = endpointFor(req.path)
        if (endpoint != null) return endpoint.name
        ResourceHandler resource = resourceFor(req)
        if (resource != null) return resource.name
        if (filter?.ownsPath(req.path)) return 'security'
        'other'
    }

    /** Grava a sessão (IO, possivelmente Redis) fora do event loop, se ela foi usada. */
    private CompletableFuture<GroxResponse> commitSession(GroxRequest req, GroxResponse r) {
        RequestSession rs = RequestSession.existing(req)
        if (rs == null) return CompletableFuture.completedFuture(r)
        // já fora do event loop (a virtual thread do handler): grava aqui mesmo, sem outro salto de thread
        if (Thread.currentThread().virtual) {
            try {
                rs.commit(r)
                return CompletableFuture.completedFuture(r)
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t)
            }
        }
        CompletableFuture.supplyAsync(() -> {
            rs.commit(r)
            r
        }, blocking)
    }

    CompletableFuture<GroxResponse> route(GroxRequest req) {
        try {
            overrideMethod(req)
            if (!safePath(req.path)) return error(req, 400, null)
            if (!(req.method in KNOWN_METHODS)) return error(req, req.method == 'TRACE' || req.method == 'CONNECT' ? 405 : 501, null)
            if (filter == null) return proceed(req)
            ResourceHandler resource = resourceFor(req)
            if (filter.bypasses(req, resource)) return proceed(req)
            // filtro fora do event loop (sessão/Redis, GORM, bcrypt)
            return CompletableFuture.supplyAsync(() -> {
                boolean special = resource != null || endpointFor(req.path) != null
                RouteMatch m = special ? null : router?.match(req)
                if (m != null) req.setAttribute(ROUTE_ATTR, m)
                filter.process(req, m)
            }, blocking).handle((GroxResponse r, Throwable e) -> {
                if (e != null) return error(req, 500, unwrap(e))
                r == null ? proceed(req) : respond(req, r)
            }).thenCompose((CompletableFuture<GroxResponse> f) -> f)
        } catch (Throwable t) {
            return error(req, 500, t)
        }
    }

    CompletableFuture<GroxResponse> proceed(GroxRequest req) {
        try {
            ResourceHandler resource = resourceFor(req)
            if (resource != null) return serve(req, resource)
            Endpoint endpoint = endpointFor(req.path)
            if (endpoint != null) return runEndpoint(req, endpoint)
            RouteMatch m = (RouteMatch) req.getAttribute(ROUTE_ATTR) ?: router?.match(req)
            if (m == null) return error(req, 404, null)
            if (m.methodNotAllowed) return error(req, 405, null)
            return finish(req, router.handle(req, m))
        } catch (Throwable t) {
            return error(req, 500, t)
        }
    }

    /** Resposta de uma etapa: exceção vira 500 e {@code errorStatus}, a página de erro. */
    CompletableFuture<GroxResponse> finish(GroxRequest req, CompletableFuture<GroxResponse> future) {
        future.handle((GroxResponse r, Throwable e) -> e != null ? error(req, 500, unwrap(e)) : respond(req, r))
                .thenCompose((CompletableFuture<GroxResponse> f) -> f)
    }

    private CompletableFuture<GroxResponse> respond(GroxRequest req, GroxResponse r) {
        r.errorStatus != null ? error(req, r.errorStatus, null, r) : CompletableFuture.completedFuture(r)
    }

    /** Endpoint especial (management, console), fora do event loop. */
    private CompletableFuture<GroxResponse> runEndpoint(GroxRequest req, Endpoint endpoint) {
        if (filter == null && !endpoint.allowedWithoutSecurity(req)) return error(req, 403, null)
        finish(req, CompletableFuture.supplyAsync(() -> endpoint.handle(req), blocking))
    }

    private CompletableFuture<GroxResponse> serve(GroxRequest req, ResourceHandler resource) {
        CompletableFuture.supplyAsync(() -> resource.serve(req), blocking).handle((GroxResponse r, Throwable e) ->
            e != null ? error(req, 500, unwrap(e)) : (r != null ? CompletableFuture.completedFuture(r) : error(req, 404, null))
        ).thenCompose((CompletableFuture<GroxResponse> f) -> f)
    }

    /** Rejeita caminhos com segmentos {@code .}/{@code ..} (evita contornar as regras de URL). */
    private static final java.util.regex.Pattern DOT_SEGMENT = ~/(^|\/)\.{1,2}(\/|$)/

    @groovy.transform.CompileStatic
    static boolean safePath(String path) {
        path != null && path.startsWith('/') && !path.contains('\\') && !path.contains('\u0000') &&
                (path.indexOf('.') < 0 || !DOT_SEGMENT.matcher(path).find())
    }

    /** {@code _method} (formulários) ou {@code X-HTTP-Method-Override} em POST. */
    static void overrideMethod(GroxRequest req) {
        if (req.method != 'POST') return
        String m = (req.formParameters?.get('_method')?.getAt(0) ?: req.getHeader('X-HTTP-Method-Override'))?.toUpperCase()
        if (m in ['PUT', 'PATCH', 'DELETE']) req.method = m
    }

    CompletableFuture<GroxResponse> error(GroxRequest req, int status, Throwable ex, GroxResponse original = null) {
        Integer mapped = ex == null ? null : exceptionStatuses.find { Class<? extends Throwable> type, Integer s -> type.isInstance(ex) }?.value
        if (mapped != null) {
            // ex.: usuário sem tenant tentando acessar dados multi-tenant (403)
            log.warn("${req}: ${ex.message}")
            status = mapped
            ex = null
        }
        if (ex != null) log.error("Erro processando ${req}", ex)
        if (req.getAttribute(ERROR_DISPATCH) != null) {
            return CompletableFuture.completedFuture(defaultError(req, status, ex, original))
        }
        req.setAttribute(ERROR_DISPATCH, true)
        req.setAttribute('grox.error.status', status)
        if (ex != null) req.setAttribute('exception', ex)
        // atributos de erro da Servlet API (views de erro do Grails: request['javax.servlet.error.status_code'])
        ['javax', 'jakarta'].each { String p ->
            req.setAttribute("${p}.servlet.error.status_code".toString(), status)
            req.setAttribute("${p}.servlet.error.request_uri".toString(), req.path)
            if (ex != null) {
                req.setAttribute("${p}.servlet.error.exception".toString(), ex)
                req.setAttribute("${p}.servlet.error.message".toString(), ex.message)
            }
        }
        CompletableFuture<GroxResponse> page = router?.errorPage(req, status)
        if (page != null) return finish(req, page)
        CompletableFuture.completedFuture(defaultError(req, status, ex, original))
    }

    GroxResponse defaultError(GroxRequest req, int status, Throwable ex, GroxResponse original) {
        GroxResponse resp = original ?: new GroxResponse()
        resp.resetBuffer()
        resp.status = status
        resp.errorStatus = null
        // a mensagem da exceção (SQL, tabelas, caminhos) só aparece em development; fora dele fica no log
        String message = original?.errorMessage ?: (ex && development ? ex.message : reason(status))
        boolean json = req.path.endsWith('.json') || MimeTypes.fromAccept(req.getHeader('Accept')) == 'json'
        if (json) {
            resp.contentType = 'application/json'
            Map body = [status: status, error: reason(status), message: message, path: req.path]
            resp.writer.write(JsonOutput.toJson(body))
        } else {
            resp.contentType = 'text/html'
            String trace = ex && development ? "<pre>${Html.escape(stackTrace(ex))}</pre>" : ''
            resp.writer.write("""<!DOCTYPE html><html><head><title>${status} ${reason(status)}</title></head>
<body><h1>${status} ${reason(status)}</h1><p>${Html.escape(message ?: '')}</p><p><code>${Html.escape(req.method)} ${Html.escape(req.path)}</code></p>${trace}</body></html>""")
        }
        resp
    }

    static Throwable unwrap(Throwable e) {
        while ((e instanceof CompletionException || e instanceof ExecutionException) && e.cause != null) e = e.cause
        e
    }

    static String reason(int status) {
        io.netty.handler.codec.http.HttpResponseStatus.valueOf(status).reasonPhrase()
    }

    private static String stackTrace(Throwable t) {
        def sw = new StringWriter()
        t.printStackTrace(new PrintWriter(sw))
        sw.toString()
    }

    @Override
    void close() {
        blocking.shutdown()
    }
}
