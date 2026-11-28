package org.grox.http

import java.nio.charset.Charset

/** Resposta HTTP bufferizada; convertida para Netty ao final da ação. */
class GroxResponse implements jakarta.servlet.http.HttpServletResponse {

    int status = 200
    final HeaderMap headers = new HeaderMap()
    String contentType
    String characterEncoding = 'UTF-8'
    /** Corpo/estado definido por render, redirect, respond ou sendError. */
    boolean committed
    /** Status enviado via {@link #sendError}, tratado pelos mapeamentos de erro. */
    Integer errorStatus
    String errorMessage

    private final Buffer buffer = new Buffer()
    /** Corpo pronto, sem cópia para o buffer (estático em cache, página escrita de uma vez); vai ao buffer se houver mais escrita. */
    private byte[] fixedBody

    /** O array interno do {@link ByteArrayOutputStream}: o Netty o embrulha sem a cópia do {@code toByteArray()}. */
    private static class Buffer extends ByteArrayOutputStream {
        byte[] array() { buf }
    }

    /** Leva o corpo pronto ao buffer antes de uma escrita que o complementa. */
    private void materialize() {
        if (fixedBody != null) {
            byte[] b = fixedBody
            fixedBody = null
            buffer.write(b, 0, b.length)
        }
    }

    /** Define o corpo inteiro sem copiá-lo (o array não deve ser alterado depois). */
    void setBody(byte[] bytes) {
        writer?.flush()
        buffer.reset()
        fixedBody = bytes
    }
    private PrintWriter writer
    private boolean writerClosed

    @groovy.transform.CompileStatic
    void setHeader(String name, Object value) {
        headers.set(name, value?.toString())
    }

    @Override
    void setHeader(String name, String value) {
        headers.set(name, value)
    }

    /**
     * {@code response.addCookie(new Cookie('x', 'v'))}: cabeçalho Set-Cookie. Nome ou valor com caracteres inválidos
     * ({@code ;}, espaço, aspas...) dão {@code IllegalArgumentException}, como na Servlet API: sem isso,
     * {@code new Cookie('lang', params.lang)} deixava o usuário injetar atributos ({@code pt; Domain=...}).
     */
    void addCookie(jakarta.servlet.http.Cookie c) {
        def nc = new io.netty.handler.codec.http.cookie.DefaultCookie(c.name, c.value ?: '')
        if (c.path) nc.path = c.path else nc.path = '/'
        if (c.domain) nc.domain = c.domain
        if (c.maxAge >= 0) nc.maxAge = c.maxAge
        nc.secure = c.secure
        nc.httpOnly = c.httpOnly
        addHeader('Set-Cookie', io.netty.handler.codec.http.cookie.ServerCookieEncoder.STRICT.encode(nc))
    }

    /** Nomes dos cabeçalhos já definidos (Servlet API). */
    Collection<String> getHeaderNames() {
        new ArrayList<>(headers.keySet())
    }

    /** Todos os valores de um cabeçalho (Servlet API). */
    Collection<String> getHeaders(String name) {
        new ArrayList<>(headers.get(name) ?: [])
    }

    void addHeader(String name, Object value) {
        headers.add(name, value?.toString())
    }

    @Override
    void addHeader(String name, String value) {
        headers.add(name, value)
    }

    String getHeader(String name) {
        headers.getFirst(name)
    }

    boolean containsHeader(String name) {
        headers.containsKey(name)
    }

    /**
     * Como o converter JSON do Grails ({@code render x as JSON}), que faz flush e fecha o writer: escritas
     * seguintes são descartadas (o Tomcat ignora escrita em writer fechado). Código que renderiza duas vezes
     * (ex.: {@code json(data); render()}) responde só o primeiro JSON. {@link #resetBuffer} reabre.
     */
    void closeWriter() {
        writer?.flush()
        writer = new PrintWriter(Writer.nullWriter())
        writerClosed = true
    }

    PrintWriter getWriter() {
        materialize()
        if (writer == null) writer = new PrintWriter(new OutputStreamWriter(buffer, Charset.forName(characterEncoding)), false)
        writer
    }

    PrintWriter getOut() {
        getWriter()
    }

    jakarta.servlet.ServletOutputStream getOutputStream() {
        writer?.flush()
        materialize()
        if (outputStream == null) outputStream = new BufferStream(buffer)
        outputStream
    }

    private BufferStream outputStream

    /** O corpo bufferizado como {@code ServletOutputStream} (escrita síncrona, sempre pronta). */
    private static class BufferStream extends jakarta.servlet.ServletOutputStream {
        private final ByteArrayOutputStream target

