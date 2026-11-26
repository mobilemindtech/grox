package sample

import org.grox.GroxApp
import org.grox.core.GroxRuntime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/** Console web: acesso, CSRF, bindings, tenant, rollback e erros. */
class ConsoleTest {

    static GroxRuntime app
    static String base

    @BeforeAll
    static void start() {
        System.setProperty('grails.env', 'test')
        app = GroxApp.run(Application)
        base = "http://localhost:${app.port}"
    }

    @AfterAll
    static void stop() {
        app?.close()
    }

    static Map run(TestClient c, String code, boolean rollback = false) {
        String token = (c.page('/console').body() =~ /'X-CSRF-TOKEN': '([^']+)'/)[0][1]
        def r = c.send('POST', '/console/execute', [code: code, rollback: rollback], ['X-CSRF-TOKEN': token])
        assert r.status() == 200
        (Map) TestClient.parse(r)
    }

    @Test
    void 'somente admin, com CSRF'() {
        assert new TestClient(base).page('/console').status() == 302
        // sem sessão e sem token: não há o que forjar (sem CSRF), e a autorização recusa o anônimo
        assert new TestClient(base).send('POST', '/console/execute', [code: '1'], [Accept: 'application/json']).status() == 401
        assert new TestClient(base).loggedIn('maria', 'maria').page('/console').status() == 403
        def admin = new TestClient(base).loggedIn('admin', 'admin')
        def page = admin.page('/console')
        assert page.status() == 200 && page.body().contains('<textarea id="codigo"')
        assert admin.send('POST', '/console/execute', [code: '1']).status() == 403
        // JWT (stateless) não usa CSRF
        def jwt = new TestClient(base).withToken('admin', 'admin')
        assert TestClient.parse(jwt.send('POST', '/console/execute', [code: '6 * 7'])).result == '42'
    }

    @Test
    void 'execute recusa formulario text-plain e requisicoes de outro site'() {
        // o <form enctype="text/plain"> que monta um JSON válido: com Basic o navegador reenvia as credenciais
        def r = new TestClient(base).sendText('POST', '/console/execute', '{"code":"6 * 7","x":"="}', 'text/plain',
                [Authorization: 'Basic ' + 'admin:admin'.bytes.encodeBase64()])
        assert r.status() == 403 && !r.body().contains('42')

        def jwt = new TestClient(base).withToken('admin', 'admin')
        String host = URI.create(base).authority
        assert jwt.send('POST', '/console/execute', [code: '1'], [Origin: 'http://evil.example']).status() == 403
        assert jwt.send('POST', '/console/execute', [code: '1'], ['Sec-Fetch-Site': 'cross-site']).status() == 403
        assert jwt.send('POST', '/console/execute', [code: '1'], [Origin: "http://${host}".toString(), 'Sec-Fetch-Site': 'same-origin']).status() == 200
    }

    @Test
    void 'sem seguranca so aceita Host localhost'() {
        assert org.grox.plugins.console.WebConsole.isLoopbackHost('localhost:8080')
        assert org.grox.plugins.console.WebConsole.isLoopbackHost('127.0.0.1') && org.grox.plugins.console.WebConsole.isLoopbackHost('[::1]:8080')
        assert !org.grox.plugins.console.WebConsole.isLoopbackHost('evil.example:8080')     // DNS rebinding
        assert !org.grox.plugins.console.WebConsole.isLoopbackHost('127.0.0.1.evil.example')
        assert !org.grox.plugins.console.WebConsole.isLoopbackHost(null)
    }

    @Test
    void 'bindings, domain classes, tenant, println e rollback'() {
        def admin = new TestClient(base).loggedIn('admin', 'admin')
        def r = run(admin, 'println "oi"\n[tenant: Tenants.currentId(), user: springSecurityService.principal.username, ' +
                'beans: ctx.beanNames.contains("usuarioService") && ctx.getBean("ultimoToken") != null, svc: usuarioService.contar() >= 0, env: grailsApplication.environmentName]')
        assert r.output == 'oi\n'
        assert r.result == "['tenant':'acme', 'user':'admin', 'beans':true, 'svc':true, 'env':'test']"
        long antes = Usuario.withNewSession { Usuario.count() }
        def rb = run(admin, 'new Usuario(nome: "Temporário", email: "tmp@x.com").save(failOnError: true); Usuario.count()', true)
        assert rb.rolledBack && rb.result == (antes + 1).toString()
        assert Usuario.withNewSession { Usuario.count() } == antes
        assert run(admin, 'Tarefa.list()*.titulo').result.contains('Revisar contrato')
    }

    @Test
    void 'erros de compilacao e de execucao'() {
        def admin = new TestClient(base).loggedIn('admin', 'admin')
        def e1 = run(admin, 'def x = ')
        assert e1.error.contains('linha 1: Unexpected end of input')
        def e2 = run(admin, 'println "antes"\n1 / 0')
        assert e2.error.startsWith('java.lang.ArithmeticException') && e2.error.contains('(Console.groovy:2)')
        assert !e2.error.contains('WebConsole.groovy') && e2.output == 'antes\n'
    }
}
