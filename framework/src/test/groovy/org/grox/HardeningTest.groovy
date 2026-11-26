package org.grox

import org.grox.http.GroxRequest
import org.grox.http.StaticResources
import org.grox.web.ControllerSupport
import org.grox.web.json.JsonSupport
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets

/** Endurecimentos pequenos da borda: cabeçalhos, JSON em script, charset, proxies e cache de estáticos. */
class HardeningTest {

    @Test
    void 'Content-Disposition sem CRLF nem aspas soltas, com nome UTF-8'() {
        assert ControllerSupport.contentDisposition('relatorio.xls') == 'attachment;filename="relatorio.xls"'
        assert ControllerSupport.contentDisposition('a"b\r\nSet-Cookie: x=1.pdf') == 'attachment;filename="a\\"bSet-Cookie: x=1.pdf"'
        assert ControllerSupport.contentDisposition('Relatório.xls') == "attachment;filename=\"Relat_rio.xls\";filename*=UTF-8''Relat%C3%B3rio.xls"
    }

    @Test
    void 'JSON nao fecha a tag script'() {
        String json = JsonSupport.toJson([nome: '</script><script>alert(1)</script>', linha: 'a b'])
        assert !json.contains('</') && json.contains('<\\/script>') && json.contains('\\u2028')
        assert new groovy.json.JsonSlurper().parseText(json).nome == '</script><script>alert(1)</script>'
    }

    @Test
    void 'charset desconhecido cai em UTF-8 sem excecao'() {
        def r = new GroxRequest(method: 'POST', path: '/', uri: '/')
        r.headers.add('Content-Type', 'application/x-www-form-urlencoded; charset=foo')
        assert r.charset == StandardCharsets.UTF_8
        r.headers.set('Content-Type', 'text/plain; charset="bad name!"')
        assert r.charset == StandardCharsets.UTF_8
        r.headers.set('Content-Type', 'text/plain; charset=ISO-8859-1')
        assert r.charset.name() == 'ISO-8859-1'
    }

    @Test
    void 'X-Forwarded so de proxy confiavel'() {
        Closure<GroxRequest> req = { String remote ->
            def r = new GroxRequest(method: 'GET', path: '/x', uri: '/x', remoteAddr: remote)
            r.headers.add('Host', 'app.local:8080')
            r.headers.add('X-Forwarded-Host', 'evil.example')
            r.headers.add('X-Forwarded-Proto', 'https')
            r.headers.add('X-Forwarded-For', '203.0.113.9, 10.0.0.2')
            r
        }
        def viaProxy = req('/10.0.0.2:4321')
        assert viaProxy.fromTrustedProxy && viaProxy.scheme == 'https' && viaProxy.serverName == 'evil.example' && viaProxy.clientAddr == '203.0.113.9'
        def direto = req('/198.51.100.7:4321')
        assert !direto.fromTrustedProxy && direto.scheme == 'http' && direto.serverName == 'app.local' && direto.clientAddr == '198.51.100.7'
        assert direto.requestURL.toString() == 'http://app.local:8080/x'
        assert req('/0:0:0:0:0:0:0:1:5555').clientAddr == '203.0.113.9'   // IPv6 loopback é confiável
        // o cliente manda um X-Forwarded-For forjado e o proxy acrescenta o endereço real à direita
        def forjado = new GroxRequest(method: 'GET', path: '/x', uri: '/x', remoteAddr: '/10.0.0.2:4321')
        forjado.headers.add('X-Forwarded-For', '1.2.3.4, 203.0.113.9, 10.0.0.3')
        assert forjado.clientAddr == '203.0.113.9'
        // só proxies confiáveis na lista: vale o mais à esquerda
        def interno = new GroxRequest(method: 'GET', path: '/x', uri: '/x', remoteAddr: '/10.0.0.2:4321')
        interno.headers.add('X-Forwarded-For', '192.168.0.5, 10.0.0.3')
        assert interno.clientAddr == '192.168.0.5'
    }

    @Test
    void 'cache de estaticos nao guarda caminhos inexistentes'() {
        def statics = new StaticResources('/static', null, HardeningTest.classLoader, false)
        100.times { assert statics.serve(new GroxRequest(method: 'GET', path: "/static/nao-existe-${it}.js", uri: '/x')) == null }
        assert ((Map) statics.@cache).isEmpty()
    }

    @Test
    void 'multipart malformado responde 400'() {
        def s = new org.grox.http.NettyServer('127.0.0.1', 0, 1024 * 1024, 1,
                { GroxRequest r -> java.util.concurrent.CompletableFuture.completedFuture(new org.grox.http.GroxResponse()) } as org.grox.http.RequestDispatcher).start()
        try {
            def socket = new Socket('127.0.0.1', s.port)
            socket.soTimeout = 5000
            String body = '--X\r\nlixo sem cabecalho\r\n\r\n--X--\r\n'   // parte sem Content-Disposition
            socket.outputStream.write(("POST / HTTP/1.1\r\nHost: x\r\nContent-Type: multipart/form-data; boundary=X\r\n" +
                    "Content-Length: ${body.length()}\r\nConnection: close\r\n\r\n${body}").getBytes('UTF-8'))
            String r = socket.inputStream.text
            assert r.startsWith('HTTP/1.1 400')   // erro do cliente, não 500 com stack trace
        } finally {
            s.close()
        }
    }

    @Test
    void 'i18n com cache: ausente, argumentos e uso concorrente'() {
        def ms = new org.grox.i18n.MessageSource(HardeningTest.classLoader, 'messages', Locale.forLanguageTag('pt-BR'))
        2.times {
            assert ms.getMessage('codigo.que.nao.existe') == 'codigo.que.nao.existe'
            assert ms.getMessage('codigo.que.nao.existe', null, 'padrão {0}', null) == 'padrão {0}'
            assert ms.getMessage('codigo.que.nao.existe', ['x'], 'padrão {0}', null) == 'padrão x'
            assert ms.getMessage('springSecurity.errors.login.fail') == 'Usuário ou senha inválidos.'
        }
        // MessageFormat não é thread-safe: o cache precisa entregar uma cópia por uso
        List<String> results = java.util.Collections.synchronizedList([])
        (1..16).collect { int n -> Thread.startVirtualThread { 200.times { results << ms.getMessage('x', [n], 'valor {0}', null) } } }*.join()
        assert results.size() == 3200 && results.every { it ==~ /valor \d+/ }
    }
}
