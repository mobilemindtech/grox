package org.grox.pug

import de.neuland.pug4j.filter.Filter
import groovy.util.logging.Slf4j
import org.grox.core.GroxModule
import org.grox.view.GspModule

/**
 * Views em Pug: {@code views/<caminho>.pug} atendem o {@code render view:} quando não há a {@code .gsp}, nos
 * controllers do Grails e nas rotas do {@code grox-dsl}. Com o {@code grox-markdown} no classpath, o filtro
 * {@code :markdown} usa o mesmo commonmark.
 */
@Slf4j
class PugModule extends GroxModule {

    PugViews views

    /** Depois do {@link GspModule} (300), que cria as views. */
    int getOrder() { 310 }

    @Override
    void configureWeb() {
        def gsp = runtime.module(GspModule)?.views
        if (gsp == null) {
            log.warn('grox-pug sem o módulo GSP: as views .pug não serão usadas')
            return
        }
        boolean reload = config.getProperty('grox.pug.reload', Boolean, config.getProperty('grox.gsp.reload', Boolean, runtime.development))
        boolean pretty = config.getProperty('grox.pug.prettyPrint', Boolean, false)
        views = new PugViews(gsp.engine.locator, reload, runtime.application.classLoader, filters(), pretty)
        gsp.register(views)
    }

    /** {@code :markdown} com o commonmark, se estiver no classpath (grox-markdown). */
    private Map<String, Filter> filters() {
        try {
            Class.forName('org.commonmark.parser.Parser', false, PugModule.classLoader)
            return [markdown: new MarkdownFilter()]
        } catch (ClassNotFoundException ignored) {
            return [:]
        }
    }
}
