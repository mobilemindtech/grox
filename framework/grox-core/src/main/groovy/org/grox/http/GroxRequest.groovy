package org.grox.http

import groovy.json.JsonSlurper

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** Requisição HTTP imutável (exceto atributos), independente do Netty. */
class GroxRequest implements jakarta.servlet.http.HttpServletRequest, org.springframework.web.multipart.MultipartHttpServletRequest {

    /** Atributo com o usuário autenticado (um {@link java.security.Principal}), que a segurança preenche. */
    static final String PRINCIPAL_ATTRIBUTE = 'grox.security.authentication'

    String method
    String uri
    String path
    String queryString
    HeaderMap headers = new HeaderMap()
    byte[] body = new byte[0]
    Map<String, List<String>> queryParameters = [:]
    Map<String, List<String>> formParameters = [:]
    /** Cookies da requisição por nome (uso interno; {@link #getCookies()} é a forma da Servlet API). */
    Map<String, String> cookieValues = [:]
    /** Arquivos de {@code multipart/form-data}, por nome do campo. */
    Map<String, List<GroxMultipartFile>> files = [:]
    String remoteAddr

    private final Map<String, Object> attributes = Collections.synchronizedMap(new LinkedHashMap<String, Object>())
    private Object parsedJson
    private boolean jsonParsed

    String getHeader(String name) {
        headers.getFirst(name)
    }

    @Override
    Enumeration<String> getHeaders(String name) {
        Collections.enumeration(headers.get(name) ?: Collections.<String>emptyList())
    }

    @Override
    Enumeration<String> getHeaderNames() {
        Collections.enumeration(headers.keySet())
    }

    String getContentType() {
        getHeader('Content-Type')
    }

    boolean isJsonRequest() {
        contentType?.toLowerCase()?.contains('json')
    }

    /** Charset do Content-Type; desconhecido ou inválido vira UTF-8 (sem exceção por requisição). */
    Charset getCharset() {
        def m = contentType =~ /(?i)charset=([^;\s]+)/
        if (!m.find()) return StandardCharsets.UTF_8
        String name = m.group(1).replace('"', '')
        try {
            return Charset.isSupported(name) ? Charset.forName(name) : StandardCharsets.UTF_8
        } catch (java.nio.charset.IllegalCharsetNameException ignored) {
            return StandardCharsets.UTF_8
        }
    }

    String getText() {
        new String(body, charset)
    }

    /**
     * Visão do JSON parseado ({@code JSONObject} do Grails, definida pelo módulo Grails na partida); null
     * devolve os mapas e listas do {@code JsonSlurper}.
     */
    static volatile java.util.function.UnaryOperator<Object> jsonView

    /** Sessão HTTP da requisição, definida por quem a atende (o {@code WebRequest}); sem ele, null. */
    java.util.function.Function<Boolean, GroxSession> sessionProvider

    /** Corpo JSON parseado ({@code request.JSON}), como no Grails. */
    synchronized Object getJSON() {
        if (!jsonParsed) {
            jsonParsed = true
            Object parsed = body.length ? new JsonSlurper().parseText(text) : null
            parsedJson = parsed != null && jsonView != null ? jsonView.apply(parsed) : parsed
        }
        parsedJson
    }

    /** {@code request.json}: o mesmo que {@code request.JSON} (extensão Grox). */
    Object getJson() {
        getJSON()
    }

    boolean isXhr() {
        getHeader('X-Requested-With') == 'XMLHttpRequest'
    }

    String getRequestURI() {
        path
    }

    String getForwardURI() {
        path
    }

    String getContextPath() {
        ''
    }

    String getParameter(String name) {
        (queryParameters[name] ?: formParameters[name])?.getAt(0)
    }

    /** {@code request.getFile('foto')}: primeiro arquivo do campo, ou null. */
    GroxMultipartFile getFile(String name) {
        files[name]?.getAt(0)
    }

    List<GroxMultipartFile> getFiles(String name) {
        files[name] ?: []
    }

    Iterator<String> getFileNames() {
        files.keySet().iterator()
    }

