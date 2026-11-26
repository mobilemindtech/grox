package sample

import org.grox.GroxApp
import org.grox.core.GroxRuntime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test


/** Health, métricas, JWT refresh/logout e GSPs pré-compiladas. */
class OperacaoTest {

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

    @Test
    void 'health e info publicos, metrics e prometheus so para admin'() {
        def anon = new TestClient(base)
        def health = anon.send('GET', '/actuator/health')
        assert health.status() == 200
        assert TestClient.parse(health) == [status: 'UP', checks: [db: [status: 'UP']]]
        assert TestClient.parse(anon.send('GET', '/actuator/info')).groovy == GroovySystem.version
        assert anon.send('GET', '/actuator/metrics', null, [Accept: 'application/json']).status() == 401
        assert new TestClient(base).withToken('maria', 'maria').send('GET', '/actuator/metrics').status() == 403

        def admin = new TestClient(base).withToken('admin', 'admin')
        admin.send('GET', '/api/usuarios')
        def metrics = TestClient.parse(admin.send('GET', '/actuator/metrics'))
        assert metrics.http.total > 0 && metrics.http.status['2xx'] > 0
        assert metrics.routes['usuario.index'].count >= 1
        assert metrics.jvm.heapMaxMb > 0
        String prom = admin.send('GET', '/actuator/prometheus').body()
        assert prom.contains('grox_route_requests_total{route="usuario.index"}') && prom.contains('# TYPE grox_http_requests_active gauge')
    }

    @Test
    void 'jobs como no plugin quartz: GrailsJobClass e bean com o nome completo, injetado'() {
        def ga = app.grailsApplication
        def jobs = ga.getArtefacts(grails.plugins.quartz.JobArtefactHandler.TYPE) as List<grails.plugins.quartz.GrailsJobClass>
        assert jobs*.shortName == ['ResumoJob']
        def job = jobs[0]
        assert job instanceof grails.plugins.quartz.DefaultGrailsJobClass && job.fullName == 'sample.ResumoJob'
        assert !job.concurrent && job.sessionRequired && job.group == 'GRAILS_JOBS' && job.enabled
        assert job.triggers.keySet() as List == ['resumoDiario']
        assert job.triggers.resumoDiario.triggerAttributes.cronExpression == '0 0 3 * * ?'
        def bean = ga.mainContext.getBean(job.fullName)
        assert bean.usuarioService != null
        bean.execute()
        assert bean.ultimoTotal >= 0
    }

    @Test
    void 'JWT refresh e logout com revogacao'() {
        def c = new TestClient(base)
        def login = TestClient.parse(c.send('POST', '/api/login', [username: 'maria', password: 'maria']))
        assert login.refresh_token
        // RestTokenCreationEvent entregue ao ApplicationListener da app, com o token emitido
        def listener = app.grailsApplication.mainContext.getBean('ultimoToken')
        assert listener.username == 'maria' && listener.accessToken == login.access_token && listener.autenticado == 'maria'
        def refreshed = c.send('POST', '/oauth/access_token', [grant_type: 'refresh_token', refresh_token: login.refresh_token])
        assert refreshed.status() == 200
        String novo = TestClient.parse(refreshed).access_token
        assert novo && novo != login.access_token
        // refresh token não serve como access token
        c.bearer = login.refresh_token
        assert c.send('GET', '/api/usuarios').status() == 401
        c.bearer = novo
        assert c.send('GET', '/api/usuarios').status() == 200
        assert c.send('POST', '/api/logout', [refresh_token: login.refresh_token]).status() == 200
        assert c.send('GET', '/api/usuarios').status() == 401
        c.bearer = null
        assert c.send('POST', '/oauth/access_token', [grant_type: 'refresh_token', refresh_token: login.refresh_token]).status() == 403
        assert c.send('POST', '/oauth/access_token', [grant_type: 'password']).status() == 400
    }

    @Test
    void 'views vem das GSPs pre-compiladas no build'() {
        def page = app.views.engine.find('/home/index')
        assert page.type.name == 'gsp_home_index' && page.source.file == null
        assert new TestClient(base).page('/').body().contains('Bem-vindo ao Grox')
    }
}
