package org.grox

import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.NettyServer
import org.grox.http.RequestDispatcher
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

/** Limites da borda HTTP: slowloris, conexão ociosa, número de conexões, resposta inválida, 100-continue. */
class NettyLimitsTest {

    /** /lento espera 1,5 s; /crlf devolve um cabeçalho inválido; o resto responde o caminho. */
    static final RequestDispatcher APP = { GroxRequest req ->
        def resp = new GroxResponse(contentType: 'text/plain')
        if (req.path == '/crlf') resp.setHeader('X-Nome', 'a\r\nSet-Cookie: x=1')
        if (req.path == '/lento') return CompletableFuture.supplyAsync { Thread.sleep(1500); resp.writer.write('lento'); resp }
        resp.writer.write(req.path)
        CompletableFuture.completedFuture(resp)
    } as RequestDispatcher

    static NettyServer start(Closure config = {}) {
        def s = new NettyServer('127.0.0.1', 0, 1024, 1, APP)
        s.with(config)
        s.start()
    }

    static Socket connect(NettyServer s) {
        def socket = new Socket('127.0.0.1', s.port)
        socket.soTimeout = 5000
        socket
    }

    /** Lê até a conexão fechar (ou o timeout do socket): o texto recebido. */
    static String readAll(Socket socket) {
        def out = new ByteArrayOutputStream()
        byte[] buf = new byte[4096]
        try {
            int n
            while ((n = socket.inputStream.read(buf)) >= 0) out.write(buf, 0, n)
        } catch (SocketTimeoutException ignored) {
            return out.toString('UTF-8') + '<<timeout>>'
        }
        out.toString('UTF-8')
    }

    static void send(Socket socket, String text) {
        socket.outputStream.write(text.getBytes(StandardCharsets.UTF_8))
        socket.outputStream.flush()
    }

    @Test
    void 'requisicao enviada aos poucos e fechada no prazo'() {
        def s = start { requestTimeoutSeconds = 1; idleTimeoutSeconds = 0 }
        try {
            def socket = connect(s)
            long t0 = System.currentTimeMillis()
            send(socket, 'GET / HTTP/1.1\r\nHost: x\r\n')          // cabeçalhos pela metade
            for (int i = 0; i < 3; i++) { Thread.sleep(400); try { send(socket, 'X-A: 1\r\n') } catch (IOException ignored) { break } }
            assert !readAll(socket).contains('<<timeout>>')        // fechada pelo servidor
            assert System.currentTimeMillis() - t0 < 4000
        } finally {
            s.close()
        }
    }

    @Test
    void 'conexao ociosa fecha, mas nao com resposta pendente'() {
        def s = start { requestTimeoutSeconds = 0; idleTimeoutSeconds = 1 }
        try {
            def socket = connect(s)
            send(socket, 'GET /lento HTTP/1.1\r\nHost: x\r\n\r\n')   // action demorada: mais que o idle
            String r = readAll(socket)
            assert r.startsWith('HTTP/1.1 200') && r.contains('lento') && !r.contains('<<timeout>>')
        } finally {
            s.close()
        }
    }

    @Test
    void 'limite de conexoes'() {
        def s = start { maxConnections = 1; idleTimeoutSeconds = 0 }
        try {
            def first = connect(s)
            send(first, 'GET /a HTTP/1.1\r\nHost: x\r\n\r\n')
            assert first.inputStream.read() >= 0                    // a primeira é atendida
            def second = connect(s)
            send(second, 'GET /b HTTP/1.1\r\nHost: x\r\n\r\n')
            assert readAll(second) == ''                            // a excedente é fechada
            first.close()
        } finally {
            s.close()
        }
    }

    @Test
    void 'resposta invalida vira 500 e nao trava as seguintes'() {
        def s = start { idleTimeoutSeconds = 0 }
        try {
            def socket = connect(s)
            send(socket, 'GET /crlf HTTP/1.1\r\nHost: x\r\n\r\nGET /depois HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n')
            String r = readAll(socket)
            assert r.startsWith('HTTP/1.1 500') && !r.contains('Set-Cookie') && r.contains('/depois')
        } finally {
            s.close()
        }
    }

    @Test
    void 'expect 100-continue acima do limite recebe 413 sem enviar o corpo'() {
        def s = start { idleTimeoutSeconds = 0 }
        try {
            def socket = connect(s)
            send(socket, 'POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 5000\r\nExpect: 100-continue\r\n\r\n')
            byte[] buf = new byte[512]
            int n = socket.inputStream.read(buf)
            assert new String(buf, 0, n, 'UTF-8').startsWith('HTTP/1.1 413')
        } finally {
            s.close()
        }
    }

    @Test
    void 'pipelining acima do limite pausa a leitura e responde tudo em ordem'() {
        def s = start { maxPipelined = 4; idleTimeoutSeconds = 0 }
        try {
            def socket = connect(s)
            String reqs = (1..40).collect { "GET /r${it} HTTP/1.1\r\nHost: x\r\n" + (it == 40 ? 'Connection: close\r\n' : '') + '\r\n' }.join('')
            send(socket, reqs)
            String r = readAll(socket)
            assert !r.contains('<<timeout>>')
            List<String> bodies = (r =~ /\/r\d+/).collect { it }
            assert bodies == (1..40).collect { "/r${it}".toString() }
        } finally {
            s.close()
        }
    }
}