    Map<String, Object> getFileMap() {
        files.collectEntries { k, v -> [k, v ? v[0] : null] } as Map<String, Object>
    }

    /** Atributos da requisição como propriedades, como no Grails: {@code request.x}, {@code request['javax.servlet.error.status_code']}. */
    Object propertyMissing(String name) {
        getAttribute(name)
    }

    void propertyMissing(String name, Object value) {
        setAttribute(name, value)
    }

    /**
     * {@code request.cookies}, como na Servlet API ({@code request.cookies.find { it.name == 'x' }}). Cookies com
     * nome inválido para a Servlet API (que o navegador aceita) ficam de fora, sem exceção.
     */
    jakarta.servlet.http.Cookie[] getCookies() {
        List<jakarta.servlet.http.Cookie> list = []
        cookieValues.each { k, v ->
            try {
                list << new jakarta.servlet.http.Cookie(k, v)
            } catch (IllegalArgumentException ignored) {
            }
        }
        list as jakarta.servlet.http.Cookie[]
    }

    boolean isMultipart() {
        contentType?.toLowerCase()?.startsWith('multipart/')
    }

    /** API servlet: {@code request.reader} sobre o corpo já lido. */
    BufferedReader getReader() {
        new BufferedReader(new StringReader(text))
    }

    /** O corpo, já lido inteiro pelo Netty. */
    @Override
    jakarta.servlet.ServletInputStream getInputStream() {
        new BodyStream(body)
    }

    private static class BodyStream extends jakarta.servlet.ServletInputStream {
        private final ByteArrayInputStream source

        BodyStream(byte[] body) {
            source = new ByteArrayInputStream(body)
        }

        @Override
        int read() { source.read() }

        @Override
        int read(byte[] b, int off, int len) { source.read(b, off, len) }

        @Override
        int available() { source.available() }

        @Override
        boolean isFinished() { source.available() == 0 }

        @Override
        boolean isReady() { true }

        @Override
        void setReadListener(jakarta.servlet.ReadListener listener) {
            throw unsupported('setReadListener')
        }
    }

    /**
     * {@code grox.http.trustedProxies}: endereços cujos {@code X-Forwarded-*} são aceitos (regex).
     * Padrão igual ao {@code internalProxies} do RemoteIpValve do Tomcat: loopback e redes privadas.
     */
    static volatile java.util.regex.Pattern trustedProxies = ~/10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|169\.254\.\d{1,3}\.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.1[6-9]\.\d{1,3}\.\d{1,3}|172\.2[0-9]\.\d{1,3}\.\d{1,3}|172\.3[0-1]\.\d{1,3}\.\d{1,3}|0:0:0:0:0:0:0:1|::1/

    /** Endereço (sem porta) de quem abriu a conexão. */
    String getRemoteHost() {
        remoteAddr?.replaceFirst(/^\//, '')?.replaceFirst(/:\d+$/, '')
    }

    /** A conexão veio de um proxy confiável: os {@code X-Forwarded-*} valem. */
    boolean isFromTrustedProxy() {
        String h = remoteHost
        h != null && trustedProxies != null && trustedProxies.matcher(h).matches()
    }

    /** Cabeçalho {@code X-Forwarded-*} (primeiro valor), só se vier de proxy confiável. */
    String forwarded(String name) {
        fromTrustedProxy ? getHeader(name)?.split(',')?.first()?.trim() ?: null : null
    }

    /**
     * Endereço do cliente. Atrás de proxy confiável, o {@code X-Forwarded-For} é lido da direita para a esquerda,
     * pulando os proxies confiáveis, como o {@code RemoteIpValve} do Tomcat: o proxy acrescenta o endereço real
     * à direita do valor que o cliente mandou, então o primeiro valor da esquerda pode ser forjado.
     */
    String getClientAddr() {
        if (!fromTrustedProxy) return remoteHost
        String header = getHeader('X-Forwarded-For')
        if (!header) return remoteHost
        String[] hops = header.split(',')
        String leftmost = null
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim()
            if (!hop) continue
            leftmost = hop
            if (!trustedProxies.matcher(hop).matches()) return hop
        }
        leftmost ?: remoteHost
    }

