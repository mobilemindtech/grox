package org.grox

import org.grox.http.GroxRequest
import org.grox.http.HttpPipeline
import org.junit.jupiter.api.Test

/** Página/JSON de erro padrão: a mensagem da exceção só aparece em development. */
class ErrorResponseTest {

    static HttpPipeline dispatcher(boolean dev) {
        new HttpPipeline(development: dev)
    }

    static final Exception SQL = new IllegalStateException("Duplicate entry 'x' for key 'usuario.email' (tabela usuario)")

    @Test
    void 'fora de development a mensagem da excecao nao vai para a resposta'() {
        def json = new GroxRequest(method: 'GET', path: '/x.json', uri: '/x.json')
        def html = new GroxRequest(method: 'GET', path: '/x', uri: '/x')
        String prodJson = dispatcher(false).defaultError(json, 500, SQL, null).text
        String prodHtml = dispatcher(false).defaultError(html, 500, SQL, null).text
        assert !prodJson.contains('usuario') && prodJson.contains('Internal Server Error')
        assert !prodHtml.contains('usuario') && !prodHtml.contains('<pre>')
        assert dispatcher(true).defaultError(json, 500, SQL, null).text.contains('usuario.email')
        assert dispatcher(true).defaultError(html, 500, SQL, null).text.contains('<pre>')
    }
}
