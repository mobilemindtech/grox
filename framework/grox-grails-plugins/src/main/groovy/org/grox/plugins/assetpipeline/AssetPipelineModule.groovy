package org.grox.plugins.assetpipeline

import org.grox.assets.AssetsModule
import org.grox.core.GroxModule

/** Beans do plugin asset-pipeline sobre o servidor de assets do Grox: {@code assetResourceLocator} e {@code grailsResourceLocator}. */
class AssetPipelineModule extends GroxModule implements org.grox.view.GspContributor {

    AssetResourceLocator locator

    int getOrder() { 605 }

    /** Tags {@code asset:} e {@code assetPath}/{@code assetPathExists} no namespace {@code g}. */
    List<Class<?>> getTagLibClasses() { [AssetTagLib, AssetMethodTagLib] }

    @Override
    void contributeBeans(Map<String, Object> beans) {
        if (runtime.module(AssetsModule) == null) return
        locator = new AssetResourceLocator(null)
        beans.assetResourceLocator = locator
        beans.grailsResourceLocator = locator   // findResourceForURI (assets e public/)
    }

    /** O servidor de assets nasce no {@code configureWeb} do {@link AssetsModule}, que roda antes. */
    @Override
    void configureWeb() {
        locator?.server = runtime.module(AssetsModule)?.server
        AssetTagLib.server = runtime.module(AssetsModule)?.server
    }
}
