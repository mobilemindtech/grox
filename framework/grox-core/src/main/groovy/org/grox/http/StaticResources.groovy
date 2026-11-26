package org.grox.http

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Arquivos estáticos em {@code /static/**}: diretório de desenvolvimento
 * ({@code src/main/resources/public}) ou classpath {@code public/}.
 */
class StaticResources implements ResourceHandler {

    String getName() { 'static' }


    static final Map<String, String> MIME = [
            css : 'text/css', js: 'text/javascript', mjs: 'text/javascript', html: 'text/html', htm: 'text/html',
            json: 'application/json', map: 'application/json', txt: 'text/plain', xml: 'application/xml',
            png : 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', svg: 'image/svg+xml',
            webp: 'image/webp', ico: 'image/x-icon', woff: 'font/woff', woff2: 'font/woff2', ttf: 'font/ttf',
            pdf : 'application/pdf'
    ]

    final String prefix
    final File directory
    final ClassLoader loader
    final boolean development
    /** Fora de desenvolvimento, os arquivos do classpath (imutáveis) ficam em memória. */
    private final Map<String, Object[]> cache = new java.util.concurrent.ConcurrentHashMap<>()
    private static final Object[] MISSING = new Object[0]

    StaticResources(String prefix, File directory, ClassLoader loader, boolean development) {
        this.prefix = prefix
        this.directory = directory
        this.loader = loader
        this.development = development
    }

    private Object[] load(String rel) {
        URL u = loader.getResource('public' + rel)
        if (u == null || (u.protocol == 'file' && new File(u.toURI()).directory)) return MISSING
        def conn = u.openConnection()
        conn.useCaches = false
        [conn.inputStream.withCloseable { it.bytes }, conn.lastModified] as Object[]
    }

    boolean handles(GroxRequest req) {
        (req.method == 'GET' || req.method == 'HEAD') && req.path.startsWith(prefix + '/')
    }

    /** @return resposta ou null se não existir */
    GroxResponse serve(GroxRequest req) {
        String rel = req.path.substring(prefix.length())
        if (rel.contains('..') || rel.endsWith('/')) return null
        byte[] bytes = null
        long lastModified = 0
        File f = directory ? new File(directory, rel.substring(1)) : null
        // symlink (ou outro truque de caminho) que sai de public/ não é servido
        if (f?.file && !f.canonicalPath.startsWith(directory.canonicalPath + File.separator)) return null
        if (f?.file) {
            bytes = f.bytes
            lastModified = f.lastModified()
        } else {
            Object[] entry = development ? null : cache.get(rel)
            if (entry == null) {
                entry = load(rel)
                // só arquivos existentes: caminhos aleatórios não podem encher a memória
                if (!development && !entry.is(MISSING)) cache.put(rel, entry)
            }
            if (entry.is(MISSING) || entry.length == 0) return null
            bytes = (byte[]) entry[0]
            lastModified = (long) entry[1]
        }
        def resp = new GroxResponse()
        String lm = lastModified ? DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(lastModified).atOffset(ZoneOffset.UTC)) : null
        if (lm) resp.setHeader('Last-Modified', lm)
        resp.setHeader('Cache-Control', development ? 'no-cache' : 'public, max-age=3600')
        if (lm && req.getHeader('If-Modified-Since') == lm) {
            resp.status = 304
            return resp
        }
        String ext = rel.contains('.') ? rel.substring(rel.lastIndexOf('.') + 1).toLowerCase() : ''
        resp.contentType = MIME[ext] ?: 'application/octet-stream'
        resp.outputStream.write(bytes)
        resp
    }
}
