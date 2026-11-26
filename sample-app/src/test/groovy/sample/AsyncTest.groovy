package sample

import org.grox.GroxApp
import org.grox.core.GroxRuntime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

import java.util.concurrent.CompletableFuture

/** Actions assíncronas: ator liberado, view/respond ao completar, contexto, erro e timeout. */
class AsyncTest {

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

    /** Tempo para 5 requisições simultâneas numa action de 400 ms (controller com 1 ator). */
    static long cincoAoMesmoTempo(String action) {
        def http = groovy.http.HttpBuilder.http(base)
        long t0 = System.currentTimeMillis()
        List<CompletableFuture<groovy.http.HttpResult>> fs = (1..5).collect {
            http.getAsync("/async/${action}?ms=400".toString())
        }
        assert fs*.join().every { it.status() == 200 && it.body() == 'ok' }
        System.currentTimeMillis() - t0
    }

    @Test
    void 'assincrona libera o ator (sincrona enfileira)'() {
        cincoAoMesmoTempo('esperaAsync')
        long sync = cincoAoMesmoTempo('espera')
        long async = cincoAoMesmoTempo('esperaAsync')
        assert sync >= 1900      // 5 × 400 ms em fila no único ator
        assert async < 1200      // em paralelo
    }

    @Test
    void 'Map da tarefa renderiza a view com layout, interceptors e usuario'() {
        def c = new TestClient(base).loggedIn('maria', 'maria')
        def r = c.page('/async/relatorio')
        assert r.status() == 200
        assert r.body().contains('<title>Relatório assíncrono · Grox Sample</title>')
        assert r.body() =~ /<p id="total">\d+ usuário\(s\)<\/p>/
        assert r.body().contains('<p id="usuario">maria</p>') && r.body().contains('<p id="virtual">true</p>')
        assert r.headers().firstValue('X-Tempo-Ms').present      // after() do interceptor rodou
    }

    @Test
    void 'tasks em paralelo com tenant do usuario e respond'() {
        def c = new TestClient(base).withToken('joao', 'joao')
        def m = TestClient.parse(c.send('GET', '/async/painel.json'))
        assert m.tenant == 'globex' && m.tarefas >= 1 && m.usuarios >= 0
    }

    @Test
    void 'erro na tarefa vira 500 e demora demais vira 503'() {
        def c = new TestClient(base)
        def erro = c.send('GET', '/async/falhaAsync', null, [Accept: 'application/json'])
        assert erro.status() == 500 && TestClient.parse(erro).erro == 'falhou na tarefa'
        def lenta = c.send('GET', '/async/lentaDemais', null, [Accept: 'application/json'])
        assert lenta.status() == 503
    }
}
