package sample

import groovy.http.HttpBuilder
import groovy.http.HttpResult
import groovy.json.JsonSlurper

import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/** Cliente HTTP de teste ({@code groovy.http.HttpBuilder}) com cookies, login por formulário, JWT e CSRF automático. */
class TestClient {

    final String base
    final CookieManager cookies = new CookieManager()
    final HttpBuilder http
    String bearer

    TestClient(String base) {
        this.base = base
        CookieManager jar = cookies
        // sem seguir redirecionamentos: os testes conferem o 302 e o Location
        this.http = HttpBuilder.http {
            baseUri base
            followRedirects false
            clientConfig { it.cookieHandler(jar) }
        }
    }

    /** Requisição com corpo JSON opcional (objeto serializado) e cabeçalhos; o JWT vai no Authorization. */
    HttpResult send(String method, String path, Object payload = null, Map<String, String> headers = [:]) {
        String token = bearer
        http.request(method, url(path)) {
            if (token && !headers.containsKey('Authorization')) header 'Authorization', "Bearer ${token}"
            headers.each { k, v -> header k, v }
            if (payload != null) json(payload)
        }
    }

    /** Requisição com o corpo como texto puro (JSON inválido, CSV...), no Content-Type dado. */
    HttpResult sendText(String method, String path, String body, String contentType, Map<String, String> headers = [:]) {
        http.request(method, url(path)) {
            headers.each { k, v -> header k, v }
            header 'Content-Type', contentType
            text(body)
        }
    }

    HttpResult page(String path) {
        send('GET', path, null, [Accept: 'text/html,application/xhtml+xml'])
    }

    /** GET de conteúdo binário (PDF, imagens): o corpo vem em ISO-8859-1, que preserva os bytes (ver {@link #bytes}). */
    HttpResult binary(String path, Map<String, String> headers = [:]) {
        http.get(url(path)) {
            headers.each { k, v -> header k, v }
            bodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.ISO_8859_1)
        }
    }

    /** Os bytes do corpo de uma resposta de {@link #binary}. */
    static byte[] bytes(HttpResult r) {
        r.body().getBytes(StandardCharsets.ISO_8859_1)
    }

    /** POST de formulário; inclui o token CSRF da sessão se {@code csrf}. */
    HttpResult form(String path, Map<String, Object> fields, boolean csrf = true) {
        Map<String, Object> all = new LinkedHashMap(fields)
        if (csrf && !all.containsKey('_csrf')) all._csrf = csrfToken()
        http.post(url(path)) {
            header 'Accept', 'text/html'
            delegate.form(all.collectEntries { k, v -> [k, v.toString()] })   // o form do RequestSpec, não o deste cliente
        }
    }

    /** POST multipart/form-data com campos e um arquivo (inclui o _csrf). */
    HttpResult multipart(String path, Map<String, Object> fields, String fileField, String filename, String contentType, byte[] content) {
        String boundary = '----grox' + System.nanoTime()
        def out = new ByteArrayOutputStream()
        Closure<Void> w = { String s -> out.write(s.getBytes('UTF-8')); null }
        ([_csrf: csrfToken()] + fields).each { k, v ->
            w("--${boundary}\r\nContent-Disposition: form-data; name=\"${k}\"\r\n\r\n${v}\r\n")
        }
        w("--${boundary}\r\nContent-Disposition: form-data; name=\"${fileField}\"; filename=\"${filename}\"\r\nContent-Type: ${contentType}\r\n\r\n")
        out.write(content)
        w("\r\n--${boundary}--\r\n")
        http.post(url(path)) {
            header 'Content-Type', "multipart/form-data; boundary=${boundary}"
            header 'Accept', 'text/html'
            delegate.bytes(out.toByteArray())
        }
    }

    /** Token CSRF da sessão atual (o formulário de login sempre o contém). */
    String csrfToken() {
        def m = page('/login/auth').body() =~ /name="_csrf" value="([^"]+)"/
        m.find() ? m.group(1) : null
    }

    HttpResult login(String username, String password) {
        form('/login/authenticate', [username: username, password: password])
    }

    TestClient loggedIn(String username, String password) {
        def r = login(username, password)
        assert r.status() == 302 && !r.headers().firstValue('Location').get().contains('login_error')
        this
    }

    String token(String username, String password) {
        def r = send('POST', '/api/login', [username: username, password: password])
        assert r.status() == 200
        parse(r).access_token
    }

    TestClient withToken(String username, String password) {
        bearer = token(username, password)
        this
    }

    /**
     * URL absoluta: base + caminho, como o servidor recebe. Sem isso, o {@code baseUri} resolveria
     * {@code //outro.host/x} como outro host (os testes de segurança mandam caminhos assim).
     */
    String url(String path) {
        path.startsWith('http://') || path.startsWith('https://') ? path : base + path
    }

    String sessionId() {
        cookies.cookieStore.cookies.find { it.name == 'GROXSESSIONID' }?.value
    }

    static Object parse(HttpResult r) {
        new JsonSlurper().parseText(r.body())
    }
}
