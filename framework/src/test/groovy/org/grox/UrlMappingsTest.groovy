package org.grox

import org.grox.routing.UrlMapping
import org.grox.routing.UrlMappingsBuilder
import org.grox.routing.UrlMappingsHolder
import org.junit.jupiter.api.Test

import static org.grox.routing.UrlMapping.MARK

class UrlMappingsTest {

    static String v(String name) { MARK + name + MARK }

    @Test
    void 'padrao default captura controller, action, id e format'() {
        def m = UrlMapping.parse("/${v('controller')}/${v('action')}?/${v('id')}?(.${v('format')})?")
        assert m.pattern == '/$controller/$action?/$id?(.$format)?'
        assert m.match('/livro') == [controller: 'livro']
        assert m.match('/livro/show/5') == [controller: 'livro', action: 'show', id: '5']
        assert m.match('/livro/show/5.json') == [controller: 'livro', action: 'show', id: '5', format: 'json']
        assert m.match('/livro/index.json') == [controller: 'livro', action: 'index', format: 'json']
        assert m.match('/livro/show/5/') == [controller: 'livro', action: 'show', id: '5']
        assert m.match('/') == null
    }

    @Test
    void 'raiz, literais mistos, curingas e constraints'() {
        assert UrlMapping.parse('/').match('/') == [:]
        assert UrlMapping.parse('/').match('/x') == null
        assert UrlMapping.parse("/livro-${v('id')}").match('/livro-42') == [id: '42']
        assert UrlMapping.parse("/arquivos/${v('path')}**").match('/arquivos/a/b/c.txt') == [path: 'a/b/c.txt']
        assert UrlMapping.parse('/static/**').match('/static/css/app.css') == [:]
        def m = UrlMapping.parse("/ano/${v('ano')}")
        m.constraints.ano = [matches: /\d{4}/]
        assert m.match('/ano/2026') == [ano: '2026']
        assert m.match('/ano/abc') == null
    }

    @Test
    void 'reverse gera URL e informa parametros consumidos'() {
        def m = UrlMapping.parse("/${v('controller')}/${v('action')}?/${v('id')}?(.${v('format')})?")
        Set used = [] as Set
        assert m.reverse([controller: 'livro', action: 'show', id: 5, sort: 'x'], used) == '/livro/show/5'
        assert used == ['controller', 'action', 'id'] as Set
        assert m.reverse([controller: 'livro'], [] as Set) == '/livro'
        assert m.reverse([controller: 'a b', action: 'x'], [] as Set) == '/a%20b/x'
        // valores do usuário não mudam a estrutura do link (?, #, /)
        assert m.reverse([controller: 'livro', action: 'show', id: '1?admin=1#x'], [] as Set) == '/livro/show/1%3Fadmin%3D1%23x'
        assert m.reverse([controller: 'livro', action: 'show', id: 1, format: 'json?x'], [] as Set) == '/livro/show/1.json%3Fx'
    }

    @Test
    void 'DSL completo com group, resources, verbos, corpo e erros'() {
        Closure mappings = {
            "/$controller/$action?/$id?(.$format)?" {
                constraints {
                    id(matches: /\d+/)
                }
            }
            "/"(controller: 'home')
            "/sobre"(view: '/sobre')
            "/antigo"(redirect: '/novo')
            "/ajuda"(controller: 'site', action: 'pagina') {
                pagina = 'ajuda'
            }
            "/livros/$id"(controller: 'livro') {
                action = [GET: 'show', DELETE: 'delete']
            }
            post "/login"(controller: 'auth', action: 'entrar')
            group "/api", {
                "/usuarios"(resources: 'usuario')
            }
            "404"(controller: 'erro', action: 'naoEncontrado')
        }
        def builder = new UrlMappingsBuilder()
        mappings.delegate = builder
        mappings.resolveStrategy = Closure.DELEGATE_FIRST
        mappings.call()
        def holder = new UrlMappingsHolder(builder.mappings)

        assert holder.errorMapping(404).attrs == [controller: 'erro', action: 'naoEncontrado']
        def byPattern = holder.mappings.groupBy { it.pattern }
        assert byPattern['/ajuda'][0].attrs.params == [pagina: 'ajuda']
        assert byPattern['/login'][0].httpMethod == 'POST'
        assert byPattern['/$controller/$action?/$id?(.$format)?'][0].constraints == [id: [matches: /\d+/]]
        def rest = holder.mappings.findAll { it.pattern.startsWith('/api/usuarios') }
        assert rest.collect { "${it.httpMethod} ${it.pattern} ${it.attrs.action}".toString() }.toSet() == [
                'GET /api/usuarios/create(.$format)? create',
                'GET /api/usuarios/$id/edit(.$format)? edit',
                'GET /api/usuarios(.$format)? index',
                'POST /api/usuarios(.$format)? save',
                'GET /api/usuarios/$id(.$format)? show',
                'PUT /api/usuarios/$id(.$format)? update',
                'PATCH /api/usuarios/$id(.$format)? patch',
                'DELETE /api/usuarios/$id(.$format)? delete',
        ] as Set
        // mais específico primeiro
        assert holder.mappings.findIndexOf { it.pattern.startsWith('/api/usuarios/create') } <
                holder.mappings.findIndexOf { it.pattern == '/api/usuarios/$id(.$format)?' && it.httpMethod == 'GET' }
        assert holder.mappings.last().pattern == '/$controller/$action?/$id?(.$format)?'
    }

    static class Cedente {
        Long id
    }

    @Test
    void 'createLink com resource: nome ou instancia (controller e id da instancia)'() {
        Closure mappings = {
            "/$controller/$action?/$id?"()
        }
        def builder = new UrlMappingsBuilder()
        mappings.delegate = builder
        mappings.resolveStrategy = Closure.DELEGATE_FIRST
        mappings.call()
        def links = new org.grox.routing.LinkGenerator(new UrlMappingsHolder(builder.mappings), [:], null)
        def cedente = new Cedente(id: 7)
        // g:form url="[resource: cedenteInstance, action: 'update']" dos scaffolds do Grails
        assert links.link([url: [resource: cedente, action: 'update']], 'outro') == '/cedente/update/7'
        assert links.link([resource: cedente]) == '/cedente/show/7'
        assert links.link([resource: 'livro', action: 'index']) == '/livro/index'
        assert links.link([resource: new Cedente(), action: 'save']) == '/cedente/save'
    }

    /** Destino de rota sem o módulo Grails (o roteamento do core só conhece {@link org.grox.routing.RouteTarget}). */
    static class Destino implements org.grox.routing.RouteTarget {
        String defaultAction
        Set<String> acoes

        boolean hasAction(String name) { name in acoes }
    }

    @Test
    void 'roteamento e links para um RouteTarget qualquer, sem controllers'() {
        def destinos = [livro: new Destino(defaultAction: 'index', acoes: ['index', 'show'] as Set)]
        def holder = UrlMappingsHolder.build([])
        def m = holder.match('/livro/show/5', 'GET', destinos)
        assert m.controller.is(destinos.livro) && m.actionName == 'show' && m.params.id == '5'
        assert holder.match('/livro', 'GET', destinos).actionName == 'index'
        assert holder.match('/livro/apagar', 'GET', destinos) == null
        assert new org.grox.routing.LinkGenerator(holder, destinos, null).link([controller: 'livro', action: 'show', id: 5]) == '/livro/show/5'
    }
}
