package org.grox

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.HeaderMap
import org.grox.session.MemorySessionRepository
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

/** Request, response e sessão do Grox implementam a API servlet do jar oficial (jakarta.servlet-api), sem container. */
class ServletApiTest {

    static GroxRequest request() {
        def headers = new HeaderMap()
        headers.set('Host', 'exemplo.com:8443')
        headers.set('Accept-Language', 'pt-BR,pt;q=0.9,en;q=0.5')
        headers.set('Content-Type', 'application/x-www-form-urlencoded; charset=ISO-8859-1')
        headers.set('If-Modified-Since', 'Wed, 21 Oct 2026 07:28:00 GMT')
        headers.set('X-Tentativas', '3')
        headers.add('X-Multi', 'a')
        headers.add('X-Multi', 'b')
        new GroxRequest(method: 'POST', uri: '/a?x=1', path: '/a', queryString: 'x=1', headers: headers, body: 'nome=Ana'.bytes,
                queryParameters: [x: ['1'], tag: ['q']], formParameters: [nome: ['Ana'], tag: ['f']],
                cookieValues: [ok: 'v', 'nome inválido': 'x'], remoteAddr: '/10.0.0.5:51234')
    }

    @Test
    void 'interfaces vem do jar oficial, sem copias do framework'() {
        assert HttpServletRequest.protectionDomain.codeSource.location.path.contains('jakarta.servlet-api')
        assert request() instanceof HttpServletRequest
        assert new GroxResponse() instanceof HttpServletResponse
        assert new MemorySessionRepository('S', 60).create() instanceof HttpSession
    }

    @Test
    void 'request: parametros, cabecalhos, locale, corpo e cookies'() {
        HttpServletRequest r = request()
        assert r.getParameter('tag') == 'q' && r.getParameterValues('tag') == ['q', 'f'] as String[]
        assert Collections.list(r.parameterNames) == ['x', 'tag', 'nome']
        assert r.parameterMap.nome == ['Ana'] as String[] && r.getParameterValues('falta') == null
        assert Collections.list(r.getHeaders('X-Multi')) == ['a', 'b'] && Collections.list(r.headerNames).contains('Host')
        assert r.getIntHeader('X-Tentativas') == 3 && r.getIntHeader('Falta') == -1
        assert r.getDateHeader('If-Modified-Since') == java.time.Instant.parse('2026-10-21T07:28:00Z').toEpochMilli()
        assert r.locale == Locale.forLanguageTag('pt-BR') && Collections.list(r.locales).size() == 3
        assert r.characterEncoding == 'ISO-8859-1' && r.contentLength == 8
        assert r.inputStream.text == 'nome=Ana' && r.reader.text == 'nome=Ana'
        assert r.serverName == 'exemplo.com' && r.serverPort == 8443 && r.remotePort == 51234 && !r.secure
        assert r.cookies*.name == ['ok']   // nome inválido na Servlet API fica de fora, sem exceção
        r.setAttribute('a', 1)
        assert Collections.list(r.attributeNames) == ['a']
        assert r.getSession(false) == null && !r.requestedSessionIdValid
        assert !r.asyncSupported && r.dispatcherType == jakarta.servlet.DispatcherType.REQUEST
        def e = assertThrows(UnsupportedOperationException) { r.servletContext }
        assert e.message.contains('container')
    }

    @Test
    void 'response: cabecalhos, datas, outputStream e reset'() {
        HttpServletResponse resp = new GroxResponse()
        resp.setHeader('X-A', '1')
        resp.addHeader('X-A', '2')
        resp.setIntHeader('X-N', 5)
        resp.setDateHeader('Expires', 0L)
        assert resp.getHeaders('X-A') == ['1', '2'] && resp.getHeader('X-N') == '5'
        assert resp.getHeader('Expires') == 'Thu, 1 Jan 1970 00:00:00 GMT'
        resp.outputStream.write('oi'.bytes)
        resp.outputStream << ' mundo'.bytes
        assert ((GroxResponse) resp).text == 'oi mundo'
        resp.status = 404
        resp.reset()
        assert resp.status == 200 && !resp.containsHeader('X-A') && ((GroxResponse) resp).text == ''
        resp.sendRedirect('/destino', 301, true)
        assert resp.status == 301 && resp.getHeader('Location') == '/destino'
        assert resp.encodeURL('/x') == '/x'
    }

    @Test
    void 'sessao: isNew e nomes dos atributos'() {
        def repo = new MemorySessionRepository('S', 60)
        HttpSession s = repo.create()
        assert s.isNew()
        s.setAttribute('a', 1)
        assert Collections.list(s.attributeNames) == ['a']
        repo.save(s)
        assert !repo.find(s.id).isNew()
    }

    @Test
    void 'request: userPrincipal e remoteUser pela autenticacao da seguranca'() {
        HttpServletRequest r = request()
        assert r.userPrincipal == null && r.remoteUser == null
        def auth = new org.grox.security.Authentication(principal: new org.grox.security.GroxUser(username: 'maria'), method: 'form')
        r.setAttribute(org.grox.security.SecurityContextHolder.REQUEST_ATTR, auth)
        assert r.userPrincipal.is(auth) && r.remoteUser == 'maria'
    }
}
