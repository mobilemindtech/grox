package sample

import org.grox.GroxApp
import org.grox.core.GroxRuntime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import sample.auth.AuthenticationToken

/**
 * spring-security-rest com tokens em GORM e cabeçalho X-Auth-Token (como no piloto app A),
 * em vez do JWT com Authorization: Bearer do application.yml.
 */
class TokenGormTest {

    static final Map<String, String> PROPS = [
            'grails.plugin.springsecurity.rest.token.storage.useGorm'                   : 'true',
            'grails.plugin.springsecurity.rest.token.storage.gorm.tokenDomainClassName': 'sample.auth.AuthenticationToken',
            'grails.plugin.springsecurity.rest.token.validation.useBearerToken'         : 'false',
            'grails.plugin.springsecurity.rest.token.validation.headerName'             : 'X-Auth-Token',
    ]

    static GroxRuntime app
    static String base

    @BeforeAll
    static void start() {
        System.setProperty('grails.env', 'test')
        PROPS.each { k, v -> System.setProperty(k, v) }
        app = GroxApp.run(Application)
        base = "http://localhost:${app.port}"
    }

    @AfterAll
    static void stop() {
        app?.close()
        PROPS.keySet().each { System.clearProperty(it) }
    }

    static TestClient client() {
        new TestClient(base)
    }

    static String login(String username, String password) {
        def r = client().send('POST', '/api/login', [username: username, password: password])
        assert r.status() == 200
        TestClient.parse(r).access_token
    }

    static int status(String method, String path, Map<String, String> headers, Object json = null) {
        client().send(method, path, json, [Accept: 'application/json'] + headers).status()
    }

    @Test
    void 'login grava token opaco no GORM, sem expiracao nem refresh'() {
        def r = client().send('POST', '/api/login', [username: 'maria', password: 'maria'])
        def body = TestClient.parse(r)
        assert r.status() == 200
        assert body.username == 'maria' && body.roles == ['ROLE_USER'] && body.token_type == 'Bearer'
        assert body.access_token ==~ /[0-9a-v]{20,}/ && !body.access_token.contains('.')
        assert !body.containsKey('expires_in') && !body.containsKey('refresh_token')
        assert AuthenticationToken.withNewSession { AuthenticationToken.findByTokenValue(body.access_token)?.username } == 'maria'
        assert client().send('POST', '/api/login', [username: 'maria', password: 'x']).status() == 401
    }

    @Test
    void 'X-Auth-Token autentica sem sessao, Bearer e token invalido nao'() {
        String maria = login('maria', 'maria')
        def ok = client().send('GET', '/api/usuarios', null, ['X-Auth-Token': maria, Accept: 'application/json'])
        assert ok.status() == 200 && !ok.headers().firstValue('Set-Cookie').present
        assert status('POST', '/api/usuarios', ['X-Auth-Token': maria], [nome: 'X', email: 'x@x.com']) == 403
        assert status('POST', '/api/usuarios', ['X-Auth-Token': login('admin', 'admin')], [nome: 'Via Gorm', email: 'gorm@x.com']) == 201
        // usuário em memória (yml) também recebe token no GORM
        assert status('DELETE', '/api/usuarios/999999', ['X-Auth-Token': login('suporte', 'suporte')]) == 404

        assert status('GET', '/api/usuarios', [Authorization: "Bearer ${maria}".toString()]) == 401   // useBearerToken: false
        assert status('GET', '/api/usuarios', ['X-Auth-Token': 'nao-existe']) == 401
        assert status('GET', '/api/usuarios', [:]) == 401
    }

    @Test
    void 'logout apaga o token e refresh nao existe com GORM'() {
        String token = login('joao', 'joao')
        assert status('GET', '/api/usuarios', ['X-Auth-Token': token]) == 200
        assert status('POST', '/api/logout', ['X-Auth-Token': token]) == 200
        assert status('GET', '/api/usuarios', ['X-Auth-Token': token]) == 401
        assert AuthenticationToken.withNewSession { AuthenticationToken.countByTokenValue(token) } == 0
        assert status('POST', '/api/logout', [:]) == 401
        // sem JWT o endpoint de refresh não existe: vira uma URL comum (POST anônimo recusado pela autorização)
        def refresh = client().send('POST', '/oauth/access_token', [grant_type: 'refresh_token', refresh_token: 'x'], [Accept: 'application/json'])
        assert refresh.status() == 401 && !refresh.body().contains('"access_token"')
    }
}
