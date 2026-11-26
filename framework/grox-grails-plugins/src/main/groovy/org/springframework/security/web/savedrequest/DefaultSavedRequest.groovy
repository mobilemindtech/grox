package org.springframework.security.web.savedrequest

/** Requisição salva pelo Grox antes do login (só GET): caminho e query string. */
class DefaultSavedRequest implements SavedRequest {
    final String requestURI
    final String queryString
    final String requestURL

    DefaultSavedRequest(String uri, String serverUrl) {
        int q = uri.indexOf('?')
        this.requestURI = q >= 0 ? uri.substring(0, q) : uri
        this.queryString = q >= 0 ? uri.substring(q + 1) : null
        this.requestURL = (serverUrl ?: '') + requestURI
    }

    @Override String getRedirectUrl() { requestURL + (queryString ? "?${queryString}" : '') }

    @Override String getMethod() { 'GET' }
}
