package sample

import org.grox.GroxApp
import org.grox.core.GroxRuntime
import org.junit.jupiter.api.Test

import java.util.concurrent.CompletableFuture

/** Desligamento gracioso (classe própria: fechar a aplicação reinicia o GORM global da JVM). */
class DesligamentoTest {

    @Test
    void 'close espera a requisicao em andamento e depois recusa conexoes'() {
        System.setProperty('grails.env', 'test')
        GroxRuntime app = GroxApp.run(Application)
        int port = app.port
        // socket cru: o HttpClient do JDK reenvia o GET em outra conexão ao ver Connection: close
        CompletableFuture<String> lenta = CompletableFuture.supplyAsync {
            new Socket('127.0.0.1', port).withCloseable { s ->
                s.outputStream.write("GET /home/lento?ms=800 HTTP/1.1\r\nHost: localhost\r\n\r\n".bytes)
                s.outputStream.flush()
                s.inputStream.text
            }
        }
        long limite = System.currentTimeMillis() + 5000
        while (app.server.inFlight.get() == 0 && System.currentTimeMillis() < limite) Thread.sleep(10)
        assert app.server.inFlight.get() == 1
        long t0 = System.currentTimeMillis()
        app.close()
        assert System.currentTimeMillis() - t0 >= 300
        String resposta = lenta.get()
        assert resposta.startsWith('HTTP/1.1 200 OK') && resposta.toLowerCase().contains('connection: close') && resposta.endsWith('terminou')
        try {
            new Socket('127.0.0.1', port).close()
            assert false: 'o servidor deveria recusar conexões'
        } catch (ConnectException ignored) {
        }
    }
}