    String getScheme() {
        forwarded('X-Forwarded-Proto') ?: 'http'
    }

    /** Host pedido, com porta: {@code X-Forwarded-Host} (proxy confiável) ou {@code Host}. */
    String getHostHeader() {
        forwarded('X-Forwarded-Host') ?: getHeader('Host')?.trim() ?: 'localhost'
    }

    String getServerName() {
        hostHeader.replaceFirst(/:\d+$/, '')
    }

    /** URL completa sem query string, como {@code HttpServletRequest.getRequestURL()}. */
    StringBuffer getRequestURL() {
        new StringBuffer("${scheme}://${hostHeader}${path}")
    }

    /** {@code request.format}: formato do corpo da requisição, pelo Content-Type ({@code all} sem corpo). */
    String getFormat() {
        MimeTypes.fromContentType(getContentType())
    }

    /** Exceção que levou ao mapeamento de erro ({@code "500"(controller: 'error')}), como no Grails. */
    Throwable getException() {
        (Throwable) getAttribute('exception')
    }

    /** Sessão da requisição corrente ({@code request.session}, como no Servlet). */
    GroxSession getSession() {
        getSession(true)
    }

    GroxSession getSession(boolean create) {
        sessionProvider?.apply(create)
    }

    @groovy.transform.CompileStatic
    Object getAttribute(String name) {
        attributes.get(name)
    }

    @groovy.transform.CompileStatic
    void setAttribute(String name, Object value) {
        if (value == null) attributes.remove(name)
        else attributes.put(name, value)
    }

    @groovy.transform.CompileStatic
    void removeAttribute(String name) {
        attributes.remove(name)
    }

    // --- resto da API servlet (jakarta.servlet-api): o que faz sentido sem container. O que depende de um
    // container (ServletContext, async, dispatcher, partes, login do container) lança UnsupportedOperationException.

    private static UnsupportedOperationException unsupported(String method) {
        new UnsupportedOperationException("${method}: o Grox não roda em container de servlet")
    }

    /** {@code HTTP/1.1}; o Netty não expõe a versão depois da conversão. */
    String protocol = 'HTTP/1.1'
    private String characterEncodingOverride

    @Override
    String getCharacterEncoding() {
        characterEncodingOverride ?: (contentType =~ /(?i)charset=([^;\s]+)/).with { it.find() ? it.group(1).replace('"', '') : null }
    }

    @Override
    void setCharacterEncoding(String encoding) {
        characterEncodingOverride = encoding
    }

    @Override
    int getContentLength() { body.length }

    @Override
    long getContentLengthLong() { body.length }

    @Override
    Enumeration<String> getAttributeNames() {
        synchronized (attributes) {
            Collections.enumeration(new ArrayList<>(attributes.keySet()))
        }
    }

    /** Parâmetros da query string e do formulário, nessa ordem. */
    @Override
    Enumeration<String> getParameterNames() {
        Set<String> names = new LinkedHashSet<>(queryParameters.keySet())
        names.addAll(formParameters.keySet())
        Collections.enumeration(names)
    }

    @Override
    String[] getParameterValues(String name) {
        List<String> values = (queryParameters[name] ?: []) + (formParameters[name] ?: [])
        values ? values as String[] : null
    }

    @Override
    Map<String, String[]> getParameterMap() {
        Map<String, String[]> m = new LinkedHashMap<>()
        Collections.list(getParameterNames()).each { String n -> m.put(n, getParameterValues(n)) }
        Collections.unmodifiableMap(m)
    }

    @Override
    int getServerPort() {
        def m = hostHeader =~ /:(\d+)$/
        m.find() ? m.group(1) as int : (secure ? 443 : 80)
    }

    @Override
    int getRemotePort() {
        def m = remoteAddr =~ /:(\d+)$/
        m.find() ? m.group(1) as int : -1
    }

    @Override
    String getLocalName() { serverName }

    @Override
    String getLocalAddr() { null }

    @Override
    int getLocalPort() { serverPort }

