package org.springframework.security.web.savedrequest

import org.grox.security.SecurityFilter
import org.grox.web.RequestContext

/** Compatibilidade com o {@code HttpSessionRequestCache}: a URL que o Grox guardou antes de pedir o login. */
class HttpSessionRequestCache {

    SavedRequest getRequest(Object request = null, Object response = null) {
        def wr = RequestContext.currentOrNull()
        String uri = (String) wr?.getSession(false)?.getAttribute(SecurityFilter.SAVED_REQUEST)
        if (!uri) return null
        def r = wr.request
        String host = r.getHeader('Host')
        new DefaultSavedRequest(uri, host ? "${r.scheme ?: 'http'}://${host}".toString() : '')
    }

    void removeRequest(Object request = null, Object response = null) {
        RequestContext.currentOrNull()?.getSession(false)?.removeAttribute(SecurityFilter.SAVED_REQUEST)
    }
}
