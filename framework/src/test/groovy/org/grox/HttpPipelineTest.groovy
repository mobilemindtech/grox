package org.grox

import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.HttpPipeline
import org.grox.http.RequestFilter
import org.grox.http.ResourceHandler
import org.grox.routing.RouteMatch
import org.grox.routing.RouteTarget
import org.grox.routing.Router
import org.junit.jupiter.api.Test

import java.util.concurrent.CompletableFuture

/** O pipeline HTTP do core com um roteador e um filtro próprios, sem o módulo Grails. */
class HttpPipelineTest {

    static class Destino implements RouteTarget {
        String defaultAction = 'index'

        boolean hasAction(String name) { true }
    }

    /** /ola responde, /falha lança, /proibido devolve errorStatus 403, /tenant lança a exceção mapeada. */
    static class Rotas implements Router {
        List<Integer> paginasDeErro = []

        RouteMatch match(GroxRequest req) {
            req.path in ['/ola', '/falha', '/proibido', '/tenant'] ? new RouteMatch(controller: new Destino(), actionName: req.path.substring(1)) : null
        }

        CompletableFuture<GroxResponse> handle(GroxRequest req, RouteMatch m) {
            switch (m.actionName) {
                case 'falha': return CompletableFuture.failedFuture(new IllegalStateException('quebrou'))
                case 'tenant': throw new SemTenant()
                case 'proibido':
                    def r = new GroxResponse()
                    r.sendError(403, 'sem acesso')
                    return CompletableFuture.completedFuture(r)
                default:
                    def r = new GroxResponse(contentType: 'text/plain')
                    r.writer.write("ola ${m.actionName}")
                    return CompletableFuture.completedFuture(r)
            }
        }

        CompletableFuture<GroxResponse> errorPage(GroxRequest req, int status) {
            paginasDeErro << status
            null
        }

        String routeKey(RouteMatch m) { "rotas.${m.actionName}" }

        boolean hasTarget(String name) { false }
    }

    static class SemTenant extends RuntimeException {}

    static GroxRequest get(String path, String accept = 'application/json') {
        new GroxRequest(method: 'GET', path: path, uri: path, headers: new org.grox.http.HeaderMap().tap { set('Accept', accept) })
    }

    static GroxResponse send(HttpPipeline p, GroxRequest req) {
        p.dispatch(req).get()
    }

    @Test
    void 'rotas, erros, excecoes mapeadas, recursos e filtro sem Grails'() {
        def rotas = new Rotas()
        def p = new HttpPipeline(router: rotas, securityHeaders: ['X-Frame-Options': 'DENY'])
        p.exceptionStatuses[SemTenant] = 403
        p.resources << ([getName: { 'static' }, handles: { GroxRequest r -> r.path.startsWith('/static/') },
                         serve  : { GroxRequest r -> new GroxResponse(contentType: 'text/css').tap { writer.write('a{}') } }] as ResourceHandler)
        try {
            GroxResponse ola = send(p, get('/ola'))
            assert ola.status == 200 && ola.text == 'ola ola' && ola.getHeader('X-Frame-Options') == 'DENY'
            assert p.routeKey(get('/ola').tap { setAttribute(HttpPipeline.ROUTE_ATTR, rotas.match(it)) }) == 'rotas.ola'
            assert send(p, get('/nada')).status == 404
            GroxResponse falha = send(p, get('/falha'))
            assert falha.status == 500 && !falha.text.contains('quebrou')   // fora de development, sem a mensagem
            assert send(p, get('/proibido')).text.contains('sem acesso')
            assert send(p, get('/tenant')).status == 403
            assert rotas.paginasDeErro == [404, 500, 403, 403]
            assert send(p, get('/static/a.css')).text == 'a{}'
            assert send(p, get('/../x')).status == 400
            assert send(p, new GroxRequest(method: 'TRACE', path: '/ola', uri: '/ola')).status == 405

            // filtro: recursos passam direto; as rotas passam pelo filtro, que pode responder
            p.filter = [bypasses: { GroxRequest r, ResourceHandler res -> res != null },
                        process : { GroxRequest r, RouteMatch m -> m?.actionName == 'ola' ? null : new GroxResponse().tap { sendError(401, 'login') } },
                        ownsPath: { String path -> path == '/login' }] as RequestFilter
            assert send(p, get('/ola')).status == 200
            assert send(p, get('/falha')).status == 401
            assert send(p, get('/static/a.css')).status == 200
            assert p.routeKey(get('/login')) == 'security'
        } finally {
            p.close()
        }
    }
}
