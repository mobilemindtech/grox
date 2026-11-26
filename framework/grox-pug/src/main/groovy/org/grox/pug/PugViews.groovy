package org.grox.pug

import de.neuland.pug4j.Pug4J
import de.neuland.pug4j.PugEngine
import de.neuland.pug4j.RenderContext
import de.neuland.pug4j.filter.Filter
import de.neuland.pug4j.model.PugModel
import de.neuland.pug4j.template.PugTemplate
import org.grox.view.ViewEngine
import org.grox.view.gsp.GspRenderContext
import org.grox.view.gsp.ViewLocator

/**
 * Views {@code .pug} (pug4j): expressões em Groovy ({@link GroovyExpressionHandler}) e TagLibs como elementos
 * ({@link TagLibCompiler}). Com reload (desenvolvimento) cada renderização relê o template e os que ele inclui;
 * sem reload, os templates e as expressões ficam em cache.
 */
class PugViews implements ViewEngine {

    final ViewLocator locator
    final PugEngine engine
    final boolean prettyPrint

    PugViews(ViewLocator locator, boolean reload, ClassLoader loader, Map<String, Filter> filters = [:], boolean prettyPrint = false) {
        this.locator = locator
        this.prettyPrint = prettyPrint
        def handler = new GroovyExpressionHandler(loader)
        engine = PugEngine.builder()
                .templateLoader(new ViewTemplateLoader(locator))
                .expressionHandler(handler)
                .caching(!reload)
                .filters(filters)
                .build()
    }

    String getExtension() { 'pug' }

    boolean exists(String path) {
        locator.find(path, 'pug') != null
    }

    void render(String path, Map model, GspRenderContext rc) {
        PugTemplate template = engine.getTemplate(path.startsWith('/') ? path : '/' + path)
        def context = RenderContext.builder().prettyPrint(prettyPrint).defaultMode(Pug4J.Mode.HTML).build()
        // como o PugEngine.render, mas com o TagLibCompiler (o pug4j instancia o Compiler dele direto)
        def pugModel = new PugModel(context.globalVariables)
        engine.filters.each { String name, Filter f -> pugModel.addFilter(name, f) }
        pugModel.putAll(model ?: [:])
        rc.pushView(path)
        try {
            new TagLibCompiler(template, context, engine, rc).compile(pugModel, rc.out)
        } finally {
            rc.popView()
        }
    }
}
