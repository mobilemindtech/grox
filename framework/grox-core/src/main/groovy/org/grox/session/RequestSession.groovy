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

    void commit(GroxResponse response) {
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

    private String attributes() {
        boolean secure = cookieSecure == 'true' || (cookieSecure == 'auto' && request.scheme == 'https')
        '; Path=/; HttpOnly; SameSite=Lax' + (secure ? '; Secure' : '')
    }

    String cookie(String id) {
        "${repository.cookieName}=${id}${attributes()}"
    }

    String expiredCookie() {
        "${repository.cookieName}=; Max-Age=0${attributes()}"
    }
}
