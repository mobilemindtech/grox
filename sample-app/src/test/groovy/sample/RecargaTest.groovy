package sample

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.grox.core.GroxRuntime
import org.grox.core.reload.DevReloader
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

import extra.NotaService

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Recarga em desenvolvimento com o DevReloader de verdade, sobre uma cópia das classes, views e assets
 * (os fontes do sample-app não são tocados). Cada teste recompila uma classe e chama {@code reload()}.
 * As classes do sample-lib (biblioteca org.grox.lib) ficam num segundo diretório, como o plugin passa.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation)
class RecargaTest {

    static Path tmp
    static Path classes
    static Path libClasses
    static DevReloader reloader
    static TestClient client
    static String basic = 'Basic ' + 'maria:maria'.bytes.encodeBase64()

    @BeforeAll
    static void start() {
        tmp = Files.createTempDirectory('grox-recarga')
        classes = tmp.resolve('classes')
        copy(Paths.get(Application.protectionDomain.codeSource.location.toURI()), classes)
        libClasses = tmp.resolve('lib-classes')
        copyClasses(Paths.get(NotaService.protectionDomain.codeSource.location.toURI()), libClasses)
        copy(Paths.get('grails-app/views'), tmp.resolve('views'))
        copy(Paths.get('grails-app/assets'), tmp.resolve('assets'))
        System.setProperty('grails.env', 'development')
        System.setProperty('server.port', '0')
        System.setProperty('grox.gsp.viewsDir', tmp.resolve('views').toString())
        System.setProperty('grox.assets.dir', tmp.resolve('assets').toString())
        // intervalo longo: o teste chama reload() diretamente
        reloader = new DevReloader(Application.name, new String[0], [classes, libClasses], [], 3_600_000L)
        GroxRuntime app = reloader.start()
        client = new TestClient("http://localhost:${app.port}")
    }

    @AfterAll
    static void stop() {
        reloader?.close()
        ['grails.env', 'server.port', 'grox.gsp.viewsDir', 'grox.assets.dir'].each { System.clearProperty(it) }
        tmp?.toFile()?.deleteDir()
    }

    static void copy(Path from, Path to) {
        Files.walk(from).withCloseable { stream ->
            stream.forEach { Path p ->
                Path dest = to.resolve(from.relativize(p).toString())
                if (Files.isDirectory(p)) Files.createDirectories(dest)
                else Files.copy(p, dest)
            }
        }
    }

    /** Classes de um diretório ou de um jar (o teste vê o sample-lib empacotado). */
    static void copyClasses(Path location, Path to) {
        if (Files.isDirectory(location)) {
            copy(location, to)
            return
        }
        FileSystems.newFileSystem(location).withCloseable { fs -> copy(fs.getPath('/'), to) }
    }

    /** Recompila um fonte do sample-app (com trocas de texto) para a cópia das classes, como o Gradle faria. */
    static void recompile(String source, Map<String, String> replacements, Path target = classes) {
        String code = new File(source).getText('UTF-8')
        replacements.each { String a, String b ->
            assert code.contains(a)
            code = code.replace(a, b)
        }
        Path file = tmp.resolve('src').resolve(source)
        Files.createDirectories(file.parent)
        file.toFile().setText(code, 'UTF-8')
        def config = new CompilerConfiguration(targetDirectory: target.toFile())
        def unit = new CompilationUnit(config, null, new GroovyClassLoader(RecargaTest.classLoader, config))
        unit.addSource(file.toFile())
        unit.compile()
    }

    static String ola() {
        client.send('GET', '/ola/Ana').body()
    }

    static int visitas() {
        (client.send('GET', '/home/contador').body() =~ /\d+/)[0] as int
    }

    static String tarefas() {
        def r = client.send('GET', '/tarefa/index', null, [Accept: 'application/json', Authorization: basic])
        assert r.status() == 200
        r.body()
    }

    @Test
    @Order(1)
    void 'recarga leve troca o service e mantem sessao e Hibernate'() {
        assert ola() == 'Olá, Ana!'
        assert visitas() == 1
        GroxRuntime antes = reloader.current
        recompile('grails-app/services/sample/SaudacaoService.groovy', ['"Olá, ${nome}!"': '"Oi, ${nome}!"'])
        reloader.reload()
        assert ola() == 'Oi, Ana!'
        assert visitas() == 2
        assert !reloader.current.is(antes)
        assert reloader.current.datastore.is(antes.datastore)
        assert reloader.current.server.is(antes.server)
        // nada mudou: nenhuma geração nova
        GroxRuntime atual = reloader.current
        reloader.reload()
        assert reloader.current.is(atual)
    }

