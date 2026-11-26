package org.grox

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.grox.core.reload.AppClassLoader
import org.grox.core.reload.AppClasses
import org.grox.core.reload.SwitchableDispatcher
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.RequestDispatcher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/** Recarga em desenvolvimento: camadas, fixação, diferenças entre retratos, classloaders e a entrada do Netty. */
class ReloadTest {

    @TempDir
    Path tmp

    static final Map<String, String> SOURCES = [
            'grails-app/controllers/app/OlaController.groovy': '''package app
class OlaController {
    OlaService olaService
    def index() { [1, 2].collect { it * 2 }; olaService.msg() }
}''',
            'grails-app/services/app/OlaService.groovy'     : '''package app
class OlaService { String msg() { 'v1' } }''',
            'grails-app/services/app/UsadoService.groovy'   : '''package app
class UsadoService { String id() { 'usado' } }''',
            // em src/: estável mesmo com o nome terminando em Service (como no app A)
            'src/main/groovy/app/NumeroService.groovy'      : '''package app
class NumeroService { int proximo() { 1 } }''',
            'src/main/groovy/app/Util.groovy'               : '''package app
class Util { static String de(UsadoService s) { s.id() } }''',
            // referência só no corpo de um método (class literal, como num doWithSpring de plugin)
            'grails-app/services/app/CorpoService.groovy'   : '''package app
class CorpoService { }''',
            'src/main/groovy/app/Registro.groovy'           : '''package app
class Registro { static Class tipo() { CorpoService } }''',
    ]

    /** Compila como o build da app: caminhos em grails-app/ ativam a ArtefactTransformation. */
    Path compile(Map<String, String> sources, String out = 'classes') {
        Path target = tmp.resolve(out)
        def config = new CompilerConfiguration(targetDirectory: target.toFile())
        def unit = new CompilationUnit(config, null, new GroovyClassLoader(getClass().classLoader, config))
        sources.each { String rel, String code ->
            Path file = tmp.resolve('src').resolve(rel)
            Files.createDirectories(file.parent)
            file.toFile().text = code
            unit.addSource(file.toFile())
        }
        unit.compile()
        target
    }

    @Test
    void 'divide em camadas pela anotacao e fixa o que a camada estavel referencia'() {
        def classes = AppClasses.scan([compile(SOURCES)])
        assert classes.reloadable == ['app.OlaController', 'app.OlaService'] as Set
        assert classes.stable == ['app.NumeroService', 'app.Util', 'app.UsadoService', 'app.Registro', 'app.CorpoService'] as Set
        assert classes.pinned == ['app.UsadoService': 'app.Util', 'app.CorpoService': 'app.Registro']
        assert classes.isReloadable('app.OlaController$_index_closure1')
    }

    @Test
    void 'diferencas entre retratos pelo bytecode'() {
        Path dir = compile(SOURCES)
        def before = AppClasses.scan([dir])
        compile(SOURCES)
        assert AppClasses.scan([dir]).changedSince(before).empty     // recompilar sem mudar gera os mesmos bytes
        compile(SOURCES + ['grails-app/services/app/OlaService.groovy': '''package app
class OlaService { String msg() { 'v2' } }'''])
        def after = AppClasses.scan([dir])
        assert after.changedSince(before) == ['app.OlaService'] as Set
        Files.delete(dir.resolve('app/NumeroService.class'))
        assert AppClasses.scan([dir]).changedSince(after) == ['app.NumeroService'] as Set
    }

    @Test
    void 'geracoes trocam a camada recarregavel e compartilham a estavel'() {
        Path dir = compile(SOURCES)
        def v1 = AppClasses.scan([dir])
        def stable = new AppClassLoader('estavel', [dir], v1, v1.stable, true, ClassLoader.systemClassLoader)
        def g1 = new AppClassLoader('g1', [dir], v1, v1.reloadable, false, stable)
        compile(SOURCES + ['grails-app/services/app/OlaService.groovy': '''package app
class OlaService { String msg() { 'v2' } }'''])
        def v2 = AppClasses.scan([dir])
        def g2 = new AppClassLoader('g2', [dir], v2, v2.reloadable, false, stable)

        assert !g1.loadClass('app.OlaService').is(g2.loadClass('app.OlaService'))
        assert g1.loadClass('app.Util').is(g2.loadClass('app.Util'))
        assert g1.loadClass('app.UsadoService').classLoader.is(stable)
        // a camada estável não entrega artefatos recarregáveis (nem os deixa cair no classpath do sistema)
        try {
            stable.loadClass('app.OlaService')
            assert false
        } catch (ClassNotFoundException e) {
            assert e.message.contains('camada estável')
        }
        // o bytecode vem do retrato: apagar o diretório (como o compilador faz) não afeta a geração
        dir.toFile().deleteDir()
        assert g1.loadClass('app.OlaService').getDeclaredConstructor().newInstance().msg() == 'v1'
        assert g2.loadClass('app.OlaService').getDeclaredConstructor().newInstance().msg() == 'v2'
        assert g1.loadClass('app.OlaController$_index_closure1') != null
    }

    static class Fixo implements RequestDispatcher {
        final String nome
        final CompletableFuture<GroxResponse> resposta

        Fixo(String nome, CompletableFuture<GroxResponse> resposta = null) {
            this.nome = nome
            this.resposta = resposta
        }

        @Override
        CompletableFuture<GroxResponse> dispatch(GroxRequest request) {
            if (resposta) return resposta
            def r = new GroxResponse()
            r.writer.print(nome)
            CompletableFuture.completedFuture(r)
        }
    }

    static String texto(CompletableFuture<GroxResponse> f) {
        f.join().text
    }

    @Test
    void 'entrada segura as requisicoes na recarga completa e conta as em andamento'() {
        def entry = new SwitchableDispatcher()
        def req = new GroxRequest()
        def pendente = new CompletableFuture<GroxResponse>()
        assert entry.switchTo(new Fixo('g1', pendente)) == null
        CompletableFuture<GroxResponse> lenta = entry.dispatch(req)
        def g1 = entry.switchTo(new Fixo('g2'))
        assert g1.inFlight.get() == 1 && !g1.awaitIdle(50)
        assert texto(entry.dispatch(req)) == 'g2'
        pendente.complete(new GroxResponse())
        assert lenta.join() != null && g1.awaitIdle(1000)

        entry.hold()
        CompletableFuture<GroxResponse> esperando = entry.dispatch(req)
        Thread.sleep(50)
        assert !esperando.done
        entry.switchTo(new Fixo('g3'))
        assert texto(esperando) == 'g3'

        entry.switchTo(new SwitchableDispatcher.Failure(new IllegalStateException('quebrou')))
        def erro = entry.dispatch(req).join()
        assert erro.status == 503 && erro.text.contains('quebrou')
    }
}
