package org.grox.web

import org.grox.http.MimeTypes

/**
 * Negociação de conteúdo: {@code params.format} (extensão na URL ou mapeamento), cabeçalho
 * Accept e, na falta dele, Content-Type JSON da requisição. {@code all} = sem preferência.
 */
class Formats {

    static final Map<String, String> MIME_TO_FORMAT = MimeTypes.MIME_TO_FORMAT
    static final Map<String, String> FORMAT_TO_MIME = MimeTypes.FORMAT_TO_MIME

    static String resolve(WebRequest wr) {
        Object p = wr.params.get('format')
        if (p) return p.toString().toLowerCase()
        String f = fromAccept(wr.request.getHeader('Accept'))
        if (f == 'all' && wr.request.jsonRequest) return 'json'
        f
    }

    static String fromAccept(String accept) {
        MimeTypes.fromAccept(accept)
    }

    /**
     * Formato da requisição (o {@code request.format} do Grails), pelo Content-Type: {@code form},
     * {@code multipartForm}, {@code json}... Sem corpo, {@code all}.
     */
    static String fromContentType(String contentType) {
        MimeTypes.fromContentType(contentType)
    }

    static String mimeType(String format) {
        MimeTypes.mimeType(format)
    }
}
