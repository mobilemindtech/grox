package org.grox

import org.grox.core.Config
import org.grox.dsl.ProjectDsl
import org.grox.dsl.RouteBuilder
import org.grox.dslapp.AppScript
import org.junit.jupiter.api.Test

/** DSL de projeto: o script é lido sem subir a app; config, segurança, rotas e beans viram a declaração. */
class ProjectDslTest {

    static class SemDsl {}

    static class ComDsl {
        static app = {
            config { grox.origem = 'classe' }
            routes { get('/a') { render text: 'a' } }
            bootstrap {
                init { }
                destroy { }
            }
        }
    }

    @Test
    void 'script grox lido sem subir a aplicacao'() {
        def spec = ProjectDsl.of(AppScript)
        assert spec.config.grox.routes.poolSize == 4 && spec.config.server.port == 9999
        assert spec.config.grails.plugin.springsecurity.useBasicAuth == true
        assert spec.config.grails.plugin.springsecurity.rest.active == false
        assert spec.config.grox.security.users == [[username: 'ana', password: '{noop}ana', roles: ['ROLE_X']]]
        assert spec.config.grox.security.rules == [[pattern: '/x/**', access: ['ROLE_X']],
                                                  [pattern: '/y', access: ['permitAll', 'isAuthenticated()'], httpMethod: 'GET']]
        assert spec.routes.size() == 1 && spec.beans.size() == 1
    }

    @Test
    void 'classe com static app e classe sem DSL'() {
        assert ProjectDsl.of(SemDsl) == null
        def spec = ProjectDsl.of(ComDsl)
        assert spec.config.grox.origem == 'classe' && spec.routes.size() == 1
        assert spec.inits.size() == 1 && spec.destroys.size() == 1
    }

    @Test
    void 'rotas: barra entre prefixo e caminho, verbos sem caminho, ns e interceptores por escopo'() {
        assert RouteBuilder.join('/user', '{id}') == '/user/{id}'
        assert RouteBuilder.join('/user', '/{id}') == '/user/{id}'
        assert RouteBuilder.join('/user', '') == '/user'
        assert RouteBuilder.join('/user', '/') == '/user'
        assert RouteBuilder.join('', 'x') == '/x'
        def b = new RouteBuilder(null)
        Closure body = {
            before { 'raiz' }
            ns('/user') {
                after { 'grupo' }
                get { }
                post('{id}') { }
            }
            get('/fora') { }
        }
        body.delegate = b
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.call()
        assert b.routes*.path == ['/user', '/user/{id}', '/fora']
        assert b.routes*.httpMethod == ['GET', 'POST', 'GET']
        assert b.routes[0].scope.chain()*.kind == ['before', 'after']
        assert b.routes[2].scope.chain()*.kind == ['before']
    }

    @Test
    void 'padroes da DSL nao sobrescrevem os arquivos'() {
        def config = new Config([server: [port: 8080], grox: [a: 1]])
        config.mergeDefaults([server: [port: 9999, address: '127.0.0.1'], grox: [a: 2, b: 3], novo: [x: '${server.port}']])
        assert config.getProperty('server.port', Integer) == 8080 && config.getProperty('server.address', String) == '127.0.0.1'
        assert config.getProperty('grox.a', Integer) == 1 && config.getProperty('grox.b', Integer) == 3
        assert config.getProperty('novo.x', String) == '8080'   // placeholders resolvidos com a configuração final
    }
}