    /** Pelo {@code Accept-Language}; sem ele, o locale padrão. */
    @Override
    Locale getLocale() {
        getLocales().nextElement()
    }

    @Override
    Enumeration<Locale> getLocales() {
        String header = getHeader('Accept-Language')
        List<Locale> locales = []
        if (header) {
            try {
                locales = Locale.LanguageRange.parse(header).findAll { it.range != '*' }.collect { Locale.forLanguageTag(it.range) }
            } catch (IllegalArgumentException ignored) {
            }
        }
        Collections.enumeration(locales ?: [Locale.default])
    }

    @Override
    boolean isSecure() { scheme == 'https' }

    @Override
    jakarta.servlet.RequestDispatcher getRequestDispatcher(String path) { throw unsupported('getRequestDispatcher') }

    @Override
    jakarta.servlet.ServletContext getServletContext() { throw unsupported('getServletContext') }

    @Override
    jakarta.servlet.AsyncContext startAsync() { throw unsupported('startAsync') }

    @Override
    jakarta.servlet.AsyncContext startAsync(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
        throw unsupported('startAsync')
    }

    @Override
    boolean isAsyncStarted() { false }

    @Override
    boolean isAsyncSupported() { false }

    @Override
    jakarta.servlet.AsyncContext getAsyncContext() { throw new IllegalStateException('Requisição não está em modo assíncrono') }

    @Override
    jakarta.servlet.DispatcherType getDispatcherType() { jakarta.servlet.DispatcherType.REQUEST }

    @Override
    String getRequestId() { Integer.toHexString(System.identityHashCode(this)) }

    @Override
    String getProtocolRequestId() { '' }

    @Override
    jakarta.servlet.ServletConnection getServletConnection() { throw unsupported('getServletConnection') }

    /** A autenticação é da segurança do Grox ({@code springSecurityService}), não do container. */
    @Override
    String getAuthType() { null }

    @Override
    long getDateHeader(String name) {
        String v = getHeader(name)
        if (v == null) return -1L
        try {
            java.time.ZonedDateTime.parse(v, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Cabeçalho ${name} não é uma data: ${v}", e)
        }
    }

    @Override
    int getIntHeader(String name) {
        String v = getHeader(name)
        v == null ? -1 : Integer.parseInt(v.trim())
    }

    @Override
    String getPathInfo() { null }

    @Override
    String getPathTranslated() { null }

    @Override
    String getServletPath() { path }

    @Override
    String getRemoteUser() { userPrincipal?.name }

    @Override
    boolean isUserInRole(String role) { false }

    @Override
    java.security.Principal getUserPrincipal() {
        Object p = getAttribute(PRINCIPAL_ATTRIBUTE)
        p instanceof java.security.Principal ? (java.security.Principal) p : null
    }

    @Override
    String getRequestedSessionId() { null }

    @Override
    String changeSessionId() {
        GroxSession s = getSession(false)
        if (s == null) throw new IllegalStateException('Requisição sem sessão')
        s.changeSessionId()
    }

    @Override
    boolean isRequestedSessionIdValid() { getSession(false) != null }

    @Override
    boolean isRequestedSessionIdFromCookie() { getSession(false) != null }

    @Override
    boolean isRequestedSessionIdFromURL() { false }

    @Override
    boolean authenticate(jakarta.servlet.http.HttpServletResponse response) { throw unsupported('authenticate') }

    @Override
    void login(String username, String password) { throw unsupported('login') }

    @Override
    void logout() { throw unsupported('logout') }

    /** Os arquivos do multipart estão em {@link #getFile}/{@link #getFiles} (API do Spring). */
    @Override
    Collection<jakarta.servlet.http.Part> getParts() { throw unsupported('getParts') }

    @Override
    jakarta.servlet.http.Part getPart(String name) { throw unsupported('getPart') }

    @Override
    public <T extends jakarta.servlet.http.HttpUpgradeHandler> T upgrade(Class<T> handlerClass) { throw unsupported('upgrade') }

    @Override
    String toString() {
        "$method $uri"
    }
}