    @Test
    @Order(2)
    void 'controller com action nova, views e assets depois da recarga'() {
        recompile('grails-app/controllers/sample/HomeController.groovy', ['    def contador() {': '''    def nova() {
        render text: 'action nova', contentType: 'text/plain'
    }

    def contador() {'''])
        reloader.reload()
        assert client.send('GET', '/home/nova').body() == 'action nova'
        Path gsp = tmp.resolve('views/home/index.gsp')
        gsp.toFile().text = gsp.toFile().text.replace('</body>', '<p id="recarga">gsp nova</p></body>')
        assert client.page('/').body().contains('<p id="recarga">gsp nova</p>')
        tmp.resolve('assets/stylesheets/base.css').toFile() << '\n.recarga { color: red; }\n'
        assert client.send('GET', '/assets/application.css').body().contains('.recarga { color: red; }')
    }

    @Test
    @Order(3)
    void 'domain class muda: recarga completa com Hibernate novo e a mesma sessao'() {
        GroxRuntime antes = reloader.current
        assert !tarefas().contains('descricao')
        recompile('grails-app/domain/sample/Tarefa.groovy', ['    String tenantId': '    String tenantId\n    String descricao',
                                                            'titulo blank: false, size: 1..200': 'titulo blank: false, size: 1..200\n        descricao nullable: true'])
        reloader.reload()
        assert !reloader.current.datastore.is(antes.datastore)
        assert reloader.current.server.is(antes.server)
        assert tarefas().contains('"descricao":null')
        assert visitas() == 3
        assert client.send('GET', '/home/nova').body() == 'action nova'   // a camada recarregável veio junto
    }

    @Test
    @Order(4)
    void 'recarga completa que falha responde 503 com o erro e se recupera na seguinte'() {
        // campo obrigatório: o BootStrap não consegue gravar as tarefas
        recompile('grails-app/domain/sample/Tarefa.groovy', ['    String tenantId': '    String tenantId\n    String obrigatorio'])
        reloader.reload()
        def r = client.send('GET', '/ola/Ana')
        assert r.status() == 503 && r.body().contains('obrigatorio')
        recompile('grails-app/domain/sample/Tarefa.groovy', [:])
        reloader.reload()
        assert ola() == 'Oi, Ana!'
        assert visitas() == 4
        assert !tarefas().contains('descricao')
    }

    /** Classloader da classe {@code name} na hierarquia do objeto (services com @Transactional vêm num proxy). */
    static String loaderName(Object o, String name) {
        Class<?> c = o.getClass()
        while (c != null && c.name != name) c = c.superclass
        assert c != null
        c.classLoader.name
    }

    static String nota(String texto) {
        def r = client.send('GET', "/home/nota?texto=${texto}")
        assert r.status() == 200
        r.body()
    }

    @Test
    @Order(5)
    void 'taglib de uma biblioteca grox-lib recarrega como a da aplicacao'() {
        assert nota('a') ==~ /\d+ nota\(s\)\|.*/
        GroxRuntime atual = reloader.current
        // NotaService fica na camada estável: o FormatadorNota (src/main/groovy da biblioteca) o referencia
        assert loaderName(atual.services.beans.notaService, 'extra.NotaService').startsWith('grox-stable-')
        recompile('../sample-lib/grox-app/taglib/extra/NotaTagLib.groovy', ["out << g.message(": "out << '#' << g.message("], libClasses)
        reloader.reload()
        assert !reloader.current.is(atual)
        assert reloader.current.datastore.is(atual.datastore)   // leve: Hibernate e dados continuam
        assert nota('b') ==~ /#\d+ nota\(s\)\|.*/
    }

    @Test
    @Order(6)
    void 'domain de uma biblioteca muda: recarga completa'() {
        GroxRuntime antes = reloader.current
        recompile('../sample-lib/grox-app/domain/extra/Nota.groovy', ['    String texto': '    String texto\n    String autor',
                                                                 'texto blank: false': 'texto blank: false\n        autor nullable: true'], libClasses)
        reloader.reload()
        assert !reloader.current.datastore.is(antes.datastore)
        assert reloader.current.server.is(antes.server)
        def nota = reloader.current.services.beans.notaService.anotar('c')
        assert nota.hasProperty('autor')
        assert loaderName(nota, 'extra.Nota').startsWith('grox-stable-')
    }
}
