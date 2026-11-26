package org.grox.http

/** Tipos MIME e os formatos do Grails ({@code html}, {@code json}, {@code form}...). */
class MimeTypes {

    static final Map<String, String> MIME_TO_FORMAT = [
            'text/html'            : 'html',
            'application/xhtml+xml': 'html',
            'application/json'     : 'json',
            'text/json'            : 'json',
            'application/hal+json' : 'json',
            'application/xml'      : 'xml',
            'text/xml'             : 'xml',
            'text/plain'           : 'text',
            'text/csv'             : 'csv',
            '*/*'                  : 'all',
    ]

    static final Map<String, String> FORMAT_TO_MIME = [
            html: 'text/html', json: 'application/json', xml: 'application/xml', text: 'text/plain', csv: 'text/csv'
    ]

    static String fromAccept(String accept) {
        if (!accept) return 'all'
        List<List> types = []
        accept.split(',').eachWithIndex { String part, int i ->
            String[] pieces = part.trim().split(';')
            double q = 1d
            pieces.drop(1).each { String param ->
                String[] kv = param.trim().split('=')
                if (kv[0] == 'q' && kv.length > 1) q = quality(kv[1])
            }
            types << [pieces[0].trim().toLowerCase(), q, i]
        }
        types.sort { a, b -> (b[1] as double) <=> (a[1] as double) ?: (a[2] as int) <=> (b[2] as int) }
        for (List t : types) {
            String f = MIME_TO_FORMAT[t[0]]
            if (f) return f
            if (t[0].toString().endsWith('+json')) return 'json'
        }
        'all'
    }

    /**
     * Formato da requisição (o {@code request.format} do Grails), pelo Content-Type: {@code form},
     * {@code multipartForm}, {@code json}... Sem corpo, {@code all}.
     */
    static String fromContentType(String contentType) {
        if (!contentType) return 'all'
        String mime = contentType.split(';')[0].trim().toLowerCase()
        if (mime == 'application/x-www-form-urlencoded') return 'form'
        if (mime == 'multipart/form-data') return 'multipartForm'
        String f = MIME_TO_FORMAT[mime]
        if (f) return f
        mime.endsWith('+json') ? 'json' : 'all'
    }

    static String mimeType(String format) {
        FORMAT_TO_MIME[format] ?: 'text/html'
    }

    /** O {@code q} de um tipo do {@code Accept}; inválido (vem do cliente) conta como 0, sem exceção. */
    private static double quality(String value) {
        String v = value.trim()
        if (!(v ==~ /(0(\.\d{0,3})?|1(\.0{0,3})?)/)) return 0d
        Double.parseDouble(v)
    }
}
