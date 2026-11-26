package org.grox.plugins.assetpipeline

import org.grox.view.taglib.TagLibApi

/**
 * Como a {@code AssetMethodTagLib} do asset-pipeline: {@code assetPath} e {@code assetPathExists}
 * também no namespace {@code g}, para {@code ${assetPath(src: 'logo.png')}} numa GSP ou
 * {@code assetPath(src: ...)} dentro de outra TagLib, sem o prefixo {@code asset:}.
 */
class AssetMethodTagLib implements TagLibApi {

    static returnObjectForTags = ['assetPath', 'assetPathExists']

    def assetPath = { Map attrs ->
        renderContext.tags.namespace('asset').invokeMethod('assetPath', attrs)
    }

    def assetPathExists = { Map attrs ->
        renderContext.tags.namespace('asset').invokeMethod('assetPathExists', attrs)
    }
}
