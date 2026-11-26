package org.grox.assets

import groovy.util.logging.Slf4j
import org.grox.core.GroxModule

/**
 * Asset pipeline: servidor de assets (desenvolvimento ou pré-compilados). Os beans do plugin
 * ({@code assetResourceLocator}, {@code grailsResourceLocator}) são do {@code AssetPipelineModule}.
 */
@Slf4j
class AssetsModule extends GroxModule {

    AssetServer server

    int getOrder() { 350 }

    @Override
    void configureWeb() {
        File dir = runtime.devDirectory('grox.assets.dir', org.grox.core.AppLayout.current(runtime.config) + '/assets')
        server = new AssetServer(config.getProperty('grails.assets.mapping', String, 'assets'), dir, runtime.application.classLoader)
        runtime.module(org.grox.http.HttpModule)?.pipeline?.resources?.add(server)   // depois dos estáticos
        log.debug("Assets: ${server.development ? "desenvolvimento (${dir})" : "${server.manifest.size()} pré-compilados"}")
    }
}