        BufferStream(ByteArrayOutputStream target) {
            this.target = target
        }

        @Override
        void write(int b) { target.write(b) }

        @Override
        void write(byte[] b, int off, int len) { target.write(b, off, len) }

        @Override
        boolean isReady() { true }

        @Override
        void setWriteListener(jakarta.servlet.WriteListener listener) {
            throw new UnsupportedOperationException('Grox não tem IO assíncrono de servlet')
        }
    }

    /** A action já escreveu o corpo ({@code response.outputStream << bytes}, {@code response.writer}). */
    boolean hasBody() {
        writer?.flush()
        buffer.size() > 0 || (fixedBody != null && fixedBody.length > 0)
    }

    void resetBuffer() {
        writer?.flush()
        buffer.reset()
        fixedBody = null
        if (writerClosed) {
            writer = null
            writerClosed = false
        }
    }

    void sendRedirect(String location, int code = 302) {
        status = code
        setHeader('Location', location)
        committed = true
    }

    @Override
    void sendRedirect(String location, int code, boolean clearBuffer) {
        if (clearBuffer) resetBuffer()
        sendRedirect(location, code)
    }

    void sendError(int code, String message = null) {
        status = code
        errorStatus = code
        errorMessage = message
        committed = true
    }

    // --- resto da API servlet: o que faz sentido numa resposta bufferizada ---

    @Override
    void setContentLength(int length) {}   // calculado pelo Grox a partir do corpo

    @Override
    void setContentLengthLong(long length) {}

    /** O corpo é sempre bufferizado inteiro. */
    @Override
    void setBufferSize(int size) {}

    @Override
    int getBufferSize() { size() }

    /** O envio é no fim da action; aqui só descarrega o writer no buffer. */
    @Override
    void flushBuffer() {
        writer?.flush()
    }

    @Override
    void reset() {
        resetBuffer()
        status = 200
        headers.clear()
        contentType = null
        errorStatus = null
        errorMessage = null
    }

    Locale locale

    @Override
    void setLocale(Locale locale) {
        this.locale = locale
        if (locale) setHeader('Content-Language', locale.toLanguageTag())
    }

    @Override
    Locale getLocale() {
        locale ?: Locale.default
    }

    /** Sem reescrita de URL (a sessão vai sempre em cookie). */
    @Override
    String encodeURL(String url) { url }

    @Override
    String encodeRedirectURL(String url) { url }

    @Override
    void setDateHeader(String name, long date) {
        setHeader(name, httpDate(date))
    }

    @Override
    void addDateHeader(String name, long date) {
        addHeader(name, httpDate(date))
    }

    private static String httpDate(long millis) {
        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC))
    }

    @Override
    void setIntHeader(String name, int value) {
        setHeader(name, String.valueOf(value))
    }

    @Override
    void addIntHeader(String name, int value) {
        addHeader(name, String.valueOf(value))
    }

    /** Texto inteiro de uma vez (ex.: a página renderizada): codifica uma vez, sem PrintWriter/OutputStreamWriter. */
    void writeText(String text) {
        if (writerClosed) return
        writer?.flush()
        byte[] b = text.getBytes(Charset.forName(characterEncoding))
        if (fixedBody == null && buffer.size() == 0) {
            fixedBody = b   // a página inteira de uma vez: sem copiar para o buffer
        } else {
            materialize()
            buffer.write(b, 0, b.length)
        }
    }

    /** Tamanho do corpo em bytes, sem copiá-lo. */
    int size() {
        writer?.flush()
        fixedBody != null ? fixedBody.length : buffer.size()
    }

    /** O corpo (uma cópia, se estiver no buffer). */
    byte[] toBytes() {
        writer?.flush()
        fixedBody != null ? fixedBody : buffer.toByteArray()
    }

    /** O array com o corpo, sem cópia: os primeiros {@link #size()} bytes (só para escrever a resposta). */
    byte[] bodyArray() {
        writer?.flush()
        fixedBody != null ? fixedBody : buffer.array()
    }

    String getText() {
        new String(toBytes(), characterEncoding)
    }

    String getContentTypeHeader() {
        if (!contentType) return null
        boolean textual = contentType.startsWith('text/') || contentType.contains('json') ||
                contentType.contains('xml') || contentType.contains('javascript')
        (textual && !contentType.contains('charset')) ? "${contentType};charset=${characterEncoding}" : contentType
    }
}
