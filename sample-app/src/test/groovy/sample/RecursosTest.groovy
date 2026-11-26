package sample

import org.grox.GroxApp
import org.grox.core.GroxRuntime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test


/** Paginação, ordenação, where/criteria, upload, typeMismatch e interceptors. */
class RecursosTest {

    static GroxRuntime app
    static String base
    static TestClient admin

    @BeforeAll
    static void start() {
        System.setProperty('grails.env', 'test')
        app = GroxApp.run(Application)
        base = "http://localhost:${app.port}"
        def api = new TestClient(base).withToken('admin', 'admin')
        (1..23).each { i -> assert api.send('POST', '/api/usuarios', [nome: "Pessoa ${String.format('%02d', i)}", email: "p${i}@x.com", idade: 10 + i]).status() == 201 }
        admin = new TestClient(base).loggedIn('admin', 'admin')
    }

    @AfterAll
    static void stop() {
        app?.close()
    }

    @Test
    void 'paginate e sortableColumn'() {
        String html = admin.page('/usuario/index?max=5&offset=5&sort=idade&order=desc').body()
        assert html.contains('<span class="currentStep">2</span>')
        assert html.contains('class="prevLink">Anterior</a>') && html.contains('class="nextLink">Próxima</a>')
        assert html.contains('href="/usuario/index?sort=idade&amp;order=desc&amp;offset=10&amp;max=5" class="nextLink"')
        assert html.contains('<th class="sortable sorted desc"><a href="/usuario/index?max=5&amp;sort=idade&amp;order=asc">Idade</a></th>')
        List<String> nomes = (html =~ /show\/\d+">(Pessoa \d+)</).collect { it[1] }
        assert nomes == ['Pessoa 18', 'Pessoa 17', 'Pessoa 16', 'Pessoa 15', 'Pessoa 14']
        String last = admin.page('/usuario/index?max=5&offset=20').body()
        assert last.contains('<span class="currentStep">5</span>') && !last.contains('nextLink')
    }

    @Test
    void 'busca com where e estatisticas com createCriteria'() {
        String html = admin.page('/usuario/index?q=PESSOA%2007').body()
        assert html.contains('>Pessoa 07</a>') && html.contains('<p class="submenu">Total: 1</p>')
        def stats = TestClient.parse(admin.send('GET', '/usuario/estatisticas.json'))
        assert stats.semGrupo >= 23 && stats.grupos instanceof List
    }

    @Test
    void 'upload multipart, validacao do tipo e download'() {
        long id = TestClient.parse(new TestClient(base).withToken('admin', 'admin').send('POST', '/api/usuarios', [nome: 'Com Foto', email: 'foto@x.com'])).id
        byte[] png = [0x89, 0x50, 0x4E, 0x47, 1, 2, 3] as byte[]
        def ok = admin.multipart("/usuario/enviarFoto/${id}", [:], 'foto', 'eu.png', 'image/png', png)
        assert ok.status() == 302
        assert admin.page("/usuario/show/${id}").body().contains('Foto eu.png enviada (7 bytes)')
        def download = admin.binary("/usuario/foto/${id}")
        assert download.status() == 200 && TestClient.bytes(download) == png && download.headers().firstValue('Content-Type').get() == 'image/png'

        def bad = admin.multipart("/usuario/enviarFoto/${id}", [:], 'foto', 'x.txt', 'text/plain', 'oi'.bytes)
        assert bad.status() == 302
        assert admin.page("/usuario/show/${id}").body().contains('Formato de imagem não suportado: text/plain')
        // o arquivo inválido não foi gravado (validação falhou dentro da transação)
        assert Usuario.withNewSession { Usuario.get(id).fotoTipo } == 'image/png'
        // sem token CSRF no multipart
        def semCsrf = admin.sendText('POST', "/usuario/enviarFoto/${id}", '--x--\r\n', 'multipart/form-data; boundary=x')
        assert semCsrf.status() == 403
    }

    @Test
    void 'typeMismatch em formulario e em JSON'() {
        def form = admin.form('/usuario/save', [nome: 'Tipo', email: 'tipo@x.com', idade: 'abc'])
        assert form.status() == 422 && form.body().contains('<li>O campo [idade] deve ser um número válido</li>')
        assert form.body().contains('value="abc"')
        def json = new TestClient(base).withToken('admin', 'admin').send('POST', '/api/usuarios', [nome: 'Tipo', email: 'tipo@x.com', idade: 'doze'])
        assert json.status() == 422
        def e = TestClient.parse(json).errors
        assert e*.field == ['idade'] && e[0].message == 'O campo [idade] deve ser um número válido' && e[0].'rejected-value' == 'doze'
    }

    @Test
    void 'interceptors: before e after global, modelo alterado e ordem'() {
        def r = admin.page('/usuario/index')
        assert r.headers().firstValue('X-Tempo-Ms').present
        assert r.body() =~ /<p class="rodape-lista">\d\d:\d\d:\d\d<\/p>/
        assert admin.page('/').headers().firstValue('X-Tempo-Ms').present
        def interceptors = app.webContext.interceptors.interceptors
        assert interceptors*.getClass()*.simpleName == ['AuditoriaInterceptor', 'MavInterceptor', 'UsuarioInterceptor']   // mesma order: pelo nome
    }
}
