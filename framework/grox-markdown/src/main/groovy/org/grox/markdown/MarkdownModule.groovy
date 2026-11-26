package org.grox.markdown

import org.grox.core.GroxModule
import org.grox.view.GspContributor
import org.grox.view.GspModule

/**
 * Views em Markdown ({@code views/<caminho>.md}, quando não há a {@code .gsp}) e a tag {@code <md:render>}.
 */
class MarkdownModule extends GroxModule implements GspContributor {

    MarkdownViews views

    /** Depois do {@link GspModule} (300) e do Pug (310). */
    int getOrder() { 320 }

    @Override
    List<Class<?>> getTagLibClasses() { [MarkdownTagLib] }

    @Override
    void configureWeb() {
        def gsp = runtime.module(GspModule)?.views
        if (gsp == null) return
        boolean reload = config.getProperty('grox.markdown.reload', Boolean, config.getProperty('grox.gsp.reload', Boolean, runtime.development))
        views = new MarkdownViews(gsp.engine.locator, gsp.engine, reload)
        gsp.register(views)
    }
}
