package org.grox.session

import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.GroxSession

/**
 * Sessão de uma requisição: carregada sob demanda (uma vez, mesmo com segurança e
 * redespacho de erro) e gravada no fim, com o cookie quando é nova ou mudou de id.
 */
class RequestSession {

    static final String ATTR = 'grox.requestSession'

    final GroxRequest request
    final SessionRepository repository
    private GroxSession session
    private boolean resolved

    private RequestSession(GroxRequest request, SessionRepository repository) {
        this.request = request
        this.repository = repository
    }

    static RequestSession of(GroxRequest request, SessionRepository repository) {
        RequestSession rs = (RequestSession) request.getAttribute(ATTR)
        if (rs == null) {
            rs = new RequestSession(request, repository)
            request.setAttribute(ATTR, rs)
        }
        rs
    }

    static RequestSession existing(GroxRequest request) {
        (RequestSession) request.getAttribute(ATTR)
    }

    GroxSession get(boolean create) {
        if (!resolved) {
            resolved = true
            session = repository.find(request.cookieValues[repository.cookieName])
        }
        if (session?.invalidated) {
            invalidatedDuringRequest = true
            session = null
        }
        if (session == null && create) session = repository.create()
        session
    }

    boolean hasCookie() {
        request.cookieValues.containsKey(repository.cookieName)
    }

    /**
     * Token CSRF sem sessão (<i>double submit</i>): o mesmo valor vai no cookie e no formulário. Para visitantes sem
     * sessão: um {@code <g:form>} numa página pública não cria mais uma sessão por requisição. O cookie é
     * {@code HttpOnly} e {@code SameSite=Lax} (um POST de outro site não o leva) e, em HTTPS, tem o prefixo
     * {@code __Host-}, que impede outro subdomínio de gravá-lo.
     */
    static final String CSRF_COOKIE = 'GROX-CSRF'

    /** Token novo desta requisição, gravado no cookie no {@link #commit}. */
    private String newCsrf

    /** O token do cookie (o que veio na requisição ou o novo desta), criando um se não houver. */
    @groovy.transform.CompileStatic
    String csrfCookieToken() {
        if (newCsrf != null) return newCsrf
        String atual = requestCsrfCookie()
        if (atual != null) return atual
        newCsrf = SessionIds.next()
        newCsrf
    }

    /** O token do cookie que veio na requisição (null se ausente ou fora do formato). */
    @groovy.transform.CompileStatic
    String requestCsrfCookie() {
        String v = request.cookieValues.get(csrfCookieName())
        if (v == null || v.length() < 16 || v.length() > 64) return null
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i)
            if (!((c >= (char) 'a' && c <= (char) 'z') || (c >= (char) 'A' && c <= (char) 'Z') || (c >= (char) '0' && c <= (char) '9') || c == (char) '-' || c == (char) '_')) return null
        }
        v
    }

    @groovy.transform.CompileStatic
    private String csrfCookieName() {
        secure() ? '__Host-' + CSRF_COOKIE : CSRF_COOKIE
    }

    void commit(GroxResponse response) {
        if (newCsrf != null) response.addHeader('Set-Cookie', "${csrfCookieName()}=${newCsrf}${attributes()}".toString())
        if (session == null) {
            if (invalidatedDuringRequest) response.addHeader('Set-Cookie', expiredCookie())
            return
        }
        if (session.invalidated) {
            response.addHeader('Set-Cookie', expiredCookie())
            return
        }
        boolean sendCookie = session.isNew() || session.idChanged
        repository.save(session)
        if (sendCookie) response.addHeader('Set-Cookie', cookie(session.id))
    }

    private boolean invalidatedDuringRequest

    void invalidate() {
        GroxSession s = get(false)
        if (s != null) {
            s.invalidate()
            invalidatedDuringRequest = true
            session = null
        }
    }

    /** {@code grox.session.cookie.secure}: {@code auto} (Secure em HTTPS, inclusive atrás de proxy), {@code true} ou {@code false}. */
    static volatile String cookieSecure = 'auto'

    @groovy.transform.CompileStatic
    private boolean secure() {
        cookieSecure == 'true' || (cookieSecure == 'auto' && request.scheme == 'https')
    }

    private String attributes() {
        '; Path=/; HttpOnly; SameSite=Lax' + (secure() ? '; Secure' : '')
    }

    String cookie(String id) {
        "${repository.cookieName}=${id}${attributes()}"
    }

    String expiredCookie() {
        "${repository.cookieName}=; Max-Age=0${attributes()}"
    }
}
