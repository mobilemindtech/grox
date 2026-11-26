package sample

import org.grox.GroxApp
import org.grox.core.GroxRuntime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/** Sessões no Redis: login e flash funcionam e a sessão sobrevive ao reinício da aplicação. */
class RedisSessionAppTest {

    static Process redis
    static int redisPort

    @BeforeAll
    static void start() {
        boolean installed
        try {
            installed = new ProcessBuilder('redis-server', '--version').start().waitFor() == 0
        } catch (IOException ignored) {
            installed = false
        }
        Assumptions.assumeTrue(installed, 'redis-server não instalado')
        redisPort = new ServerSocket(0).withCloseable { it.localPort }
        redis = new ProcessBuilder('redis-server', '--port', redisPort.toString(), '--save', '', '--appendonly', 'no').start()
        for (int i = 0; i < 50; i++) {
            try {
                new Socket('localhost', redisPort).close()
                break
            } catch (IOException ignored) {
                Thread.sleep(100)
            }
        }
        System.setProperty('grails.env', 'test')
        System.setProperty('grox.session.store', 'redis')
        System.setProperty('grox.session.redis.url', "redis://localhost:${redisPort}")
    }

    @AfterAll
    static void stop() {
        redis?.destroy()
    }

    static String redisCli(String... args) {
        def p = new ProcessBuilder(['redis-cli', '-p', redisPort.toString()] + args.toList()).redirectErrorStream(true).start()
        p.inputStream.text.trim()
    }

    @Test
    void 'sessao no Redis sobrevive ao reinicio da aplicacao'() {
        GroxRuntime app = GroxApp.run(Application)
        TestClient c
        String port
        try {
            assert app.webContext.sessions.class.simpleName == 'RedisSessionRepository'
            port = app.port.toString()
            c = new TestClient("http://localhost:${port}").loggedIn('admin', 'admin')
            def saved = c.form('/usuario/save', [nome: 'Redis', email: 'redis@x.com'])
            assert saved.status() == 302
            assert c.page(saved.headers().firstValue('Location').get()).body().contains('Usuário Redis criado')
            assert redisCli('EXISTS', "grox:session:${c.sessionId()}") == '1'
            assert redisCli('TTL', "grox:session:${c.sessionId()}").toInteger() > 1700
        } finally {
            app.close()
        }
        // nova instância (outra porta), mesmo Redis: o cookie continua autenticado
        GroxRuntime again = GroxApp.run(Application)
        try {
            def other = new TestClient("http://localhost:${again.port}")
            c.cookies.cookieStore.cookies.each { cookie ->
                other.cookies.cookieStore.add(URI.create("http://localhost:${again.port}"), cookie)
            }
            assert other.page('/usuario/create').status() == 200
            assert other.page('/').body().contains('<span class="usuario">admin</span>')
            assert other.form('/logoff', [:]).status() == 302
            assert redisCli('EXISTS', "grox:session:${c.sessionId()}") == '0'
        } finally {
            again.close()
        }
    }
}
