package org.grox.assets

import org.grox.http.GroxRequest
import org.grox.http.GroxResponse

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Serve {@code /assets/**}.
 * <ul>
 *   <li>desenvolvimento ({@code grails-app/assets} presente e reload ligado): processa na hora e
 *       reprocessa quando algum arquivo da cadeia de require muda</li>
 *   <li>produção: arquivos pré-compilados do classpath ({@code META-INF/assets}); nomes com
 *       digest têm cache de 1 ano (immutable)</li>
 * </ul>
 */
class AssetServer implements org.grox.http.ResourceHandler {

    String getName() { 'assets' }


    final String prefix
    final AssetProcessor dev
    final Map<String, String> manifest
    final ClassLoader loader
    private final Map<String, AssetProcessor.Result> devCache = new ConcurrentHashMap<>()
    private final Map<String, byte[]> prodCache = new ConcurrentHashMap<>()
    private final Set<String> digested

    AssetServer(String mapping, File devDir, ClassLoader loader) {
        this.prefix = '/' + (mapping ?: 'assets').replaceAll(/^\/|\/$/, '')
        this.loader = loader
        this.dev = devDir?.directory ? new AssetProcessor(devDir) : null
        this.manifest = loadManifest(loader)
        this.digested = manifest.values() as Set
    }

    static Map<String, String> loadManifest(ClassLoader loader) {
        URL url = loader.getResource(AssetCompiler.MANIFEST)
        if (url == null) return [:]
        def props = new Properties()
        url.withReader('UTF-8') { props.load(it) }
        props.collectEntries { k, v -> [(k.toString()): v.toString()] } as Map<String, String>
    }

    boolean isDevelopment() {
        dev != null
    }

    boolean handles(GroxRequest req) {
        (req.method == 'GET' || req.method == 'HEAD') && req.path.startsWith(prefix + '/')
    }

    /** URL pública do asset (com digest em produção). */
    String url(String logical) {
        String l = logical.replaceAll(/^\//, '')
        prefix + '/' + (dev == null ? (manifest[l] ?: l) : l)
    }

    boolean exists(String logical) {
        String l = logical.replaceAll(/^\//, '')
        dev != null ? dev.find(l) != null : manifest.containsKey(l)
    }

    /** Conteúdo processado do asset pelo caminho lógico, ou null ({@code assetResourceLocator}). */
    byte[] contentOf(String logical) {
        String rel = logical?.replaceAll(/^\//, '')
        if (!rel || rel.contains('..')) return null
        if (dev != null) return dev.process(rel) { String ref -> prefix + '/' + ref }?.content
        String file = manifest[rel]
        if (file == null) return null
        URL u = loader.getResource(AssetCompiler.OUTPUT + '/' + file)
        u == null ? null : u.openStream().withCloseable { it.bytes }
    }

    /** @return resposta ou null se não existir */
    GroxResponse serve(GroxRequest req) {
        String rel = req.path.substring(prefix.length() + 1)
        if (rel.contains('..')) return null
        return dev != null ? serveDev(req, rel) : serveCompiled(req, rel)
    }

    private GroxResponse serveDev(GroxRequest req, String rel) {
        AssetProcessor.Result r = devCache.get(rel)
        if (r == null || r.dependencies.any { !it.exists() || it.lastModified() > r.lastModified }) {
            r = dev.process(rel) { String ref -> prefix + '/' + ref }
            if (r == null) {
                devCache.remove(rel)
                return null
            }
            devCache.put(rel, r)
        }
        response(req, r.content, r.contentType, 'no-cache', r.lastModified)
    }

    private GroxResponse serveCompiled(GroxRequest req, String rel) {
        boolean immutable = digested.contains(rel)
        if (!immutable && !manifest.containsKey(rel)) return null
        byte[] content = prodCache.computeIfAbsent(rel) {
            URL u = loader.getResource(AssetCompiler.OUTPUT + '/' + rel)
            u == null ? new byte[0] : u.openStream().withCloseable { it.bytes }
        }
        String ext = AssetProcessor.extension(rel)
        response(req, content, StaticMime.of(ext), immutable ? 'public, max-age=31536000, immutable' : 'public, max-age=300', 0L)
    }

    /** ETag por conteúdo em cache (chave por identidade: arrays não sobrescrevem equals); some com o conteúdo. */
    private final Map<byte[], String> etags = Collections.synchronizedMap(new WeakHashMap<byte[], String>())

    private String etag(byte[] content) {
        String e = etags.get(content)
        if (e == null) {
            e = '"' + MessageDigest.getInstance('MD5').digest(content).encodeHex().toString() + '"'
            etags.put(content, e)
        }
        e
    }

    private GroxResponse response(GroxRequest req, byte[] content, String contentType, String cache, long lastModified) {
        def resp = new GroxResponse()
        String etag = etag(content)
        resp.setHeader('ETag', etag)
        resp.setHeader('Cache-Control', cache)
        if (lastModified) resp.setHeader('Last-Modified', DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(lastModified).atOffset(ZoneOffset.UTC)))
        if (req.getHeader('If-None-Match') == etag) {
            resp.status = 304
            return resp
        }
        resp.contentType = contentType
        resp.outputStream.write(content)
        resp
    }
}
