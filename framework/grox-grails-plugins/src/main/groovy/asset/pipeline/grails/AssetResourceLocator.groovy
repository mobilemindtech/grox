package asset.pipeline.grails

import org.springframework.core.io.Resource

/**
 * Tipo do bean {@code assetResourceLocator} do asset-pipeline ({@code AssetResourceLocator assetResourceLocator}
 * injetado); a implementação é a do Grox ({@code org.grox.plugins.assetpipeline.AssetResourceLocator}).
 */
abstract class AssetResourceLocator {
    abstract Resource findAssetForURI(String uri)
}
