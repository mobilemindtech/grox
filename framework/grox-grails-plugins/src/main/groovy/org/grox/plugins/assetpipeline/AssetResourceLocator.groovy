package org.grox.plugins.assetpipeline

import org.grox.assets.AssetServer

/**
 * Bean {@code assetResourceLocator} do asset-pipeline: localiza um asset (compilado ou,
 * em desenvolvimento, em {@code grails-app/assets}) pelo caminho lógico.
 */
class AssetResourceLocator extends asset.pipeline.grails.AssetResourceLocator implements org.grails.core.io.ResourceLocator {
    /** Definido pelo {@link AssetPipelineModule} quando o {@link AssetServer} é criado (depois dos beans). */
    AssetServer server

    AssetResourceLocator(AssetServer server) { this.server = server }

    /** Recurso com {@code inputStream}, {@code byteArray}, {@code filename}; null se não existir. */
    @Override
    AssetResource findAssetForURI(String uri) {
        byte[] bytes = server?.contentOf(uri?.replaceFirst('^/+', ''))
        bytes == null ? null : new AssetResource(uri, bytes)
    }

    /**
     * Bean {@code grailsResourceLocator}: {@code /images/x.png} nos assets ({@code images/x.png} ou {@code x.png}) e,
     * senão, nos estáticos do classpath ({@code public/images/x.png}).
     */
    @Override
    org.springframework.core.io.Resource findResourceForURI(String uri) {
        String path = uri?.replaceFirst('^/+', '')
        // sem '..' nem '\': o caminho pode vir da requisição (public/../application.yml leria a configuração)
        if (!path || path.split('[/\\\\]').any { it == '..' } || path.contains('\\')) return null
        AssetResource r = findAssetForURI(path) ?: (path.contains('/') ? findAssetForURI(path.substring(path.indexOf('/') + 1)) : null)
        if (r != null) return r
        InputStream is = Thread.currentThread().contextClassLoader?.getResourceAsStream("public/${path}")
        is == null ? null : new AssetResource(path, is.withCloseable { it.bytes })
    }

    static class AssetResource implements org.springframework.core.io.Resource {
        final String filename
        final byte[] byteArray

        AssetResource(String filename, byte[] bytes) {
            this.filename = filename
            this.byteArray = bytes
        }

        InputStream getInputStream() { new ByteArrayInputStream(byteArray) }

        boolean exists() { true }

        long contentLength() { byteArray.length }
    }
}
